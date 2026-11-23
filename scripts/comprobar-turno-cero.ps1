param(
    [ValidateRange(1, 10)][int]$Caso,
    [switch]$Traza,
    [string]$Url = 'http://127.0.0.1:38089'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$client = [System.Net.Http.HttpClient]::new()
$client.BaseAddress = [uri]$Url
$titles = @(
    'La ventana de carga', 'Tres cajas por debajo de cero', 'Los ceros de la etiqueta',
    'Ocho huecos para dos camiones', 'El precinto fantasma', 'La impresora en silencio',
    'El porte gratis', 'La radio sin indicativo', 'Las botas desaparecidas', 'El porton cerrado'
)

function Send-Request {
    param([string]$Path, [string]$Method = 'GET', [hashtable]$Body, [string]$RequestId,
          [switch]$ShowEvidence)

    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::new($Method), $Path)
    try {
        if ($null -ne $Body) {
            $json = $Body | ConvertTo-Json -Depth 5 -Compress
            $request.Content = [System.Net.Http.StringContent]::new(
                $json, [System.Text.Encoding]::UTF8, 'application/json')
        }
        if ($RequestId) { $request.Headers.Add('X-Request-ID', $RequestId) }
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        try {
            $raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            $status = [int]$response.StatusCode
            $correlation = if ($response.Headers.Contains('X-Request-ID')) {
                $response.Headers.GetValues('X-Request-ID') -join ', '
            }
            if ($ShowEvidence) {
                Write-Host "$Method $Path"
                if ($null -ne $Body) { Write-Host $json }
                Write-Host "HTTP $status   X-Request-ID $correlation"
                if ($raw) { Write-Host $raw }
            }
            $data = if ($raw) {
                try { ConvertFrom-Json -InputObject $raw }
                catch { $raw }
            }
            return [pscustomobject]@{ Status = $status; Data = $data; RequestId = $correlation }
        } finally {
            $response.Dispose()
        }
    } finally {
        $request.Dispose()
    }
}

function Invoke-Dispatch {
    param([string]$Path, [string]$Method = 'GET', [hashtable]$Body, [string]$RequestId)
    $result = Send-Request "/api/v1/dispatch$Path" $Method $Body $RequestId -ShowEvidence:($Traza -or $Caso -gt 0)
    if ($result.Status -in 401,403 -or $result.Status -ge 500) {
        throw "HTTP $($result.Status) en $Path. Busca $($result.RequestId) en el log."
    }
    return $result
}

function Test-Incident {
    param([int]$Number, [hashtable]$Run)

    switch ($Number) {
        1 {
            $answer = Invoke-Dispatch '/plan?openedAt=2026-11-23T22%3A14%3A00Z'
            return ($answer.Status -eq 200 -and ([datetimeoffset]$answer.Data.closesAt -eq
                [datetimeoffset]'2026-11-23T22:15:30Z'))
        }
        2 {
            $valid = Invoke-Dispatch '/scans' POST @{parcel = @{barcode = '732'; units = 3}}
            $invalid = Invoke-Dispatch '/scans' POST @{parcel = @{barcode = '732'; units = -3}}
            return ($valid.Status -eq 200 -and $valid.Data.units -eq 3 -and $invalid.Status -eq 400)
        }
        3 {
            $control = Invoke-Dispatch '/labels?barcode=732'
            $answer = Invoke-Dispatch '/labels?barcode=000417'
            return ($control.Status -eq 200 -and $control.Data.sku -ceq 'DRILL-18V' -and
                $answer.Status -eq 200 -and $answer.Data.sku -ceq 'GLOVE-100' -and
                $answer.Data.barcode -is [string] -and $answer.Data.barcode -ceq '000417')
        }
        4 {
            $spaces = foreach ($wave in 'ECHO-17', 'NORTH-02', 'ECHO-17', 'NORTH-02') {
                $answer = Invoke-Dispatch "/capacity?wave=$wave&dock=A"
                if ($answer.Status -eq 200) { $answer.Data.maxParcels }
            }
            return (($spaces -join ',') -eq '8,3,8,3')
        }
        5 {
            $attempt = [guid]::NewGuid().ToString()
            $answer = Invoke-Dispatch '/seals' POST @{attemptId = $attempt; intact = $false}
            $state = Invoke-Dispatch "/manifests/$attempt"
            return ($answer.Status -eq 422 -and $state.Status -eq 200 -and
                $state.Data.sealed -eq $false -and $state.Data.receipt -eq $false -and
                $state.Data.released -eq $false)
        }
        6 {
            $attempt = [guid]::NewGuid().ToString()
            $answer = Invoke-Dispatch '/seals' POST @{attemptId = $attempt; intact = $true}
            $state = Invoke-Dispatch "/manifests/$attempt"
            $ready = $answer.Status -eq 201 -and $state.Status -eq 200 -and
                $state.Data.sealed -eq $true -and $state.Data.receipt -eq $true -and
                $state.Data.released -eq $false
            if ($ready) { $Run.Attempt = $attempt }
            return $ready
        }
        7 {
            $answer = Invoke-Dispatch '/quote?orderId=1'
            return ($answer.Status -eq 200 -and $answer.Data.reference -ceq 'QUOTE-1-49.80' -and
                $answer.Data.cost -eq 7.5)
        }
        8 {
            $valid = $true
            foreach ($prefix in 'echo', 'norte') {
                $id = "$prefix-$([guid]::NewGuid())"
                $answer = Invoke-Dispatch '/radio' -RequestId $id
                $valid = $valid -and $answer.Status -eq 200 -and $answer.RequestId -ceq $id -and
                    $answer.Data.requestId -ceq $id -and $answer.Data.worker -clike 'dispatch-*'
            }
            return $valid
        }
        9 {
            $first = Invoke-Dispatch '/catalog?page=1'
            $last = Invoke-Dispatch '/catalog?page=4'
            return ($first.Status -eq 200 -and $last.Status -eq 200 -and $first.Data.totalPages -eq 4 -and
                ($first.Data.products.sku -join ',') -ceq 'BOOT-42,DRILL-18V' -and
                ($last.Data.products.sku -join ',') -ceq 'SCREW-M8,TAPE-50')
        }
        10 {
            $path = "/manifests/$([guid]::NewGuid())"
            $answer = Invoke-Dispatch "$path/release" POST
            $state = Invoke-Dispatch $path
            return ($answer.Status -eq 409 -and $answer.Data.status -eq 409 -and
                $answer.Data.code -ceq 'LOADING_BLOCKED' -and $state.Status -eq 200 -and
                $state.Data.released -eq $false)
        }
    }
}

$exitCode = 0
try {
    $login = Send-Request '/api/v1/auth/login' POST @{username = 'operations'; password = 'DevPassword123!'}
    if ($login.Status -ne 200 -or -not $login.Data.accessToken) {
        throw "No se pudo iniciar sesion en $Url (HTTP $($login.Status))."
    }
    $client.DefaultRequestHeaders.Authorization =
        [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $login.Data.accessToken)
    $run = @{}
    $passed = 0
    $numbers = if ($Caso) { $Caso } else { 1..10 }
    foreach ($number in $numbers) {
        try {
            if (Test-Incident $number $run) {
                $passed++
                $verdict = 'OK'
            } else {
                $verdict = 'FALLA'
                $exitCode = 1
            }
        } catch {
            $verdict = "SIN COMPROBAR - $($_.Exception.Message)"
            $exitCode = 1
        }
        Write-Host ('{0:00} {1} [{2}]' -f $number, $titles[$number - 1], $verdict)
    }
    if (-not $Caso) {
        if ($passed -eq 10) {
            $path = "/manifests/$($run.Attempt)"
            $release = Invoke-Dispatch "$path/release" POST
            $state = Invoke-Dispatch $path
            if ($release.Status -ne 200 -or $release.Data.released -ne $true -or
                $state.Status -ne 200 -or $state.Data.released -ne $true) {
                throw 'El backend no confirmo la salida del camion.'
            }
            Write-Host "10/10 - ECHO-17 ha salido. Manifiesto $($run.Attempt)"
        } else {
            Write-Host "$passed/10 - ECHO-17 sigue en el muelle."
        }
    }
} catch {
    Write-Host $_.Exception.Message
    $exitCode = 1
} finally {
    try {
        if ($client.DefaultRequestHeaders.Authorization) {
            $null = Send-Request '/api/v1/auth/logout' POST
        }
    } catch {
        Write-Warning 'No se pudo cerrar la sesion de la comprobacion.'
    } finally {
        $client.Dispose()
    }
}
exit $exitCode
