export async function showSetlistPicker(player, setlists, pick, open) {
 if (document.querySelector('.zl-setlist-picker')) return;
 const t = text => window.__ZL_I18N__?.t(text) || text;
 const modal = document.createElement('div');
 modal.className = 'zl-audio-modal zl-choice-dialog zl-setlist-picker';
 modal.innerHTML = '<section class="zl-audio-box zl-small-dialog"><header><strong>Repertórios salvos</strong><button data-close aria-label="Fechar">×</button></header><div class="zl-setlist-entries"></div><p data-setlist-status role="status"></p><footer class="zl-library-actions"><button data-create-setlist>Criar novo repertório</button><button data-import-setlist>Abrir repertório salvo</button><button data-clear-setlists>Limpar lista</button></footer></section>';
 const list = modal.querySelector('.zl-setlist-entries');
 const status = modal.querySelector('[data-setlist-status]');
 const clear = modal.querySelector('[data-clear-setlists]');
 const opener = document.activeElement;
 const close = () => { modal.remove(); opener?.focus(); };
 const report = error => { status.textContent = error.message; };
 async function render() {
  const {entries,last} = await setlists.list();
  list.replaceChildren();
  clear.disabled = !entries.length;
  if (!entries.length) list.textContent = t('Nenhum repertório salvo na lista.');
  for (const entry of entries) {
   const row = document.createElement('div');
   row.className = 'zl-setlist-entry';
   const button = document.createElement('button');
   button.dataset.openSetlist = '';
   button.textContent = entry.name;
   button.dataset.noI18n = '';
   button.title = entry.name;
   if (last === entry.id) button.classList.add('zl-state-active');
   button.onclick = async () => {
    close();
    try {
     const selection = await setlists.open(entry.id);
     if (selection) await open(selection,{openFirst:true});
    } catch {
     player.status(t('Não foi possível abrir o repertório salvo. Confira se o arquivo ainda está na pasta.'),true);
    }
   };
   const remove = document.createElement('button');
   remove.dataset.removeSetlist = '';
   remove.textContent = 'Remover da lista';
   remove.onclick = async () => {
    try { await setlists.remove(entry.id); await render(); }
    catch(error) { report(error); }
   };
   row.append(button,remove);
   list.append(row);
  }
 }
 clear.onclick = async () => {
  if (!confirm(t('Limpar a lista de repertórios salvos? Os arquivos serão mantidos.'))) return;
  try { await setlists.clear(); await render(); }
  catch(error) { report(error); }
 };
 modal.querySelector('[data-close]').onclick = close;
 modal.querySelector('[data-create-setlist]').onclick = () => { close(); pick('library-folder'); };
 modal.querySelector('[data-import-setlist]').onclick = () => { close(); pick('library'); };
 (document.fullscreenElement || document.body).append(modal);
 modal.querySelector('[data-close]').focus();
 try { await render(); }
 catch(error) { report(error); }
}
