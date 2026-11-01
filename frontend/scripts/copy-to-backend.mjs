import { cpSync, existsSync, readdirSync, mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const frontendRoot = resolve(scriptDir, '..');
const browserOutput = join(frontendRoot, 'dist', 'frontend', 'browser');
const backendStatic = resolve(frontendRoot, '..', 'src', 'main', 'resources', 'static');

if (!existsSync(browserOutput)) throw new Error(`Angular build not found: ${browserOutput}`);
// Copia el build sin borrar posibles recursos locales ajenos a Angular.
mkdirSync(backendStatic, { recursive: true });
cpSync(browserOutput, backendStatic, { recursive: true });
console.log(`Copied ${readdirSync(browserOutput).length} Angular build entries to ${backendStatic}`);
