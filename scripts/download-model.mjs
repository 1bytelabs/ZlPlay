import fs from 'node:fs/promises';
import crypto from 'node:crypto';
const target='native/speech/ggml-base-q5_1.bin',expected='422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898';
const sha=data=>crypto.createHash('sha256').update(data).digest('hex');
const existing=await fs.readFile(target).catch(()=>null);if(existing&&sha(existing)===expected){console.log('Speech model already verified.');}else{const response=await fetch('https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin');if(!response.ok)throw Error('Model download failed: '+response.status);const data=Buffer.from(await response.arrayBuffer());if(sha(data)!==expected)throw Error('Unexpected speech model checksum. Nothing installed.');await fs.mkdir('native/speech',{recursive:true});const tmp=target+'.tmp';try{await fs.writeFile(tmp,data);await fs.rename(tmp,target);}finally{await fs.unlink(tmp).catch(()=>{});}console.log('Speech model installed and verified.');}
