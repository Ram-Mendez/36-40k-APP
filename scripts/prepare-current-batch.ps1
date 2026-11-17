param([ValidateSet('01','03','04','05')][string]$Ticket='01')
$ErrorActionPreference='Stop'
$base='http://localhost:38088/api/v1'
$root=Split-Path -Parent $PSScriptRoot
$stamp=[DateTime]::Now.ToString('MMddHHmmssfff')
$login=Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body '{"username":"admin","password":"DevPassword123!"}'
$headers=@{Authorization="Bearer $($login.accessToken)"}
function Call-App([string]$Method,[string]$Path,$Body=$null) {
    $options=@{Method=$Method;Uri="$base$Path";Headers=$headers}
    if($null -ne $Body){$options.ContentType='application/json';$options.Body=$Body | ConvertTo-Json -Depth 8 -Compress}
    Invoke-RestMethod @options
}
function New-Product([string]$Suffix,[decimal]$Price,[string]$Name='Batch B product') {
    Call-App POST '/products' @{sku="B2-$stamp-$Suffix";name=$Name;unitPrice=$Price}
}
function New-Buyer {
    Call-App POST '/customers' @{name="Batch B buyer $stamp";taxId="B2-$stamp";email='buyer@example.test';address='Madrid'}
}
function New-Sale($Buyer,$Lines,[switch]$Complete) {
    $order=Call-App POST '/orders' @{customerId=$Buyer.id}
    foreach($line in $Lines){$null=Call-App PUT "/orders/$($order.id)/lines/$($line.productId)" @{quantity=$line.quantity}}
    $order=Call-App POST "/orders/$($order.id)/confirm"
    if($Complete){$order=Call-App POST "/orders/$($order.id)/complete"}
    $order
}
$result=switch($Ticket) {
    '01' {
        $supplier=Call-App POST '/suppliers' @{name="Batch B supplier $stamp";taxId="B2-$stamp";email='supplier@example.test';leadTimeDays=1}
        $p=New-Product 'P' 10; $q=New-Product 'Q' 20
        $purchase=Call-App POST '/purchase-orders' @{supplierId=$supplier.id}
        foreach($product in @($p,$q)){$null=Call-App PUT "/purchase-orders/$($purchase.id)/lines/$($product.id)" @{quantity=10;unitCost=8}}
        $purchase=Call-App POST "/purchase-orders/$($purchase.id)/submit"
        $first=@{requestId=[guid]::NewGuid().ToString();deliveryReference="B2-$stamp";lines=@(@{productId=$p.id;quantity=2},@{productId=$q.id;quantity=3})}
        $retry=@{requestId=$first.requestId;deliveryReference=$first.deliveryReference;lines=@($first.lines[1],$first.lines[0])}
        [ordered]@{purchaseId=$purchase.id;productP=$p.id;productQ=$q.id;endpoint="POST $base/purchase-orders/$($purchase.id)/receipts";firstBody=$first;retryBody=$retry}
    }
    '03' {
        $buyer=New-Buyer; $p=New-Product 'P' 10; $q=New-Product 'Q' 20
        foreach($product in @($p,$q)){$null=Call-App POST "/inventory/$($product.id)/adjustments" @{quantity=20;reason='Batch B opening stock'}}
        $a=New-Sale $buyer @(@{productId=$p.id;quantity=1},@{productId=$q.id;quantity=1}) -Complete
        $b=New-Sale $buyer @(@{productId=$p.id;quantity=3}) -Complete
        [ordered]@{customerId=$buyer.id;customerName=$buyer.name;orderA=$a.id;orderB=$b.id;expectedOrders=2;expectedNetSales=60;expectedShippingCost=($a.shippingCost+$b.shippingCost);endpoint="GET $base/reports/sales?size=100"}
    }
    '04' {
        $buyer=New-Buyer; $p=New-Product 'P' 10
        $null=Call-App POST "/inventory/$($p.id)/adjustments" @{quantity=20;reason='Batch B opening stock'}
        $order=New-Sale $buyer @(@{productId=$p.id;quantity=2})
        [ordered]@{orderId=$order.id;productId=$p.id;status=$order.status;endpoint="POST $base/orders/$($order.id)/complete"}
    }
    '05' {
        for($i=1;$i -le 101;$i++){$null=New-Product "UI$i" 1 ('ZZ Batch B {0} {1:d3}' -f $stamp,$i)}
        $page=Call-App GET '/products?size=100'
        [ordered]@{created=101;totalElements=$page.totalElements;totalPages=$page.totalPages;endpoint="GET $base/products?size=100&page=1";frontend='http://localhost:4200'}
    }
}
$output=Join-Path $root 'target/current-batch'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$json=$result | ConvertTo-Json -Depth 8
[IO.File]::WriteAllText((Join-Path $output "$Ticket.json"),$json,[Text.UTF8Encoding]::new($false))
$envPath=Join-Path $root 'http/http-client.private.env.json'
$environment=@{current=@{}}
if(Test-Path $envPath){
    $previous=Get-Content $envPath -Raw | ConvertFrom-Json
    if($null -ne $previous.current){foreach($property in $previous.current.PSObject.Properties){$environment.current[$property.Name]=$property.Value}}
}
if($Ticket -eq '01') {
    $environment.current.purchaseId=$result.purchaseId
    $environment.current.productP=$result.productP
    $environment.current.productQ=$result.productQ
    $environment.current.receiptRequestId=$result.firstBody.requestId
    $environment.current.deliveryReference=$result.firstBody.deliveryReference
}
if($Ticket -eq '03'){$environment.current.reportCustomerId=$result.customerId}
if($Ticket -eq '04'){$environment.current.slowOrderId=$result.orderId}
[IO.File]::WriteAllText($envPath,($environment | ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
Write-Output $json
Write-Output "Datos guardados en $output\$Ticket.json"
