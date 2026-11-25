# Generates branding/logo.png and branding/logo.ico from the same logo design
# used in app/src/main/res/drawable/banner.xml.
# Run from the repo root: pwsh -File branding/generate-logo.ps1

Add-Type -AssemblyName System.Drawing

function New-Logo {
    param([int]$Size)

    $bmp = New-Object System.Drawing.Bitmap $Size, $Size
    $g   = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode     = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode   = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

    # Design canvas is 512x512; scale to $Size.
    $k = $Size / 512.0
    $rect = { param($x,$y,$w,$h) New-Object System.Drawing.RectangleF (([single]($x*$k)), ([single]($y*$k)), ([single]($w*$k)), ([single]($h*$k))) }
    $pt   = { param($x,$y) New-Object System.Drawing.PointF (([single]($x*$k)), ([single]($y*$k))) }

    $black   = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(0x0B, 0x0B, 0x0B))
    $white   = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
    $sky     = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(0x1F, 0x29, 0x37))
    $sun     = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(0xFF, 0xC1, 0x07))
    $brown   = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(0x8E, 0x6A, 0x4A))
    $orange  = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(0xFF, 0x66, 0x00))
    $tvDark  = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(0x11, 0x18, 0x27))

    # Dark background
    $g.FillRectangle($black, (& $rect 0 0 512 512))

    # White photo frame + night-sky interior
    $g.FillRectangle($white, (& $rect 40 40 432 432))
    $g.FillRectangle($sky,   (& $rect 56 56 400 400))

    # Sun
    $g.FillEllipse($sun, (& $rect 128 128 84 84))

    # Back mountain (brown)
    $backMtn = @((& $pt 56 456), (& $pt 180 200), (& $pt 324 456))
    $g.FillPolygon($brown, [System.Drawing.PointF[]]$backMtn)

    # Front mountain (orange)
    $frontMtn = @(
        (& $pt 56 456),  (& $pt 128 320), (& $pt 212 380),
        (& $pt 288 260), (& $pt 388 376), (& $pt 456 320), (& $pt 456 456)
    )
    $g.FillPolygon($orange, [System.Drawing.PointF[]]$frontMtn)

    # --- TV badge, bottom-right ---

    # Stand
    $stand = @((& $pt 380 476), (& $pt 428 476), (& $pt 440 500), (& $pt 368 500))
    $g.FillPolygon($white, [System.Drawing.PointF[]]$stand)

    # Dark outline for separation
    $g.FillRectangle($black,  (& $rect 328 338 158 134))
    # Bezel
    $g.FillRectangle($white,  (& $rect 332 342 150 126))
    # Screen
    $g.FillRectangle($tvDark, (& $rect 342 352 130 106))

    # Mini mountains in the TV
    $miniMtn = @(
        (& $pt 342 458), (& $pt 372 410), (& $pt 404 438),
        (& $pt 436 400), (& $pt 472 458)
    )
    $g.FillPolygon($orange, [System.Drawing.PointF[]]$miniMtn)

    # Mini sun
    $g.FillEllipse($sun, (& $rect 438 382 20 20))

    $g.Dispose()
    return $bmp
}

function Save-Png {
    param([System.Drawing.Bitmap]$Bmp, [string]$Path)
    $Bmp.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
}

function Save-Ico {
    param([System.Drawing.Bitmap[]]$Bmps, [string]$Path)
    $payloads = @()
    foreach ($b in $Bmps) {
        $ms = New-Object System.IO.MemoryStream
        $b.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
        $payloads += ,$ms.ToArray()
    }

    $fs = [System.IO.File]::Open($Path, [System.IO.FileMode]::Create)
    $bw = New-Object System.IO.BinaryWriter $fs
    try {
        $bw.Write([uint16]0)              # reserved
        $bw.Write([uint16]1)              # type: 1 = ICO
        $bw.Write([uint16]$Bmps.Length)   # count

        $offset = 6 + (16 * $Bmps.Length)
        for ($i = 0; $i -lt $Bmps.Length; $i++) {
            $dim = $Bmps[$i].Width
            $w = if ($dim -ge 256) { 0 } else { $dim }
            $h = if ($dim -ge 256) { 0 } else { $dim }
            $bw.Write([byte]$w)
            $bw.Write([byte]$h)
            $bw.Write([byte]0)            # palette
            $bw.Write([byte]0)            # reserved
            $bw.Write([uint16]1)          # planes
            $bw.Write([uint16]32)         # bit count
            $bw.Write([uint32]$payloads[$i].Length)
            $bw.Write([uint32]$offset)
            $offset += $payloads[$i].Length
        }
        foreach ($p in $payloads) { $bw.Write($p) }
    } finally {
        $bw.Dispose(); $fs.Dispose()
    }
}

$outDir  = $PSScriptRoot
$pngPath = Join-Path $outDir 'logo.png'
$icoPath = Join-Path $outDir 'logo.ico'

$primary = New-Logo -Size 512
Save-Png -Bmp $primary -Path $pngPath

$icoSizes = 16, 32, 48, 64, 128, 256
$bmps = foreach ($sz in $icoSizes) { New-Logo -Size $sz }
Save-Ico -Bmps $bmps -Path $icoPath

foreach ($b in $bmps) { $b.Dispose() }
$primary.Dispose()

Write-Host "Wrote $pngPath"
Write-Host "Wrote $icoPath"
