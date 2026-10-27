import {libraryKeyState,flatNote} from './library-keys.mjs';
import {PAD_NOTES,padNote} from './performance-model.mjs';
const manifests=new WeakMap(),pending=new WeakMap();
export function effectiveSongKey(song={},data){
 const base=value=>typeof value==='string'?value.replace(/(?:min|maj|m)$/i,''):value;
 const state=libraryKeyState({...song,originalKey:base(song.originalKey),tonalKey:base(song.tonalKey)},data?{...data,settings:{...data.settings,originalKey:base(data.settings?.originalKey||data.song?.key),lastTonalKey:base(data.settings?.lastTonalKey)}}:data);
 let key=state.target;
 if(!padNote(base(song.tonalKey))&&!padNote(base(data?.settings?.lastTonalKey))&&state.original&&Number.isInteger(data?.settings?.liveTranspose))key=PAD_NOTES[(PAD_NOTES.indexOf(state.original)+data.settings.liveTranspose+120)%12];
 if(!key)return '';
 const target=String(song.tonalKey||data?.settings?.lastTonalKey||''),original=String(song.originalKey||data?.settings?.originalKey||data?.song?.key||'');const minor=/m$|min$/i.test(target)||(!/maj$/i.test(target)&&/m$|min$/i.test(original));
 return flatNote(key)+(minor?'m':'');
}
export function queueSongKey(song){if(!song)return '';const file=song.selectedSource||song.projects?.[0]||song.rpps?.[0];return effectiveSongKey(song,file&&manifests.get(file));}
export function warmSongKey(song){const file=song?.selectedSource||song?.projects?.[0]||song?.rpps?.[0];if(!file||!/\.zlplay$/i.test(file.name)||manifests.has(file))return Promise.resolve(queueSongKey(song));if(!pending.has(file))pending.set(file,Promise.resolve().then(()=>file.text()).then(JSON.parse).then(data=>manifests.set(file,data)).catch(()=>{}));return pending.get(file).then(()=>queueSongKey(song));}
