import {installTransportFixes} from './transport-fixes.js';
import {upgradeChoices} from './choice-controls.js';
export function installRepairs(s){
 installTransportFixes(s);const update=root=>{upgradeChoices(root);for(const select of root.querySelectorAll('select[data-click-source],select[data-auto-mode]')){const label=select.closest('label');if(label)for(const node of [...label.childNodes])if(node.nodeType===3&&/^(Click|Modo)$/.test(node.textContent.trim()))node.remove();}root.querySelectorAll?.('[data-metro]').forEach(input=>{input.hidden=true;input._zlSwitchSync?.();if(input.nextElementSibling?.classList.contains('zl-switch'))input.nextElementSibling.hidden=true;});};
 const observer=new MutationObserver(records=>{for(const r of records)for(const node of r.addedNodes)if(node.nodeType===1)update(node);});observer.observe(document.body,{childList:true,subtree:true});update(document);
 setInterval(()=>{for(const select of document.querySelectorAll('select[data-cycle],select[data-searchable]'))select._choiceRefresh?.();},180);
}
