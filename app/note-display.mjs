const flats={'C#':'Db','D#':'Eb','F#':'Gb','G#':'Ab','A#':'Bb'};
export function flatNote(text){return String(text).replace(/\b([A-G]#)(?:\/[A-G]b)?/g,(_,note)=>flats[note]||note);}
