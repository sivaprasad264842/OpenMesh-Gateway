const http = require('http');

const PORT = 8081;

const server = http.createServer((req, res) => {
    let body = [];
    req.on('data', chunk => body.push(chunk));
    req.on('end', () => {
        const responseData = {
            service: 'downstream-service-alpha',
            status: 'UP',
            timestamp: new Date().toISOString(),
            method: req.method,
            path: req.url,
            headersReceived: {
                'x-user-id': req.headers['x-user-id'] || null,
                'x-tenant-id': req.headers['x-tenant-id'] || null,
                'x-user-roles': req.headers['x-user-roles'] || null,
                'x-gateway-verified': req.headers['x-gateway-verified'] || null,
                'x-gateway-timestamp': req.headers['x-gateway-timestamp'] || null,
                'traceparent': req.headers['traceparent'] || null
            }
        };

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(responseData, null, 2));
    });
});

server.listen(PORT, '0.0.0.0', () => {
    console.log(`Downstream Alpha Mock Service running on port ${PORT}`);
});
