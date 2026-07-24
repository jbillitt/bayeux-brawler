const express = require('express');
const bodyParser = require('body-parser');
const cors = require('cors');
const fs = require('fs');
const path = require('path');

const app = express();
app.use(cors());
app.use(bodyParser.json({ limit: '10mb' }));

const RENDERER_DIR = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'java', 'com', 'example', 'game');
const TAPESTRY_RENDERER_PATH = path.join(RENDERER_DIR, 'TapestryRenderer.kt');
const SIMULATION_MODELS_PATH = path.join(RENDERER_DIR, 'SimulationModels.kt');
// The renderer was split into focused files; asset functions are searched across all of them.
const RENDERER_FILES = ['TapestryRenderer.kt', 'BuildingRenderer.kt', 'MountRenderer.kt', 'StitchCraft.kt'];

function injectIntoTapestryRenderer(itemType, safeName, codeSnippet) {
    let content = fs.readFileSync(TAPESTRY_RENDERER_PATH, 'utf-8');
    const markerStart = `// --- CUSTOM ${safeName} ---`;
    const markerEnd = `// --- END CUSTOM ${safeName} ---`;

    const existingBlockRegex = new RegExp(`${markerStart.replace(/\-/g, '\\-')}[\\s\\S]*?${markerEnd.replace(/\-/g, '\\-')}\\n?`);
    content = content.replace(existingBlockRegex, '');

    let injectedBlock = '';

    if (itemType === 'WeaponHead' || itemType === 'Attachment') {
        injectedBlock = `${markerStart}\n"${safeName}" -> {\n${codeSnippet}\n}\n${markerEnd}\n`;
        const searchStr = 'when (headId) {';
        const idx = content.indexOf(searchStr);
        if (idx !== -1) {
            content = content.slice(0, idx + searchStr.length) + '\n' + injectedBlock + content.slice(idx + searchStr.length);
        } else {
            throw new Error('Could not find "when (headId) {" in TapestryRenderer.kt');
        }
    } else if (itemType === 'Shield') {
        injectedBlock = `${markerStart}\nelse if (fighter.shield.id == "${safeName}") {\n${codeSnippet.replace(/drawStitchedFill\(scope, (path_[0-9]+), /g, 'drawStitchedFill(scope, $1, sColor')}\n}\n${markerEnd}\n`;
        const searchStr = 'if (hasKite) {';
        const idx = content.indexOf(searchStr);
        if (idx !== -1) {
            content = content.slice(0, idx) + injectedBlock + '            ' + content.slice(idx);
        } else {
            throw new Error('Could not find shield injection point in TapestryRenderer.kt');
        }
    } else if (itemType === 'Headgear') {
        injectedBlock = `${markerStart}\nif (helmId == "${safeName}") {\n${codeSnippet}\n}\n${markerEnd}\n`;
        const searchStr = 'val helmId = fighter.headgear.id';
        const idx = content.indexOf(searchStr);
        if (idx !== -1) {
            const insertIdx = content.indexOf('\n', idx) + 1;
            content = content.slice(0, insertIdx) + '\n' + injectedBlock + content.slice(insertIdx);
        } else {
            throw new Error('Could not find headgear injection point in TapestryRenderer.kt');
        }
    } else if (itemType === 'Armor') {
        injectedBlock = `${markerStart}\n"${safeName}" -> {\n${codeSnippet}\n}\n${markerEnd}\n`;
        const searchStr = 'when (fighter.armor.id) {';
        const idx = content.indexOf(searchStr);
        if (idx !== -1) {
            content = content.slice(0, idx + searchStr.length) + '\n' + injectedBlock + content.slice(idx + searchStr.length);
        } else {
             content += '\n' + injectedBlock;
        }
    } else {
        // ponytail: no known injection point for this itemType (e.g. WeaponHandle, RangedGear) —
        // the renderer draws handles/ranged gear via one big if/else chain, not a clean when(id) dispatch,
        // so there's nowhere safe to splice in new code. Fail loudly instead of appending dead code past
        // the end of the class, which silently broke the Kotlin build for anyone who picked these types.
        throw new Error(`Item type "${itemType}" has no injection point yet in TapestryRenderer.kt. Weapon Handles and Ranged Gear are drawn via shared if/else logic (see drawWeapon()), not a per-id dispatch — add a case there by hand, or ask for a dispatch point to be added.`);
    }

    fs.writeFileSync(TAPESTRY_RENDERER_PATH, content);
}

function checkSimulationModels(itemType, safeName) {
    const content = fs.readFileSync(SIMULATION_MODELS_PATH, 'utf-8');
    if (content.includes(`"${safeName}"`)) {
        return `Item ID "${safeName}" found in SimulationModels.kt. Code injected successfully!`;
    } else {
        let enumName = 'GameData.' + itemType;
        if (itemType === 'Headgear') enumName = 'GameData.HeadgearPiece';
        if (itemType === 'Armor') enumName = 'GameData.ArmorPiece';
        return `New item detected! Code injected successfully. Please add stat balancing to enum ${enumName} in SimulationModels.kt for ID "${safeName}".`;
    }
}

app.post('/api/inject', (req, res) => {
    try {
        const { itemType, itemName, codeSnippet } = req.body;
        const safeName = itemName.toLowerCase().replace(/[^a-z0-9]/g, '_');

        injectIntoTapestryRenderer(itemType, safeName, codeSnippet);
        const simMsg = checkSimulationModels(itemType, safeName);

        res.json({ success: true, message: simMsg });
    } catch (err) {
        console.error(err);
        res.status(500).json({ success: false, message: err.message });
    }
});

// ---------------------------------------------------------------------------
// Existing-asset browser: lets the web UI load, edit and save back the paths
// for things that were never created through this tool (buildings, mounts,
// ancillary followers) plus items that were (CUSTOM-marked blocks).
//
// Editing model: we hand the client the raw Kotlin text of one region (a
// function body or a `when` branch body). The client can either eyeball-edit
// that text directly, or — for regions that only use moveTo/lineTo/close —
// load it into the canvas too. Saving always writes back whatever text is in
// the textarea, verbatim, into the same region. No curve-aware canvas
// round-trip: quadraticTo/loops would be silently flattened if we tried to
// regenerate from canvas points, which would visibly wreck the art. Raw text
// swap can't lose data like that.
// ---------------------------------------------------------------------------

// Finds the index of the `}` that matches the `{` at content[openIdx],
// skipping braces that appear inside "..." string literals or //.../* */ comments.
function findMatchingBrace(content, openIdx) {
    let depth = 0;
    let i = openIdx;
    while (i < content.length) {
        const c = content[i];
        if (c === '"') {
            // skip string literal (handles \" escapes; good enough for this codebase)
            i++;
            while (i < content.length && content[i] !== '"') {
                if (content[i] === '\\') i++;
                i++;
            }
        } else if (c === '/' && content[i + 1] === '/') {
            while (i < content.length && content[i] !== '\n') i++;
        } else if (c === '{') {
            depth++;
        } else if (c === '}') {
            depth--;
            if (depth === 0) return i;
        }
        i++;
    }
    return -1;
}

// Locates a region by finding `anchorRegex` and then brace-matching the next `{` after it.
// Returns { bodyStart, bodyEnd } = the span *inside* that brace pair (exclusive of the braces).
function locateRegion(content, anchorRegex) {
    const m = anchorRegex.exec(content);
    if (!m) return null;
    const openIdx = content.indexOf('{', m.index + m[0].length - 1);
    if (openIdx === -1) return null;
    const closeIdx = findMatchingBrace(content, openIdx);
    if (closeIdx === -1) return null;
    return { bodyStart: openIdx + 1, bodyEnd: closeIdx };
}

const ANCILLARY_WHEN_ANCHOR = /Draw Back Arm holding something[\s\S]*?when \(anc\) \{/;

// Structural/dispatch functions stay uneditable; body-part functions are now a first-class
// editable group (the magazine wants "edit how feet look on legs").
const EXCLUDED_FNS = new Set([
    'drawCharacter', 'drawWeaponHead', 'drawAncillaries',
    'drawStitchedFill', 'drawStitchedStrap',
    'drawDamageDecals', 'drawDamageFlurry', 'drawBackgroundObject'
]);
const BODY_FNS = new Set([
    'drawLegs', 'drawTorso', 'drawHead', 'drawWeapon',
    'drawFrontArmAndWeapon', 'drawBackArmAndShield',
    'drawFollowerBody', 'drawFollowerHead'
]);
// Plain-English labels so "edit how feet look" is findable
const BODY_LABELS = {
    drawLegs: 'Legs & feet', drawTorso: 'Torso & tunic', drawHead: 'Head & face',
    drawWeapon: 'Weapon in hand', drawFrontArmAndWeapon: 'Front arm', drawBackArmAndShield: 'Back arm & shield',
    drawFollowerBody: 'Follower body (legs, tunic, heraldry)', drawFollowerHead: 'Follower head, hair & face'
};
// Creatures that live in TapestryRenderer, not MountRenderer
const CREATURE_FNS = new Set(['drawRebelSnail', 'drawBossSignature', 'drawDogHead']);

// --- Gear-region discovery: the when(id)/if(id) branches the renderer actually dispatches on ---

/** Branches of a `when (<anchor>) { "id" -> { ... } }` block. Returns [{id, bodyStart, bodyEnd}]. */
function findWhenBranchRegions(content, anchorStr, idPrefix) {
    const out = [];
    const anchorIdx = content.indexOf(anchorStr);
    if (anchorIdx === -1) return out;
    const whenOpen = content.indexOf('{', anchorIdx);
    const whenClose = findMatchingBrace(content, whenOpen);
    if (whenClose === -1) return out;
    const re = new RegExp(`"(${idPrefix}_\\w+)"(?:\\s*,\\s*"\\w+")*\\s*->\\s*\\{`, 'g');
    re.lastIndex = whenOpen;
    let m;
    while ((m = re.exec(content)) !== null && m.index < whenClose) {
        const open = content.indexOf('{', m.index + m[0].length - 1);
        const close = findMatchingBrace(content, open);
        if (close === -1) continue;
        out.push({ id: m[1], bodyStart: open + 1, bodyEnd: close });
        re.lastIndex = close;
    }
    return out;
}

/** Guard-chain branches like `if (helmId == "helm_x" ...) {` — first occurrence per id wins. */
function findGuardRegions(content, guardExpr, idPrefix) {
    const out = [];
    const seen = new Set();
    const re = new RegExp(`${guardExpr} == "(${idPrefix}_\\w+)"[^{\\n]*\\{`, 'g');
    let m;
    while ((m = re.exec(content)) !== null) {
        if (seen.has(m[1])) continue;
        const open = content.indexOf('{', m.index + m[0].length - 1);
        const close = findMatchingBrace(content, open);
        if (close === -1) continue;
        seen.add(m[1]);
        out.push({ id: m[1], bodyStart: open + 1, bodyEnd: close });
    }
    return out;
}

const GEAR_REGION_FINDERS = {
    weaponhead: c => findWhenBranchRegions(c, 'when (headId) {', 'head'),
    armor: c => findWhenBranchRegions(c, 'when (fighter.armor.id) {', 'armor'),
    headgear: c => findGuardRegions(c, 'helmId', 'helm'),
    handle: c => findGuardRegions(c, 'fighter\\.weaponHandle\\.id', 'handle')
};

function discoverGearRegions(content) {
    const assets = [];
    for (const [type, finder] of Object.entries(GEAR_REGION_FINDERS)) {
        for (const r of finder(content)) {
            assets.push({ type, id: r.id, label: r.id, source: content.slice(r.bodyStart, r.bodyEnd) });
        }
    }
    return assets;
}

function findGearRegion(content, type, id) {
    const finder = GEAR_REGION_FINDERS[type];
    if (!finder) return null;
    return finder(content).find(r => r.id === id) || null;
}

function discoverAssets() {
    const assets = [];
    const discoveredIds = new Set();
    const tapestryContent = fs.existsSync(TAPESTRY_RENDERER_PATH) ? fs.readFileSync(TAPESTRY_RENDERER_PATH, 'utf-8') : '';

    for (const file of RENDERER_FILES) {
        const fullPath = path.join(RENDERER_DIR, file);
        if (!fs.existsSync(fullPath)) continue;
        const content = fs.readFileSync(fullPath, 'utf-8');
        
        const re = /(?:private |internal )?fun (draw[A-Z]\w*)\(/g;
        let m;
        while ((m = re.exec(content)) !== null) {
            const fnName = m[1];
            if (EXCLUDED_FNS.has(fnName)) continue;
            if (discoveredIds.has(fnName)) continue;

            const region = findFunctionRegion(content, fnName);
            if (region) {
                // Armor branches in the renderer are one-line dispatches to draw*Texture fns —
                // the textures ARE the editable armor art, so they surface as the armor group.
                const type = fnName.endsWith('Texture') ? 'armor'
                    : BODY_FNS.has(fnName) ? 'body'
                    : CREATURE_FNS.has(fnName) ? 'creature'
                    : (file.includes('MountRenderer') || file.includes('Stilts') ? 'creature' : 'building');
                assets.push({ type, id: fnName, label: BODY_LABELS[fnName] || fnName.replace('draw', ''), source: content.slice(region.bodyStart, region.bodyEnd) });
                discoveredIds.add(fnName);
            }
        }
    }

    if (tapestryContent) {
        for (const a of discoverGearRegions(tapestryContent)) {
            if (discoveredIds.has(a.id)) continue;
            assets.push(a);
            discoveredIds.add(a.id);
        }
    }

    if (tapestryContent) {
        const whenAnchor = ANCILLARY_WHEN_ANCHOR.exec(tapestryContent);
        if (whenAnchor) {
            const whenStart = whenAnchor.index;
            const rest = tapestryContent.slice(whenStart);
            const ancRe = /com\.example\.game\.Ancillary\.(\w+) ->/g;
            let m;
            while ((m = ancRe.exec(rest)) !== null) {
                const ancId = m[1];
                if (discoveredIds.has(ancId)) continue;
                const r = findAncillaryRegion(tapestryContent, ancId);
                if (r) {
                    assets.push({ type: 'ancillary', id: ancId, label: ancId, source: tapestryContent.slice(r.bodyStart, r.bodyEnd) });
                    discoveredIds.add(ancId);
                }
            }
        }

        for (const c of findCustomRegions(tapestryContent)) {
            assets.push({ type: 'custom', id: c.id, label: c.id, source: c.body });
            discoveredIds.add(c.id);
        }
    }

    return { assets, discoveredIds };
}

function findFunctionRegion(content, fnName) {
    // Optional return type: fun drawX(...): Color {
    return locateRegion(content, new RegExp(`(?:private |internal )?fun ${fnName}\\([^)]*\\)(?:\\s*:\\s*[\\w.<>?]+)?\\s*\\{`));
}

// Searches every renderer file for the function; returns { file, region } or null.
function findFunctionRegionAcrossFiles(fnName) {
    for (const file of RENDERER_FILES) {
        const fullPath = path.join(RENDERER_DIR, file);
        if (!fs.existsSync(fullPath)) continue;
        const content = fs.readFileSync(fullPath, 'utf-8');
        const region = findFunctionRegion(content, fnName);
        if (region) return { file: fullPath, content, region };
    }
    return null;
}

function findAncillaryRegion(content, ancId) {
    const whenAnchor = ANCILLARY_WHEN_ANCHOR.exec(content);
    if (!whenAnchor) return null;
    const whenStart = whenAnchor.index;
    const branchRegex = new RegExp(`com\\.example\\.game\\.Ancillary\\.${ancId} -> \\{`);
    const rest = content.slice(whenStart);
    const bm = branchRegex.exec(rest);
    if (!bm) return null;
    const openIdx = whenStart + bm.index + bm[0].length - 1;
    const closeIdx = findMatchingBrace(content, openIdx);
    if (closeIdx === -1) return null;
    return { bodyStart: openIdx + 1, bodyEnd: closeIdx };
}

function findCustomRegions(content) {
    const out = [];
    const re = /\/\/ --- CUSTOM (\w+) ---\n([\s\S]*?)\/\/ --- END CUSTOM \1 ---/g;
    let m;
    while ((m = re.exec(content)) !== null) {
        out.push({ id: m[1], body: m[2] });
    }
    return out;
}

app.get('/api/assets', (req, res) => {
    try {
        const { assets } = discoverAssets();
        res.json({ success: true, assets });
    } catch (err) {
        console.error(err);
        res.status(500).json({ success: false, message: err.message });
    }
});

app.post('/api/asset/save', (req, res) => {
    try {
        const { type, id, source } = req.body;
        const { discoveredIds } = discoverAssets();

        if (type === 'building' || type === 'creature' || type === 'body' || id.startsWith('draw')) {
            if (!discoveredIds.has(id)) throw new Error(`Unknown asset id "${id}"`);
            const hit = findFunctionRegionAcrossFiles(id);
            if (!hit) throw new Error(`Could not re-locate "${id}" in any renderer file (has the surrounding code changed?)`);
            const newContent = hit.content.slice(0, hit.region.bodyStart) + source + hit.content.slice(hit.region.bodyEnd);
            fs.writeFileSync(hit.file, newContent);
            res.json({ success: true, message: `Saved "${id}" back into ${path.basename(hit.file)}.` });
            return;
        }

        let content = fs.readFileSync(TAPESTRY_RENDERER_PATH, 'utf-8');
        let region;

        if (GEAR_REGION_FINDERS[type]) {
            region = findGearRegion(content, type, id);
        } else if (type === 'ancillary') {
            if (!discoveredIds.has(id)) throw new Error(`Unknown ancillary id "${id}"`);
            region = findAncillaryRegion(content, id);
        } else if (type === 'custom') {
            const markerStart = `// --- CUSTOM ${id} ---\n`;
            const markerEnd = `// --- END CUSTOM ${id} ---`;
            const startIdx = content.indexOf(markerStart);
            const endIdx = content.indexOf(markerEnd);
            if (startIdx === -1 || endIdx === -1) throw new Error(`Could not find CUSTOM block "${id}"`);
            region = { bodyStart: startIdx + markerStart.length, bodyEnd: endIdx };
        } else {
            throw new Error(`Unknown asset type "${type}"`);
        }

        if (!region) throw new Error(`Could not re-locate "${id}" in TapestryRenderer.kt (has the surrounding code changed?)`);

        content = content.slice(0, region.bodyStart) + source + content.slice(region.bodyEnd);
        fs.writeFileSync(TAPESTRY_RENDERER_PATH, content);
        res.json({ success: true, message: `Saved "${id}" back into TapestryRenderer.kt.` });
    } catch (err) {
        console.error(err);
        res.status(500).json({ success: false, message: err.message });
    }
});

// --- Data-driven art (assets/art/*.json) ---
// The game reads these directly. No Kotlin, no rebuild: edit here, relaunch the app, see it.

const ART_DIR = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'assets', 'art');
const VALID_OPS = new Set(['M', 'L', 'Q', 'C', 'Z', 'RECT', 'OVAL', 'CIRCLE', 'LINE']);
const ARG_COUNT = { M: 2, L: 2, Q: 4, C: 6, Z: 0, RECT: 4, OVAL: 4, CIRCLE: 3, LINE: 4 };

/** Rejects anything the Kotlin loader would choke on, so a bad save cannot break the game. */
function validateArt(art) {
    const errs = [];
    if (!art || typeof art !== 'object') return ['Asset must be a JSON object.'];
    if (!art.id) errs.push('Asset needs an id.');
    if (!art.palette || typeof art.palette !== 'object') errs.push('Asset needs a palette object.');
    if (!Array.isArray(art.layers)) errs.push('Asset needs a layers array.');

    const paletteKeys = new Set(Object.keys(art.palette || {}));
    for (const [key, hex] of Object.entries(art.palette || {})) {
        if (!/^#?[0-9a-fA-F]{6}$/.test(String(hex).replace(/^0x/i, '').replace(/^ff/i, ''))) {
            if (!/^#[0-9a-fA-F]{6}$/.test(hex)) errs.push(`Palette "${key}" is not a #RRGGBB colour.`);
        }
    }
    (art.layers || []).forEach((layer, i) => {
        const where = `Layer ${i} (${layer.id || 'unnamed'})`;
        if (layer.fill && !paletteKeys.has(layer.fill)) errs.push(`${where} fills with "${layer.fill}", which is not in the palette.`);
        if (layer.stroke && !paletteKeys.has(layer.stroke)) errs.push(`${where} strokes with "${layer.stroke}", which is not in the palette.`);
        if (!Array.isArray(layer.commands)) { errs.push(`${where} has no commands array.`); return; }
        layer.commands.forEach((cmd, c) => {
            if (!Array.isArray(cmd) || cmd.length === 0) { errs.push(`${where}, command ${c} is empty.`); return; }
            const op = String(cmd[0]).toUpperCase();
            if (!VALID_OPS.has(op)) { errs.push(`${where}, command ${c}: "${cmd[0]}" is not a known op.`); return; }
            if (cmd.length - 1 !== ARG_COUNT[op]) {
                errs.push(`${where}, command ${c}: ${op} takes ${ARG_COUNT[op]} numbers, got ${cmd.length - 1}.`);
            }
            cmd.slice(1).forEach(n => { if (typeof n !== 'number' || !isFinite(n)) errs.push(`${where}, command ${c}: "${n}" is not a number.`); });
        });
    });
    for (const [name, a] of Object.entries(art.anchors || {})) {
        if (['x0', 'x1', 'y'].some(k => typeof a[k] !== 'number')) errs.push(`Anchor "${name}" needs numeric x0, x1 and y.`);
    }
    return errs;
}

app.get('/api/art', (req, res) => {
    try {
        if (!fs.existsSync(ART_DIR)) return res.json([]);
        const list = fs.readdirSync(ART_DIR).filter(f => f.endsWith('.json')).map(f => {
            const art = JSON.parse(fs.readFileSync(path.join(ART_DIR, f), 'utf-8'));
            return {
                id: art.id || f.replace(/\.json$/, ''),
                layers: (art.layers || []).length,
                spawns: !!art.spawn,
                anchors: Object.keys(art.anchors || {})
            };
        });
        res.json(list);
    } catch (err) {
        res.status(500).json({ success: false, message: err.message });
    }
});

app.get('/api/art/:id', (req, res) => {
    const file = path.join(ART_DIR, `${path.basename(req.params.id)}.json`);
    if (!fs.existsSync(file)) return res.status(404).json({ success: false, message: `No asset called "${req.params.id}".` });
    res.json(JSON.parse(fs.readFileSync(file, 'utf-8')));
});

app.post('/api/art/:id', (req, res) => {
    const errs = validateArt(req.body);
    if (errs.length) return res.status(400).json({ success: false, message: errs.join('\n') });
    try {
        if (!fs.existsSync(ART_DIR)) fs.mkdirSync(ART_DIR, { recursive: true });
        const file = path.join(ART_DIR, `${path.basename(req.params.id)}.json`);
        fs.writeFileSync(file, JSON.stringify(req.body, null, 2) + '\n');
        res.json({ success: true, message: `Saved ${req.params.id}. Relaunch the app to see it.` });
    } catch (err) {
        res.status(500).json({ success: false, message: err.message });
    }
});

// --- Gear catalogue: every equippable id + display name, parsed from SimulationModels.kt ---
app.get('/api/gear', (req, res) => {
    try {
        const content = fs.readFileSync(SIMULATION_MODELS_PATH, 'utf-8');
        const buckets = { head: [], handle: [], shield: [], armor: [], helm: [] };
        const re = /\("((?:head|handle|shield|armor|helm)_\w+)",\s*"([^"]+)"/g;
        let m;
        while ((m = re.exec(content)) !== null) {
            const prefix = m[1].split('_')[0];
            if (buckets[prefix] && !buckets[prefix].some(g => g.id === m[1])) {
                buckets[prefix].push({ id: m[1], name: m[2] });
            }
        }
        // Stage subjects for the true renderer: building types and ancillary enum names
        const backgrounds = [];
        const bgBlock = content.match(/enum class BackgroundObjectType \{([\s\S]*?)\n\}/);
        if (bgBlock) {
            // Strip comments first, then take every NAME — several sit on one line
            const body = bgBlock[1].replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*/g, '');
            for (const bm of body.matchAll(/\b([A-Z][A-Z0-9_]+)\b/g)) backgrounds.push(bm[1]);
        }
        const ancillaries = [...content.matchAll(/^\s*([A-Z][A-Z0-9_]+)\("anc_/gm)].map(m => m[1]);
        res.json({ success: true, gear: buckets, backgrounds, ancillaries });
    } catch (err) {
        res.status(500).json({ success: false, message: err.message });
    }
});

// --- True preview: the game's own Kotlin renderer draws the frames via a Robolectric test ---
// The client POSTs a loadout+animation spec; we write it where MagazinePreviewTest reads it,
// run that one test, and answer with the frame PNG urls as soon as the test drops its marker.
const { spawn } = require('child_process');
const REPO_ROOT = path.join(__dirname, '..', '..');
const PREVIEW_DIR = path.join(REPO_ROOT, 'app', 'build', 'magazine_preview');
let previewRunning = false;

app.post('/api/preview', (req, res) => {
    if (previewRunning) return res.status(409).json({ success: false, message: 'A render is already in progress — wait for it to finish.' });
    previewRunning = true;
    try {
        fs.mkdirSync(PREVIEW_DIR, { recursive: true });
        for (const f of fs.readdirSync(PREVIEW_DIR)) {
            if (f.endsWith('.png') || f === 'done.marker') fs.unlinkSync(path.join(PREVIEW_DIR, f));
        }
        fs.writeFileSync(path.join(PREVIEW_DIR, 'spec.json'), JSON.stringify(req.body, null, 2));
    } catch (err) {
        previewRunning = false;
        return res.status(500).json({ success: false, message: err.message });
    }

    const child = spawn('gradle', [':app:testDebugUnitTest', '--tests', 'com.example.game.MagazinePreviewTest', '--rerun', '--console=plain'],
        { cwd: REPO_ROOT, shell: true, stdio: ['ignore', 'pipe', 'pipe'] });
    let log = '';
    child.stdout.on('data', d => { log += d; });
    child.stderr.on('data', d => { log += d; });

    const t0 = Date.now();
    let exited = null;
    child.on('exit', code => { exited = code; });

    // The gradle daemon on this machine sometimes finishes the work but never returns to the
    // client (known wedge) — so we answer off the test's own done.marker, not gradle's exit.
    const poll = setInterval(() => {
        if (fs.existsSync(path.join(PREVIEW_DIR, 'done.marker'))) {
            clearInterval(poll); previewRunning = false;
            try { child.kill(); } catch (e) {}
            const frames = fs.readdirSync(PREVIEW_DIR)
                .filter(f => /^frame_\d+\.png$/.test(f))
                .sort((a, b) => parseInt(a.match(/\d+/)[0]) - parseInt(b.match(/\d+/)[0]))
                .map(f => `/preview/${f}?t=${Date.now()}`);
            return res.json({ success: true, frames });
        }
        const timedOut = Date.now() - t0 > 300000;
        if (timedOut || (exited !== null && exited !== 0)) {
            clearInterval(poll); previewRunning = false;
            try { child.kill(); } catch (e) {}
            return res.status(500).json({
                success: false,
                message: (timedOut ? 'Render timed out after 5 minutes.\n' : `gradle exited with code ${exited}.\n`) + log.slice(-3000)
            });
        }
    }, 2000);
});

app.use('/preview', express.static(PREVIEW_DIR));

app.use(express.static(__dirname));

const PORT = 3000;
if (require.main === module) {
    app.listen(PORT, () => {
        console.log(`Vector Builder Server running on http://localhost:${PORT}`);
    });
}

module.exports = { findMatchingBrace, locateRegion, findFunctionRegion, findFunctionRegionAcrossFiles, findAncillaryRegion, findCustomRegions, discoverAssets };
