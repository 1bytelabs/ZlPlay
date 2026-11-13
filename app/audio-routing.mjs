export const MASTER_CHANNEL = 64;
export const MAIN_AUDIO_ROUTES = {
  master: ['masterOutput', 'stereo:0'],
  click: ['clickOutput', 'master'],
  guide: ['guideOutput', 'master'],
  pad: ['padOutput', 'master'],
  vs: ['vsOutput', 'master'],
};

export function mainAudioRoute(player, key, fallbackOverride) {
  const [field, fallback] = MAIN_AUDIO_ROUTES[key];
  return (!player._levelAuditionTarget && player.sharedAudioRouting?.[key]) || player[field] || fallbackOverride || fallback;
}

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
  if (player.isClickTrack?.(track)) return mainAudioRoute(player, 'click');
  if (player.isGuideTrack?.(track)) return mainAudioRoute(player, 'guide', track.output);
  if (track.output) return track.output;
  if (player.isSpecialTrack(track)) return 'master';
  return player.ensureGroupState(track.group || 'VS').output || mainAudioRoute(player, 'vs');
}
