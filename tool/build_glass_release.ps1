param([switch]$SkipDependencies, [string]$Revision = '')
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/glass_environment.ps1"
Set-Location $projectDir
if (-not (Test-Path -LiteralPath "$env:FLUTTER_ROOT/bin/flutter.bat")) {
    & rtk git clone --depth 1 --branch 3.47.6 https://github.com/flutter/flutter.git $env:FLUTTER_ROOT
    if ($LASTEXITCODE -ne 0) { throw 'Flutter SDK clone failed' }
}
. "$PSScriptRoot/glass_environment.ps1" -Prepare
if (-not $SkipDependencies) {
    $pubLog = Join-Path $glassBuildDir 'flutter-pub.log'
    & rtk proxy flutter pub get *> $pubLog
    if ($LASTEXITCODE -ne 0) {
        $desktopSymlinkFailure = Select-String -LiteralPath $pubLog -SimpleMatch 'Building with plugins requires symlink support.'
        if (-not $desktopSymlinkFailure -or -not (Test-Path -LiteralPath '.flutter-plugins-dependencies')) {
            Get-Content -LiteralPath $pubLog -Tail 20
            throw 'Flutter dependency/plugin resolution failed'
        }
        Write-Output 'Android plugin metadata generated; unavailable Windows desktop symlink step skipped.'
    }
    . "$PSScriptRoot/glass_environment.ps1" -PatchPackages
}

# pub get may stop at unavailable desktop symlinks before emitting the Android
# Java registrant. Native Gradle dependencies alone do not register runtime plugins.
. "$PSScriptRoot/generate_android_plugins.ps1"

$nativeHashes = @{
    'arm64-v8a' = '98df6410375cc7a4be7e6eff56f9ccd88fa52678973cc23bcf7e934ab8c8682d'
    'armeabi-v7a' = '75ba2199848cd6224817320ddfabb7d7ea48fb4cfb1721b0090043d5a8a38602'
    'x86_64' = '1caa6198de22808bd7f10fcff2923f5eb174cc42ef24569aeafb98c6597457a4'
}
$nativeDir = Join-Path $projectDir 'build/media_kit_libs_android_video/20260906'
New-Item -ItemType Directory -Force -Path $nativeDir | Out-Null
foreach ($abi in $nativeHashes.Keys) {
    $jar = Join-Path $nativeDir "default-$abi.jar"
    if (-not (Test-Path -LiteralPath $jar)) {
        & rtk proxy curl.exe --fail --location --retry 2 --output $jar "https://github.com/My-Responsitories/libmpv-android-video-build/releases/download/20260906/default-$abi.jar"
        if ($LASTEXITCODE -ne 0) { throw "Native dependency download failed: $abi" }
    }
    if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLower() -ne $nativeHashes[$abi]) {
        throw "Native dependency checksum mismatch: $abi"
    }
}

$keyFile = Join-Path $projectDir 'android/app/piliplus-glass-release.jks'
$propertiesFile = Join-Path $projectDir 'android/key.properties'
if (-not (Test-Path -LiteralPath $propertiesFile)) {
    if (Test-Path -LiteralPath $keyFile) { throw 'Existing release key has no configuration; preserve it.' }
    $env:GLASS_STOREPASS = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
    try {
        & rtk proxy "$env:JAVA_HOME/bin/keytool.exe" -genkeypair -noprompt -keystore $keyFile -storetype JKS -alias piliplus-glass -keyalg RSA -keysize 3072 -validity 10000 -storepass:env GLASS_STOREPASS -keypass:env GLASS_STOREPASS -dname 'CN=PiliPlus Liquid Glass, OU=Local build, O=PiliPlus, C=CN'
        if ($LASTEXITCODE -ne 0) { throw 'Release key generation failed' }
        @("storeFile=piliplus-glass-release.jks", "storePassword=$env:GLASS_STOREPASS", 'keyAlias=piliplus-glass', "keyPassword=$env:GLASS_STOREPASS") | Set-Content -LiteralPath $propertiesFile -Encoding utf8
    } finally { Remove-Item Env:GLASS_STOREPASS -ErrorAction SilentlyContinue }
}

$commit = (& rtk git rev-parse HEAD).Trim()
$versionBase = (Select-String -LiteralPath 'pubspec.yaml' -Pattern '^version: ([^+]+)').Matches.Groups[1].Value
$versionName = "$versionBase-glass-$($commit.Substring(0,9))"
if ($Revision) { $versionName += "-$Revision" }
$versionCode = [int]((& rtk git rev-list --count HEAD).Trim())
@{
    'pili.name' = $versionName; 'pili.code' = $versionCode;
    'pili.hash' = $commit; 'pili.time' = [DateTimeOffset]::Now.ToUnixTimeSeconds()
} | ConvertTo-Json | Set-Content -LiteralPath 'pili_release.json' -Encoding utf8
& rtk proxy flutter build apk --release --no-pub --no-tree-shake-icons "--build-name=$versionName" "--build-number=$versionCode" --dart-define-from-file=pili_release.json
if ($LASTEXITCODE -ne 0) { throw 'Release build failed' }

$archive = [IO.Compression.ZipFile]::OpenRead((Join-Path $projectDir 'build/app/outputs/flutter-apk/app-release.apk'))
try {
    $hasRuntimeRegistrant = $false
    foreach ($dex in $archive.Entries | Where-Object FullName -Match '^classes\d*\.dex$') {
        $stream = $dex.Open()
        $buffer = [IO.MemoryStream]::new()
        try {
            $stream.CopyTo($buffer)
            if ([Text.Encoding]::Latin1.GetString($buffer.ToArray()).Contains('Lio/flutter/plugins/GeneratedPluginRegistrant;')) {
                $hasRuntimeRegistrant = $true
                break
            }
        } finally { $stream.Dispose(); $buffer.Dispose() }
    }
    if (-not $hasRuntimeRegistrant) { throw 'APK is missing Android runtime plugin registration' }
} finally { $archive.Dispose() }

$distDir = Join-Path $projectDir 'dist'
New-Item -ItemType Directory -Force -Path $distDir | Out-Null
$apk = Join-Path $distDir "PiliPlus-liquid-glass-$versionName-universal-release.apk"
Copy-Item -LiteralPath 'build/app/outputs/flutter-apk/app-release.apk' -Destination $apk
Copy-Item -LiteralPath 'android/liquidglass/LICENSE' -Destination (Join-Path $distDir 'AndroidLiquidGlass-LICENSE.txt')
Copy-Item -LiteralPath 'android/liquidglass/AM-plus-plus-LICENSE' -Destination (Join-Path $distDir 'AM-plus-plus-LICENSE.txt')
Copy-Item -LiteralPath 'android/liquidglass/NOTICE.md' -Destination (Join-Path $distDir 'NOTICE-liquid-glass.md')
Copy-Item -LiteralPath 'tool/LIQUID_GLASS.md' -Destination (Join-Path $distDir 'README-liquid-glass.md')
& rtk proxy "$env:ANDROID_HOME/build-tools/37.0.0/apksigner.bat" verify --verbose --print-certs $apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
"$((Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLower())  $([IO.Path]::GetFileName($apk))" | Set-Content -LiteralPath "$apk.sha256" -Encoding utf8
Write-Output "Release APK: $apk"
