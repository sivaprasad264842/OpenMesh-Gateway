// OpenMesh-Gateway Control Plane JavaScript Logic

let currentToken = '';

document.addEventListener('DOMContentLoaded', () => {
    initTabs();
    checkHealth();
    loadRoutes();
    loadCircuitBreakers();

    // Event Listeners
    document.getElementById('refreshRoutesBtn').addEventListener('click', refreshRoutes);
    document.getElementById('reloadRoutesTableBtn').addEventListener('click', loadRoutes);
    document.getElementById('addRouteForm').addEventListener('submit', handleAddRoute);
    document.getElementById('generateTokenBtn').addEventListener('click', generateToken);
    document.getElementById('sendRequestBtn').addEventListener('click', sendTestRequest);
    document.getElementById('runBurstTestBtn').addEventListener('click', runBurstTest);

    // Initial Token generation
    generateToken();
});

// Tab Navigation
function initTabs() {
    const tabs = document.querySelectorAll('.tab-btn');
    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            tabs.forEach(t => t.classList.remove('active'));
            document.querySelectorAll('.tab-content').forEach(c => c.style.display = 'none');

            tab.classList.add('active');
            const targetId = tab.getAttribute('data-target');
            document.getElementById(targetId).style.display = 'block';
        });
    });
}

// Health Check
async function checkHealth() {
    try {
        const res = await fetch('/actuator/health');
        const data = await res.json();
        const indicator = document.getElementById('healthStatus');
        const dot = document.getElementById('healthDot');
        if (data.status === 'UP') {
            indicator.textContent = 'ONLINE (UP)';
            dot.style.backgroundColor = '#16a34a';
        } else {
            indicator.textContent = data.status || 'DEGRADED';
            dot.style.backgroundColor = '#dc2626';
        }
    } catch (e) {
        document.getElementById('healthStatus').textContent = 'OFFLINE';
        document.getElementById('healthDot').style.backgroundColor = '#dc2626';
    }
}

// Load Dynamic & Static Routes
async function loadRoutes() {
    const tbody = document.getElementById('routesTableBody');
    tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:#64748b; padding:20px;">Loading routes...</td></tr>';

    try {
        const res = await fetch('/admin/v1/routes');
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const routes = await res.json();

        if (routes.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:#64748b; padding:20px;">No dynamic routes registered. Bootstrap routes active via application.yml.</td></tr>';
            document.getElementById('statTotalRoutes').textContent = '3 (Static)';
            return;
        }

        document.getElementById('statTotalRoutes').textContent = routes.length;
        tbody.innerHTML = '';

        routes.forEach(route => {
            const tr = document.createElement('tr');

            const predicatesHtml = (route.predicates || []).map(p => `<span class="badge badge-blue" style="margin-right:4px;">${escapeHtml(p)}</span>`).join('');
            const filtersHtml = (route.filters || []).map(f => `<span class="badge badge-gray" style="margin-right:4px;">${escapeHtml(f)}</span>`).join('');
            
            let scopesHtml = '<span class="badge badge-gray">None</span>';
            if (route.metadata && route.metadata.requiredScopes) {
                const scopes = Array.isArray(route.metadata.requiredScopes) ? route.metadata.requiredScopes : [route.metadata.requiredScopes];
                scopesHtml = scopes.map(s => `<span class="badge badge-amber" style="margin-right:4px;">${escapeHtml(s)}</span>`).join('');
            }

            tr.innerHTML = `
                <td><strong>${escapeHtml(route.id)}</strong></td>
                <td><code>${escapeHtml(route.uri)}</code></td>
                <td>${predicatesHtml || '<span class="badge badge-gray">Default</span>'}</td>
                <td>${filtersHtml || '<span class="badge badge-gray">None</span>'}</td>
                <td>${scopesHtml}</td>
                <td>
                    <button class="btn btn-danger btn-sm" onclick="deleteRoute('${escapeHtml(route.id)}')">Delete</button>
                </td>
            `;
            tbody.appendChild(tr);
        });
    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="6" style="text-align:center; color:#b91c1c; padding:20px;">Failed to load routes: ${e.message}</td></tr>`;
    }
}

// Add New Dynamic Route
async function handleAddRoute(e) {
    e.preventDefault();
    const routeId = document.getElementById('newRouteId').value.trim();
    const targetUri = document.getElementById('newRouteUri').value.trim();
    const pathPredicate = document.getElementById('newRoutePath').value.trim();
    const filterConfig = document.getElementById('newRouteFilters').value.trim();
    const requiredScope = document.getElementById('newRouteScope').value.trim();

    const predicates = [];
    if (pathPredicate) {
        predicates.push(pathPredicate.startsWith('Path=') ? pathPredicate : `Path=${pathPredicate}`);
    }

    const filters = [];
    if (filterConfig) {
        filters.push(filterConfig);
    }

    const metadata = {};
    if (requiredScope) {
        metadata.requiredScopes = [requiredScope];
    }

    const payload = {
        id: routeId,
        uri: targetUri,
        predicates: predicates,
        filters: filters,
        order: 0,
        metadata: metadata
    };

    try {
        const res = await fetch('/admin/v1/routes', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            showAlert('routeAlertContainer', 'Route successfully registered and hot-reloaded!', 'success');
            document.getElementById('addRouteForm').reset();
            loadRoutes();
        } else {
            const err = await res.text();
            showAlert('routeAlertContainer', `Failed to add route: ${err}`, 'danger');
        }
    } catch (err) {
        showAlert('routeAlertContainer', `Network error: ${err.message}`, 'danger');
    }
}

// Delete Route
async function deleteRoute(routeId) {
    if (!confirm(`Are you sure you want to delete dynamic route '${routeId}'?`)) return;

    try {
        const res = await fetch(`/admin/v1/routes/${encodeURIComponent(routeId)}`, {
            method: 'DELETE'
        });

        if (res.ok || res.status === 204) {
            showAlert('routeAlertContainer', `Route '${routeId}' deleted successfully!`, 'success');
            loadRoutes();
        } else {
            showAlert('routeAlertContainer', `Failed to delete route '${routeId}'`, 'danger');
        }
    } catch (e) {
        showAlert('routeAlertContainer', `Error deleting route: ${e.message}`, 'danger');
    }
}

// Force Route Refresh
async function refreshRoutes() {
    try {
        const res = await fetch('/admin/v1/routes/refresh', { method: 'POST' });
        if (res.ok) {
            showAlert('routeAlertContainer', 'Gateway routes hot-reloaded across all reactive workers!', 'success');
            loadRoutes();
        }
    } catch (e) {
        showAlert('routeAlertContainer', `Refresh failed: ${e.message}`, 'danger');
    }
}

// Generate RS256 JWT Token
async function generateToken() {
    const tenantId = document.getElementById('tokenTenantId').value.trim();
    const userId = document.getElementById('tokenUserId').value.trim();
    const tier = document.getElementById('tokenTier').value;
    const role = document.getElementById('tokenRole').value;
    const scope = document.getElementById('tokenScope').value.trim();

    const payload = {
        tenantId: tenantId,
        userId: userId,
        roles: [role],
        scopes: scope ? scope.split(' ') : ['read'],
        tier: tier,
        expiresInMinutes: 60
    };

    try {
        const res = await fetch('/auth/token', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            const data = await res.json();
            currentToken = data.token;
            document.getElementById('generatedTokenPreview').value = currentToken;
            document.getElementById('statActiveTenant').textContent = tenantId;
            document.getElementById('statTenantTier').textContent = `${tier} (${data.roles.join(', ')})`;
        }
    } catch (e) {
        console.error('Token generation failed', e);
    }
}

// Send Test Request through Gateway
async function sendTestRequest() {
    const path = document.getElementById('reqPath').value.trim();
    const method = document.getElementById('reqMethod').value;
    const useAuth = document.getElementById('reqUseAuth').checked;
    const sendSpoofed = document.getElementById('reqSpoofHeaders').checked;

    const headers = {};
    if (useAuth && currentToken) {
        headers['Authorization'] = `Bearer ${currentToken}`;
    }

    if (sendSpoofed) {
        headers['X-User-Id'] = 'hacker-attacker';
        headers['X-Tenant-Id'] = 'evil-corp';
        headers['X-Gateway-Verified'] = 'false-spoof';
    }

    const startTime = performance.now();
    const statusBadge = document.getElementById('respStatusBadge');
    const latencyBadge = document.getElementById('respLatencyBadge');
    const headersBox = document.getElementById('respHeadersBox');
    const bodyBox = document.getElementById('respBodyBox');

    statusBadge.textContent = 'Sending...';
    statusBadge.className = 'badge badge-gray';
    headersBox.textContent = '';
    bodyBox.textContent = '';

    try {
        const res = await fetch(path, {
            method: method,
            headers: headers
        });

        const elapsed = Math.round(performance.now() - startTime);
        latencyBadge.textContent = `${elapsed} ms`;

        // Status styling
        statusBadge.textContent = `HTTP ${res.status} ${res.statusText}`;
        if (res.status === 200) {
            statusBadge.className = 'badge badge-green';
        } else if (res.status === 429) {
            statusBadge.className = 'badge badge-amber';
        } else {
            statusBadge.className = 'badge badge-red';
        }

        // Headers
        const headerEntries = [];
        for (const [key, value] of res.headers.entries()) {
            headerEntries.push(`${key}: ${value}`);
        }
        headersBox.textContent = headerEntries.join('\n') || 'None';

        // Body
        const text = await res.text();
        try {
            const parsed = JSON.parse(text);
            bodyBox.textContent = JSON.stringify(parsed, null, 2);
        } catch {
            bodyBox.textContent = text;
        }

    } catch (err) {
        statusBadge.textContent = 'Network Error';
        statusBadge.className = 'badge badge-red';
        bodyBox.textContent = err.message;
    }
}

// Run Burst Rate Limiting Test
async function runBurstTest() {
    const burstContainer = document.getElementById('burstResultsGrid');
    const path = document.getElementById('reqPath').value.trim();
    burstContainer.innerHTML = '<span style="color:#64748b;">Executing 15 rapid requests to test token bucket burst capacity...</span>';

    const headers = {};
    if (currentToken) {
        headers['Authorization'] = `Bearer ${currentToken}`;
    }

    burstContainer.innerHTML = '';
    let droppedCount = 0;
    let allowedCount = 0;

    for (let i = 1; i <= 15; i++) {
        try {
            const res = await fetch(path, { method: 'GET', headers: headers });
            const chip = document.createElement('span');
            chip.className = 'burst-chip';

            if (res.status === 200) {
                allowedCount++;
                chip.style.backgroundColor = '#dcfce7';
                chip.style.color = '#15803d';
                chip.textContent = `#${i}: 200 OK (Rem: ${res.headers.get('x-ratelimit-remaining') || '?'})`;
            } else if (res.status === 429) {
                droppedCount++;
                chip.style.backgroundColor = '#fee2e2';
                chip.style.color = '#b91c1c';
                chip.textContent = `#${i}: 429 Too Many Requests (Retry: ${res.headers.get('retry-after') || '?'}s)`;
            } else {
                chip.style.backgroundColor = '#f1f5f9';
                chip.style.color = '#334155';
                chip.textContent = `#${i}: ${res.status}`;
            }

            burstContainer.appendChild(chip);
        } catch (e) {
            console.error(e);
        }
    }
}

// Load Circuit Breaker States
async function loadCircuitBreakers() {
    try {
        const res = await fetch('/actuator/circuitbreakers');
        if (res.ok) {
            const data = await res.json();
            const container = document.getElementById('circuitBreakerContainer');
            if (data.circuitBreakers && Object.keys(data.circuitBreakers).length > 0) {
                container.innerHTML = '';
                for (const [name, info] of Object.entries(data.circuitBreakers)) {
                    const card = document.createElement('div');
                    card.className = 'stat-box';
                    const stateColor = (info.state === 'CLOSED') ? '#16a34a' : (info.state === 'HALF_OPEN' ? '#b45309' : '#dc2626');
                    card.innerHTML = `
                        <div class="stat-label">${escapeHtml(name)}</div>
                        <div class="stat-value" style="color:${stateColor}; font-size:18px;">${info.state || 'CLOSED'}</div>
                        <div class="stat-hint" style="color:#64748b;">Failure Rate: ${info.failureRate || '0%'}</div>
                    `;
                    container.appendChild(card);
                }
            }
        }
    } catch (e) {
        console.log('Circuit breaker actuator not exposed or empty');
    }
}

// Helper: Show Alert Message
function showAlert(containerId, message, type) {
    const container = document.getElementById(containerId);
    if (!container) return;
    container.innerHTML = `
        <div class="alert alert-${type}">
            ${escapeHtml(message)}
        </div>
    `;
    setTimeout(() => {
        container.innerHTML = '';
    }, 5000);
}

// Helper: Escape HTML
function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
