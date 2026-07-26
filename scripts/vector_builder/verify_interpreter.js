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
           get sceneDark() { return sceneDark; }, ptAxis, resolvePt,
           addShape, setShapeKind: k => shapeKind = k, setFillColor: c => fillColor = c,
           setLineColor: c => lineColor = c, applyLineColor, nearestSegment, pathSegments,
           setView: v => Object.assign(view, v), sourceFault };`);
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
  ['handle_trumpet', 'handleSel', 'handle_trumpet', s => s.some(x => x.type === 'fill' && x.color === '#D6A420'), 'brass bell, not thread-black (named colour)'],
  // Heads that build geometry from the haft basis via a local `fun at(along, across)`.
  // Both drew nothing: the helper was unknown, and `val dx = ...; val dy = ...` on one
  // line meant only dx was ever read.
  ['head_axe',       'headSel',   'head_axe',      s => s.length >= 4,                                       'blade, socket and beard (local fun helper)'],
  ['head_pitchfork', 'headSel',   'head_pitchfork', s => s.filter(x => x.type === 'line').length >= 4,        'head bar + three tines (local fun helper)'],
  // Six nails, each a Triple of Offsets in a named list walked by a destructuring forEach.
  ['handle_plank',   'handleSel', 'handle_plank',  s => s.filter(x => x.type === 'line').length >= 18,        'all six nails (listOf(Triple(..)).forEach)'],
  // Buildings pass strokeWidth positionally — drawLine(beam, a, b, 8f). Read as named-only, every
  // roof beam fell back to the 3px default and the hall's diagonals came out spindly.
  ['drawFeastingHall', 'headSel', '',             s => s.some(x => x.type === 'line' && x.width === 8),      'roof beams keep their 8f width (positional strokeWidth)'],
];

// A handle must sit on the shape it belongs to. When translate() was read as an absolute
// origin instead of a delta — and a fully-qualified androidx…Offset( missed its index —
// the wardog drew its body 130px from its own handles.
const ALIGNMENT = ['drawWardog', 'handle_plank', 'handle_anchor'];

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
// Every draggable handle must carry the same transform its shape was drawn under. When
// translate() was read as an absolute origin instead of a delta — and a fully-qualified
// androidx…Offset( missed its point index — the wardog drew its body 130px away from its
// own handles, which is what destroys confidence in dragging anything.
for (const id of ALIGNMENT) {
  const a = byId[id];
  if (!a) { console.log(`SKIP ${id}`); continue; }
  stub(id.startsWith('handle') ? 'handleSel' : 'headSel').value = id;
  api.parseScene(a.source);
  const withT = api.editPts.filter(p => p.t).length;
  const inXf = withT > 0;
  const ok = !inXf || withT >= api.editPts.length / 2;
  if (!ok) failed++;
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${id.padEnd(18)} handles carry their transform (${withT}/${api.editPts.length})`);
}

// The shape tool must drop a self-contained path: its own corners, and its own literal fill
// colour. A shape drawn instead as a run of drawLine calls picks up whatever colour its
// neighbours are given — recolouring one line recolours the lot — which is the whole reason
// to have square and triangle tools at all.
stub('shapeSize').value = '20';
for (const [kind, sides] of [['triangle', 3], ['square', 4]]) {
  stub('source').value = '';
  api.setShapeKind(kind);
  api.setFillColor('#4C613D');
  api.addShape({ x: 0, y: 0 });
  const emitted = stub('source').value;
  const fills = api.scene.filter(s => s.type === 'fill');
  const corners = (emitted.match(/\b(moveTo|lineTo)\(/g) || []).length;
  const ok = corners === sides && fills.length === 1 && fills[0].color === '#4C613D';
  if (!ok) failed++;
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${(kind + ' tool').padEnd(18)} ${sides} corners, own fill colour, independent of neighbours`);
  if (!ok) console.log(`       got ${corners} corners, fills ${JSON.stringify(fills.map(f => f.color))}`);
}

// Line colour on a path used to repaint the whole outline: clicking one side of a square
// recoloured all four. Drop a square, click the midpoint of its top edge, and only that edge
// may change — the original outline call must survive untouched.
{
  stub('source').value = '';
  api.setShapeKind('square');
  api.setFillColor('#4C613D');
  api.addShape({ x: 0, y: 0 });          // corners at (-10,-10)..(10,10)
  const before = stub('source').value;
  const outlineCalls = s => (s.match(/drawPath\([^,]+, [^,]+, style = StitchedStroke\)/g) || []).length;

  const sq = api.scene.find(s => s.cmds && s.type === 'stroke') || api.scene.find(s => s.cmds);
  const segs = api.pathSegments(sq);
  api.setLineColor('#9E3624');
  api.applyLineColor(sq, { x: 0, y: -10 });   // midpoint of the top edge
  const after = stub('source').value;

  const gained = outlineCalls(after) - outlineCalls(before);
  const edgePath = /val square_\d+_edge1 = Path\(\)\.apply \{\s*\n\s*moveTo\([^\n]*\)\s*\n\s*lineTo\([^\n]*\)\s*\n\s*\}/.test(after);
  // The square's own outline must still be there, still in its original colour.
  const originalKept = after.includes(before.trim().split('\n').pop().trim());
  const ok = segs.length === 4 && gained === 1 && edgePath && originalKept
             && (after.match(/Color\(0xFF9E3624\)/g) || []).length === 1;
  if (!ok) failed++;
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${'line colour'.padEnd(18)} recolours one edge, leaves the other 3 and the fill alone`);
  if (!ok) console.log(`       segs=${segs.length} gained=${gained} edgePath=${edgePath} originalKept=${originalKept}`);
}

// A click nowhere near an edge must not silently repaint a random one.
{
  stub('source').value = '';
  api.setShapeKind('square');
  api.addShape({ x: 0, y: 0 });
  const sq = api.scene.find(s => s.cmds);
  const ok = api.nearestSegment(sq, { x: 200, y: 200 }) === null;
  if (!ok) failed++;
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${'line colour'.padEnd(18)} a click far from any edge picks none`);
}

// The save guard refuses to write Kotlin that would not compile. It is only safe to have it
// refuse if it says nothing about the art that is already in the tree — one false positive and
// it would block every save in that region. So run it over every asset the editor can open.
{
  const complaints = assets.map(a => [a.id, api.sourceFault(a.source, a.source)]).filter(([, f]) => f);
  const ok = complaints.length === 0;
  if (!ok) failed++;
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${'save guard'.padEnd(18)} clean on all ${assets.length} existing assets (no false positives)`);
  complaints.slice(0, 5).forEach(([id, f]) => console.log(`       ${id}: ${f}`));
}
// ...and it must actually catch the two breakages that reached the tree.
{
  const cases = [
    ['scope.drawCircle(Color(0xFF2C2219), radius = 2f, center = , style = StitchedStroke)', 'argument lost its value'],
    ['scope.drawLine(Color.Yellow, androidx.compose.ui.geometry.Offset(cx, cy), androidx.compose.ui.geometry., strokeWidth = 4f)', 'an argument cut off after its package name'],
    ['scope.drawLine(Color(0xFF222222), , , strokeWidth = 2.5f)', 'empty positional arguments'],
    ['scope.drawPath(bowPath, Color(0xFF2C2219), style = StitchedStroke)', 'an edit that deletes a val but keeps its drawPath',
     'val bowPath = Path().apply { moveTo(1f, 2f) }\nscope.drawPath(bowPath, Color(0xFF2C2219), style = StitchedStroke)']
  ];
  for (const [src, what, prev] of cases) {
    const ok = api.sourceFault(src, prev) !== null;
    if (!ok) failed++;
    console.log(`${ok ? 'ok  ' : 'FAIL'} ${'save guard'.padEnd(18)} rejects ${what}`);
  }
}

if (process.env.PROBE) {
  for (const id of ['drawEcclesia', 'drawPalaceArch', 'drawAbbeyNave', 'drawDomedTower', 'drawFeastingHall', 'drawBayeuxBuilding', 'drawBuildingBosham', 'drawFortDinan', 'drawBuildingManor', 'drawFortPalace']) {
    const a = byId[id]; if (!a) { console.log(`?? ${id}`); continue; }
    api.parseScene(a.source);
    const kinds = {};
    api.scene.forEach(s => kinds[s.type] = (kinds[s.type] || 0) + 1);
    console.log(`${id.padEnd(20)} shapes=${String(api.scene.length).padEnd(4)} skipped=${api.sceneSkipped} dark=${api.sceneDark} pts=${api.editPts.length} ${JSON.stringify(kinds)}`);
  }
}

console.log(failed ? `\n${failed} check(s) failed.` : '\nAll interpreter checks passed.');
process.exit(failed ? 1 : 0);
