<#
.SYNOPSIS
    Smart Certificate Generator - Windows PowerShell PostgreSQL Restore Script
.PARAMETER BackupFile
    Path to binary custom dump file (.dump)
#>
param (
    [Parameter(Mandatory=$true)]
    [string]$BackupFile,
    [string]$DbHost = $(if ($env:DB_HOST) { $env:DB_HOST } else { "localhost" }),
    [string]$DbPort = $(if ($env:DB_PORT) { $env:DB_PORT } else { "5432" }),
    [string]$DbName = $(if ($env:DB_NAME) { $env:DB_NAME } else { "smart_certificate" }),
    [string]$DbUser = $(if ($env:DB_USER) { $env:DB_USER } else { "smart_cert_user" }),
    [string]$DbPass = $(if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "smart_cert_local_pass" })
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BackupFile)) {
    Write-Error "Backup file '$BackupFile' does not exist."
    exit 1
}

$ChecksumFile = "${BackupFile}.sha256"
if (Test-Path $ChecksumFile) {
    Write-Host "[INFO] Verifying SHA-256 Checksum..." -ForegroundColor Cyan
    $ExpectedHash = (Get-Content $ChecksumFile).Split(' ')[0].Trim()
    $ActualHash = (Get-FileHash -Path $BackupFile -Algorithm SHA256).Hash
    if ($ExpectedHash.ToUpper() -ne $ActualHash.ToUpper()) {
        Write-Error "Checksum mismatch! Expected: $ExpectedHash, Got: $ActualHash"
        exit 1
    }
    Write-Host "[SUCCESS] SHA-256 Checksum verified." -ForegroundColor Green
}

Write-Host "==========================================================" -ForegroundColor Red
Write-Host "WARNING: Restoring database '$DbName' on ${DbHost}:${DbPort}." -ForegroundColor Red
Write-Host "Current data will be overwritten." -ForegroundColor Red
Write-Host "==========================================================" -ForegroundColor Red

$Confirmation = Read-Host "Type 'RESTORE' to continue"
if ($Confirmation -ne "RESTORE") {
    Write-Host "[CANCELLED] Aborting restore." -ForegroundColor Yellow
    exit 0
}

$env:PGPASSWORD = $DbPass
try {
    if (Get-Command pg_restore -ErrorAction SilentlyContinue) {
        pg_restore -h $DbHost -p $DbPort -U $DbUser -d $DbName --clean --if-exists --no-owner --no-privileges -v $BackupFile
    } else {
        Write-Host "[INFO] pg_restore not in PATH. Running restore via PostgreSQL container..." -ForegroundColor Yellow
        docker cp $BackupFile smart-certificate-postgres:/tmp/restore.dump
        docker exec -e PGPASSWORD=$DbPass smart-certificate-postgres pg_restore -U $DbUser -d $DbName --clean --if-exists --no-owner --no-privileges /tmp/restore.dump
        docker exec smart-certificate-postgres rm -f /tmp/restore.dump
    }

    Write-Host "[SUCCESS] Restore finished. Verifying Flyway migration history..." -ForegroundColor Green
    docker exec -e PGPASSWORD=$DbPass smart-certificate-postgres psql -U $DbUser -d $DbName -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}
