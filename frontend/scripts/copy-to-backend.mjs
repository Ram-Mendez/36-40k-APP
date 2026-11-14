import { cpSync, existsSync, readdirSync, mkdirSync, readFileSync, unlinkSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const frontendRoot = resolve(scriptDir, '..');
const browserOutput = join(frontendRoot, 'dist', 'frontend', 'browser');
const backendStatic = resolve(frontendRoot, '..', 'src', 'main', 'resources', 'static');

if (!existsSync(browserOutput)) throw new Error(`Angular build not found: ${browserOutput}`);
// Retire only generated bundles referenced by the previous index; preserve other static resources.
mkdirSync(backendStatic, { recursive: true });
const oldIndex = join(backendStatic, 'index.html');
if (existsSync(oldIndex)) {
  const oldBundles = new Set([...readFileSync(oldIndex, 'utf8').matchAll(/(?:src|href)="((?:main|styles|polyfills|runtime|chunk)-[A-Za-z0-9_-]+\.(?:js|css))"/g)].map(match => match[1]));
  // Follow generated imports as well, including lazily loaded views.
  for (const name of oldBundles) {
    const bundle = resolve(backendStatic, name);
    if (dirname(bundle) !== backendStatic || !name.endsWith('.js') || !existsSync(bundle)) continue;
    for (const match of readFileSync(bundle, 'utf8').matchAll(/["']\.\/((?:chunk|main|polyfills|runtime)-[A-Za-z0-9_-]+\.js)["']/g)) oldBundles.add(match[1]);
  }
  for (const name of oldBundles) {
    const oldBundle = resolve(backendStatic, name);
    if (dirname(oldBundle) === backendStatic && !existsSync(join(browserOutput, name)) && existsSync(oldBundle)) unlinkSync(oldBundle);
  }
}
cpSync(browserOutput, backendStatic, { recursive: true });
console.log(`Copied ${readdirSync(browserOutput).length} Angular build entries to ${backendStatic}`);
