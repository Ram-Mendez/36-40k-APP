param(
    [ValidateSet('01', '02', '03', '04', '05', 'all')]
    [string]$Ticket = '01'
)

$ErrorActionPreference = 'Stop'
$repoPath = Split-Path -Parent $PSScriptRoot
$checks = [ordered]@{
    '01' = @{ Test = 'QuoteServiceTest,QuoteHttpTest'; Pom = 'services/shipping-service/pom.xml' }
    '02' = @{ Test = 'Batch16CustomerValidationTest'; Pom = 'pom.xml' }
    '03' = @{ Test = 'Batch16ReportPermissionsTest'; Pom = 'pom.xml' }
    '04' = @{ Test = 'Batch16PaginationTest'; Pom = 'pom.xml' }
    '05' = @{ Test = 'Batch16TracePropagationTest,ShippingClientTest'; Pom = 'pom.xml' }
}
$selected = if ($Ticket -eq 'all') { @($checks.Keys) } else { @($Ticket) }
$failed = @()

Push-Location -LiteralPath $repoPath
try {
    foreach ($id in $selected) {
        Write-Host "Batch 16/11 - ticket $id" -ForegroundColor Cyan
        $check = $checks[$id]
        & .\mvnw.cmd -B -ntp -f $check.Pom "-Dtest=$($check.Test)" test
        if ($LASTEXITCODE -ne 0) { $failed += $id }
    }
} finally {
    Pop-Location
}

if ($failed.Count -gt 0) {
    Write-Host "Checks failed: $($failed -join ', '). See the Maven output above." -ForegroundColor Yellow
    exit 1
}
Write-Host 'All selected checks passed.' -ForegroundColor Green
exit 0
