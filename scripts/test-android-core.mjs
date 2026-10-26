import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url));
const out=path.join(root,'.cache/android-core-tests');fs.mkdirSync(out,{recursive:true});
const executable=name=>process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin',name+(process.platform==='win32'?'.exe':'')):name;
for(const [name,args] of [
 ['javac',['--release','17','-d',out,'android/app/src/main/java/com/onebytelabs/zlplay/audio/DemoMixer.java','android/app/src/main/java/com/onebytelabs/zlplay/audio/PcmTrack.java','android/app/src/main/java/com/onebytelabs/zlplay/audio/WavReader.java','android/core-tests/MixerTest.java','android/core-tests/WavReaderTest.java','android/app/src/main/java/com/onebytelabs/zlplay/audio/WavFile.java','android/app/src/main/java/com/onebytelabs/zlplay/audio/StreamBank.java','android/core-tests/StreamingWavTest.java']],
 ['java',['-cp',out,'MixerTest']],
 ['java',['-cp',out,'WavReaderTest']],
 ['java',['-Xmx48m','-cp',out,'StreamingWavTest']]
]){const result=spawnSync(executable(name),args,{cwd:root,stdio:'inherit'});if(result.error)throw result.error;if(result.status!==0)process.exit(result.status||1);}
