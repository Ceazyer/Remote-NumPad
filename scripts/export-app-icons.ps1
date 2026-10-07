param([string]$SourceImage = (Join-Path $PSScriptRoot '../assets/app-icon-light/source.png'))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$assetRoot = Join-Path $projectRoot 'assets/app-icon-light'
$resourceRoot = Join-Path $projectRoot 'android/app/src/main/res'
New-Item -ItemType Directory -Force $assetRoot | Out-Null
$source = [Drawing.Image]::FromFile([IO.Path]::GetFullPath($SourceImage))
if ($source.Width -ne $source.Height) { $source.Dispose(); throw 'The launcher source must be square.' }

# Only resize and encode the generated artwork; do not redraw, mask or recolor it.
function Export-IconPng([int]$Side, [string]$Destination) {
    $bitmap = [Drawing.Bitmap]::new($Side, $Side, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    $attributes = [Drawing.Imaging.ImageAttributes]::new()
    $memory = [IO.MemoryStream]::new()
    try {
        $graphics.CompositingQuality = [Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $attributes.SetWrapMode([Drawing.Drawing2D.WrapMode]::TileFlipXY)
        $graphics.DrawImage($source, [Drawing.Rectangle]::new(0, 0, $Side, $Side), 0, 0,
            $source.Width, $source.Height, [Drawing.GraphicsUnit]::Pixel, $attributes)
        $bitmap.Save($memory, [Drawing.Imaging.ImageFormat]::Png)
        $bytes = $memory.ToArray()
        if ($Destination) {
            New-Item -ItemType Directory -Force ([IO.Path]::GetDirectoryName($Destination)) | Out-Null
            [IO.File]::WriteAllBytes($Destination, $bytes)
        }
        return ,$bytes
    } finally { $memory.Dispose(); $attributes.Dispose(); $graphics.Dispose(); $bitmap.Dispose() }
}

try {
    $sizes = @(16, 20, 24, 32, 48, 64, 128, 256)
    $frames = [Collections.Generic.List[byte[]]]::new()
    foreach ($size in $sizes) { $frames.Add((Export-IconPng $size (Join-Path $assetRoot "preview-$size.png"))) }
    $file = [IO.File]::Create((Join-Path $assetRoot 'remote-numpad-light.ico'))
    $writer = [IO.BinaryWriter]::new($file)
    try {
        $writer.Write([uint16]0); $writer.Write([uint16]1); $writer.Write([uint16]$sizes.Count)
        $offset = 6 + 16 * $sizes.Count
        for ($index = 0; $index -lt $sizes.Count; $index++) {
            $sideByte = if ($sizes[$index] -eq 256) { 0 } else { $sizes[$index] }
            $writer.Write([byte]$sideByte); $writer.Write([byte]$sideByte)
            $writer.Write([byte]0); $writer.Write([byte]0)
            $writer.Write([uint16]1); $writer.Write([uint16]32)
            $writer.Write([uint32]$frames[$index].Length); $writer.Write([uint32]$offset)
            $offset += $frames[$index].Length
        }
        foreach ($frame in $frames) { $writer.Write($frame) }
    } finally { $writer.Dispose(); $file.Dispose() }

    $densities = @{'mdpi'=48; 'hdpi'=72; 'xhdpi'=96; 'xxhdpi'=144; 'xxxhdpi'=192}
    foreach ($density in $densities.Keys) {
        $null = Export-IconPng $densities[$density] (Join-Path $resourceRoot "mipmap-$density/ic_launcher.png")
    }
    $null = Export-IconPng 512 (Join-Path $resourceRoot 'drawable-nodpi/app_icon_light.png')
    Write-Output 'Exported 8 Windows ICO frames, 5 Android density icons and the adaptive bitmap.'
} finally { $source.Dispose() }
