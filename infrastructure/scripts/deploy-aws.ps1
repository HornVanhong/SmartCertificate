<#
.SYNOPSIS
    Smart Certificate Generator - AWS Terraform Deployment & ECR Publishing CLI
.DESCRIPTION
    Runs Terraform and AWS CLI within official Docker containers, requiring zero local CLI installations.
.PARAMETER Action
    The action to execute: plan, apply, destroy, init, build-images
.PARAMETER TmpVars
    Path to terraform.tfvars (defaults to infrastructure/aws/terraform/terraform.tfvars)
#>
param (
    [Parameter(Mandatory=$false, Position=0)]
    [ValidateSet("init", "plan", "apply", "destroy", "build-images", "status")]
    [string]$Action = "plan",

    [Parameter(Mandatory=$false)]
    [string]$Region = $(if ($env:AWS_REGION) { $env:AWS_REGION } else { "ap-southeast-2" }),

    [Parameter(Mandatory=$false)]
    [switch]$AutoApprove,

    [Parameter(Mandatory=$false)]
    [string[]]$Target = @()
)

$ErrorActionPreference = "Stop"
$TfDir = Resolve-Path "$PSScriptRoot\..\aws\terraform"
$TfVars = Join-Path $TfDir "terraform.tfvars"
$TfVarsExample = Join-Path $TfDir "terraform.tfvars.example"
$EnvAwsFile = Join-Path $PSScriptRoot "..\..\.env.aws"

# Load .env.aws if present
if (Test-Path $EnvAwsFile) {
    Get-Content $EnvAwsFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line.Split("=", 2)
            $k = $parts[0].Trim()
            $v = $parts[1].Trim().Trim('"').Trim("'")
            [System.Environment]::SetEnvironmentVariable($k, $v, "Process")
        }
    }
}

# 1. Check for terraform.tfvars
if (-not (Test-Path $TfVars)) {
    Write-Host "[WARN] 'terraform.tfvars' not found in $TfDir" -ForegroundColor Yellow
    Write-Host "[INFO] Creating from 'terraform.tfvars.example'..." -ForegroundColor Cyan
    Copy-Item $TfVarsExample $TfVars
    Write-Host "[SUCCESS] Created $TfVars. Please review your settings before running 'apply'." -ForegroundColor Green
}

# 2. Check AWS Credentials
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Smart Certificate Generator - AWS Deployment Assistant" -ForegroundColor Cyan
Write-Host "Action: $Action | Region: $Region" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Common docker volume mounts: only mount ~/.aws if explicit environment variables are not set
$AwsMount = @()
if (-not $env:AWS_ACCESS_KEY_ID -and (Test-Path "$HOME\.aws")) {
    $NormalizedAws = ($HOME -replace '\\', '/') + "/.aws"
    $AwsMount = @("-v", "${NormalizedAws}:/root/.aws:ro")
}

$AwsEnvArgs = @(
    "-e", "AWS_ACCESS_KEY_ID=$($env:AWS_ACCESS_KEY_ID)",
    "-e", "AWS_SECRET_ACCESS_KEY=$($env:AWS_SECRET_ACCESS_KEY)",
    "-e", "AWS_DEFAULT_REGION=$Region"
)
if ($env:AWS_SESSION_TOKEN) {
    $AwsEnvArgs += @("-e", "AWS_SESSION_TOKEN=$($env:AWS_SESSION_TOKEN)")
}

$rawIdentity = docker run --rm @AwsMount @AwsEnvArgs amazon/aws-cli:latest sts get-caller-identity
$ExitCode = $LASTEXITCODE

if ($ExitCode -ne 0) {
    Write-Host "`n[ERROR] Unable to authenticate with AWS:" -ForegroundColor Red
    Write-Host $rawIdentity -ForegroundColor Yellow
    exit 1
}

$IdentityJson = $rawIdentity | Out-String | ConvertFrom-Json
Write-Host "[SUCCESS] Authenticated as: $($IdentityJson.Arn)" -ForegroundColor Green
Write-Host "[INFO] AWS Account ID: $($IdentityJson.Account)" -ForegroundColor Green

# 3. Handle Docker Image Build & Push to ECR
if ($Action -eq "build-images") {
    $Registry = "$($IdentityJson.Account).dkr.ecr.$Region.amazonaws.com"
    Write-Host "`n[INFO] Logging in to Amazon ECR: $Registry..." -ForegroundColor Cyan

    $LoginPass = (docker run --rm @AwsMount @AwsEnvArgs amazon/aws-cli:latest ecr get-login-password --region $Region).Trim()

    $LoginPass | docker login --username AWS --password-stdin $Registry

    Write-Host "[INFO] Building & pushing Backend Docker image..." -ForegroundColor Cyan
    docker build -t "$Registry/smart-certificate-backend:latest" -f backend/Dockerfile backend/
    if ($LASTEXITCODE -ne 0) { throw "Backend Docker build failed with exit code $LASTEXITCODE" }
    docker push "$Registry/smart-certificate-backend:latest"
    if ($LASTEXITCODE -ne 0) { throw "Backend Docker push failed with exit code $LASTEXITCODE" }

    Write-Host "[INFO] Building & pushing Frontend Docker image..." -ForegroundColor Cyan
    docker build -t "$Registry/smart-certificate-frontend:latest" --build-arg NEXT_PUBLIC_API_BASE_URL=/api/v1 -f frontend/Dockerfile frontend/
    if ($LASTEXITCODE -ne 0) { throw "Frontend Docker build failed with exit code $LASTEXITCODE" }
    docker push "$Registry/smart-certificate-frontend:latest"
    if ($LASTEXITCODE -ne 0) { throw "Frontend Docker push failed with exit code $LASTEXITCODE" }

    Write-Host "[SUCCESS] Images pushed successfully to Amazon ECR!" -ForegroundColor Green
    exit 0
}

# 4. Handle Terraform Commands
$TfEnv = @(
    "-e", "AWS_ACCESS_KEY_ID=$env:AWS_ACCESS_KEY_ID",
    "-e", "AWS_SECRET_ACCESS_KEY=$env:AWS_SECRET_ACCESS_KEY",
    "-e", "AWS_SESSION_TOKEN=$env:AWS_SESSION_TOKEN",
    "-e", "AWS_DEFAULT_REGION=$Region"
)

$TfDirMounted = ($TfDir.Path -replace '\\', '/')

$TargetArgs = @()
if ($Target) {
    $AllTargets = ($Target -join ",").Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ }
    foreach ($t in $AllTargets) {
        $TargetArgs += "-target=$t"
    }
}

if ($Action -eq "init") {
    Write-Host "`n[INFO] Running 'terraform init'..." -ForegroundColor Cyan
    $runArgs = @("run", "--rm") + $AwsMount + $TfEnv + @("-v", "${TfDirMounted}:/workspace", "-w", "/workspace", "hashicorp/terraform:latest", "init")
    & docker @runArgs
} elseif ($Action -eq "plan") {
    Write-Host "`n[INFO] Running 'terraform plan'..." -ForegroundColor Cyan
    $runArgs = @("run", "--rm") + $AwsMount + $TfEnv + @("-v", "${TfDirMounted}:/workspace", "-w", "/workspace", "hashicorp/terraform:latest", "plan") + $TargetArgs
    & docker @runArgs
} elseif ($Action -eq "apply") {
    Write-Host "`n[INFO] Running 'terraform apply'..." -ForegroundColor Cyan
    $ApproveArgs = if ($AutoApprove) { @("-auto-approve") } else { @() }
    $TtyArg = if ([Console]::IsInputRedirected -or $AutoApprove) { @() } else { @("-it") }
    $runArgs = @("run", "--rm") + $TtyArg + $AwsMount + $TfEnv + @("-v", "${TfDirMounted}:/workspace", "-w", "/workspace", "hashicorp/terraform:latest", "apply") + $TargetArgs + $ApproveArgs
    & docker @runArgs
} elseif ($Action -eq "destroy") {
    Write-Host "`n[WARN] Running 'terraform destroy'..." -ForegroundColor Red
    $ApproveArgs = if ($AutoApprove) { @("-auto-approve") } else { @() }
    $TtyArg = if ([Console]::IsInputRedirected -or $AutoApprove) { @() } else { @("-it") }
    $runArgs = @("run", "--rm") + $TtyArg + $AwsMount + $TfEnv + @("-v", "${TfDirMounted}:/workspace", "-w", "/workspace", "hashicorp/terraform:latest", "destroy") + $TargetArgs + $ApproveArgs
    & docker @runArgs
} elseif ($Action -eq "status") {
    Write-Host "`n[INFO] Running 'terraform output'..." -ForegroundColor Cyan
    $runArgs = @("run", "--rm") + $AwsMount + $TfEnv + @("-v", "${TfDirMounted}:/workspace", "-w", "/workspace", "hashicorp/terraform:latest", "output")
    & docker @runArgs
}
