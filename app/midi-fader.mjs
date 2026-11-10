export const MIDI_CC_MODES = [
  ['twos-complement', 'MIDI CC relativo (1 / 127)'],
  ['binary-offset', 'MIDI CC relativo (65 / 63)'],
  ['sign-magnitude', 'MIDI CC relativo (1 / 65)']
];

export function readMidiControl(data) {
  if (data?.length !== 3) return null;
  const [status, number, value] = data;
  const channel = status & 0x0f;
  switch (status & 0xf0) {
    case 0x90:
      return value > 0 ? {binding: {type: 'midi-note', channel, note: number}, value} : null;
    case 0xb0:
      return {binding: {type: 'midi-cc', channel, cc: number}, value};
    case 0xe0:
      return {binding: {type: 'midi-pitch-bend', channel}, value: (value << 7) | number};
    default:
      return null;
  }
}

export function midiFaderValue(binding, value, current = 0) {
  if (binding.type === 'midi-note') return (value - 1) / 126;
  if (binding.type === 'midi-pitch-bend') return value / 16383;
  let delta;
  switch (binding.mode) {
    case 'twos-complement':
      delta = value === 64 ? 0 : value > 64 ? value - 128 : value;
      break;
    case 'binary-offset':
      delta = value - 64;
      break;
    case 'sign-magnitude':
      delta = value >= 64 ? 64 - value : value;
      break;
    default:
      return value / 127;
  }
  return delta === 0 ? null : Math.max(0, Math.min(1, current + delta / 127));
}
