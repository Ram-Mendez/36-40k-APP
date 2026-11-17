param([ValidateSet('01','02','03','04','05','all')][string]$Ticket='all')
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$tests=@{
    '01'='CurrentBatchIntegrationTest#receiptRetryWithReorderedLinesKeepsTheSameReceiptAndStock'
    '02'='CurrentBatchIntegrationTest#logoutOnlyRevokesItsOwnLoginFamily'
    '03'='CurrentBatchIntegrationTest#salesReportCountsOrdersRatherThanLinesEvenWhenTotalsAreEqual'
    '04'='CurrentBatchTimeoutTest'
}
$failed=@()
Push-Location $root
try {
    if($Ticket -eq 'all') {
        & .\mvnw.cmd -B -ntp '-Dtest=CurrentBatchIntegrationTest,CurrentBatchTimeoutTest' test
        if($LASTEXITCODE -ne 0){$failed+='Java'}
    } elseif($Ticket -ne '05') {
        & .\mvnw.cmd -B -ntp "-Dtest=$($tests[$Ticket])" test
        if($LASTEXITCODE -ne 0){$failed+=$Ticket}
    }
    if($Ticket -in @('05','all')) {
        & node frontend/scripts/test-current-batch.mjs
        if($LASTEXITCODE -ne 0){$failed+='Angular'}
    }
} finally {Pop-Location}
if($failed.Count){Write-Host "Checks fallidos: $($failed -join ', ')";exit 1}
Write-Host 'Checks seleccionados correctos.'
exit 0
