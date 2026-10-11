$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$vendor = Join-Path $PSScriptRoot 'vendor'
New-Item -ItemType Directory -Force -Path $vendor | Out-Null
$tmp = Join-Path $env:TEMP ('hdrg-ffmpeg-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
try {
  $zip = Join-Path $tmp 'ffmpeg.zip'
  Invoke-WebRequest -Uri 'https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip' -OutFile $zip
  Expand-Archive -LiteralPath $zip -DestinationPath (Join-Path $tmp 'extracted')
  foreach ($name in @('ffmpeg.exe', 'ffprobe.exe')) {
    $file = Get-ChildItem (Join-Path $tmp 'extracted') -Filter $name -Recurse -File | Select-Object -First 1
    if (!$file) { throw "$name missing in downloaded archive" }
    Copy-Item $file.FullName (Join-Path $vendor $name) -Force
  }
} finally {
  Remove-Item $tmp -Force -Recurse -ErrorAction SilentlyContinue
}
