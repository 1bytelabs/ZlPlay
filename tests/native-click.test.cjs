const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const { NativeAudio } = require('../desktop/native-audio.cjs');
function readPcm24(bytes) {
  let format, data;
  for (let offset = 12; offset + 8 <= bytes.length;) {
    const name = bytes.toString('ascii', offset, offset + 4), size = bytes.readUInt32LE(offset + 4);
    if (name === 'fmt ') format = bytes.subarray(offset + 8, offset + 8 + size);
    if (name === 'data') data = bytes.subarray(offset + 8, offset + 8 + size);
    offset += 8 + size + size % 2;
  }
  assert.equal(format.readUInt16LE(14), 24);
  assert.equal(format.readUInt16LE(2), 2);
  const channels = [[], []];
  for (let i = 0; i < data.length / 6; i++) for (let ch = 0; ch < 2; ch++) channels[ch].push(data.readIntLE(i * 6 + ch * 3, 3) / 8388608);
  return { channels, sampleRate: format.readUInt32LE(4) };
}
function reference(wave, frames, { bpm = 120, accent = false, gain = 1, meter = 4, accentPosition = 0 } = {}) {
  const step = 60 / bpm, output = [];
  for (let frame = 0; frame < frames; frame++) {
    const time = frame / wave.sampleRate, values = [0, 0];
    for (let beat = 0; beat * step <= time + 1e-9; beat++) {
      const strong = accent && beat % meter === accentPosition;
      const at = (time - beat * step) * wave.sampleRate * (strong ? 1.16 : 1);
      if (at < 0 || at >= wave.channels[0].length) continue;
      const index = Math.floor(at), fraction = at - index;
      for (let ch = 0; ch < 2; ch++) {
        const first = wave.channels[ch][index], next = wave.channels[ch][index + 1] || 0;
        values[ch] += (first + (next - first) * fraction) * gain * (strong ? 1.3 : 1);
      }
    }
    output.push(...values);
  }
  return output;
}
test('Click do motor usa o WAV do Solo Click, incluindo acento e caudas sobrepostas', { timeout: 30000 }, async () => {
  const bytes = await fs.readFile('app/Meuclick.wav');
  assert.deepEqual(await fs.readFile('native/bin/Meuclick.wav'), bytes);
  const wave = readPcm24(bytes), frames = 22050;
  const engine = new NativeAudio(require('../desktop/runtime-paths.cjs').nativeExecutable(path.resolve('native/bin')));
  try {
    for (const setting of [{ bpm: 120 }, { bpm: 120, accent: true }, { bpm: 400, accent: true }, { bpm: 120, pitch: 12 }]) {
      await engine.request({ op: 'load', offline: true, sampleRate: 44100, duration: 3,
        tracks: [{ gain: 0, pan: 0, pitch: setting.pitch || 0, items: [{ path: path.resolve('app/Meuclick.wav'), position: 0, offset: 0, length: .414966 }] }],
        click: true, clickGain: 1, accent: !!setting.accent, meter: 4, accentPosition: 0, tempos: [{ time: 0, bpm: setting.bpm }] });
      const result = await engine.request({ op: 'renderTest', frames, position: 0, capture: true });
      const expected = reference(wave, frames, setting);
      let maximumError = 0;
      for (let i = 0; i < expected.length; i++) maximumError = Math.max(maximumError, Math.abs(expected[i] - result.samples[i]));
      assert.ok(maximumError < .00002, `${JSON.stringify(setting)}: diferença máxima ${maximumError}`);
    }
  } finally { engine.close(); }
});
