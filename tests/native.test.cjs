const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const { NativeAudio } = require('../desktop/native-audio.cjs');
const executable = require('../desktop/runtime-paths.cjs').nativeExecutable(path.resolve('native/bin'));
function wav(seconds, fn) {
  const frames = Math.round(seconds * 44100), b = Buffer.alloc(44 + frames * 4);
  b.write('RIFF'); b.writeUInt32LE(b.length - 8, 4); b.write('WAVEfmt ', 8); b.writeUInt32LE(16, 16); b.writeUInt16LE(1, 20); b.writeUInt16LE(2, 22); b.writeUInt32LE(44100, 24); b.writeUInt32LE(176400, 28); b.writeUInt16LE(4, 32); b.writeUInt16LE(16, 34); b.write('data', 36); b.writeUInt32LE(frames * 4, 40);
  for (let i = 0; i < frames; i++) { const value = Math.round(fn(i) * 32767); b.writeInt16LE(value, 44 + i * 4); b.writeInt16LE(value, 46 + i * 4); } return b;
}
function power(samples, frequency) {
  let re = 0, im = 0; const n = samples.length / 2;
  for (let i = 0; i < n; i++) { const v = samples[2 * i] * (.5 - .5 * Math.cos(2 * Math.PI * i / (n - 1))); const angle = 2 * Math.PI * frequency * i / 44100; re += v * Math.cos(angle); im += v * Math.sin(angle); }
  return re * re + im * im;
}
test('motor nativo: referência local, mix, cortes, loop e tom sem PCM integral', { timeout: 60000 }, async () => {
  await fs.mkdir('tools/native-tests', { recursive: true });
  const tone = path.resolve('tools/native-tests/tone.wav'), impulse = path.resolve('tools/native-tests/impulse.wav');
  await fs.writeFile(tone, wav(3, i => .2 * Math.sin(2 * Math.PI * 440 * i / 44100)));
  await fs.writeFile(impulse, wav(3, i => i === 4410 ? .5 : 0));
  const engine = new NativeAudio(executable);
  try {
    const [meta] = await engine.request({ op: 'probe', files: [{ token: 'tone', path: tone }] });
    assert.equal(meta.sampleRate, 44100); assert.equal(meta.duration, 3);
    const load = (tracks, extra = {}) => engine.request({ op: 'load', offline: true, duration: 3, sampleRate: 44100, tracks, ...extra });
    const track = (file, extra = {}, clip = {}) => ({ gain: 1, pan: 0, pitch: 0, ...extra, items: [{ path: file, position: 0, offset: 0, length: 3, ...clip }] });
    const render = (extra = {}) => engine.request({ op: 'renderTest', frames: 16384, position: .5, capture: true, ...extra });
    await load([track(tone)]); const dry = await render();
    assert.ok(dry.rms > .13 && dry.rms < .15, `rms ${dry.rms}`);
    await load([track(tone), track(tone)]); const double = await render();
    assert.ok(Math.abs(double.rms / dry.rms - 2) < .001);
    await load([track(tone, { gain: 0 })]); assert.equal((await render()).peak, 0);
    await load([track(tone, { pan: -1 })]); const left = await render();
    assert.ok(left.samples.filter((_, i) => i % 2).every(v => Math.abs(v) < 1e-6));
    // The impulse in the file is at .1s. Position .2, source offset .05 => .25s on the project clock.
    await load([track(impulse, {}, { position: .2, offset: .05, length: 2 })]);
    const cut = await render({ position: 0, frames: 16384 });
    let at = -1; for (let i = 0; i < cut.samples.length / 2; i++) if (cut.samples[2 * i] > .49) { at = i; break; }
    assert.equal(at, 11025);
    await load([track(impulse)], { guidePreview: { track: 0, start: .2, end: .4, source: .05 } });
    const preview = await render({ position: 0, frames: 16384 });
    assert.ok(preview.samples[2 * 11025] > .49, 'guide preview must use the requested source interval');
    await load([track(tone)], { loop: { enabled: true, start: .1, end: .2 } });
    const loop = await render({ position: .1, frames: 12000 });
    let difference = 0; for (let i = 0; i < 4410; i++) difference = Math.max(difference, Math.abs(loop.samples[2 * i] - loop.samples[2 * (i + 4410)]));
    assert.ok(difference < 1e-5, `loop ${difference}`);
    await load([track(tone, { pitch: 12 })]); const shifted = await render({ frames: 32768 });
    assert.ok(power(shifted.samples, 880) > power(shifted.samples, 440) * 100, 'pitch should move the dominant tone to 880 Hz');
    await assert.rejects(engine.request({ op: 'probe', files: [{ path: path.resolve('missing.wav') }] }));
  } finally { engine.close(); }
});
