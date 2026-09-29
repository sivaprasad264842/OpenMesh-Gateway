#!/usr/bin/env bash
# OpenMesh-Gateway Automated Validation Script (Bash / cURL)

set -e

GATEWAY_URL=${1:-"http://localhost:8080"}

echo "========================================================"
echo "  OpenMesh-Gateway: Automated Traffic & Security Test   "
echo "========================================================"

echo -e "\n[1/7] Testing Gateway Health..."
curl -s "${GATEWAY_URL}/actuator/health" | jq .

echo -e "\n[2/7] Generating RS256 Signed JWT Token for Tenant 'tenant-acme'..."
TOKEN_JSON=$(curl -s -X POST "${GATEWAY_URL}/auth/token" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "tenant-acme",
    "userId": "alice-architect",
    "roles": ["ROLE_USER", "ROLE_ADMIN"],
    "scopes": ["read", "write"],
    "tier": "PRO"
  }')

JWT=$(echo "${TOKEN_JSON}" | jq -r .token)
echo "Generated Token: ${JWT:0:50}..."

echo -e "\n[3/7] Sending Authenticated Request to Downstream Alpha (/service-alpha/orders)..."
curl -s -H "Authorization: Bearer ${JWT}" "${GATEWAY_URL}/service-alpha/orders" | jq .

echo -e "\n[4/7] Testing Anti-Spoofing: Sending Forged Headers..."
curl -s -H "Authorization: Bearer ${JWT}" \
  -H "X-User-Id: hacker-impostor" \
  -H "X-Tenant-Id: forged-tenant" \
  -H "X-Gateway-Verified: fake" \
  "${GATEWAY_URL}/service-alpha/orders" | jq .headersReceived

echo -e "\n[5/7] Testing Rate Limiting (Burst Requests)..."
for i in {1..25}; do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${JWT}" "${GATEWAY_URL}/service-alpha/test")
  if [ "$STATUS" -eq 429 ]; then
    echo "Request #$i: HTTP 429 Too Many Requests (Rate limit triggered successfully!)"
    break
  else
    echo "Request #$i: HTTP $STATUS"
  fi
done

echo -e "\n[6/7] Testing Circuit Breaker & Timeout Fallback Handlers..."
curl -s "${GATEWAY_URL}/fallback/timeout" | jq .
curl -s "${GATEWAY_URL}/fallback/service-unavailable" | jq .

echo -e "\n[7/7] Checking Prometheus Metrics Exporter..."
curl -s "${GATEWAY_URL}/actuator/prometheus" | grep "openmesh_gateway" | head -n 5

echo -e "\n========================================================"
echo "  All OpenMesh-Gateway Automated Tests Completed!       "
echo "========================================================"
