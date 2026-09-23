# Compila libmsbcg729.so (G.729) para as 4 ABIs e coloca em app/src/main/jniLibs.
#
#     .\native\build-g729.ps1
#
# Precisa de: Android NDK (C:\dev\Android\Sdk\ndk\<versao>), CMake e Ninja
# (os do Qt servem) e o AAR do linphone-sdk-android ja baixado pelo Gradle
# (qualquer build do app resolve isso). A versao do SDK e lida de
# gradle/libs.versions.toml e tem de ser a mesma dos headers em
# native/third_party/linphone-sdk-<versao>: a .so e ligada as bibliotecas do SDK
# que vao no APK, e headers de outra versao podem nao bater com elas.
#
# Refazer sempre que a versao do linphone-sdk-android mudar.

param(
    [string]$Ndk = "C:\dev\Android\Sdk\ndk\27.2.12479018",
    [string]$CMake = "C:\Qt\Tools\CMake_64\bin\cmake.exe",
    [string]$Ninja = "C:\Qt\Tools\Ninja\ninja.exe",
    [string[]]$Abis = @("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
)

$ErrorActionPreference = 'Stop'
$nativeDir = $PSScriptRoot
$androidDir = Split-Path -Parent $nativeDir

$toml = Get-Content (Join-Path $androidDir 'gradle\libs.versions.toml') -Raw
if ($toml -notmatch 'linphoneSdk\s*=\s*"([^"]+)"') { throw "linphoneSdk nao encontrado em libs.versions.toml" }
$sdkVersion = $Matches[1]
if (-not (Test-Path (Join-Path $nativeDir "third_party\linphone-sdk-$sdkVersion"))) {
    throw "Headers da versao $sdkVersion ausentes em native\third_party. Copie bctoolbox/ortp/mediastreamer2 include/ dessa versao."
}

$aar = Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\org.linphone\linphone-sdk-android\$sdkVersion" -Recurse -Filter *.aar -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $aar) { throw "AAR do linphone-sdk-android $sdkVersion nao encontrado no cache do Gradle. Rode um build do app antes." }

$work = Join-Path $androidDir "build\g729"
$jniRoot = Join-Path $work "sdk-jni"
Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $jniRoot | Out-Null
tar -xf $aar.FullName -C $jniRoot jni
Write-Host "linphone-sdk-android $sdkVersion  ($($aar.Name))" -ForegroundColor Cyan

$toolchain = Join-Path $Ndk "build\cmake\android.toolchain.cmake"
foreach ($abi in $Abis) {
    $buildDir = Join-Path $work $abi
    & $CMake -S $nativeDir -B $buildDir -G Ninja `
        "-DCMAKE_MAKE_PROGRAM=$Ninja" `
        "-DCMAKE_TOOLCHAIN_FILE=$toolchain" `
        "-DANDROID_ABI=$abi" `
        "-DANDROID_PLATFORM=android-28" `
        "-DANDROID_STL=none" `
        "-DCMAKE_BUILD_TYPE=Release" `
        "-DLINPHONE_JNI_DIR=$jniRoot\jni\$abi"
    if ($LASTEXITCODE -ne 0) { throw "cmake (configuracao) falhou para $abi" }
    & $CMake --build $buildDir
    if ($LASTEXITCODE -ne 0) { throw "cmake (build) falhou para $abi" }

    $dest = Join-Path $androidDir "app\src\main\jniLibs\$abi"
    New-Item -ItemType Directory -Force $dest | Out-Null
    Copy-Item (Join-Path $buildDir "libmsbcg729.so") $dest -Force
    Write-Host "$abi -> $dest\libmsbcg729.so" -ForegroundColor Green
}
