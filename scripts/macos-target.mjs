import path from 'node:path';
export function macosTarget(env=process.env,platform=process.platform){
  if(platform!=='darwin'||!env.ZLPLAY_MAC_ARCH)return null;
  const arch=env.ZLPLAY_MAC_ARCH;
  if(!['arm64','x64'].includes(arch))throw Error('ZLPLAY_MAC_ARCH must be arm64 or x64.');
  return {arch,nativeDirectory:path.resolve('native','bin-'+arch),speechDirectory:path.resolve('native','speech-'+arch),
    cmakeArgs:['-DCMAKE_SYSTEM_NAME=Darwin','-DCMAKE_SYSTEM_PROCESSOR='+(arch==='x64'?'x86_64':'arm64'),'-DCMAKE_OSX_ARCHITECTURES='+(arch==='x64'?'x86_64':'arm64'),'-DCMAKE_OSX_DEPLOYMENT_TARGET=15.0']};
}
