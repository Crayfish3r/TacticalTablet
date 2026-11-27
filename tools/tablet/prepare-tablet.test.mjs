import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {compile, sourcePath, modelPath, texturePath, animationVector, faceCorners, rotate} from './prepare-tablet.mjs';

const source = readFileSync(sourcePath, 'utf8');

test('committed geometry and PNG reproduce the source byte for byte', () => {
  const result = compile(source);
  assert.equal(result.json, readFileSync(modelPath, 'utf8'));
  assert.deepEqual(result.texture, readFileSync(texturePath));
  const model = JSON.parse(result.json);
  assert.equal(model.cubes.length, 156);
  assert.equal(model.groups.length, 8);
  assert.equal(model.animations.draw.length, 0.65);
  assert.equal(model.animations.holster.length, 0.45);
  assert.equal(model.animations.draw.rotation.length, 25);
});

test('Blockbench 4.x animation signs differ from rest Euler rotations', () => {
  assert.deepEqual(animationVector('position', [16, 32, 48]), [-1, 2, 3]);
  assert.deepEqual(animationVector('rotation', [30, 40, 50]), [-30, -40, 50]);
  const v = rotate([1, 0, 0], [90, 90, 90]);
  assert.ok(Math.abs(v[0]) < 1e-12 && Math.abs(v[1]) < 1e-12 && Math.abs(v[2] + 1) < 1e-12);
  const model = JSON.parse(compile(source).json);
  assert.deepEqual(model.animations.draw.rotation[0], [0, -54, 23, -14]);
  assert.deepEqual(model.animations.holster.rotation.at(-1), [0.45, -54, 23, -14]);
});

test('display north face retains four UV corners and readable front basis', () => {
  const screen = JSON.parse(compile(source).json).cubes.find(c => c.name === 'screen_glass');
  const vertices = screen.vertices.slice(0,4);
  assert.deepEqual(vertices.map(v => v.slice(3,5)), [[16,16], [16,400], [656,400], [656,16]].map(uv => uv.map(n => n / 1024)));
  assert.ok(vertices[0][0] > vertices[3][0]); // NORTH viewed from -Z: positive X is left.
  const cameraLeft = rotate(vertices[0].slice(0,3), [0,180,0]);
  const cameraRight = rotate(vertices[3].slice(0,3), [0,180,0]);
  assert.ok(cameraLeft[0] < cameraRight[0]); // Proper Y rotation, no mirror scale.
  assert.deepEqual(vertices[0].slice(5), [0,0,-1]);
});

test('every face has outward winding, including up/down', () => {
  for (const name of ['north','south','east','west','up','down']) {
    const [a,b,c] = faceCorners(name, [-1,-2,-3], [1,2,3]);
    const u = b.map((x,i) => x-a[i]), v = c.map((x,i) => x-a[i]);
    const n = [u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0]];
    assert.ok(n.reduce((sum,x,i) => sum + x*a[i], 0) > 0, name);
  }
});

test('nested group pivots, cube rotations, reversed UV and quarter turns survive', () => {
  const m = JSON.parse(source);
  const group = m.outliner[0].children[0];
  const cube = m.elements.find(c => c.uuid === group.children[0]);
  cube.from = [1,0,0]; cube.to = [2,1,1]; cube.origin = [1,0,0]; cube.rotation = [0,0,90];
  group.origin = [0,0,0]; group.rotation = [0,90,0];
  cube.faces.north.uv = [100,200,20,40]; cube.faces.north.rotation = 90;
  const result = JSON.parse(compile(JSON.stringify(m)).json).cubes.find(c => c.id === cube.uuid);
  const topLeft = result.vertices[0];
  // Cube TL [2,1,0] -> rotate around [1,0,0]: [0,1,0]
  // -> group Y90: [0,1,0] -> subtract root [0,8,0] and divide by 16.
  assert.ok(Math.abs(topLeft[0]) < 1e-12);
  assert.ok(Math.abs(topLeft[1] + 7/16) < 1e-12);
  assert.ok(Math.abs(topLeft[2]) < 1e-12);
  assert.deepEqual(topLeft.slice(3,5), [100/1024, 40/1024]);
  assert.equal(result.group, group.uuid);
});

test('unsupported features fail explicitly rather than silently losing geometry/motion', () => {
  for (const mutate of [m => m.elements[0].box_uv = true,
    m => m.animations[0].animators[m.outliner[0].uuid].keyframes[0].interpolation = 'bezier',
    m => m.animations[0].animators[m.outliner[0].uuid].keyframes[0].data_points[0].x = 'query.time']) {
    const m = JSON.parse(source); mutate(m);
    assert.throws(() => compile(JSON.stringify(m)));
  }
});
