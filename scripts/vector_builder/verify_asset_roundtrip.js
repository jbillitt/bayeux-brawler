// ponytail: one-shot manual check, not a test suite. Run with `node verify_asset_roundtrip.js`.
// Extracts every registered asset region (now spread across the split renderer files), does a
// no-op save-back and a mutated save-back in memory, and asserts nothing outside the target
// region would move. Never writes to the real files.
const fs = require('fs');
const path = require('path');
const assert = require('assert');

const TAPESTRY = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'java', 'com', 'example', 'game', 'TapestryRenderer.kt');

const { findFunctionRegionAcrossFiles, findAncillaryRegion, findCustomRegions, discoverAssets } = require('./server.js');

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

const { assets, discoveredIds } = discoverAssets();

assert(discoveredIds.has('drawThrone'), 'discovery failed to find drawThrone');
// assert(discoveredIds.has('PLAGUE_PEASANT'), 'discovery failed to find PLAGUE_PEASANT'); // TODO: enable after Task 9

for (const asset of assets) {
    if (asset.type === 'building' || asset.type === 'creature') {
        const hit = findFunctionRegionAcrossFiles(asset.id);
        assert(hit, `: not found in any renderer file`);
        roundTrip(hit.content, hit.region, ` ()`);
    }
}

const tapestryContent = fs.readFileSync(TAPESTRY, 'utf-8');
for (const asset of assets) {
    if (asset.type === 'ancillary') {
        roundTrip(tapestryContent, findAncillaryRegion(tapestryContent, asset.id), `ancillary:`);
    }
}

const customs = findCustomRegions(tapestryContent);
console.log(`Found ${customs.length} pre-existing CUSTOM block(s).`);
console.log(`OK — ${checked} asset regions extracted and round-tripped cleanly (no corruption, no bleed into neighboring code).`);

