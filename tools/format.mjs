import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseStrict } from './validate.mjs';

const directory = fileURLToPath(new URL('../configs', import.meta.url));
for (const name of fs.readdirSync(directory).filter(name => name.endsWith('.json'))) {
  const file = path.join(directory, name);
  const value = parseStrict(fs.readFileSync(file, 'utf8'));
  fs.writeFileSync(file, JSON.stringify(value, null, 2) + '\n', 'utf8');
}
console.log('Formatted configuration JSON as UTF-8 without BOM');
