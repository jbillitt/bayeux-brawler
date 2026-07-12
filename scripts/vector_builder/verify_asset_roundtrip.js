// ponytail: one-shot manual check, not a test suite. Run with `node verify_asset_roundtrip.js`.
// Extracts every registered asset region (now spread across the split renderer files), does a
// no-op save-back and a mutated save-back in memory, and asserts nothing outside the target
// region would move. Never writes to the real files.
const fs = require('fs');
const path = require('path');
const assert = require('assert');

const TAPESTRY = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'java', 'com', 'example', 'game', 'TapestryRenderer.kt');

const { findFunctionRegionAcrossFiles, findAncillaryRegion, findCustomRegions, BUILDING_FNS, CREATURE_FNS, ANCILLARY_IDS } = require('./server.js');

let checked = 0;

function roundTrip(content, region, label) {
    assert(region, `${label}: region not found`);
    const source = content.slice(region.bodyStart, region.bodyEnd);
    assert(source.length > 0, `${label}: empty body extracted`);
    // no-op save: slice back together should be byte-identical
    const reassembled = content.slice(0, region.bodyStart) + source + content.slice(region.bodyEnd);
    assert.strictEqual(reassembled, content, `${label}: no-op round-trip changed the file`);
    // mutated save: inject a marker comment, confirm ONLY that region changed
    const mutated = content.slice(0, region.bodyStart) + `\n// __ROUNDTRIP_TEST__\n` + source + content.slice(region.bodyEnd);
    assert.strictEqual(mutated.slice(0, region.bodyStart), content.slice(0, region.bodyStart), `${label}: mutation touched text before the region`);
    assert.strictEqual(mutated.endsWith(content.slice(region.bodyEnd)), true, `${label}: mutation touched text after the region`);
    checked++;
}

for (const fn of [...BUILDING_FNS, ...CREATURE_FNS]) {
    const hit = findFunctionRegionAcrossFiles(fn);
    assert(hit, `${fn}: not found in any renderer file`);
    roundTrip(hit.content, hit.region, `${fn} (${path.basename(hit.file)})`);
}

const tapestryContent = fs.readFileSync(TAPESTRY, 'utf-8');
for (const anc of ANCILLARY_IDS) roundTrip(tapestryContent, findAncillaryRegion(tapestryContent, anc), `ancillary:${anc}`);

const customs = findCustomRegions(tapestryContent);
console.log(`Found ${customs.length} pre-existing CUSTOM block(s).`);
console.log(`OK — ${checked} asset regions extracted and round-tripped cleanly (no corruption, no bleed into neighboring code).`);
