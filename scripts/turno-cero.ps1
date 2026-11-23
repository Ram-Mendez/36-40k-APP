$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $repo 'compose.batch17.yaml'

docker compose -f $compose up -d --wait postgres
if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar PostgreSQL.' }

$database = docker compose -f $compose exec -T postgres psql -X -U lab_20261117 -d postgres -Atqc "SELECT 1 FROM pg_database WHERE datname='turno_cero'"
if ($LASTEXITCODE -ne 0) { throw 'No se pudo consultar PostgreSQL.' }
if ($database -ne '1') {
    docker compose -f $compose exec -T postgres createdb -U lab_20261117 turno_cero
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear turno_cero.' }
}

Write-Output 'Base turno_cero lista en 127.0.0.1:35690. Ejecuta Turno Cero y Shipping APP en IntelliJ.'
