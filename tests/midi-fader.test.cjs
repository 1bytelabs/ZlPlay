const assert = require('node:assert/strict');
const test = require('node:test');
const midi = import('../app/midi-fader.mjs');

test('MIDI Note learns from note-on and ignores both forms of note-off', async () => {
  const { readMidiControl, midiFaderValue } = await midi;
  const message = readMidiControl(Uint8Array.of(0x92, 60, 64));
  assert.deepEqual(message, { binding: { type: 'midi-note', channel: 2, note: 60 }, value: 64 });
  assert.equal(midiFaderValue(message.binding, 1), 0);
  assert.equal(midiFaderValue(message.binding, message.value), 0.5);
  assert.equal(midiFaderValue(message.binding, 127), 1);
  assert.equal(readMidiControl(Uint8Array.of(0x82, 60, 64)), null);
  assert.equal(readMidiControl(Uint8Array.of(0x92, 60, 0)), null);
  assert.equal(readMidiControl(Uint8Array.of(0x92, 60)), null);
});

test('absolute CC and pitch bend follow the positions sent by the controller', async () => {
  const { readMidiControl, midiFaderValue } = await midi;
  const cc = readMidiControl(Uint8Array.of(0xb3, 16, 96));
  assert.deepEqual(cc.binding, { type: 'midi-cc', channel: 3, cc: 16 });
  assert.equal(midiFaderValue(cc.binding, cc.value), 96 / 127);
  const pitchBend = readMidiControl(Uint8Array.of(0xe3, 55, 64));
  assert.deepEqual(pitchBend.binding, { type: 'midi-pitch-bend', channel: 3 });
  assert.equal(pitchBend.value, 8247);
  assert.equal(midiFaderValue(pitchBend.binding, 0), 0);
  assert.equal(midiFaderValue(pitchBend.binding, 16383), 1);
});

test('relative knobs adjust the current fader in both directions', async () => {
  const { midiFaderValue } = await midi;
  for (const [mode, up, down, neutral] of [
    ['twos-complement', 1, 127, 0],
    ['binary-offset', 65, 63, 64],
    ['sign-magnitude', 1, 65, 64]
  ]) {
    const binding = { type: 'midi-cc', mode };
    assert.equal(midiFaderValue(binding, up, 0.5), 0.5 + 1 / 127);
    assert.equal(midiFaderValue(binding, down, 0.5), 0.5 - 1 / 127);
    assert.equal(midiFaderValue(binding, neutral, 0.5), null);
    assert.equal(midiFaderValue(binding, up, 1), 1);
    assert.equal(midiFaderValue(binding, down, 0), 0);
  }
});
