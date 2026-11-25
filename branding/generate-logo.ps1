# Generates branding/logo.png and branding/logo.ico from the flower logo used
# in app/src/main/res/drawable/banner.xml. Uses WPF (PresentationCore) so the
# original bezier paths render identically to the Android VectorDrawable.
#
# Must be run with an STA apartment:
#   powershell.exe -STA -NoProfile -File branding/generate-logo.ps1

Add-Type -AssemblyName PresentationCore, PresentationFramework, WindowsBase

if ([System.Threading.Thread]::CurrentThread.ApartmentState -ne 'STA') {
    throw 'generate-logo.ps1 must run on an STA thread. Invoke with: powershell.exe -STA -NoProfile -File ...'
}

# Petals: color + XAML/SVG path (both parsers accept this subset).
$petals = @(
    @{ Color = '#FFFA2921'; Data = 'M375.48,267.63c38.64,34.21,69.78,70.87,89.82,105.42c34.42-61.56,57.42-134.71,57.71-181.3c0-0.33,0-0.63,0-0.91c0-68.94-68.77-95.77-128.01-95.77s-128.01,26.83-128.01,95.77c0,0.94,0,2.2,0,3.72C300.01,209.24,339.15,235.47,375.48,267.63z' },
    @{ Color = '#FFED79B5'; Data = 'M164.7,455.63c24.15-26.87,61.2-55.99,103.01-80.61c44.48-26.18,88.97-44.47,128.02-52.84c-47.91-51.76-110.37-96.24-154.6-110.91c-0.31-0.1-0.6-0.19-0.86-0.28c-65.57-21.3-112.34,35.81-130.64,92.15c-18.3,56.34-14.04,130.04,51.53,151.34C162.05,454.77,163.25,455.16,164.7,455.63z' },
    @{ Color = '#FFFFB400'; Data = 'M681.07,302.19c-18.3-56.34-65.07-113.45-130.64-92.15c-0.9,0.29-2.1,0.68-3.54,1.15c-3.75,35.93-16.6,81.27-35.96,125.76c-20.59,47.32-45.84,88.27-72.51,118c69.18,13.72,145.86,12.98,190.26-1.14c0.31-0.1,0.6-0.2,0.86-0.28C695.11,432.22,699.37,358.52,681.07,302.19z' },
    @{ Color = '#FF1E83F7'; Data = 'M336.54,510.71c-11.15-50.39-14.8-98.36-10.7-138.08c-64.03,29.57-125.63,75.23-153.26,112.76c-0.19,0.26-0.37,0.51-0.53,0.73c-40.52,55.78-0.66,117.91,47.27,152.72c47.92,34.82,119.33,53.54,159.86-2.24c0.56-0.76,1.3-1.78,2.19-3.01C363.28,602.32,347.02,558.08,336.54,510.71z' },
    @{ Color = '#FF18C249'; Data = 'M617.57,482.52c-35.33,7.54-82.42,9.33-130.72,4.66c-51.37-4.96-98.11-16.32-134.63-32.5c8.33,70.03,32.73,142.73,59.88,180.6c0.19,0.26,0.37,0.51,0.53,0.73c40.52,55.78,111.93,37.06,159.86,2.24c47.92-34.82,87.79-96.95,47.27-152.72C619.2,484.77,618.46,483.75,617.57,482.52z' }
)

function New-Brush { param([string]$Argb) New-Object System.Windows.Media.SolidColorBrush ([System.Windows.Media.ColorConverter]::ConvertFromString($Argb)) }

function Render-Logo {
    param([int]$Size)

    $visual = New-Object System.Windows.Media.DrawingVisual
    $ctx = $visual.RenderOpen()

    $scale = $Size / 792.0
    $ctx.PushTransform((New-Object System.Windows.Media.ScaleTransform $scale, $scale))

    foreach ($p in $petals) {
        $geom  = [System.Windows.Media.Geometry]::Parse($p.Data)
        $brush = New-Brush $p.Color
        $ctx.DrawGeometry($brush, $null, $geom)
    }

    $black  = New-Brush '#FF0B0B0B'
    $white  = New-Brush '#FFFFFFFF'
    $screen = New-Brush '#FF111827'

    # Stand (behind bezel)
    $stand = [System.Windows.Media.Geometry]::Parse('M660,724 L712,724 L724,756 L648,756 Z')
    $ctx.DrawGeometry($white, $null, $stand)

    # TV badge: dark outline -> white bezel -> dark screen
    $ctx.DrawRectangle($black,  $null, (New-Object System.Windows.Rect 588, 586, 196, 142))
    $ctx.DrawRectangle($white,  $null, (New-Object System.Windows.Rect 594, 592, 184, 130))
    $ctx.DrawRectangle($screen, $null, (New-Object System.Windows.Rect 606, 604, 160, 106))

    # Play triangle inside the screen
    $play = [System.Windows.Media.Geometry]::Parse('M644,624 L644,690 L712,657 Z')
    $ctx.DrawGeometry($white, $null, $play)

    $ctx.Pop()
    $ctx.Close()

    $rtb = New-Object System.Windows.Media.Imaging.RenderTargetBitmap `
        $Size, $Size, 96, 96, ([System.Windows.Media.PixelFormats]::Pbgra32)
    $rtb.Render($visual)
    return $rtb
}

function Get-PngBytes {
    param($Bitmap)
    $encoder = New-Object System.Windows.Media.Imaging.PngBitmapEncoder
    $encoder.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($Bitmap))
    $ms = New-Object System.IO.MemoryStream
    $encoder.Save($ms)
    return $ms.ToArray()
}

function Save-Png {
    param($Bitmap, [string]$Path)
    [System.IO.File]::WriteAllBytes($Path, (Get-PngBytes $Bitmap))
}

function Save-Ico {
    param([int[]]$Sizes, [string]$Path)

    $payloads = New-Object System.Collections.Generic.List[byte[]]
    foreach ($sz in $Sizes) {
        $rtb = Render-Logo -Size $sz
        $payloads.Add((Get-PngBytes $rtb))
    }

    $fs = [System.IO.File]::Open($Path, [System.IO.FileMode]::Create)
    $bw = New-Object System.IO.BinaryWriter $fs
    try {
        $bw.Write([uint16]0)                 # reserved
        $bw.Write([uint16]1)                 # type: 1 = ICO
        $bw.Write([uint16]$Sizes.Length)

        $offset = 6 + 16 * $Sizes.Length
        for ($i = 0; $i -lt $Sizes.Length; $i++) {
            $sz = $Sizes[$i]
            $w = if ($sz -ge 256) { 0 } else { $sz }
            $h = if ($sz -ge 256) { 0 } else { $sz }
            $bw.Write([byte]$w)
            $bw.Write([byte]$h)
            $bw.Write([byte]0)               # palette
            $bw.Write([byte]0)               # reserved
            $bw.Write([uint16]1)             # planes
            $bw.Write([uint16]32)            # bit count
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

Save-Png -Bitmap (Render-Logo -Size 512) -Path $pngPath
Save-Ico -Sizes 16, 32, 48, 64, 128, 256 -Path $icoPath

Write-Host "Wrote $pngPath"
Write-Host "Wrote $icoPath"
