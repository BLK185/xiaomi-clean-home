$ErrorActionPreference = 'Stop'
function Get-XchPython {
    $root = Split-Path $PSScriptRoot -Parent
    $runtime = Join-Path $root '.cache/python'
    $exe = Join-Path $runtime 'python.exe'
    if (!(Test-Path -LiteralPath $exe)) {
        if (![Environment]::Is64BitOperatingSystem) { throw 'Windows 64 bits requis.' }
        New-Item -ItemType Directory -Force -Path $runtime | Out-Null
        $zip = Join-Path $root '.cache/python-3.12.10-embed-amd64.zip'
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        if (!(Test-Path -LiteralPath $zip)) {
            Invoke-WebRequest -UseBasicParsing 'https://www.python.org/ftp/python/3.12.10/python-3.12.10-embed-amd64.zip' -OutFile $zip
        }
        if ((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ne '4acbed6dd1c744b0376e3b1cf57ce906f9dc9e95e68824584c8099a63025a3c3') {
            throw 'Verification du runtime Python echouee. Supprimer le fichier ZIP du cache puis relancer.'
        }
        Expand-Archive -LiteralPath $zip -DestinationPath $runtime -Force
    }
    return $exe
}
