$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)
if (!(Test-Path .env)) {
    $bytes = New-Object byte[] 24
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    $password = -join ($bytes | ForEach-Object { $_.ToString("x2") })
    (Get-Content .env.example -Raw).Replace("troque-por-uma-senha-local", $password) | Set-Content .env
    Write-Host "Configuração local criada em .env (não versionada)."
}
docker compose up -d --wait db
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& .\mvnw.cmd -B verify
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "Ambiente verificado. Para iniciar: ./scripts/iniciar.ps1"
