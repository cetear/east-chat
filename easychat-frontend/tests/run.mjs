import { build } from 'esbuild'
import { mkdir } from 'node:fs/promises'
import { pathToFileURL } from 'node:url'
import path from 'node:path'
await mkdir('node_modules/.tmp', { recursive: true })
const outfile = path.resolve('node_modules/.tmp/integration-tests.mjs')
await build({
  entryPoints: ['tests/integration.test.ts'], outfile, bundle: true, platform: 'node', format: 'esm',
  packages: 'external', alias: { '@': path.resolve('src') },
  define: { 'import.meta.env': JSON.stringify({ VITE_API_BASE_URL: '', VITE_AUTH_ENABLED: 'true' }) },
})
globalThis.window = new EventTarget()
await import(pathToFileURL(outfile).href)
