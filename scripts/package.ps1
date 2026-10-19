param([string]$Destination = '')
$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not $Destination) { $Destination = Join-Path $projectRoot 'dist\business-operations-backend-1.0.0.zip' }
$Destination = [System.IO.Path]::GetFullPath($Destination)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Destination) | Out-Null
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$stream = [System.IO.File]::Open($Destination, [System.IO.FileMode]::Create)
$archive = New-Object System.IO.Compression.ZipArchive($stream, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    $files = Get-ChildItem -LiteralPath $projectRoot -File -Force -Recurse | Where-Object {
        $relative = $_.FullName.Substring($projectRoot.Length + 1)
        $relative -notmatch '^(\.git|\.idea|target|dist|logs)[\\/]' -and
        $relative -ne '.env' -and $_.Extension -notin @('.zip','.log','.iml')
    }
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($projectRoot.Length + 1).Replace('\','/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName,
            ('business-operations-backend/' + $relative), [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $archive.Dispose(); $stream.Dispose() }
Get-Item -LiteralPath $Destination | Select-Object FullName,Length
Get-FileHash -LiteralPath $Destination -Algorithm SHA256 | Format-List Algorithm,Hash,Path
