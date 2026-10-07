$ErrorActionPreference = 'Stop'
$numpadRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$numpadPrivate = Join-Path $numpadRoot '.private/android-signing'
$numpadKey = Join-Path $numpadPrivate 'remotenumpad-release.p12'
$numpadSecrets = Join-Path $numpadPrivate 'secrets.json'
if (Test-Path -LiteralPath $numpadKey) {
    if (-not (Test-Path -LiteralPath $numpadSecrets)) { throw 'Existing key has no password file; do not overwrite it.' }
    Write-Output 'Existing release key preserved.'
    exit 0
}
New-Item -ItemType Directory -Force $numpadPrivate | Out-Null
$numpadAcl = Get-Acl -LiteralPath $numpadPrivate
$numpadAcl.SetAccessRuleProtection($true, $false)
$numpadOwner = [Security.Principal.WindowsIdentity]::GetCurrent().User
$numpadRule = [Security.AccessControl.FileSystemAccessRule]::new($numpadOwner, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
$numpadAcl.SetAccessRule($numpadRule)
Set-Acl -LiteralPath $numpadPrivate -AclObject $numpadAcl
$numpadPassword = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:REMOTE_NUMPAD_STORE_PASS = $numpadPassword
try {
    $numpadJava = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:\Program Files\Android\Android Studio\jbr' }
    & (Join-Path $numpadJava 'bin/keytool.exe') -genkeypair -keystore $numpadKey -storetype PKCS12 -alias remotenumpad -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Remote NumPad Release' -storepass:env REMOTE_NUMPAD_STORE_PASS -keypass:env REMOTE_NUMPAD_STORE_PASS
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
    $numpadRecord = @{ storeFile=$numpadKey; storePassword=$numpadPassword; keyAlias='remotenumpad'; keyPassword=$numpadPassword }
    [IO.File]::WriteAllText($numpadSecrets, ($numpadRecord | ConvertTo-Json))
    Write-Output 'Release key created in the private signing directory. Password was not printed.'
} finally { Remove-Item Env:REMOTE_NUMPAD_STORE_PASS -ErrorAction SilentlyContinue }
