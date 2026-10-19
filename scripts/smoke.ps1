param([string]$BaseUrl = 'http://localhost:8086')
$ErrorActionPreference = 'Stop'

function Assert-Equal($Actual, $Expected, [string]$Message) {
    if ($Actual -ne $Expected) { throw "$Message - expected=$Expected actual=$Actual" }
}
function Invoke-Api([string]$Method, [string]$Path, $Body = $null, [string]$Token = '') {
    $headers = @{'X-Request-ID' = $script:requestId}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $parameters = @{Method=$Method; Uri="$BaseUrl$Path"; Headers=$headers; ContentType='application/json'}
    if ($null -ne $Body) { $parameters.Body = ConvertTo-Json -InputObject $Body -Depth 10 -Compress }
    Invoke-RestMethod @parameters
}
function Assert-HttpStatus([string]$Method, [string]$Path, $Body, [string]$Token, [int]$Expected) {
    try { Invoke-Api $Method $Path $Body $Token | Out-Null }
    catch {
        if ($null -ne $_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq $Expected) { return }
        throw
    }
    throw "Expected HTTP $Expected for $Method $Path"
}

$script:requestId = 'smoke-' + [Guid]::NewGuid().ToString('N').Substring(0,12)
$health = Invoke-Api GET '/actuator/health/readiness'
Assert-Equal $health.status 'UP' 'Readiness'
$openapi = Invoke-Api GET '/v3/api-docs'
if (-not $openapi.paths.'/api/v1/orders/{id}/confirm') { throw 'Missing OpenAPI order contract' }
$swagger = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/swagger-ui/index.html"
Assert-Equal $swagger.StatusCode 200 'Swagger'
Assert-HttpStatus GET '/api/v1/customers' $null '' 401
$session = Invoke-Api POST '/api/v1/auth/login' @{username='operations';password='DevPassword123!'}
$token = $session.accessToken
$readonly = Invoke-Api POST '/api/v1/auth/login' @{username='readonly';password='DevPassword123!'}
Assert-HttpStatus POST '/api/v1/orders' @{customerId=1} $readonly.accessToken 403

$customer = Invoke-Api POST '/api/v1/customers' @{name='Smoke customer';taxId=('SMK'+[Guid]::NewGuid().ToString('N').Substring(0,12));email='smoke@example.test';address='Madrid'} $token
$product = Invoke-Api POST '/api/v1/products' @{sku=('SMK-'+[Guid]::NewGuid().ToString('N').Substring(0,12));name='Smoke product';unitPrice=19.95} $token
Invoke-Api POST "/api/v1/inventory/$($product.id)/adjustments" @{quantity=10;reason=$script:requestId} $token | Out-Null
$order = Invoke-Api POST '/api/v1/orders' @{customerId=$customer.id} $token
$line = Invoke-Api PUT "/api/v1/orders/$($order.id)/lines/$($product.id)" @{quantity=3} $token
Assert-Equal $line.total 59.85 'Order amount'
$confirmed = Invoke-Api POST "/api/v1/orders/$($order.id)/confirm" $null $token
Assert-Equal $confirmed.status 'CONFIRMED' 'Confirmation'
$balance = (Invoke-Api GET "/api/v1/inventory?productId=$($product.id)" $null $token).content[0]
Assert-Equal $balance.reserved 3 'Reservation'
Assert-Equal $balance.available 7 'Available while reserved'
$completed = Invoke-Api POST "/api/v1/orders/$($order.id)/complete" $null $token
Assert-Equal $completed.status 'COMPLETED' 'Completion'
if (-not $completed.shippingReference) { throw 'Missing shipping quote' }
$balance = (Invoke-Api GET "/api/v1/inventory?productId=$($product.id)" $null $token).content[0]
Assert-Equal $balance.onHand 7 'Physical stock'
Assert-Equal $balance.reserved 0 'Released reservation'
Assert-HttpStatus POST "/api/v1/orders/$($order.id)/complete" $null $token 409

$cancelledOrder = Invoke-Api POST '/api/v1/orders' @{customerId=$customer.id} $token
Invoke-Api PUT "/api/v1/orders/$($cancelledOrder.id)/lines/$($product.id)" @{quantity=2} $token | Out-Null
Invoke-Api POST "/api/v1/orders/$($cancelledOrder.id)/confirm" $null $token | Out-Null
$cancelled = Invoke-Api POST "/api/v1/orders/$($cancelledOrder.id)/cancel" $null $token
Assert-Equal $cancelled.status 'CANCELLED' 'Cancellation'
$balance = (Invoke-Api GET "/api/v1/inventory?productId=$($product.id)" $null $token).content[0]
Assert-Equal $balance.onHand 7 'Cancellation preserves physical stock'
Assert-Equal $balance.reserved 0 'Cancellation releases reserved stock'
$movements = Invoke-Api GET "/api/v1/inventory/movements?productId=$($product.id)" $null $token
Assert-Equal $movements.totalElements 5 'ADJUSTMENT RESERVE SHIP RESERVE RELEASE ledger'

$rotated = Invoke-Api POST '/api/v1/auth/refresh' @{refreshToken=$session.refreshToken}
Assert-HttpStatus GET '/api/v1/products' $null $token 401
Invoke-Api GET '/api/v1/products' $null $rotated.accessToken | Out-Null
Invoke-Api POST '/api/v1/auth/logout' $null $rotated.accessToken | Out-Null
Assert-HttpStatus GET '/api/v1/products' $null $rotated.accessToken 401

$admin = Invoke-Api POST '/api/v1/auth/login' @{username='admin';password='DevPassword123!'}
$audit = Invoke-Api GET "/api/v1/audit?requestId=$script:requestId" $null $admin.accessToken
if ($audit.totalElements -lt 8) { throw 'Missing business audit records' }
Invoke-Api POST '/api/v1/auth/logout' $null $admin.accessToken | Out-Null
Invoke-Api POST '/api/v1/auth/logout' $null $readonly.accessToken | Out-Null
[pscustomobject]@{result='PASS';requestId=$script:requestId;completedOrderId=$order.id;cancelledOrderId=$cancelledOrder.id;productId=$product.id;onHand=$balance.onHand;reserved=$balance.reserved;movements=$movements.totalElements;health=$health.status;swagger=$swagger.StatusCode} | Format-List
