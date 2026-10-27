# macOS 2.8.1 — paridade e publicação

## Destino autorizado

Atualizar o app Mac para 2.8.1 e acrescentar seus pacotes à release **v2.8.1** de `1bytelabs/ZlPlay`. Windows 2.8.1 e Mobile 0.11.1 são publicados primeiro. Preservar seus assets, checksums e notas. Não mover tags, não alterar a v2.8.0, não criar outra release. Ler AGENTS.md, CONTRIBUTING.md e PUBLICACAO-WINDOWS.md; a seção vigente prevalece sobre instruções históricas da 2.8.0.

Os fontes de referência estão na branch `codex/android-playback-prototype` e na tag v2.8.1. Integrar por comparação, preservando o trabalho Mac. Não substituir arquivos completos sem revisar diferenças específicas da plataforma.

## Paridade exigida pelo usuário

Todos os recursos compartilhados, informações e feedbacks visuais do desktop devem ser equivalentes. Preservar diferenças reais de áudio, drivers, permissões, empacotamento e assinatura Mac. Não habilitar simplesmente o ramo Windows: separar UI comum dos adaptadores de plataforma e validar no Mac.

- Mostrar **Próxima**, nome da música e abaixo seu **tom efetivo de Tocar em**, em bemóis; recorrer ao original apenas quando não houver destino diferente. O Mac atualmente não mostra nem o nome: implementar ambos. Conferir `app/queue-key.mjs`, `app/queue-model.mjs` e a integração em `app/remote-control.js`. A informação deve funcionar mesmo com remoto desligado.
- Controle remoto por QR, sem endereço/porta/senha manual: mesma rede, chave temporária e certificado TLS fixado pelo Android. Transporte, regiões/saltos agendados, Loop, Click solo, Pad/BPM e mixer devem comandar o Mac. Revisar `desktop/remote-host.cjs`, IPC em main/preload e `app/remote-control.js`; preservar restrições de origem, validação, deduplicação e limites. Adaptar APIs ao Mac e validar permissões/firewall reais. Não afirmar compatibilidade sem teste.
- Card discreto com ponto verde e número real de dispositivos, **antes da navegação Repertório** no cabeçalho da música, inclusive em tela cheia. Servidor habilitado sem cliente não significa conectado.
- Desconectar remoto com estado visual contrastante enquanto conectado, operação em andamento e estado desabilitado ao desconectar. Fechar por Escape deve limpar a janela. Desconectar nunca interrompe o áudio local.
- Badge de **Loop** com símbolo no card da região efetivamente repetida, tanto por Loop quanto por Click solo. Badge de **Salto** com símbolo na região de destino agendada. Remover ao cancelar; manter nomes legíveis em cards estreitos e interação com regiões. Referência: `app/remote-region-feedback.js`, atualmente instalada no ramo Windows.
- Conferir paridade anterior de Tap/RPP, bemóis e escolha Original/Tocar em; timeline com zoom acompanhando cursor, divisórias finas, Shift para redimensionar apenas uma região e botões EQ/Gain/submenu uniformes. Revisar HANDOFF-MAC-TIMELINE.md e alterações posteriores; não regredir o que já foi integrado.
- Todos os textos de UI novos em pt-BR/en/es. Textos legais completos preservados no idioma original e avisos traduzidos. Recursos próprios do Mobile continuam adaptados à sua interface.

## Validar e publicar

1. Atualizar package.json/package-lock.json e identificadores visuais para 2.8.1 antes de compilar. Não renomear build 2.8.0.
2. Rodar testes e auditoria. Testar UI normal/tela cheia, Próxima e tom diferente do original, pareamento Android, contagem/desconexão sem parar áudio, salto/Loop/Click solo e cancelamento. Usar fixtures sintéticas.
3. Compilar Intel e Apple Silicon, verificar arquiteturas, recursos, licenças, assinaturas e execução no hardware disponível. Informar limites de testes físicos, notarização e assinatura; não declarar testes não realizados.
4. Anexar somente `ZLPlay-2.8.1-macOS-Intel.zip`, `ZLPlay-2.8.1-macOS-Apple-Silicon.zip` e `SHA256SUMS-macOS.txt` à v2.8.1 já existente. Se houver arquivos Mac com mesmo nome, comparar hashes e substituir somente os Mac quando necessário.
5. Acrescentar seção macOS às notas existentes, com commit real de build publicado e link dos fontes correspondentes. Não apresentar a tag Windows como fonte de um build Mac diferente. Publicar a branch/commit Mac sem mover a tag.
6. Baixar os assets publicados e conferir SHA-256. Preservar status da release, todos os assets Windows/Android e suas notas. Entregar link e limitações restantes.

Diários e detalhes privados ficam exclusivamente em local-notes/, nunca no GitHub.
