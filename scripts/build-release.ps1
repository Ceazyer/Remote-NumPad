$ErrorActionPreference = 'Stop'
$numpadRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$numpadConfig = Join-Path $numpadRoot '.private/android-signing/secrets.json'
if (-not (Test-Path -LiteralPath $numpadConfig)) { throw 'Missing private release signing config. See docs/release-signing.md.' }
$numpadSigning = Get-Content -LiteralPath $numpadConfig -Raw | ConvertFrom-Json
$env:REMOTE_NUMPAD_STORE_FILE = $numpadSigning.storeFile
$env:REMOTE_NUMPAD_STORE_PASS = $numpadSigning.storePassword
$env:REMOTE_NUMPAD_KEY_ALIAS = $numpadSigning.keyAlias
$env:REMOTE_NUMPAD_KEY_PASS = $numpadSigning.keyPassword
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$numpadDotnet = if (Test-Path (Join-Path $numpadRoot '.tools/dotnet8/dotnet.exe')) { Join-Path $numpadRoot '.tools/dotnet8/dotnet.exe' } else { 'dotnet' }
try {
    & (Join-Path $numpadRoot 'android/gradlew.bat') -p (Join-Path $numpadRoot 'android') assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Android Release build failed.' }
    & $numpadDotnet publish (Join-Path $numpadRoot 'RemoteNumPad.csproj') -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:IncludeAllContentForSelfExtract=true -o (Join-Path $numpadRoot 'publish/v1.3.0/windows')
    if ($LASTEXITCODE -ne 0) { throw 'Windows Release publish failed.' }
} finally {
    Remove-Item Env:REMOTE_NUMPAD_STORE_FILE,Env:REMOTE_NUMPAD_STORE_PASS,Env:REMOTE_NUMPAD_KEY_ALIAS,Env:REMOTE_NUMPAD_KEY_PASS -ErrorAction SilentlyContinue
}
