const {test}=require('node:test'),assert=require('node:assert/strict');
class Element{
 constructor(){this.dataset={};this.style={};this.children=[];this.attributes={};}
 setAttribute(k,v){this.attributes[k]=v;}
 append(...items){for(const item of items){this.children.push(item);item.parent=this;}}
 replaceChildren(){this.children=[];}
 querySelector(){return this.children.find(x=>'regionState' in x.dataset)||null;}
 remove(){this.parent.children=this.parent.children.filter(x=>x!==this);}
}
test('Loop and scheduled Jump badges translate, clear on cancellation and survive redraw',async()=>{
 const {installRegionFeedback}=await import('../app/remote-region-feedback.js');const cards=[new Element(),new Element()];global.document={head:new Element(),createElement:()=>new Element(),createElementNS:()=>new Element(),addEventListener(){}};let language='pt-BR';const sections=[{start:0,end:10},{start:10,end:20}],s={sections,host:{querySelectorAll:()=>cards},paint(){},drawSections(){},currentLoop:true,currentLoopSection:sections[0],pendingSection:{index:1}};
 try{installRegionFeedback(s,()=>language);assert.equal(cards[0].querySelector().dataset.kind,'loop');assert.equal(cards[1].querySelector().title,'Salto');language='en';s.paint(1);assert.equal(cards[1].querySelector().title,'Jump');language='es';s.paint(1);assert.equal(cards[1].querySelector().title,'Salto');s.pendingSection=null;s.paint(1);assert.equal(cards[1].querySelector(),null);s.currentLoop=false;s.paint(1);assert.equal(cards[0].querySelector(),null);s.currentLoop=true;s.drawSections();assert.equal(cards[0].querySelector().dataset.kind,'loop');}finally{delete global.document;}
});
test('Mac source/license resources contain real revisions and original notices',()=>{
 const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),{prepareMacLicenses}=require('../scripts/prepare-macos-licenses.cjs'),out=fs.mkdtempSync(path.join(os.tmpdir(),'zl-mac-licenses-'));
 try{prepareMacLicenses(path.resolve('.'),out,{commit:'1'.repeat(40),sourceCommit:'2'.repeat(40)});const source=fs.readFileSync(path.join(out,'SOURCE_CODE.txt'),'utf8');assert(source.includes('1'.repeat(40)));assert(source.includes('/archive/'+'2'.repeat(40)+'.zip'));assert.equal(fs.readFileSync(path.join(out,'QRCode-MIT.txt'),'utf8'),fs.readFileSync('docs/licenses/QRCode-MIT.txt','utf8'));for(const lang of ['en','pt-BR','es'])assert(fs.readFileSync('desktop/macos-locales/'+lang+'.lproj/InfoPlist.strings','utf8').includes('NSLocalNetworkUsageDescription'));}finally{fs.rmSync(out,{recursive:true,force:true});}
});
