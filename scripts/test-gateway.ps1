# OpenMesh-Gateway Automated Validation Script (PowerShell)
param(
    [string]$GatewayUrl = "http://localhost:8080"
)

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "  OpenMesh-Gateway: Automated Traffic & Security Test   " -ForegroundColor Cyan
Write-Host "========================================================`n" -ForegroundColor Cyan

# 1. Health Check
Write-Host "[1/7] Testing Gateway Health..." -ForegroundColor Yellow
$health = Invoke-RestMethod -Uri "$GatewayUrl/actuator/health" -Method Get
Write-Host "Gateway Health Status: $($health.status)" -ForegroundColor Green

# 2. Mint RS256 JWT Token for Tenant
Write-Host "`n[2/7] Generating RS256 Signed JWT Token for Tenant 'tenant-acme'..." -ForegroundColor Yellow
$tokenBody = @{
    tenantId = "tenant-acme"
    userId = "alice-architect"
    roles = @("ROLE_USER", "ROLE_ADMIN")
    scopes = @("read", "write")
    tier = "PRO"
    expiresInMinutes = 60
} | ConvertTo-Json

$tokenRes = Invoke-RestMethod -Uri "$GatewayUrl/auth/token" -Method Post -Body $tokenBody -ContentType "application/json"
$jwt = $tokenRes.token
Write-Host "Generated JWT Token (first 40 chars): $($jwt.Substring(0, 40))..." -ForegroundColor Green

# 3. Authenticated Request & Downstream Identity Header Propagation
Write-Host "`n[3/7] Sending Authenticated Request to Downstream Alpha (/service-alpha/orders)..." -ForegroundColor Yellow
$headers = @{
    "Authorization" = "Bearer $jwt"
}
$alphaRes = Invoke-RestMethod -Uri "$GatewayUrl/service-alpha/orders" -Headers $headers -Method Get
Write-Host "Downstream Alpha Response:" -ForegroundColor Green
$alphaRes | ConvertTo-Json -Depth 5 | Write-Host

# 4. Anti-Spoofing Verification
Write-Host "`n[4/7] Testing Anti-Spoofing: Client attempts to inject forged identity headers..." -ForegroundColor Yellow
$spoofedHeaders = @{
    "Authorization" = "Bearer $jwt"
    "X-User-Id" = "hacker-impostor"
    "X-Tenant-Id" = "forged-tenant"
    "X-Gateway-Verified" = "fake-verification"
}
$spoofedRes = Invoke-RestMethod -Uri "$GatewayUrl/service-alpha/orders" -Headers $spoofedHeaders -Method Get
Write-Host "Downstream received X-User-Id: $($spoofedRes.headersReceived.'x-user-id') (Expected: alice-architect)" -ForegroundColor Green
Write-Host "Downstream received X-Tenant-Id: $($spoofedRes.headersReceived.'x-tenant-id') (Expected: tenant-acme)" -ForegroundColor Green
Write-Host "Downstream received X-Gateway-Verified: $($spoofedRes.headersReceived.'x-gateway-verified') (Expected: true)" -ForegroundColor Green

# 5. Token Bucket Rate Limiting Test (Burst exhaustion)
Write-Host "`n[5/7] Testing Token Bucket Rate Limiting (Firing burst requests)..." -ForegroundColor Yellow
$rateLimitDropped = $false
for ($i = 1; $i -le 25; $i++) {
    try {
        $resp = Invoke-WebRequest -Uri "$GatewayUrl/service-alpha/test" -Headers $headers -Method Get -SkipHttpErrorCheck
        if ($resp.StatusCode -eq 429) {
            Write-Host "Request #${i}: HTTP 429 Too Many Requests! Rate Limiter engaged successfully." -ForegroundColor Red
            Write-Host "Retry-After: $($resp.Headers['Retry-After']) seconds" -ForegroundColor DarkYellow
            Write-Host "X-RateLimit-Reset: $($resp.Headers['X-RateLimit-Reset'])" -ForegroundColor DarkYellow
            $rateLimitDropped = $true
            break
        } else {
            Write-Host "Request #${i}: HTTP $($resp.StatusCode) (Remaining: $($resp.Headers['X-RateLimit-Remaining']))" -ForegroundColor Gray
        }
    } catch {
        Write-Host "Request #${i}: $_" -ForegroundColor DarkRed
    }
}

# 6. Resilience4j Circuit Breaker & Timeout Fallbacks
Write-Host "`n[6/7] Testing Circuit Breaker & Timeout Fallback Handlers..." -ForegroundColor Yellow
$timeoutFallback = Invoke-RestMethod -Uri "$GatewayUrl/fallback/timeout" -Method Get -SkipHttpErrorCheck
Write-Host "Timeout Fallback Response (HTTP 504): $($timeoutFallback.message)" -ForegroundColor DarkYellow

$cbFallback = Invoke-RestMethod -Uri "$GatewayUrl/fallback/service-unavailable" -Method Get -SkipHttpErrorCheck
Write-Host "Service Unavailable Fallback Response (HTTP 503): $($cbFallback.message)" -ForegroundColor DarkYellow

# 7. Prometheus Metrics Exporter
Write-Host "`n[7/7] Checking Prometheus Metrics Exporter (/actuator/prometheus)..." -ForegroundColor Yellow
$metrics = Invoke-WebRequest -Uri "$GatewayUrl/actuator/prometheus" -Method Get
$openmeshMetrics = ($metrics.Content -split "`n") | Where-Object { $_ -match "openmesh_gateway" } | Select-Object -First 5
Write-Host "Sample Prometheus Metrics:" -ForegroundColor Green
$openmeshMetrics | ForEach-Object { Write-Host "  $_" -ForegroundColor Cyan }

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "  All OpenMesh-Gateway Automated Tests Completed!       " -ForegroundColor Cyan
Write-Host "========================================================`n" -ForegroundColor Cyan
