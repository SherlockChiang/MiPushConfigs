import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { load } from './validate.mjs';

const directory = fileURLToPath(new URL('../configs', import.meta.url));
const bundle = load(directory);
const patterns = [];
function matching(value) {
  if (typeof value === 'string') patterns.push(value);
  else if (value && !Array.isArray(value) && typeof value === 'object') Object.values(value).forEach(matching);
}
function walk(value, replacement = false) {
  if (Array.isArray(value)) {
    if (value[0] === 'replace' && typeof value[2] === 'string') patterns.push(value[2]);
    value.forEach(item => walk(item, replacement));
  } else if (value && typeof value === 'object') {
    if (!replacement && value.match) matching(value.match);
    if (!replacement && value.metaInfo) matching(value.metaInfo);
    for (const [key, item] of Object.entries(value)) walk(item, replacement || key === 'replace' || key === 'newMetaInfo');
  }
}
for (const items of bundle.rules.values()) walk(items);
const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
const result = spawnSync(java, [fileURLToPath(new URL('CheckPatterns.java', import.meta.url))], {
  input: [...new Set(patterns)].map(p => Buffer.from(p).toString('base64')).join('\n'), encoding: 'utf8'
});
if (result.error) throw result.error;
process.stdout.write(result.stdout ?? '');
process.stderr.write(result.stderr ?? '');
process.exitCode = result.status ?? 1;
