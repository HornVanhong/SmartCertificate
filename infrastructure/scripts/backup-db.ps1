<#
.SYNOPSIS
    Smart Certificate Generator - Windows PowerShell PostgreSQL Backup Script
.PARAMETER BackupDir
    Directory where database dump and checksum are written
#>
param (
    [string]$BackupDir = ".\backups",
    [string]$DbHost = $(if ($env:DB_HOST) { $env:DB_HOST } else { "localhost" }),
    [string]$DbPort = $(if ($env:DB_PORT) { $env:DB_PORT } else { "5432" }),
    [string]$DbName = $(if ($env:DB_NAME) { $env:DB_NAME } else { "smart_certificate" }),
    [string]$DbUser = $(if ($env:DB_USER) { $env:DB_USER } else { "smart_cert_user" }),
    [string]$DbPass = $(if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "smart_cert_local_pass" })
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir | Out-Null
}

$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$BackupFile = Join-Path $BackupDir "${DbName}_backup_${Timestamp}.dump"
$ChecksumFile = "${BackupFile}.sha256"

Write-Host "[INFO] Starting database backup for '$DbName' on ${DbHost}:${DbPort}..." -ForegroundColor Cyan

# Execute pg_dump via docker container if local pg_dump is not in PATH
$env:PGPASSWORD = $DbPass
try {
    if (Get-Command pg_dump -ErrorAction SilentlyContinue) {
        pg_dump -h $DbHost -p $DbPort -U $DbUser -F c -b -v -f $BackupFile $DbName
    } else {
        Write-Host "[INFO] pg_dump not found in local PATH. Running backup via PostgreSQL container..." -ForegroundColor Yellow
        docker exec -e PGPASSWORD=$DbPass smart-certificate-postgres pg_dump -U $DbUser -F c -b -f /tmp/backup.dump $DbName
        docker cp smart-certificate-postgres:/tmp/backup.dump $BackupFile
        docker exec smart-certificate-postgres rm -f /tmp/backup.dump
    }

    $Hash = (Get-FileHash -Path $BackupFile -Algorithm SHA256).Hash
    "$Hash  $([System.IO.Path]::GetFileName($BackupFile))" | Out-File -FilePath $ChecksumFile -Encoding utf8

    Write-Host "[SUCCESS] Backup created at: $BackupFile" -ForegroundColor Green
    Write-Host "[INFO] SHA-256 Checksum: $Hash" -ForegroundColor Cyan
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}
