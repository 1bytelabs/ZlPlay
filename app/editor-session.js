export function editorExit(modal, s, session, hasDraft = () => false) {
 const close = modal.querySelector('[data-close]');
 const finish = close.onclick;
 close.textContent = 'Descartar alterações';
 close.onclick = async () => {
  if (close.disabled) return;
  const dirty = s.hasEditSessionChanges(session);
  if (!dirty && !hasDraft()) return finish();
  const message = 'Descartar as alterações deste editor?';
  if (!confirm(window.__ZL_I18N__?.t(message) || message)) return;
  close.disabled = true;
  try {
   if (dirty) await s.discardEditSession(session);
   await finish();
  } catch (error) {
   s.status(error.message, true);
   close.disabled = false;
  }
 };
 const accept = document.createElement('button');
 accept.dataset.acceptEdits = '';
 accept.textContent = 'Aceitar alterações';
 accept.onclick = () => { if (!close.disabled) return finish(); };
 const actions = document.createElement('div');
 actions.className = 'zl-editor-exit-actions';
 close.before(actions);
 actions.append(accept, close);
 const note = modal.querySelector('footer span');
 if (note) note.textContent = 'Aceitar mantém as edições na sessão. Use Salvar para gravar o projeto.';
}
