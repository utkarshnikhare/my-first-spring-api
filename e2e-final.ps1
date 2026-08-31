$ErrorActionPreference = "SilentlyContinue"
$base = "http://localhost:8081"
$log = "C:\project\my-first-spring-api\e2e-final-results.txt"
"" > $log
function Step($n, $desc) { $msg = "STEP $n : $desc"; Write-Host $msg; $msg >> $log }
function Pass($msg) { $line = "  PASS: $msg"; Write-Host $line -ForegroundColor Green; $line >> $log }
function Fail($msg) { $line = "  FAIL: $msg"; Write-Host $line -ForegroundColor Red; $line >> $log }
function Check($cond, $okMsg, $failMsg) { if ($cond) { Pass($okMsg) } else { Fail($failMsg) } }
Step 1 "Empty marketplace"
$mk = Invoke-RestMethod "$base/api/marketplace" -TimeoutSec 8
Check ($mk.kitchens.Count -eq 0) "No kitchens on fresh DB" "Got $($mk.kitchens.Count)"
Step 2 "Seller OTP request"
$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$r1 = Invoke-WebRequest "$base/api/auth/otp/request" -Method POST -WebSession $s -ContentType "application/json" -Body '{"mobileNumber":"9876543210"}' -UseBasicParsing
$otp1 = ($r1.Content | ConvertFrom-Json).otp
Check ($otp1 -and $otp1.Length -eq 4) "Seller OTP: $otp1" "No OTP"
Step 3 "Seller OTP verify"
$r2 = Invoke-WebRequest "$base/api/auth/otp/verify" -Method POST -WebSession $s -ContentType "application/json" -Body ('{"mobileNumber":"9876543210","otpCode":"' + $otp1 + '","name":"Aarti Sharma"}') -UseBasicParsing
$me = $r2.Content | ConvertFrom-Json
Check ($me.authenticated -eq $true) "Seller authenticated" "Not authenticated"
Step 4 "Become seller"
$r3 = Invoke-WebRequest "$base/api/auth/become-seller" -Method POST -WebSession $s -ContentType "application/json" -Body '{}' -UseBasicParsing
$me2 = $r3.Content | ConvertFrom-Json
Check ($me2.role -eq "SELLER") "Role upgraded to SELLER" "Role: $($me2.role)"
Step 5 "Create kitchen"
$kb = '{"name":"aartiskitchen","displayName":"Aarti Kitchen","society":"Pride World City","building":"Tower A","shortDescription":"North Indian","description":"Authentic food","imageUrl":"food.jpg","whatsappLink":"https://wa.me/919876543210","instagramLink":"https://instagram.com/aartiskitchen","upiId":"aartiskitchen@upi"}'
$r4 = Invoke-WebRequest "$base/api/seller/kitchen" -Method POST -WebSession $s -ContentType "application/json" -Body $kb -UseBasicParsing
$kitchen = $r4.Content | ConvertFrom-Json
Check ($kitchen.id -gt 0) "Kitchen created id=$($kitchen.id)" "No kitchen id"
Step 6 "Create product"
$pb = '{"name":"Paneer Tikka Masala","description":"Creamy paneer","price":180,"priceUnit":"plate","availableToday":true,"maxQuantity":20}'
$r5 = Invoke-WebRequest "$base/api/seller/products?kitchenId=$($kitchen.id)" -Method POST -WebSession $s -ContentType "application/json" -Body $pb -UseBasicParsing
$prod = $r5.Content | ConvertFrom-Json
Check ($prod.id -gt 0) "Product created id=$($prod.id)" "No product id"
Step 7 "Create preorder product"
$pb2 = '{"name":"Special Thali","description":"Weekend special","price":250,"priceUnit":"plate","availableToday":false,"isPreorder":true,"availableDate":"2026-09-05","maxQuantity":10}'
$r6 = Invoke-WebRequest "$base/api/seller/products?kitchenId=$($kitchen.id)" -Method POST -WebSession $s -ContentType "application/json" -Body $pb2 -UseBasicParsing
$prod2 = $r6.Content | ConvertFrom-Json
Check ($prod2.id -gt 0) "Preorder product created id=$($prod2.id)" "No product id"
Step 8 "Marketplace shows data"
Start-Sleep 2
$mk2 = Invoke-RestMethod "$base/api/marketplace" -TimeoutSec 8
Check ($mk2.kitchens.Count -ge 1) "Kitchen visible" "No kitchens"
Step 9 "Buyer OTP request"
$b = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$r7 = Invoke-WebRequest "$base/api/auth/otp/request" -Method POST -WebSession $b -ContentType "application/json" -Body '{"mobileNumber":"9988776655"}' -UseBasicParsing
$otp2 = ($r7.Content | ConvertFrom-Json).otp
Check ($otp2 -and $otp2.Length -eq 4) "Buyer OTP: $otp2" "No OTP"
Step 10 "Buyer OTP verify"
$r8 = Invoke-WebRequest "$base/api/auth/otp/verify" -Method POST -WebSession $b -ContentType "application/json" -Body ('{"mobileNumber":"9988776655","otpCode":"' + $otp2 + '","name":"Rahul Verma","flatHouseNumber":"A-1204"}') -UseBasicParsing
$buyer = $r8.Content | ConvertFrom-Json
Check ($buyer.authenticated -eq $true) "Buyer authenticated" "Not authenticated"
Step 11 "Add item to draft"
$draftBody = '[{"productId":' + $prod.id + ',"quantity":2}]'
$r9 = Invoke-WebRequest "$base/api/buyer/orders/draft?kitchenId=$($kitchen.id)" -Method POST -WebSession $b -ContentType "application/json" -Body $draftBody -UseBasicParsing
$draft = $r9.Content | ConvertFrom-Json
Check ($draft.items.Count -eq 1) "Draft has 1 item" "Items: $($draft.items.Count)"
Step 12 "Place order"
$orderBody = '{"paymentStatus":"PENDING","buyerDetails":{"name":"Rahul Verma","mobileNumber":"9988776655","society":"Pride World City","building":"Tower A","flatHouseNumber":"A-1204"},"customInstructions":"Less spicy please"}'
$r10 = Invoke-WebRequest "$base/api/buyer/orders/place" -Method POST -WebSession $b -ContentType "application/json" -Body $orderBody -UseBasicParsing
$order = $r10.Content | ConvertFrom-Json
Check ($order.id -gt 0) "Order placed id=$($order.id)" "No order id"
Check ($order.totalAmount -eq 360) "Order total is 360" "Total: $($order.totalAmount)"
Step 13 "Mark payment"
$r11 = Invoke-WebRequest "$base/api/buyer/orders/$($order.id)/payment-status" -Method PATCH -WebSession $b -ContentType "application/json" -Body '{"paymentStatus":"PAID"}' -UseBasicParsing
$orderPaid = $r11.Content | ConvertFrom-Json
Check ($orderPaid.paymentStatus -eq "PAID") "Payment marked PAID" "Status: $($orderPaid.paymentStatus)"
Step 14 "Order in buyer history"
$r12 = Invoke-WebRequest "$base/api/buyer/orders/my" -Method GET -WebSession $b -UseBasicParsing
$ordersResp = $r12.Content | ConvertFrom-Json
$allOrders = @()
$ordersResp.PSObject.Properties | ForEach-Object { if ($_.Value -is [Array]) { $allOrders += $_.Value } }
Check ($allOrders.Count -ge 1) "Buyer has 1 order" "Orders: $($allOrders.Count)"
Step 15 "Seller sees order"
$r13 = Invoke-WebRequest "$base/api/seller/orders" -Method GET -WebSession $s -UseBasicParsing
$sellerOrders = $r13.Content | ConvertFrom-Json
Check ($sellerOrders.Count -ge 1) "Seller sees 1 order" "Seller orders: $($sellerOrders.Count)"
Step 16 "Seller advances status"
$r14 = Invoke-WebRequest "$base/api/seller/orders/$($order.id)/status" -Method PATCH -WebSession $s -ContentType "application/json" -Body '{"orderStatus":"CONFIRMED"}' -UseBasicParsing
$adv = $r14.Content | ConvertFrom-Json
Check ($adv.orderStatus -eq "CONFIRMED") "Order CONFIRMED" "Status: $($adv.orderStatus)"
Step 17 "Seller marks ready"
$r15 = Invoke-WebRequest "$base/api/seller/orders/$($order.id)/status" -Method PATCH -WebSession $s -ContentType "application/json" -Body '{"orderStatus":"READY"}' -UseBasicParsing
$adv2 = $r15.Content | ConvertFrom-Json
Check ($adv2.orderStatus -eq "READY") "Order READY" "Status: $($adv2.orderStatus)"
Step 18 "Seller marks delivered"
$r16 = Invoke-WebRequest "$base/api/seller/orders/$($order.id)/status" -Method PATCH -WebSession $s -ContentType "application/json" -Body '{"orderStatus":"DELIVERED"}' -UseBasicParsing
$adv3 = $r16.Content | ConvertFrom-Json
Check ($adv3.orderStatus -eq "DELIVERED") "Order DELIVERED" "Status: $($adv3.orderStatus)"
Step 19 "Buyer rates order"
$r17 = Invoke-WebRequest "$base/api/buyer/orders/$($order.id)/rate?rating=5" -Method POST -WebSession $b -UseBasicParsing
$rated = $r17.Content | ConvertFrom-Json
Check ($rated.orderStatus -eq "COMPLETED") "Order COMPLETED after rating" "Status: $($rated.orderStatus)"
Step 20 "Buyer updates profile"
$r18 = Invoke-WebRequest "$base/api/buyer/profile" -Method PUT -WebSession $b -ContentType "application/json" -Body '{"name":"Rahul V","society":"Pride World City","building":"Tower B","flatHouseNumber":"B-305"}' -UseBasicParsing
$profile = $r18.Content | ConvertFrom-Json
Check ($profile.flatHouseNumber -eq "B-305") "Profile flat updated" "Flat: $($profile.flatHouseNumber)"
Step 21 "Seller updates kitchen"
$r19 = Invoke-WebRequest "$base/api/seller/kitchen/$($kitchen.id)" -Method PUT -WebSession $s -ContentType "application/json" -Body '{"displayName":"Aarti Premium Kitchen","shortDescription":"Premium North Indian"}' -UseBasicParsing
$kitUpd = $r19.Content | ConvertFrom-Json
Check ($kitUpd.displayName -eq "Aarti Premium Kitchen") "Kitchen name updated" "Name: $($kitUpd.displayName)"
Step 22 "Seller edits product"
$r20 = Invoke-WebRequest "$base/api/seller/products/$($prod.id)" -Method PUT -WebSession $s -ContentType "application/json" -Body '{"name":"Paneer Tikka Masala Premium","price":200,"availableToday":true}' -UseBasicParsing
$prodUpd = $r20.Content | ConvertFrom-Json
Check ($prodUpd.price -eq 200) "Product price updated to 200" "Price: $($prodUpd.price)"
Step 23 "Seller deletes product"
$r21 = Invoke-WebRequest "$base/api/seller/products/$($prod2.id)" -Method DELETE -WebSession $s -UseBasicParsing
Check ($r21.StatusCode -eq 200) "Product deleted" "Status: $($r21.StatusCode)"
Step 24 "Search works"
$search = Invoke-RestMethod "$base/api/search?q=paneer" -TimeoutSec 8
Check ($search.products.Count -ge 1) "Search found products" "Products: $($search.products.Count)"
Step 25 "Kitchen detail page"
$kitchenName = $mk2.kitchens[0].name
$kd = Invoke-RestMethod "$base/api/kitchens/$kitchenName" -TimeoutSec 8
Check ($kd.kitchen.displayName -ne $null) "Kitchen detail loaded" "No kitchen"
Check ($kd.products.Count -ge 1) "Kitchen has products" "Products: $($kd.products.Count)"
Step 26 "Static assets serve"
$html = Invoke-WebRequest "$base/" -UseBasicParsing -TimeoutSec 5
$css = Invoke-WebRequest "$base/css/styles.css" -UseBasicParsing -TimeoutSec 5
$js = Invoke-WebRequest "$base/js/app.js" -UseBasicParsing -TimeoutSec 5
Check ($html.StatusCode -eq 200) "HTML 200" "HTML: $($html.StatusCode)"
Check ($css.StatusCode -eq 200) "CSS 200" "CSS: $($css.StatusCode)"
Check ($js.StatusCode -eq 200) "JS 200" "JS: $($js.StatusCode)"
Step 27 "Buyer logout"
$r22 = Invoke-WebRequest "$base/api/auth/logout" -Method POST -WebSession $b -UseBasicParsing
Check ($r22.StatusCode -eq 200) "Buyer logged out" "Status: $($r22.StatusCode)"
Step 28 "Seller logout"
$r23 = Invoke-WebRequest "$base/api/auth/logout" -Method POST -WebSession $s -UseBasicParsing
Check ($r23.StatusCode -eq 200) "Seller logged out" "Status: $($r23.StatusCode)"
Write-Host "`n=== TEST COMPLETE ===" -ForegroundColor Cyan
Check ($mk2.availableToday.Count -ge 1) "Available product visible" "No available products"
