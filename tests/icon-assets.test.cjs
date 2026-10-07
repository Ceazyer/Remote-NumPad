const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
function pngSize(bytes) {
  assert.equal(bytes.subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
  return [bytes.readUInt32BE(16), bytes.readUInt32BE(20)];
}
test('Windows icon contains valid PNG frames for small and high DPI launchers', () => {
  const file = path.join(root, 'assets/app-icon-light/remote-numpad-light.ico');
  assert.ok(fs.existsSync(file), 'The app needs an exported Windows icon');
  const bytes = fs.readFileSync(file);
  assert.equal(bytes.readUInt16LE(0), 0);
  assert.equal(bytes.readUInt16LE(2), 1);
  assert.equal(bytes.readUInt16LE(4), 8);
  const sizes = [];
  for (let index = 0; index < 8; index++) {
    const entry = 6 + index * 16;
    const side = bytes[entry] || 256;
    assert.equal(bytes[entry + 1] || 256, side);
    const length = bytes.readUInt32LE(entry + 8);
    const offset = bytes.readUInt32LE(entry + 12);
    assert.ok(offset >= 134 && offset + length <= bytes.length);
    assert.deepEqual(pngSize(bytes.subarray(offset, offset + length)), [side, side]);
    sizes.push(side);
  }
  assert.deepEqual(sizes, [16, 20, 24, 32, 48, 64, 128, 256]);
});
test('Android has sharp density-specific icons and a large adaptive bitmap', () => {
  const res = path.join(root, 'android/app/src/main/res');
  for (const [density, side] of [['mdpi', 48], ['hdpi', 72], ['xhdpi', 96], ['xxhdpi', 144], ['xxxhdpi', 192]]) {
    const file = path.join(res, `mipmap-${density}/ic_launcher.png`);
    assert.ok(fs.existsSync(file), `Missing ${density} icon`);
    assert.deepEqual(pngSize(fs.readFileSync(file)), [side, side]);
  }
  assert.deepEqual(pngSize(fs.readFileSync(path.join(res, 'drawable-nodpi/app_icon_light.png'))), [512, 512]);
});
