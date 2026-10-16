import { writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const apiOrigin = process.env.API_ORIGIN;
if (!apiOrigin) {
  throw new Error('API_ORIGIN must contain the deployed HTTPS backend origin.');
}

let parsedOrigin;
try {
  parsedOrigin = new URL(apiOrigin);
} catch {
  throw new Error('API_ORIGIN must be a valid HTTPS origin.');
}

if (parsedOrigin.protocol !== 'https:'
  || parsedOrigin.username
  || parsedOrigin.password
  || parsedOrigin.pathname !== '/'
  || parsedOrigin.search
  || parsedOrigin.hash) {
  throw new Error('API_ORIGIN must be an HTTPS origin without credentials, path, query, or fragment.');
}

const config = { apiBaseUrl: `${parsedOrigin.origin}/api` };
const outputPath = resolve('dist/frontend/browser/app-config.js');
await writeFile(outputPath, `window.salaryManagementConfig = ${JSON.stringify(config)};\n`, 'utf8');
console.log('Wrote public API origin configuration to the Angular build output.');
