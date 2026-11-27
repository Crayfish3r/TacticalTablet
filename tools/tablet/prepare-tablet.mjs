// Deliberately limited to TT01: cube/per-face UV geometry and linear root tracks.
import {readFileSync, writeFileSync, mkdirSync, renameSync} from 'node:fs';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';

export const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
export const sourcePath = resolve(root, 'model-sources/tablet/TacticalTablet_TT01.bbmodel');
export const modelPath = resolve(root, 'src/main/resources/assets/tacticaltablet/tablet/tt01.json');
export const texturePath = resolve(root, 'src/main/resources/assets/tacticaltablet/textures/item/tablet_tt01.png');
const manifestPath = resolve(root, 'src/test/resources/deluxewarfare-runtime-assets.tsv');
const fail = message => { throw new Error(message); };
const vector = v => {
  if (!Array.isArray(v) || v.length !== 3 || v.some(n => !Number.isFinite(Number(n)))) fail('Expected numeric vector');
  return v.map(Number);
};
const add = (a, b) => a.map((v, i) => v + b[i]);
const sub = (a, b) => a.map((v, i) => v - b[i]);
// THREE.Euler ZYX: apply X, then Y, then Z to a column vector. Rest rotations
// retain their signs. Animation signs are different (BoneAnimator.displayFrame).
export function rotate(v, degrees) {
  let [x, y, z] = v;
  const [a, b, c] = degrees.map(n => n * Math.PI / 180);
  [y, z] = [y * Math.cos(a) - z * Math.sin(a), y * Math.sin(a) + z * Math.cos(a)];
  [x, z] = [x * Math.cos(b) + z * Math.sin(b), -x * Math.sin(b) + z * Math.cos(b)];
  return [x * Math.cos(c) - y * Math.sin(c), x * Math.sin(c) + y * Math.cos(c), z];
}
export function animationVector(channel, value) {
  const [x, y, z] = vector(value);
  return channel === 'position' ? [-x / 16, y / 16, z / 16] : [-x, -y, z];
}

// Positions at texture corners TL, BL, BR, TR. Winding faces outward, with no
// UV reflection. Matches CubeFace.UVToLocal in Blockbench 4.12.6.
export function faceCorners(direction, f, t) {
  const [x, y, z] = f, [X, Y, Z] = t;
  switch (direction) {
    case 'north': return [[X,Y,z], [X,y,z], [x,y,z], [x,Y,z]];
    case 'south': return [[x,Y,Z], [x,y,Z], [X,y,Z], [X,Y,Z]];
    case 'east': return [[X,Y,Z], [X,y,Z], [X,y,z], [X,Y,z]];
    case 'west': return [[x,Y,z], [x,y,z], [x,y,Z], [x,Y,Z]];
    case 'up': return [[x,Y,z], [x,Y,Z], [X,Y,Z], [X,Y,z]];
    case 'down': return [[x,y,Z], [x,y,z], [X,y,z], [X,y,Z]];
    default: return fail(`Unknown face ${direction}`);
  }
}

export function compile(source) {
  const m = JSON.parse(source);
  const version = String(m.meta?.format_version);
  if (m.meta?.model_format !== 'free' || !(version.startsWith('4.') || version === '5.0'))
    fail('Expected Blockbench 4.x or 5.0 Generic/free');
  if (m.textures?.length !== 1 || !m.textures[0].source?.startsWith('data:image/png;base64,')) fail('Expected one embedded PNG');
  const texture = Buffer.from(m.textures[0].source.split(',')[1], 'base64');
  if (texture.subarray(0, 8).toString('hex') !== '89504e470d0a1a0a') fail('Invalid PNG');
  const width = texture.readUInt32BE(16), height = texture.readUInt32BE(20);
  if (width !== m.resolution.width || height !== m.resolution.height) fail('Texture/UV size mismatch');
  const uuid = id => typeof id === 'string' && /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(id);
  if (!Array.isArray(m.elements) || m.elements.some(e => !uuid(e.uuid))) fail('Invalid element UUID');
  const elements = new Map(m.elements.map(e => [e.uuid, e]));
  if (elements.size !== m.elements.length) fail('Duplicate element UUID');
  const groupMetadata = new Map();
  for (const group of m.groups ?? []) {
    if (!uuid(group.uuid) || elements.has(group.uuid) || groupMetadata.has(group.uuid)) fail('Invalid/duplicate group UUID');
    groupMetadata.set(group.uuid, group);
  }
  const groupIds = new Set();
  function normalizeGroup(entry) {
    const id = typeof entry === 'string' ? entry : entry.uuid;
    if (!uuid(id) || elements.has(id) || groupIds.has(id)) fail('Invalid/duplicate group in hierarchy');
    groupIds.add(id);
    const metadata = groupMetadata.get(id) ?? (version.startsWith('4.') ? entry : null);
    if (!metadata || typeof metadata.name !== 'string') fail('Missing group metadata');
    const children = typeof entry === 'string' ? metadata.children ?? [] : entry.children ?? [];
    return {...metadata, ...(typeof entry === 'object' ? entry : {}),
      origin: metadata.origin ?? [0,0,0], rotation: metadata.rotation ?? [0,0,0],
      children: children.map(child => typeof child === 'string' && elements.has(child) ? child : normalizeGroup(child))};
  }
  if (!Array.isArray(m.outliner)) fail('Missing hierarchy');
  m.outliner = m.outliner.map(normalizeGroup);
  if (groupMetadata.size && groupIds.size !== groupMetadata.size) fail('Incomplete group hierarchy');
  if (m.outliner.length !== 1 || m.outliner[0].name !== 'tablet_root') fail('Expected single tablet_root');
  const rootGroup = m.outliner[0], rootOrigin = vector(rootGroup.origin);
  const groups = [], cubes = [], visited = new Set();
  const controls = {keys: {}, indicators: {}};
  let displaySurface;
  function walk(group, parents = []) {
    if (group.export === false || group.visibility === false) fail('Hidden groups are unsupported in TT01');
    const origin = vector(group.origin), rotation = vector(group.rotation ?? [0,0,0]);
    groups.push({id: group.uuid, name: group.name, parent: parents.at(-1)?.uuid ?? null, origin, rotation});
    for (const child of group.children) {
      if (typeof child !== 'string') { walk(child, [...parents, group]); continue; }
      const cube = elements.get(child);
      if (!cube || visited.has(child)) fail('Missing/duplicate cube in hierarchy');
      visited.add(child);
      if (cube.type !== 'cube' || cube.box_uv || cube.rescale || cube.inflate
          || (cube.stretch && cube.stretch.some(n => n !== 1)) || cube.export === false || cube.visibility === false) fail(`Unsupported cube ${cube.name}`);
      const pivot = vector(cube.origin ?? [0,0,0]), angles = vector(cube.rotation ?? [0,0,0]);
      const f = vector(cube.from), t = vector(cube.to);
      if (t.some((n, i) => n < f[i])) fail('Inverted cube geometry');
      const chain = [...parents, group].slice(1).reverse(); // root transform is animated at runtime
      const transform = (point, normal = false) => {
        let v = normal ? rotate(point, angles) : add(rotate(sub(point, pivot), angles), pivot);
        for (const g of chain) v = normal ? rotate(v, g.rotation ?? [0,0,0])
          : add(rotate(sub(v, g.origin), g.rotation ?? [0,0,0]), g.origin);
        return normal ? v : sub(v, rootOrigin).map(n => n / 16);
      };
      const vertices = [];
      for (const [direction, face] of Object.entries(cube.faces)) {
        if (face.texture === null || face.enabled === false) continue;
        if (face.texture !== 0) fail('Unexpected texture reference');
        const corners = faceCorners(direction, f, t);
        const normal = {north:[0,0,-1], south:[0,0,1], east:[1,0,0], west:[-1,0,0], up:[0,1,0], down:[0,-1,0]}[direction];
        const [u, v, U, V] = face.uv;
        if (![u,v,U,V].every(Number.isFinite)) fail('Invalid per-face UV');
        const turns = (face.rotation ?? 0) / 90;
        if (!Number.isInteger(turns) || turns < 0 || turns > 3) fail('Unsupported UV rotation');
        const uv = [[u,v], [u,V], [U,V], [U,v]];
        if (group.name === 'display_surface' && cube.name === 'screen_glass' && direction === 'north') {
          if (displaySurface) fail('Duplicate display surface');
          // TL, BL, BR, TR in the same root-relative basis as the body, before animation.
          displaySurface = {cube: child, firstVertex: vertices.length,
            corners: corners.map(p => transform(p)), normal: transform(normal, true)};
        }
        corners.forEach((p, i) => {
          const tex = uv[(i + turns) % 4];
          vertices.push([...transform(p), tex[0] / width, tex[1] / height, ...transform(normal, true)]);
        });
      }
      cubes.push({id: child, name: cube.name, group: group.uuid, origin: pivot, rotation: angles, from: f, to: t, vertices});
      // Keep named parts separate at runtime; their mesh already has the full pivot/parent transform.
      const parts = /^hardware_key_[0-5]$/.test(cube.name) ? controls.keys
        : /^key_index_[0-5]$/.test(cube.name) ? controls.indicators : null;
      if (parts) {
        if (parts[cube.name] || vertices.length !== 24) fail(`Missing/duplicate complete control cube ${cube.name}`);
        parts[cube.name] = child;
      }
    }
  }
  walk(rootGroup);
  if (!displaySurface) fail('Missing display_surface/screen_glass north face');
  const keyCount = Object.keys(controls.keys).length;
  // Original 4.x three-key sources remain readable; the current 5.0 model requires all six.
  if (!(keyCount === 6 || version.startsWith('4.') && keyCount === 3)
      || Object.keys(controls.indicators).length !== keyCount
      || Array.from({length:keyCount}, (_, i) => i).some(i => !controls.keys[`hardware_key_${i}`] || !controls.indicators[`key_index_${i}`]))
    fail('Expected complete hardware_key_0..5 and key_index_0..5');
  if (visited.size !== elements.size || !groups.some(g => g.name === 'display_surface')) fail('Incomplete hierarchy/display');
  const animations = {};
  for (const a of m.animations) {
    if (!['draw', 'holster'].includes(a.name) || animations[a.name] || a.loop !== 'hold' || a.override
        || a.anim_time_update || a.blend_weight || a.start_delay || a.loop_delay) fail('Unsupported animation');
    if (Object.keys(a.animators).length !== 1) fail('TT01 animates only its root');
    const animator = a.animators[rootGroup.uuid];
    if (animator?.type !== 'bone' || animator.rotation_global) fail('Expected local root bone tracks');
    const tracks = {position: [], rotation: []};
    for (const k of animator.keyframes) {
      if (!(k.channel in tracks) || k.interpolation !== 'linear' || k.data_points.length !== 1) fail('Only single-value linear root tracks are supported');
      const p = k.data_points[0];
      if (!Number.isFinite(k.time) || k.time < 0 || k.time > a.length) fail('Invalid keyframe time');
      tracks[k.channel].push([k.time, ...animationVector(k.channel, [p.x, p.y, p.z])]);
    }
    for (const frames of Object.values(tracks)) {
      frames.sort((a,b) => a[0] - b[0]);
      if (!frames.length || frames[0][0] !== 0 || frames.at(-1)[0] !== a.length
          || frames.some((f, i) => i > 0 && f[0] <= frames[i-1][0])) fail('Incomplete/duplicate track times');
    }
    animations[a.name] = {length: a.length, ...tracks};
  }
  if (animations.draw?.length !== 0.65 || animations.holster?.length !== 0.45) fail('Expected draw=0.65 and holster=0.45 seconds');
  const data = {format: 1, sourceSha256: createHash('sha256').update(source).digest('hex'),
    texture: 'tacticaltablet:textures/item/tablet_tt01.png', width, height,
    rootRotation: vector(rootGroup.rotation), groups, cubes, displaySurface, controls, animations};
  return {json: JSON.stringify(data) + '\n', texture};
}

function atomicWrite(path, contents) {
  mkdirSync(dirname(path), {recursive: true});
  writeFileSync(path + '.tmp', contents);
  renameSync(path + '.tmp', path);
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = compile(readFileSync(sourcePath, 'utf8'));
  const assetEntries = [
    ['tacticaltablet/tablet/tt01.json', Buffer.from(result.json)],
    ['tacticaltablet/textures/item/tablet_tt01.png', result.texture]
  ].map(([name, bytes]) => [name, bytes.length, createHash('sha256').update(bytes).digest('hex'),
    ...(name.endsWith('.png') ? [bytes.readUInt32BE(16), bytes.readUInt32BE(20), [4,6].includes(bytes[25])] : [])].join('\t'));
  const manifestLines = readFileSync(manifestPath, 'utf8').trimEnd().split(/\r?\n/);
  if (process.argv.includes('--check')) {
    if (readFileSync(modelPath, 'utf8') !== result.json || !readFileSync(texturePath).equals(result.texture)) fail('Generated TT01 resources are stale');
    if (assetEntries.some(entry => !manifestLines.includes(entry))) fail('TT01 asset manifest is stale');
    console.log('TT01 resources match the source');
  } else {
    atomicWrite(modelPath, result.json);
    atomicWrite(texturePath, result.texture);
    const names = assetEntries.map(line => line.split('\t')[0]);
    const header = manifestLines.shift();
    atomicWrite(manifestPath, [header, ...manifestLines.filter(line => !names.includes(line.split('\t')[0])).concat(assetEntries).sort()].join('\n') + '\n');
    console.log('Prepared TT01 geometry, root animations and embedded PNG');
  }
}
