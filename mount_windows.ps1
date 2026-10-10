<#
 ==============================================================================
 WireFM - 1-Click WebDAV Mount for Windows (PowerShell)
 Usage: powershell -ExecutionPolicy Bypass -File .\mount_windows.ps1 [IP] [PORT]
 ==============================================================================
#>

param(
    [string]$IP,
    [string]$Port = "8081",
    [string]$Drive = "Z"
)

Write-Host "==============================================" -ForegroundColor Magenta
Write-Host "  WireFM - Windows WebDAV Network Drive Mount  " -ForegroundColor Magenta
Write-Host "==============================================" -ForegroundColor Magenta

if (-not $IP) {
    $IP = Read-Host "Enter WireFM IP address (e.g. 192.168.1.100)"
}

if (-not $IP) {
    Write-Host "[!] IP address is required." -ForegroundColor Red
    exit 1
}

$webdavUrl = "http://${IP}:${Port}/"

# Ensure WebClient service is running
Write-Host "[*] Checking WebClient Windows Service..." -ForegroundColor Cyan
$svc = Get-Service -Name "WebClient" -ErrorAction SilentlyContinue
if ($svc -and $svc.Status -ne "Running") {
    Start-Service -Name "WebClient" -ErrorAction SilentlyContinue
}

# Remove existing drive mapping if present
$driveColon = "${Drive}:"
if (Test-Path $driveColon) {
    Write-Host "[*] Unmounting existing drive ${driveColon}..." -ForegroundColor Yellow
    net use $driveColon /delete /yes 2>$null | Out-Null
}

# Map network drive
Write-Host "[*] Mapping $webdavUrl to ${driveColon}..." -ForegroundColor Cyan
$mountResult = net use $driveColon $webdavUrl /persistent:no 2>&1

if ($LASTEXITCODE -eq 0) {
    Write-Host "[✓] Mounted successfully as ${driveColon}!" -ForegroundColor Green
    Start-Process explorer.exe -ArgumentList $driveColon
} else {
    Write-Host "[!] Mount error: $mountResult" -ForegroundColor Yellow
    Write-Host "[*] Launching WebDAV directly in default browser..." -ForegroundColor Cyan
    Start-Process $webdavUrl
}
