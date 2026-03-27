# 下载 Sherpa-ONNX 中文 WeNetSpeech KWS 资源与 Android JNI 库，供 OpenClaw 离线双引擎唤醒使用。
# 需要可访问 huggingface.co 与 github.com（或提前手动下载后放到环境变量指定路径）。
param(
  [string] $ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path,
  [string] $SherpaRelease = "v1.12.34",
  [string] $SherpaAndroidTar = "",
  [switch] $SkipJni
)

$ErrorActionPreference = "Stop"

$assetsDir = Join-Path $ProjectRoot "app\src\main\assets\sherpa-kws\sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01"
$jniRoot = Join-Path $ProjectRoot "app\src\main\jniLibs"

$hfBase = "https://huggingface.co/pkufool/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01/resolve/main"
$files = @(
  "encoder-epoch-12-avg-2-chunk-16-left-64.onnx",
  "decoder-epoch-12-avg-2-chunk-16-left-64.onnx",
  "joiner-epoch-12-avg-2-chunk-16-left-64.onnx",
  "tokens.txt",
  "keywords.txt"
)

New-Item -ItemType Directory -Path $assetsDir -Force | Out-Null
Write-Host "Fetching KWS model files into $assetsDir"
foreach ($f in $files) {
  $url = "$hfBase/$f"
  $dest = Join-Path $assetsDir $f
  Write-Host "  $f"
  Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing
}

if ($SkipJni) {
  Write-Host "SkipJni set; done."
  exit 0
}

if ($SherpaAndroidTar -eq "") {
  $tarName = "sherpa-onnx-$SherpaRelease-android.tar.bz2"
  $SherpaAndroidTar = Join-Path $env:TEMP $tarName
  $gh = "https://github.com/k2-fsa/sherpa-onnx/releases/download/$SherpaRelease/$tarName"
  Write-Host "Downloading $gh"
  Invoke-WebRequest -Uri $gh -OutFile $SherpaAndroidTar -UseBasicParsing
}

$extractDir = Join-Path $env:TEMP "sherpa-onnx-android-extract"
if (Test-Path $extractDir) {
  Remove-Item -Recurse -Force $extractDir
}
New-Item -ItemType Directory -Path $extractDir | Out-Null
Write-Host "Extracting $($SherpaAndroidTar) -> $extractDir"
tar -xjf $SherpaAndroidTar -C $extractDir

$abis = @("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
$found = @{}
foreach ($abi in $abis) {
  $found[$abi] = $false
}

function Get-NearestAbiParent([System.IO.FileInfo] $soFile) {
  $dir = $soFile.Directory
  while ($null -ne $dir) {
    if ($abis -contains $dir.Name) {
      return $dir.Name
    }
    $dir = $dir.Parent
  }
  return $null
}

Get-ChildItem -Path $extractDir -Recurse -Filter "*.so" | ForEach-Object {
  $abi = Get-NearestAbiParent $_
  if ($null -ne $abi) {
    $destDir = Join-Path $jniRoot $abi
    New-Item -ItemType Directory -Path $destDir -Force | Out-Null
    $destFile = Join-Path $destDir $_.Name
    Copy-Item $_.FullName $destFile -Force
    $found[$abi] = $true
    Write-Host ("Copied {0} -> {1}" -f $_.Name, $destDir)
  }
}

$missing = $found.Keys | Where-Object { -not $found[$_] }
if ($missing.Count -gt 0) {
  Write-Warning "以下 ABI 未从 tar 中复制到 jniLibs（请打开下列目录对照手拷 .so）: $($missing -join ', ')"
  Write-Warning "解压临时目录: $extractDir"
  Write-Warning "目标: $jniRoot\<abi>\ 需含 libsherpa-onnx-jni.so、libonnxruntime.so 等"
}

Write-Host "Done. Shrink JNI with ABI filters if you do not need x86 variants."
