# Renders Fire TV / Android TV assets from the Immich 5-petal flower + TV badge.
#
# Writes PNGs into app/src/main/res/ at the correct densities:
#   drawable-nodpi/banner.png       (1280 x 720, 16:9 — Leanback banner)
#   mipmap-mdpi/ic_launcher.png     (48 x 48)
#   mipmap-hdpi/ic_launcher.png     (72 x 72)
#   mipmap-xhdpi/ic_launcher.png    (96 x 96)
#   mipmap-xxhdpi/ic_launcher.png   (144 x 144)
#   mipmap-xxxhdpi/ic_launcher.png  (192 x 192)
#
# Must be run with an STA apartment (WPF requirement):
#   powershell.exe -STA -NoProfile -File branding/generate-banner.ps1

Add-Type -AssemblyName PresentationCore, PresentationFramework, WindowsBase

if ([System.Threading.Thread]::CurrentThread.ApartmentState -ne 'STA') {
    throw 'generate-banner.ps1 must run on an STA thread. Invoke with: powershell.exe -STA -NoProfile -File ...'
}

$petals = @(
    @{ Color = '#FFFA2921'; Data = 'M375.48,267.63c38.64,34.21,69.78,70.87,89.82,105.42c34.42-61.56,57.42-134.71,57.71-181.3c0-0.33,0-0.63,0-0.91c0-68.94-68.77-95.77-128.01-95.77s-128.01,26.83-128.01,95.77c0,0.94,0,2.2,0,3.72C300.01,209.24,339.15,235.47,375.48,267.63z' },
    @{ Color = '#FFED79B5'; Data = 'M164.7,455.63c24.15-26.87,61.2-55.99,103.01-80.61c44.48-26.18,88.97-44.47,128.02-52.84c-47.91-51.76-110.37-96.24-154.6-110.91c-0.31-0.1-0.6-0.19-0.86-0.28c-65.57-21.3-112.34,35.81-130.64,92.15c-18.3,56.34-14.04,130.04,51.53,151.34C162.05,454.77,163.25,455.16,164.7,455.63z' },
    @{ Color = '#FFFFB400'; Data = 'M681.07,302.19c-18.3-56.34-65.07-113.45-130.64-92.15c-0.9,0.29-2.1,0.68-3.54,1.15c-3.75,35.93-16.6,81.27-35.96,125.76c-20.59,47.32-45.84,88.27-72.51,118c69.18,13.72,145.86,12.98,190.26-1.14c0.31-0.1,0.6-0.2,0.86-0.28C695.11,432.22,699.37,358.52,681.07,302.19z' },
    @{ Color = '#FF1E83F7'; Data = 'M336.54,510.71c-11.15-50.39-14.8-98.36-10.7-138.08c-64.03,29.57-125.63,75.23-153.26,112.76c-0.19,0.26-0.37,0.51-0.53,0.73c-40.52,55.78-0.66,117.91,47.27,152.72c47.92,34.82,119.33,53.54,159.86-2.24c0.56-0.76,1.3-1.78,2.19-3.01C363.28,602.32,347.02,558.08,336.54,510.71z' },
    @{ Color = '#FF18C249'; Data = 'M617.57,482.52c-35.33,7.54-82.42,9.33-130.72,4.66c-51.37-4.96-98.11-16.32-134.63-32.5c8.33,70.03,32.73,142.73,59.88,180.6c0.19,0.26,0.37,0.51,0.53,0.73c40.52,55.78,111.93,37.06,159.86,2.24c47.92-34.82,87.79-96.95,47.27-152.72C619.2,484.77,618.46,483.75,617.57,482.52z' }
)

function New-Brush { param([string]$Argb) New-Object System.Windows.Media.SolidColorBrush ([System.Windows.Media.ColorConverter]::ConvertFromString($Argb)) }

function Draw-Flower {
    param($Ctx, [double]$CenterX, [double]$CenterY, [double]$FittedSize)

    # Original artwork lives in a 792x792 viewport. Scale so the full artwork fits
    # FittedSize square, centered on (CenterX, CenterY).
    $scale = $FittedSize / 792.0
    $tx = $CenterX - 396.0 * $scale
    $ty = $CenterY - 396.0 * $scale
    $Ctx.PushTransform((New-Object System.Windows.Media.TranslateTransform $tx, $ty))
    $Ctx.PushTransform((New-Object System.Windows.Media.ScaleTransform $scale, $scale))

    foreach ($p in $petals) {
        $geom  = [System.Windows.Media.Geometry]::Parse($p.Data)
        $brush = New-Brush $p.Color
        $Ctx.DrawGeometry($brush, $null, $geom)
    }

    $black  = New-Brush '#FF0B0B0B'
    $white  = New-Brush '#FFFFFFFF'
    $screen = New-Brush '#FF111827'

    $stand = [System.Windows.Media.Geometry]::Parse('M660,724 L712,724 L724,756 L648,756 Z')
    $Ctx.DrawGeometry($white, $null, $stand)
    $Ctx.DrawRectangle($black,  $null, (New-Object System.Windows.Rect 588, 586, 196, 142))
    $Ctx.DrawRectangle($white,  $null, (New-Object System.Windows.Rect 594, 592, 184, 130))
    $Ctx.DrawRectangle($screen, $null, (New-Object System.Windows.Rect 606, 604, 160, 106))
    $play = [System.Windows.Media.Geometry]::Parse('M644,624 L644,690 L712,657 Z')
    $Ctx.DrawGeometry($white, $null, $play)

    $Ctx.Pop()
    $Ctx.Pop()
}

function Draw-Text {
    param($Ctx, [string]$Text, [double]$X, [double]$Y, [double]$FontSize, [string]$Argb, [string]$Weight = 'SemiBold')

    $typeface = New-Object System.Windows.Media.Typeface(
        (New-Object System.Windows.Media.FontFamily 'Segoe UI'),
        [System.Windows.FontStyles]::Normal,
        [System.Windows.FontWeights]::$Weight,
        [System.Windows.FontStretches]::Normal
    )
    $ft = New-Object System.Windows.Media.FormattedText(
        $Text,
        [System.Globalization.CultureInfo]::InvariantCulture,
        [System.Windows.FlowDirection]::LeftToRight,
        $typeface,
        $FontSize,
        (New-Brush $Argb),
        1.0
    )
    $Ctx.DrawText($ft, (New-Object System.Windows.Point $X, $Y))
    return @{ Width = $ft.Width; Height = $ft.Height }
}

function Render-Banner {
    param([int]$Width = 1280, [int]$Height = 720)

    $visual = New-Object System.Windows.Media.DrawingVisual
    $ctx = $visual.RenderOpen()

    # Cream background matching the in-app Immich-style background.
    $ctx.DrawRectangle((New-Brush '#FFF6F6F4'), $null, (New-Object System.Windows.Rect 0, 0, $Width, $Height))

    # Flower mark on the left, vertically centered.
    $flowerSize = 480.0
    Draw-Flower -Ctx $ctx -CenterX 280.0 -CenterY ($Height / 2.0) -FittedSize $flowerSize

    # Wordmark: "immich" in Immich purple, "FIRE" subtitle below.
    Draw-Text -Ctx $ctx -Text 'immich' -X 560 -Y 260 -FontSize 180 -Argb '#FF4250AF' -Weight 'SemiBold' | Out-Null
    Draw-Text -Ctx $ctx -Text 'FIRE'   -X 572 -Y 460 -FontSize  96 -Argb '#FF6B6B6B' -Weight 'Light'    | Out-Null

    $ctx.Close()

    $rtb = New-Object System.Windows.Media.Imaging.RenderTargetBitmap `
        $Width, $Height, 96, 96, ([System.Windows.Media.PixelFormats]::Pbgra32)
    $rtb.Render($visual)
    return $rtb
}

function Render-Icon {
    param([int]$Size)

    $visual = New-Object System.Windows.Media.DrawingVisual
    $ctx = $visual.RenderOpen()

    # Cream rounded-square background with a thin purple border.
    $radius = $Size * 0.18
    $bgGeom = New-Object System.Windows.Media.RectangleGeometry (New-Object System.Windows.Rect 0, 0, $Size, $Size), $radius, $radius
    $ctx.DrawGeometry((New-Brush '#FFF6F6F4'), (New-Object System.Windows.Media.Pen (New-Brush '#FFACB1DC'), ($Size * 0.02)), $bgGeom)

    Draw-Flower -Ctx $ctx -CenterX ($Size / 2.0) -CenterY ($Size / 2.0) -FittedSize ($Size * 0.82)

    $ctx.Close()

    $rtb = New-Object System.Windows.Media.Imaging.RenderTargetBitmap `
        $Size, $Size, 96, 96, ([System.Windows.Media.PixelFormats]::Pbgra32)
    $rtb.Render($visual)
    return $rtb
}

function Save-Png {
    param($Bitmap, [string]$Path)
    $dir = Split-Path -Parent $Path
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    $encoder = New-Object System.Windows.Media.Imaging.PngBitmapEncoder
    $encoder.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($Bitmap))
    $fs = [System.IO.File]::Open($Path, [System.IO.FileMode]::Create)
    try { $encoder.Save($fs) } finally { $fs.Dispose() }
    Write-Host "Wrote $Path"
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$resRoot  = Join-Path $repoRoot 'app\src\main\res'

Save-Png -Bitmap (Render-Banner -Width 1280 -Height 720) -Path (Join-Path $resRoot 'drawable-nodpi\banner.png')

$iconSizes = @{
    'mipmap-mdpi'    = 48
    'mipmap-hdpi'    = 72
    'mipmap-xhdpi'   = 96
    'mipmap-xxhdpi'  = 144
    'mipmap-xxxhdpi' = 192
}
foreach ($entry in $iconSizes.GetEnumerator()) {
    Save-Png -Bitmap (Render-Icon -Size $entry.Value) -Path (Join-Path $resRoot "$($entry.Key)\ic_launcher.png")
}

Write-Host 'Done.'
