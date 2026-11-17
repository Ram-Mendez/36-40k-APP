import http from 'node:http';

// Development fixture: a reachable service whose quotation takes 1.5 seconds.
http.createServer((request, response) => {
  const url = new URL(request.url, 'http://localhost');
  if (url.pathname !== '/quotes') { response.writeHead(404); response.end(); return; }
  console.log('Quotation received; response scheduled in 1500 ms.');
  setTimeout(() => {
    if (response.destroyed) return;
    const amount = Number(url.searchParams.get('amount'));
    response.writeHead(200, {'Content-Type': 'application/json'});
    response.end(JSON.stringify({reference: `SLOW-${url.searchParams.get('orderId')}`, cost: amount >= 200 ? 0 : 7.50}));
  }, 1500);
}).listen(39098, '127.0.0.1', () => console.log('Slow shipping on 39098. Stop Shipping APP before running this fixture. Ctrl+C stops it.'));
