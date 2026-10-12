import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseStrict, references, validate } from '../tools/validate.mjs';

test('the complete bundle has no missing references, duplicate keys or cycles', () => {
  const result = validate(fileURLToPath(new URL('../configs', import.meta.url)));
  assert.deepEqual(result.errors, []);
  for (const name of ['含间隔符的群昵称提取首词', 'QQ-屏蔽全体消息', 'QQ-复用QA通知渠道', '群消息整形-原生通知样式',
    '$-将标题相同的通知视为同一会话', '$-干掉运营消息', '$-收到消息后台唤醒应用', '$-只显示一条消息', '$-直接打开意图']) {
    assert.ok(result.rules.has(name), name);
    assert.ok(result.rules.get(name).length > 0, name);
  }
});

test('conditional test and result references are validated, inline regexes are not references', () => {
  assert.deepEqual(references([['cond', ['predicate', 'target'], [{ match: { packageName: 'com.example' } }, { stop: false }]]]), ['predicate', 'target']);
});

test('duplicate JSON properties cannot silently replace a configuration or nested match field', () => {
  assert.throws(() => parseStrict('{"configs":{"key":[],"key":[]}}'), /Duplicate/);
  assert.throws(() => parseStrict('{"match":{"extra":{"title":"a","title":"b"}}}'), /Duplicate/);
  assert.deepEqual(parseStrict('\uFEFF {"escaped":"a\\\"b", "items":[true, null, -1.2]}'), { escaped: 'a"b', items: [true, null, -1.2] });
});

test('missing nested references, cycles and file-order-dependent duplicate definitions fail', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'mipush-config-validation-'));
  try {
    const file = path.join(directory, 'one.json');
    fs.writeFileSync(file, JSON.stringify({ version: '0.1.0', configs: { a: [['cond', ['missing', 'b']]], b: ['a'] } }));
    const result = validate(directory);
    assert.ok(result.errors.some(e => e.includes('missing')));
    assert.ok(result.errors.some(e => e.includes('cycle')));
    fs.writeFileSync(path.join(directory, 'two.json'), JSON.stringify({ version: '0.1.0', configs: { a: [] } }));
    assert.throws(() => validate(directory), /duplicate configuration key/);
  } finally { fs.rmSync(directory, { recursive: true }); }
});

test('the bundle loader rejects BOM-bearing files instead of masking parser incompatibility', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'mipush-config-encoding-'));
  try {
    fs.writeFileSync(path.join(directory, 'bom.json'), '\uFEFF{"version":"0.1.0","configs":{"a":[]}}');
    assert.throws(() => validate(directory), /without BOM/);
  } finally { fs.rmSync(directory, { recursive: true }); }
});
