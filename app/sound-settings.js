const PAD_NOTES = ['C', 'Db', 'D', 'Eb', 'E', 'F', 'Gb', 'G', 'Ab', 'A', 'Bb', 'B'];

export function installSoundSettings(player) {
  const bridge = window.zlplayNative;
  const open = player.openAudioSettings.bind(player);
  player.openAudioSettings = async function (...args) {
    await open(...args);
    const card = document.querySelector('.zl-pad-folder');
    if (!card || card.hasAttribute('data-sound-bank')) return;
    card.dataset.soundBank = '';
    card.innerHTML = `
      <header><h3>Personalizar sons</h3><button data-open-sounds>Abrir pasta dos sons</button></header>
      <div class="zl-sound-grid">
        <section>
          <h4>Pads</h4>
          <p>Selecione 12 arquivos, um por nota, de até 60 segundos. Use os nomes das notas nos arquivos. Sustenidos também são aceitos.</p>
          <button data-import="pads">Importar Pads</button>
          <ul class="zl-pad-notes" data-pad-notes></ul>
          <p data-pad-summary></p>
        </section>
        <section>
          <h4>Click ZLPlay</h4>
          <p>Use sons de clique de até 10 segundos.</p>
          <div class="zl-sound-row">
            <div><strong>Normal</strong><small data-normal></small></div>
            <button data-import="normal" aria-label="Importar clique normal">Importar</button>
            <button data-preview="normal" aria-label="Ouvir clique normal">Ouvir</button>
          </div>
          <div class="zl-sound-row">
            <div><strong>Acentuação</strong><small data-accent></small></div>
            <button data-import="accent" aria-label="Importar acentuação">Importar</button>
            <button data-preview="accent" aria-label="Ouvir acentuação">Ouvir</button>
          </div>
          <button data-reset-click>Restaurar sons padrão do clique</button>
        </section>
      </div>
      <p data-sound-message role="status"></p>`;

    const message = text => card.querySelector('[data-sound-message]').textContent = text;
    const busy = () => player.playing || player.standaloneClickActive;
    async function refresh() {
      const [info, pads] = await Promise.all([bridge.sounds({action: 'status'}), bridge.padFolder('status')]);
      const available = new Set(pads.available);
      const notes = PAD_NOTES.map((note, index) => {
        const item = document.createElement('li');
        const ready = available.has(index);
        const name = info.pads.find(pad => pad.note === note)?.name;
        item.classList.toggle('is-available', ready);
        item.textContent = note;
        item.title = ready ? name || 'Pad disponível' : 'Pad indisponível';
        const status = document.createElement('span');
        status.className = 'zl-visually-hidden';
        status.textContent = ready ? 'Pad disponível' : 'Pad indisponível';
        item.append(status);
        if (ready) {
          const check = document.createElement('span');
          check.setAttribute('aria-hidden', 'true');
          check.textContent = '✓';
          item.append(check);
        }
        return item;
      });
      card.querySelector('[data-pad-notes]').replaceChildren(...notes);
      card.querySelector('[data-pad-summary]').textContent = available.size === 1 ? '1 pad disponível.' : available.size + ' pads disponíveis.';
      for (const kind of ['normal', 'accent']) {
        const status = card.querySelector('[data-' + kind + ']');
        status.toggleAttribute('data-no-i18n', Boolean(info[kind]));
        status.textContent = info[kind] || 'Padrão';
      }
    }

    for (const button of card.querySelectorAll('[data-import], [data-preview], [data-reset-click]')) {
      button.onclick = async () => {
        const preview = button.dataset.preview;
        if (busy()) return message(preview ? 'Pause a reprodução antes de ouvir o som.' : 'Pause a reprodução antes de importar sons.');
        button.disabled = true;
        try {
          if (preview) {
            await bridge.sounds({action: 'preview', accent: preview === 'accent'});
          } else {
            const action = button.dataset.import || 'reset';
            await bridge.sounds({action, language: window.__ZL_I18N__?.language || 'en'});
            if (action === 'pads') player.stopPad();
            await refresh();
          }
          message('');
        } catch (error) {
          message(error.message);
        } finally {
          button.disabled = false;
        }
      };
    }
    card.querySelector('[data-open-sounds]').onclick = () => bridge.sounds({action: 'open'}).catch(error => message(error.message));
    await refresh().catch(error => message(error.message));
  };
}
