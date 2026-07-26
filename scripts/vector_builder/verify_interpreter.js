// Runs the magazine's Kotlin interpreter (the one index.html draws the canvas from)
// headlessly over real renderer regions, so the four bug classes it used to have —
// drawPath-is-a-fill, named colours, positional Offset args, unrolled loops — stay fixed.
//
//   node scripts/vector_builder/verify_interpreter.js
const fs = require('fs');
const path = require('path');
const { discoverAssets } = require('./server.js');

// --- minimum DOM the editor script touches at load time ---
const els = {};
const stub = id => els[id] || (els[id] = {
  id, value: '', textContent: '', style: {}, dataset: {}, classList: { toggle() {}, add() {}, remove() {} },
  appendChild() {}, querySelectorAll: () => [], querySelector: () => null, addEventListener() {}, closest: () => null
});
const ctx2d = new Proxy({}, { get: (_, k) => (k === 'isPointInPath' || k === 'isPointInStroke') ? () => false : () => {} });
global.document = {
  getElementById: stub,
  querySelectorAll: () => [],
  createElement: () => ({ style: {}, dataset: {}, classList: { toggle() {}, add() {}, remove() {} }, appendChild() {} }),
  addEventListener() {}, activeElement: { tagName: 'BODY' }
};
global.requestAnimationFrame = () => {};
global.fetch = () => Promise.reject(new Error('offline'));
global.Image = class { constructor() { this.complete = false; this.naturalWidth = 0; } };
stub('canvas').width = 700; stub('canvas').height = 520;
stub('canvas').getContext = () => ctx2d;
stub('canvas').getBoundingClientRect = () => ({ left: 0, top: 0 });

const html = fs.readFileSync(path.join(__dirname, 'index.html'), 'utf-8');
const src = html.match(/<script>([\s\S]*)<\/script>/)[1];
// boot() fires network calls we don't want; everything else is pure setup.
const sandbox = { ...global, module: undefined };
const run = new Function(src.replace(/^boot\(\);$/m, '') + `
  return { parseScene, setGear: g => gearColors = g, get scene() { return scene; },
           get editPts() { return editPts; }, get sceneSkipped() { return sceneSkipped; },
           get sceneDark() { return sceneDark; } };`);
const api = run();

// Gear colours normally come from /api/gear; read them the same way here.
const sim = fs.readFileSync(path.join(__dirname, '..', '..', 'app', 'src', 'main', 'java', 'com', 'example', 'game', 'SimulationModels.kt'), 'utf-8');
const colors = {};
for (const line of sim.split('\n')) {
  const m = /\("((?:head|handle|shield|armor|helm|anc)_\w+)",\s*"([^"]+)"/.exec(line);
  const cm = line.match(/color\s*=\s*Color\(0x[0-9A-Fa-f]{2}([0-9A-Fa-f]{6})\)/);
  if (m && cm) colors[m[1]] = '#' + cm[1].toUpperCase();
}
api.setGear(colors);
for (const id of ['headSel', 'handleSel', 'shieldSel', 'armorSel', 'helmSel']) stub(id).value = '';

const { assets } = discoverAssets();
const byId = Object.fromEntries(assets.map(a => [a.id, a]));

// [asset, equipped slot value, what must appear]
const CASES = [
  ['head_scythe',    'headSel',   'head_scythe',   s => s.some(x => x.type === 'fill'),                      'filled blade (drawPath with no style = a fill)'],
  // Weapon heads are grey. They all came out mustard once, because an unresolved colour
  // fell back to a plausible-looking tint instead of the gear's own.
  ['head_scythe',    'headSel',   'head_scythe',   s => s.every(x => x.color === '#6C7175'),                 'blade takes the gear colour, not the fallback'],
  ['head_broadsword','headSel',   'head_broadsword', s => s.some(x => x.color === '#949B9E'),                'broadsword steel resolves'],
  ['head_slingshot', 'headSel',   'head_slingshot', s => s.filter(x => x.type === 'line').length >= 2,        'both prongs (positional Offset args)'],
  ['head_flail',     'headSel',   'head_flail',    s => s.filter(x => x.type === 'line').length >= 7,         'chain + 6 spikes (unrolled for loop)'],
  ['head_spiked_mace', 'headSel', 'head_spiked_mace', s => s.filter(x => x.type === 'line').length >= 12,     '12 studs (unrolled for loop)'],
  ['handle_trumpet', 'handleSel', 'handle_trumpet', s => s.some(x => x.type === 'fill' && x.color === '#D6A420'), 'brass bell, not thread-black (named colour)']
];

let failed = 0;
for (const [id, sel, equip, check, what] of CASES) {
  const a = byId[id];
  if (!a) { console.log(`SKIP ${id} — not found in the renderer`); continue; }
  stub(sel).value = equip;
  api.parseScene(a.source);
  const ok = check(api.scene);
  if (!ok) failed++;
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${id.padEnd(18)} ${what}`);
  if (!ok) console.log(`       got ${api.scene.length} shapes: ${JSON.stringify(api.scene.map(s => s.type + ':' + s.color))}`);
}
console.log(failed ? `\n${failed} check(s) failed.` : '\nAll interpreter checks passed.');
process.exit(failed ? 1 : 0);
