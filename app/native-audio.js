import { installPlaybackFeedback } from './playback-feedback.js';
import { installExecutionTempo } from './execution-tempo.js';
import { installUpgradeUi } from './upgrade-ui.js';
import { installLayout } from './desktop-layout.js';
import { installArrangement } from './arrangement-ui.js';
import { installAudioSettings } from './audio-settings.js';
// The existing UI remains responsible for project editing. Native metadata
// objects replace full AudioBuffers only for files selected from this computer.
export function installNativeAudio(Player) {
  const bridge = window.zlplayNative;
  if (!bridge) return;
  const p = Player.prototype;
  const original = Object.fromEntries(Object.getOwnPropertyNames(p).filter(k => typeof p[k] === 'function').map(k => [k, p[k]]));
  const isNative = s => !!s.tracks.length && s.tracks.every(t => t.items.every(it => it.buffer?.nativeRef));
  const fail = (s, error) => { s.playing = false; s.status(`Áudio nativo: ${error.message || error}`, true); };
  const command = (s, data) => bridge.command(data).catch(error => { fail(s, error); throw error; });
  p.wrap = function(file, name) { return { ...original.wrap.call(this, file, name), _nativeFile: file }; };
  const descriptions = new WeakMap();
  p.decodeProjectAudio = async function(file) {
    if (this.tracks.some(t => t.items.some(it => it.buffer && !it.buffer.nativeRef))) return original.decodeProjectAudio.call(this, file);
    const source = file._nativeFile || file;
    if(source?._zlToken)return bridge.describeToken(source._zlToken);
    if (source instanceof File) {
      let promise = descriptions.get(source);
      if (!promise) { promise = bridge.describe(source); descriptions.set(source, promise); }
      const metadata = await promise;
      if (metadata) return metadata;
    }
    return original.decodeProjectAudio.call(this, file);
  };
  p.nativeMix = function() {
    const trackSolo = this.tracks.some(t => t.solo), groupSolo = [...this.groupStates.values()].some(s => s.solo);
    const loop = this.currentLoop && this.currentLoopSection || this.loop && this.loopSection;
    let guidePreview = null;
    if (this.pendingSection) {
      const target = this.pendingSection.section, guide = this.tracks.findIndex(t => this.isGuideTrack(t));
      const previous = this.sections.filter(s => s.start < target.start - .001).reduce((best, s) => !best || s.start > best.start ? s : best, null);
      const source = Math.max(0, target.start - 8 / (this.executionFactor || 1), previous?.start ?? 0), lead = target.start - source;
      if (guide >= 0 && lead > .03 && !this.tracks[guide].mute && (!trackSolo || this.tracks[guide].solo)) guidePreview = { track: guide, start: this.pendingSection.boundary - lead, end: this.pendingSection.boundary, source };
    }
    return {
      master: Number(this.$('[data-master]')?.value ?? 1),
      mix: this.tracks.map(t => {
        const click = this.isClickTrack(t), guide = this.isGuideTrack(t), special = click || guide;
        const state = special ? null : this.ensureGroupState(t.group || 'VS');
        let muted = t.__clickProjectMuted || (this.soloClick && !(click && this.clickSource === 'project')) || t.mute || (loop && guide) || (trackSolo && !t.solo);
        if (!trackSolo && !special && groupSolo && !state.solo) muted = true;
        if (!trackSolo && special && this.vsSolo) muted = true;
        if (!special && (this.vsMute || state.mute)) muted = true;
        const group = this.pitchGroup(t);
        return {
          gain: muted ? 0 : Number(t.volume ?? 1) * (special ? 1 : Number(this.vsVolume ?? 1) * Number(state.volume ?? 1)),
          pan: Math.max(-1, Math.min(1, Number(t.pan || 0) + (special ? 0 : Number(this.vsPan || 0) + Number(state.pan || 0)))),
          pitch: group.base + (group.eligible ? this.liveTranspose : 0)
        };
      }),
      click: this.clickSource === 'native' && !!this.$('[data-metro]')?.checked, clickGain: this.metroGain(), clickPan: Number(this.metroPan || 0),
      meter: Number(this.$('[data-meter]')?.value || 4) === 6 ? 3 : Number(this.$('[data-meter]')?.value || 4),
      accent: !!this.$('[data-accent]')?.checked,
      accentPosition: this.$('[data-accent-position]')?.value === 'last' ? Number(this.$('[data-meter]').value) - 1 : 0,
      tempos: this.tempo.points || [{ time: 0, bpm: this.tempo.bpm }],
      guidePreview,
      jump: this.pendingSection ? { enabled: true, id: this.pendingSection.jumpId || `${this.pendingSection.index}:${this.pendingSection.boundary}:${this.pendingSection.section.start}`, boundary: this.pendingSection.boundary, target: this.pendingSection.section.start } : { enabled: false },
      loop: { enabled: !!loop, start: loop?.start || 0, end: loop?.end || 0 }
    };
  };
  p.nativeSync = function(fadeSeconds = 0) {
    this._nativeFadeSeconds=Math.max(this._nativeFadeSeconds||0,Number(fadeSeconds)||0);
    if (!isNative(this) || !this._nativeConfigured || this._nativeConfiguring) return;
    clearTimeout(this._nativeMixTimer);
    this._nativeMixTimer = setTimeout(() => { const fadeSeconds=this._nativeFadeSeconds||0;this._nativeFadeSeconds=0;command(this, { op: 'mix', ...this.nativeMix(), fadeSeconds }).catch(() => {}); }, 15);
  };
  p.prepareNative = async function() {
    if (!isNative(this)) return false;
    const mix = this.nativeMix();
    const tracks = this.tracks.map((t, i) => ({ ...mix.mix[i], items: t.items.map(it => ({ nativeRef: it.buffer.nativeRef, position: it.position || 0, offset: it.offset || 0, length: it.length ?? Math.max(0, it.buffer.duration - (it.offset || 0)), rate: it.playbackRate || 1 })) }));
    const signature = JSON.stringify(tracks.map(t => t.items));
    if (this._nativeSignature === signature && this._nativeReady && (this._nativeConfigured || this._nativeConfiguring)) { await this._nativeReady; return true; }
    this._nativeSignature = signature; this._nativeConfigured = false; this._nativeConfiguring = true;
    this._nativeReady = command(this, { op: 'load', tracks, duration: this.duration, sampleRate: this.tracks[0].items[0].buffer.sampleRate, ...mix }).then(state => {
      this._nativeConfigured = true; this._nativeConfiguring = false; this._nativeState = { ...state, stamp: performance.now() };
      return state;
    }).catch(error => { this._nativeConfiguring = false; throw error; });
    await this._nativeReady; return true;
  };
  p.finishLoadedProject = function(...args) {
    this._nativeConfigured = false; this._nativeSignature = null;
    original.finishLoadedProject.apply(this, args);
    if (!isNative(this)) return;
    const generation = this._nativeWaveGeneration = (this._nativeWaveGeneration || 0) + 1;
    this.prepareNative().catch(() => {});
    // Waveforms are requested after the UI is available and on a separate native process.
    setTimeout(async () => {
      const buffers = [...new Set(this.tracks.flatMap(t => t.items.map(it => it.buffer)))];
      for (const buffer of buffers) {
        if (this._nativeWaveGeneration !== generation) break;
        try { buffer.peaks = await bridge.peaks(buffer.nativeRef); }
        catch { continue; }
        if (this._nativeWaveGeneration !== generation) break;
        const canvas = this.host.querySelector('.vs-timeline-waveform'); if (canvas) this.drawTimelineWaveform(canvas);
      }
    }, 250);
  };
  p.play = async function(internalResume = false) {
    if (!isNative(this)) return original.play.call(this, internalResume);
    if (this.playing || this._nativeStarting) return;
    this._nativeStarting = true;
    try {
      let handoff=false;
      if(!internalResume){const free=this.standaloneClickActive,bpm=this.standaloneBpm;this.restoreProjectTempo();handoff=free&&this.clickSource==='native'&&this.$('[data-metro]').checked&&this.offset<=.05&&Math.abs(bpm-this.projectBpm())<.02;this.soloClick=false;this.$('[data-solo-click]')?.classList.remove('on');this.standaloneClickActive=false;if(this.currentLoop)this.toggleCurrentLoop(false);if(!handoff)await this.syncNativeAux({soloEnabled:false,soloImmediateStop:true});}
      await this.prepareNative();
      if (this.offset >= this.duration) this.offset = 0;
      const context = this.audioContext();
      this.master.gain.value = Number(this.$('[data-master]')?.value ?? 1);
      await context.resume();
      const state = await command(this, { op: 'play', position: this.offset, ...this.nativeMix(), handoff });
      this._nativeState = { ...state, stamp: performance.now() }; this.playing = true; this.projectLaunchAt = 0;
      this._nativeJumpSerial = state.jumpSerial || 0;
      this.ensureProjectPad(context.currentTime, !internalResume);
      this.tick();
      clearInterval(this._nativePoll);
      this._nativePoll = setInterval(async () => {
        if (this._nativePolling || !this.playing) return;
        this._nativePolling = true;
        const epoch = this._nativeEpoch;
        try { const state = await command(this, { op: 'status' }); if (epoch !== this._nativeEpoch) return; this._nativeState = { ...state, stamp: performance.now() }; if (!state.playing && state.position >= this.duration) this.stop(); }
        catch {} finally { this._nativePolling = false; }
      }, 50);
    } catch (error) { fail(this, error); } finally { this._nativeStarting = false; }
  };
  p.position = function() {
    if (!isNative(this)) return original.position.call(this);
    if (!this.playing) return this.offset;
    if (this._nativeSeeking) return this.offset;
    // The native device sample clock is authoritative; the UI only interpolates between reports.
    const state = this._nativeState;
    if (!state) return this.offset;
    let position = state.position + (state.playing ? Math.min(.1, (performance.now() - state.stamp) / 1000) : 0);
    const loop = this.currentLoop && this.currentLoopSection || this.loop && this.loopSection;
    if (loop && position >= loop.end) position = loop.start + (position - loop.end) % (loop.end - loop.start);
    return Math.max(0, Math.min(this.duration, position));
  };
  p.halt = function() {
    this._nativeEpoch = (this._nativeEpoch || 0) + 1;
    if (isNative(this)) { clearInterval(this._nativePoll); if (this._nativeConfigured) command(this, { op: 'pause' }).catch(() => {}); }
    return original.halt.call(this);
  };
  p.seek = function(value) {
    if (!isNative(this)) return original.seek.call(this, value);
    const position = Math.max(0, Math.min(this.duration, value)), epoch = this._nativeEpoch = (this._nativeEpoch || 0) + 1;
    this.offset = position; this.paint(position); this._nativeSeeking = true;
    if (this._nativeConfigured) return command(this, { op: 'seek', position, playing: this.playing, ...this.nativeMix() }).then(state => { if (epoch === this._nativeEpoch) { this._nativeState = { ...state, stamp: performance.now() }; this._nativeSeeking = false; } }).catch(() => { this._nativeSeeking = false; });
  };
  p.tick = function() {
    if (!isNative(this)) return original.tick.call(this);
    if (!this.playing) return;
    const state = this._nativeState;
    if (state && state.jumpSerial !== this._nativeJumpSerial) {
      this._nativeJumpSerial = state.jumpSerial;
      if (this.pendingSection) {
        const next = this.pendingSection;
        if (this.currentLoop) this.toggleCurrentLoop(false);
        this.loop = false; const repeat = this.$('[data-loop]'); if (repeat) repeat.checked = false;
        this.selectSection(next.index, false); this.lastStatusSectionIndex = -1;
      }
    }
    this.paint(this.position());
    this.raf = requestAnimationFrame(() => this.tick());
  };
  for (const name of ['updateGains', 'updatePans', 'toggleCurrentLoop', 'applyClickSource', 'selectSection']) p[name] = function(...args) { const result = original[name].apply(this, args); this.nativeSync(name==='updateGains'?Number(args[0])||0:0); return result; };
  p.changeLiveKey = async function(...args) { await original.changeLiveKey.apply(this, args); this.nativeSync(); };
  p.restartMetro = function() { if (isNative(this)) { this.nativeSync(); return Promise.resolve(); } return original.restartMetro.call(this); };
  p.playPendingGuide = function(...args) { if (isNative(this)) return false; return original.playPendingGuide.apply(this, args); };
  p.drawTimelineWaveform = function(canvas) {
    if (!this.tracks.some(t => t.items.some(it => it.buffer?.nativeRef))) return original.drawTimelineWaveform.call(this, canvas);
    const width = Math.max(300, canvas.clientWidth || 1200), height = canvas.clientHeight || 70, dpr = devicePixelRatio || 1;
    canvas.width = width * dpr; canvas.height = height * dpr; const ctx = canvas.getContext('2d'); ctx.scale(dpr, dpr); ctx.clearRect(0, 0, width, height);
    const peaks = new Float32Array(Math.ceil(width / 2));
    for (const t of this.tracks) for (const it of t.items) {
      const values = it.buffer?.peaks; if (!values?.length) continue;
      const start = Math.max(0, Math.floor(it.position / this.duration * peaks.length)), length = it.length ?? it.buffer.duration - it.offset;
      const end = Math.min(peaks.length, Math.ceil((it.position + length) / this.duration * peaks.length));
      for (let x = start; x < end; x++) { const seconds = (x / peaks.length * this.duration - it.position) + it.offset; const i = Math.min(values.length - 1, Math.max(0, Math.floor(seconds / it.buffer.duration * values.length))); peaks[x] = Math.max(peaks[x], values[i]); }
    }
    ctx.strokeStyle = '#b45cffaa'; ctx.lineWidth = 1; ctx.beginPath();
    for (let x = 0; x < peaks.length; x++) { const extent = peaks[x] * height * .42; ctx.moveTo(x * 2, height / 2 - extent); ctx.lineTo(x * 2, height / 2 + extent); } ctx.stroke();
  };
  document.addEventListener('input', event => { if (event.target.matches('[data-master]')) bridge.command({ op: 'master', value: Number(event.target.value) }).catch(() => {}); if (event.target.closest('.is-native-click')) window.__ZLPLAY__?.nativeSync(); });
  installAudioSettings(Player, bridge);
  installArrangement(Player);
  installPlaybackFeedback(Player);
  installExecutionTempo(Player);
  installUpgradeUi(Player,bridge);
  installLayout();
}
