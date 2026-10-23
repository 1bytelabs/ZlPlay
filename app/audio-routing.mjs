export const MASTER_CHANNEL = 64;
export function outputChannels(value) {
  if (value === 'none') return [-1,-1];
  if (!value || value === 'master') return [MASTER_CHANNEL, MASTER_CHANNEL + 1];
  const match = /^(mono|stereo):(\d+)$/.exec(value);
  if (!match) return [MASTER_CHANNEL, MASTER_CHANNEL + 1];
  const left = Number(match[2]);
  if (left > 63 || (match[1] === 'stereo' && left > 62)) return [MASTER_CHANNEL, MASTER_CHANNEL + 1];
  return [left, match[1] === 'mono' ? -1 : left + 1];
}
export function trackOutput(player, track) {
  if (player.isClickTrack?.(track)) return player.clickOutput || 'master';
  if (player.isGuideTrack?.(track)) return player.guideOutput || track.output || 'master';
  if (track.output) return track.output;
  if (player.isSpecialTrack(track)) return 'master';
  return player.ensureGroupState(track.group || 'VS').output || player.vsOutput || 'master';
}
