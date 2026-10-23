export function coefficients(type,freq,gain=0,q=.707,rate=48000){
 freq=Math.max(20,Math.min(rate*.45,freq));q=Math.max(.1,Math.min(10,q));const a=10**(gain/40),w=2*Math.PI*freq/rate,c=Math.cos(w),s=Math.sin(w),alpha=s/(2*q),root=2*Math.sqrt(a)*alpha;let b0,b1,b2,a0,a1,a2;
 if(type==='highpass'){b0=(1+c)/2;b1=-(1+c);b2=b0;a0=1+alpha;a1=-2*c;a2=1-alpha;}
 else if(type==='lowpass'){b0=(1-c)/2;b1=1-c;b2=b0;a0=1+alpha;a1=-2*c;a2=1-alpha;}
 else if(type==='low'){b0=a*((a+1)-(a-1)*c+root);b1=2*a*((a-1)-(a+1)*c);b2=a*((a+1)-(a-1)*c-root);a0=(a+1)+(a-1)*c+root;a1=-2*((a-1)+(a+1)*c);a2=(a+1)+(a-1)*c-root;}
 else if(type==='high'){b0=a*((a+1)+(a-1)*c+root);b1=-2*a*((a-1)+(a+1)*c);b2=a*((a+1)+(a-1)*c-root);a0=(a+1)-(a-1)*c+root;a1=2*((a-1)-(a+1)*c);a2=(a+1)-(a-1)*c-root;}
 else{b0=1+alpha*a;b1=-2*c;b2=1-alpha*a;a0=1+alpha/a;a1=-2*c;a2=1-alpha/a;}
 return [b0/a0,b1/a0,b2/a0,a1/a0,a2/a0];
}
export function responseDb(settings,freq,rate=48000){
 const filters=(settings.bands||[]).map((b,i)=>coefficients(b.type||(i===0?'low':i===3?'high':'peak'),b.freq,b.gain,b.q,rate));if(settings.highpass>0)filters.push(coefficients('highpass',settings.highpass,0,.707,rate));if(settings.lowpass>0)filters.push(coefficients('lowpass',settings.lowpass,0,.707,rate));const w=2*Math.PI*freq/rate;
 let db=settings.trim||0;for(const [b0,b1,b2,a1,a2] of filters){const re=b0+b1*Math.cos(w)+b2*Math.cos(2*w),im=-b1*Math.sin(w)-b2*Math.sin(2*w),dr=1+a1*Math.cos(w)+a2*Math.cos(2*w),di=-a1*Math.sin(w)-a2*Math.sin(2*w);db+=10*Math.log10(Math.max(1e-15,(re*re+im*im)/(dr*dr+di*di)));}return db;
}
