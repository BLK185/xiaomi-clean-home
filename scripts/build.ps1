param([string]$AndroidSdk = $env:ANDROID_HOME)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (!$AndroidSdk) { throw 'Definir ANDROID_HOME avec le SDK Android 34.' }
$env:ANDROID_HOME = $AndroidSdk
$private = Join-Path $root '.private'
New-Item -ItemType Directory -Force -Path $private | Out-Null
$credentials = Join-Path $private 'signing.json'
if (!(Test-Path -LiteralPath $credentials)) {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    @{ password = [Convert]::ToBase64String($bytes) } | ConvertTo-Json | Set-Content -LiteralPath $credentials -Encoding utf8
    $rng.Dispose()
}
$env:TVHOME_KEYSTORE = Join-Path $private 'release.p12'
$env:TVHOME_STORE_PASSWORD = (Get-Content -Raw -LiteralPath $credentials | ConvertFrom-Json).password
$env:TVHOME_KEY_PASSWORD = $env:TVHOME_STORE_PASSWORD
$env:TVHOME_KEY_ALIAS = 'xiaomi-clean-home'
try {
    if (!(Test-Path -LiteralPath $env:TVHOME_KEYSTORE)) {
        & "$env:JAVA_HOME/bin/keytool.exe" -genkeypair -keystore $env:TVHOME_KEYSTORE -storetype PKCS12 -storepass:env TVHOME_STORE_PASSWORD -keypass:env TVHOME_KEY_PASSWORD -alias $env:TVHOME_KEY_ALIAS -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Xiaomi Clean Home, O=Community'
        if ($LASTEXITCODE -ne 0) { throw 'Creation de la cle echouee.' }
    }
    Push-Location "$root/launcher"
    try {
        & ./gradlew.bat --no-daemon --max-workers=1 '-Djava.net.preferIPv4Stack=true' testReleaseUnitTest lintRelease assembleRelease
        if ($LASTEXITCODE -ne 0) { throw 'Compilation ou tests en echec.' }
    } finally { Pop-Location }
    New-Item -ItemType Directory -Force -Path "$root/dist" | Out-Null
    $version = [regex]::Match((Get-Content -Raw "$root/launcher/app/build.gradle"), "versionName '([^']+)'").Groups[1].Value
    if (!$version) { throw 'Version Android illisible.' }
    $name = "xiaomi-clean-home-$version.apk"
    Copy-Item -LiteralPath "$root/launcher/app/build/outputs/apk/release/app-release.apk" -Destination "$root/dist/$name" -Force
    @{version=$version; apk=$name; sha256=(Get-FileHash -LiteralPath "$root/dist/$name" -Algorithm SHA256).Hash.ToLower(); application_id='io.github.blk185.xiaomicleanhome'} | ConvertTo-Json | Set-Content -LiteralPath "$root/release-manifest.json" -Encoding utf8
} finally {
    Remove-Item Env:TVHOME_STORE_PASSWORD, Env:TVHOME_KEY_PASSWORD -ErrorAction SilentlyContinue
}
