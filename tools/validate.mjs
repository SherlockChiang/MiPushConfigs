import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// JSON.parse accepts duplicate properties silently; Android also uses the last
// definition. Reject duplicates before parsing so file order cannot hide a rule.
export function parseStrict(text) {
  text = text.replace(/^\uFEFF/, '');
  let pos = 0;
  const ws = () => { while (/\s/.test(text[pos] ?? '') && pos < text.length) pos++; };
  function string() {
    const start = pos++;
    while (pos < text.length) {
      if (text[pos++] === '"') return JSON.parse(text.slice(start, pos));
      if (text[pos - 1] === '\\') pos++;
    }
    throw new Error('Unterminated JSON string');
  }
  function value() {
    ws();
    if (text[pos] === '{') {
      pos++; ws();
      const keys = new Set();
      if (text[pos] === '}') { pos++; return; }
      while (pos < text.length) {
        ws();
        if (text[pos] !== '"') throw new Error(`Expected property at ${pos}`);
        const key = string();
        if (keys.has(key)) throw new Error(`Duplicate JSON property: ${key}`);
        keys.add(key); ws();
        if (text[pos++] !== ':') throw new Error(`Expected colon at ${pos}`);
        value(); ws();
        const sep = text[pos++];
        if (sep === '}') return;
        if (sep !== ',') throw new Error(`Expected comma at ${pos}`);
      }
    } else if (text[pos] === '[') {
      pos++; ws();
      if (text[pos] === ']') { pos++; return; }
      while (pos < text.length) {
        value(); ws();
        const sep = text[pos++];
        if (sep === ']') return;
        if (sep !== ',') throw new Error(`Expected comma at ${pos}`);
      }
    } else if (text[pos] === '"') string();
    else {
      const token = /^(?:true|false|null|-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?)/.exec(text.slice(pos));
      if (!token) throw new Error(`Invalid JSON value at ${pos}`);
      pos += token[0].length;
    }
  }
  value(); ws();
  if (pos !== text.length) throw new Error(`Trailing JSON content at ${pos}`);
  return JSON.parse(text);
}

export function references(items) {
  const refs = [];
  function expression(expr) {
    if (!Array.isArray(expr)) return;
    if (expr[0] === 'cond') {
      for (const clause of expr.slice(1)) {
        if (!Array.isArray(clause) || clause.length !== 2) throw new Error('cond clauses must contain a test and a result');
        for (const item of clause) {
          if (typeof item === 'string') refs.push(item);
          else if (Array.isArray(item)) expression(item);
        }
      }
    } else throw new Error(`Unsupported top-level dynamic reference: ${expr[0]}`);
  }
  for (const item of items) {
    if (typeof item === 'string') refs.push(item);
    else if (Array.isArray(item)) expression(item);
    else if (!item || typeof item !== 'object') throw new Error('Rule must be a reference, expression, or object');
  }
  return refs;
}

export function load(directory) {
  const rules = new Map();
  const owners = new Map();
  const files = fs.readdirSync(directory).filter(name => name.endsWith('.json')).sort();
  for (const name of files) {
    const raw = fs.readFileSync(path.join(directory, name), 'utf8');
    if (raw.startsWith('\uFEFF')) throw new Error(`${name}: save as UTF-8 without BOM for JSON parser compatibility`);
    const doc = parseStrict(raw);
    if (doc.version !== '0.1.0' || !doc.configs || Array.isArray(doc.configs)) throw new Error(`${name}: invalid version/configs`);
    for (const [key, items] of Object.entries(doc.configs)) {
      if (!Array.isArray(items)) throw new Error(`${name}: ${key} must be an array`);
      if (rules.has(key)) throw new Error(`${name}: duplicate configuration key ${key} (also ${owners.get(key)})`);
      rules.set(key, items); owners.set(key, name);
    }
  }
  if (!files.length) throw new Error('No configuration files found');
  return { rules, owners, files };
}

export function validate(directory) {
  const bundle = load(directory);
  const errors = [];
  const graph = new Map();
  for (const [key, items] of bundle.rules) {
    const refs = references(items);
    graph.set(key, refs);
    for (const ref of refs) if (!bundle.rules.has(ref)) errors.push(`${bundle.owners.get(key)} :: ${key} -> ${ref}`);
  }
  const done = new Set();
  function visit(key, stack) {
    if (stack.includes(key)) { errors.push(`Reference cycle: ${[...stack, key].join(' -> ')}`); return; }
    if (done.has(key)) return;
    for (const ref of graph.get(key) ?? []) if (bundle.rules.has(ref)) visit(ref, [...stack, key]);
    done.add(key);
  }
  for (const key of graph.keys()) visit(key, []);
  return { ...bundle, errors };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const directory = path.resolve(process.argv[2] ?? fileURLToPath(new URL('../configs', import.meta.url)));
    const result = validate(directory);
    console.log(`${result.files.length} files, ${result.rules.size} configuration keys, ${result.errors.length} errors`);
    for (const error of result.errors) console.error(error);
    if (result.errors.length) process.exitCode = 1;
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
