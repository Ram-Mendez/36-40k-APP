param([ValidateSet('01','02','03','04','05','all')][string]$Ticket='01')
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$selected=if ($Ticket -eq 'all') { @('01','02','03','04','05') } else { @($Ticket) }
$failed=@()
Push-Location $root
try {
  foreach ($id in $selected) {
    switch ($id) {
      '01' { & .\mvnw.cmd -B -ntp -f services/shipping-service/pom.xml '-Dtest=Batch17QuoteHistoryTest' test }
      '02' { & .\mvnw.cmd -B -ntp '-Dtest=BusinessIntegrationTest#staleProductUpdateIsConflictAndPreservesNewPrice' test }
      '03' { & .\mvnw.cmd -B -ntp '-Dtest=SecurityIntegrationTest#tokenRotationRejectsOldAccessAndReplayRevokesSuccessor' test }
      '04' { & .\mvnw.cmd -B -ntp '-Dtest=Batch17RecoveryTest' test }
      '05' { & node frontend/scripts/test-batch17.mjs }
    }
    if ($LASTEXITCODE -ne 0) { $failed += $id }
  }
} finally { Pop-Location }
if ($failed.Count) { Write-Host "Failed checks: $($failed -join ', ')"; exit 1 }
Write-Host 'Selected checks passed.'
exit 0
