import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { setTimeout as delay } from 'node:timers/promises';
import { fileURLToPath } from 'node:url';

// Una sesión mantiene el bloqueo y consulta si la transacción HTTP está detrás de ella.
export async function whileStockIsLocked(productId, confirm) {
  if (!Number.isSafeInteger(productId) || productId < 1) throw new Error('ID de producto inválido');
  const local = process.env.LAB_PSQL;
  const args = local ? ['-h', '127.0.0.1', '-p', '35438'] : ['compose', '-f', 'compose.lab.yaml', 'exec', '-T', 'postgres', 'psql'];
  args.push('-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', 'lab_20261117', '-d', 'lab_20261117');
  const child = spawn(local || 'docker', args, {
    cwd: fileURLToPath(new URL('../', import.meta.url)), stdio: 'pipe', timeout: 30_000
  });
  const output = createInterface({ input: child.stdout })[Symbol.asyncIterator]();
  let diagnostic = '';
  child.stderr.setEncoding('utf8').on('data', text => { diagnostic += text; });
  child.on('error', error => { diagnostic = error.message; });
  child.stdin.on('error', error => { diagnostic ||= error.message; });

  async function query(sql) {
    child.stdin.write(sql + '\n\\echo END_LAB22\n');
    const rows = [];
    while (true) {
      const { done, value } = await output.next();
      if (done) throw new Error('Sesión PostgreSQL interrumpida. ' + diagnostic.trim());
      if (value === 'END_LAB22') return rows;
      if (value) rows.push(value);
    }
  }

  let response;
  try {
    const locked = await query(`BEGIN; SELECT product_id FROM inventory_balance WHERE product_id=${productId} FOR UPDATE;`);
    if (locked[0] !== String(productId)) throw new Error('El producto preparado no está en esta base');
    let finished = false;
    response = confirm().finally(() => { finished = true; });
    // Mantén observado un posible error de red mientras se comprueba el bloqueo.
    response.catch(() => {});
    let waited = false;
    const deadline = Date.now() + 12_000;
    while (!finished && !waited) {
      if (Date.now() >= deadline) throw new Error('La petición no llegó a esperar por la fila dentro del plazo');
      const [waiting] = await query(`SELECT pg_stat_clear_snapshot();
        SELECT EXISTS (SELECT 1 FROM pg_stat_activity a
                       WHERE a.datname=current_database() AND pg_backend_pid()=ANY(pg_blocking_pids(a.pid)));`);
      waited = waiting === 't';
      if (!waited && !finished) await delay(40);
    }
    await query('COMMIT;');
    return { waited, reply: await response };
  } finally {
    // Cerrar psql también deshace un BEGIN pendiente si el escenario se interrumpe.
    child.stdin.end('\\q\n');
    if (response) await response.catch(() => {});
  }
}
