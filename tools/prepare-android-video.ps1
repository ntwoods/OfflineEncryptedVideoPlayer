param(
    [Parameter(Mandatory = $true)]
    [string]$InputFile,

    [string]$OutputFile = "app/src/main/assets/videos/Video.mp4"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) {
    throw "ffmpeg was not found in PATH. Install FFmpeg first and restart PowerShell."
}

if (-not (Get-Command ffprobe -ErrorAction SilentlyContinue)) {
    throw "ffprobe was not found in PATH. Install FFmpeg first and restart PowerShell."
}

$inputPath = (Resolve-Path $InputFile).Path
$outputPath = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $OutputFile))
$outputDirectory = Split-Path -Parent $outputPath

New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

if ($inputPath -eq $outputPath) {
    $tempPath = Join-Path $outputDirectory "Video.android.tmp.mp4"
} else {
    $tempPath = $outputPath
}

Write-Host "Creating Android-friendly playback master..."
Write-Host "Input : $inputPath"
Write-Host "Output: $outputPath"

# Target profile:
# - 1920x1080, 30 fps CFR
# - H.264 High Profile Level 4.0, 8-bit 4:2:0
# - bitrate capped to avoid decoder spikes on tablets
# - 2-second GOP for fast decoder recovery/seeking
# - AAC stereo at 48 kHz
# - faststart MP4 layout
& ffmpeg -hide_banner -y `
    -i $inputPath `
    -map "0:v:0" -map "0:a:0?" `
    -vf "scale=1920:1080:flags=lanczos,fps=30" `
    -c:v libx264 `
    -preset medium `
    -profile:v high `
    -level:v 4.0 `
    -pix_fmt yuv420p `
    -b:v 8M `
    -maxrate 10M `
    -bufsize 16M `
    -g 60 `
    -keyint_min 30 `
    -sc_threshold 40 `
    -fps_mode cfr `
    -c:a aac `
    -b:a 160k `
    -ar 48000 `
    -ac 2 `
    -movflags +faststart `
    $tempPath

if ($LASTEXITCODE -ne 0) {
    throw "FFmpeg failed. The original video was not replaced."
}

if ($inputPath -eq $outputPath) {
    Move-Item -Force $tempPath $outputPath
}

Write-Host ""
Write-Host "Finished. Final stream summary:"
& ffprobe -hide_banner -v error `
    -select_streams v:0 `
    -show_entries "stream=codec_name,profile,level,width,height,pix_fmt,r_frame_rate,avg_frame_rate,bit_rate" `
    -of default=noprint_wrappers=1 `
    $outputPath

Write-Host ""
Write-Host "Use this generated MP4 in the APK. Do not bundle the original 4K/28 Mbps source as the production playback file."
