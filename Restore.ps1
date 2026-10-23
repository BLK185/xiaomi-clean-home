$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/scripts/bootstrap.ps1"
& (Get-XchPython) "$PSScriptRoot/installer/main.py" restore @args
exit $LASTEXITCODE
