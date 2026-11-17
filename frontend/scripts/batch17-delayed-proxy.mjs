import http from 'node:http';

// Development-only fixture: buffer one genuine response, then deliver it after logout.
let held = false;
http.createServer((request, response) => {
  const delay = !held && request.method === 'GET' && request.url.split('?')[0] === '/api/v1/products';
  if (delay) held = true;
  const upstream = http.request({ hostname: '127.0.0.1', port: 38088,
    path: request.url, method: request.method, headers: request.headers }, incoming => {
    const chunks = [];
    incoming.on('data', chunk => chunks.push(chunk));
    incoming.on('end', () => {
      const send = () => {
        if (!response.destroyed) {
          response.writeHead(incoming.statusCode, incoming.headers);
          response.end(Buffer.concat(chunks));
        }
      };
      if (delay) {
        console.log('Product response held for 10 seconds. Sign out in Settings now.');
        setTimeout(send, 10000);
      } else send();
    });
  });
  upstream.on('error', () => { response.writeHead(502); response.end('Backend LAB 17-11 unavailable'); });
  request.pipe(upstream);
}).listen(38090, '127.0.0.1', () => console.log('Batch 17 delay fixture: 38090 -> 38088. Restart to rearm.'));
