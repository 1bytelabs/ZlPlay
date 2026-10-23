const path=require('node:path');
function nativeExecutable(directory,platform=process.platform){return path.join(directory,platform==='win32'?'zlplay-audio.exe':'zlplay-audio');}
function speechExecutable(directory,optimized=false,platform=process.platform){const suffix=platform==='win32'?'.exe':'';return path.join(directory,(optimized?'whisper-cli-avx2':'whisper-cli')+suffix);}
module.exports={nativeExecutable,speechExecutable};
