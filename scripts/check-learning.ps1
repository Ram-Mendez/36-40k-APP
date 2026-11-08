# Structural guard only: no builds, Docker calls, secrets, or database writes.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    $roadmapPath = 'docs/WAREHOUSE-ROADMAP.md'
    $roadmap = Get-Content -LiteralPath $roadmapPath -Raw -Encoding UTF8
    $lab = Get-Content -LiteralPath 'docs/LEARNING-LAB.md' -Raw -Encoding UTF8
    # Git normalizes CRLF using the path's attributes before calculating the blob ID.
    $roadmapBlob = git hash-object --path=$roadmapPath $roadmapPath
    if ($LASTEXITCODE -ne 0 -or $roadmapBlob -ne '91378aa907ccb90b26586aae65746783dbe97ae0') {
        throw 'The original 80-hour roadmap changed. Review the diff; do not silently update the baseline.'
    }
    $budget = [ordered]@{ J=18; T=18; D=18; M=12; I=6; S=4; A=4 }
    $originalTickets = [regex]::Matches($roadmap, '(?m)^\| ([JTDMISA]\d) \u00b7 (\d+) h \|')
    $cards = [regex]::Matches($lab, '(?ms)^## ([JTDMISA]\d) \u00b7 (\d+) h \u2014 [^\r\n]+\r?\n(.*?)(?=^## |\z)')
    if ($originalTickets.Count -ne 19 -or $cards.Count -ne 19) {
        throw 'Expected exactly 19 roadmap tickets and 19 learning cards.'
    }
    $seen = @{}
    $totals = @{}
    $references = @{}
    foreach ($card in $cards) {
        $id = $card.Groups[1].Value
        $hours = [int]$card.Groups[2].Value
        if ($seen.ContainsKey($id)) { throw "Duplicate card: $id" }
        $seen[$id] = $true
        $original = @($originalTickets | Where-Object { $_.Groups[1].Value -eq $id })
        if ($original.Count -ne 1 -or [int]$original[0].Groups[2].Value -ne $hours) {
            throw "Ticket or hours do not match the original roadmap: $id"
        }
        $block = $id.Substring(0, 1)
        if (-not $totals.ContainsKey($block)) { $totals[$block] = 0 }
        $totals[$block] += $hours
        $body = $card.Groups[3].Value
        foreach ($field in @('Objetivo', 'Previos', 'C\u00f3digo', 'Recorrido', 'Pr\u00e1ctica', 'Cierre', 'Transferencia')) {
            if ($body -notmatch ('(?m)^\*\*' + $field + ':\*\*[ \t]*\S')) {
                throw "Missing or empty $field in $id"
            }
        }
        $paths = [regex]::Matches($body, '`((?:src/|services/|infra/|scripts/|docs/|\.github/)[^\s`]+|Jenkinsfile|compose.yaml)`')
        if ($paths.Count -eq 0) { throw "No concrete source reference in $id" }
        foreach ($match in $paths) {
            $sourcePath = $match.Groups[1].Value
            if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
                throw "Source reference does not exist ($id): $sourcePath"
            }
            $references[$sourcePath] = $true
        }
    }
    foreach ($block in $budget.Keys) {
        if (-not $totals.ContainsKey($block) -or $totals[$block] -ne $budget[$block]) {
            throw "Changed hour allocation for block $block"
        }
    }
    $total = ($totals.Values | Measure-Object -Sum).Sum
    if ($total -ne 80) { throw "Expected 80 hours, got $total" }
    Write-Output "PASS: original roadmap intact; 19 cards; 80 hours; $($references.Count) source paths exist."
    Write-Output 'This checks document structure only. It does not execute application tests or assess competence.'
}
finally {
    Pop-Location
}

