import {padNote} from './performance-model.mjs';
export const flatNote=note=>['C','Db','D','Eb','E','F','Gb','G','Ab','A','Bb','B'][['C','C#','D','D#','E','F','F#','G','G#','A','A#','B'].indexOf(padNote(note))]||'';
export function rppNameKey(name=''){
 const file=String(name).replace(/\\/g,'/').split('/').pop();
 if(!/\.rpp$/i.test(file))return null;
 const match=[...file.matchAll(/\(([A-G])([#b♯♭]?)(?:m|maj|min)?\)/gi)].at(-1)||file.match(/(?:^|[\s_-])tom[\s_:=.-]*([A-G])([#b♯♭]?)(?:m)?(?=$|[\s_.-])/i);
 return match?padNote(match[1].toUpperCase()+(match[2]||'').replace('♯','#').replace('♭','b').toLowerCase()):null;
}
export function libraryKeyState(song,data=null){
 const rpps=song.rpps||[],chosen=song.selectedSource||song.projects?.[0]||rpps[0];
 const inferred=/\.rpp$/i.test(chosen?.name||'')&&rpps.length===1?rppNameKey(chosen.name):null;
 const original=padNote(song.originalKey)||padNote(data?.settings?.originalKey)||inferred;
 return {original,target:padNote(song.tonalKey)||padNote(data?.settings?.lastTonalKey)||original};
}
export function setLibraryOriginal(song,note){
 const previous=padNote(song.originalKey),follow=!padNote(song.tonalKey)||padNote(song.tonalKey)===previous;
 song.originalKey=padNote(note);
 if(follow)song.tonalKey=song.originalKey;
 return song.originalKey;
}
