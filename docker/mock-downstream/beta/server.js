const http = require('http');
const url = require('url');

const PORT = 8082;

const server = http.createServer((req, res) => {
    const parsedUrl = url.parse(req.url, true);
    const pathname = parsedUrl.pathname;
    const query = parsedUrl.query;

    console.log(`[Beta Chaos Service] Incoming ${req.method} ${pathname}`);

    // Chaos simulation: Delayed response (>1500ms to trigger gateway timeout)
    if (pathname.includes('/delay')) {
        const delayMs = parseInt(query.ms || '2000', 10);
        console.log(`Simulating latency delay of ${delayMs}ms...`);
        setTimeout(() => {
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({
                service: 'downstream-service-beta',
                status: 'DELAYED_RESPONSE_SUCCESS',
                delayMs: delayMs,
                timestamp: new Date().toISOString()
            }, null, 2));
        }, delayMs);
        return;
    }

    // Chaos simulation: 5xx error to trigger circuit breaker failure threshold
    if (pathname.includes('/error')) {
        const statusCode = parseInt(query.status || '500', 10);
        console.log(`Simulating downstream failure: HTTP ${statusCode}`);
        res.writeHead(statusCode, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            service: 'downstream-service-beta',
            status: 'ERROR',
            simulatedStatusCode: statusCode,
            message: 'Simulated microservice internal error',
            timestamp: new Date().toISOString()
        }, null, 2));
        return;
    }

    // Normal response
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
        service: 'downstream-service-beta',
        status: 'UP',
        timestamp: new Date().toISOString(),
        method: req.method,
        path: req.url,
        headersReceived: {
            'x-user-id': req.headers['x-user-id'] || null,
            'x-tenant-id': req.headers['x-tenant-id'] || null,
            'x-gateway-verified': req.headers['x-gateway-verified'] || null,
            'traceparent': req.headers['traceparent'] || null
        }
    }, null, 2));
});

server.listen(PORT, '0.0.0.0', () => {
    console.log(`Downstream Beta Chaos Service running on port ${PORT}`);
});
