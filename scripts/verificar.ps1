$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)
& .\mvnw.cmd -B verify
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git diff --check
exit $LASTEXITCODE
