# Handoff macOS — timeline e controles (25/10/2026)

Aplicar somente as quatro mudanças de interface desta rodada, preservando o trabalho específico do Mac e as correções anteriores de Tap/RPP.

1. Timeline principal com zoom acompanha a reprodução por páginas ao chegar perto da borda. Também reposiciona ao voltar em loop/salto. Não seguir quando pausado, durante arraste do cursor ou enquanto editores estão abertos.
2. Linhas verticais finas na waveform em cada início/fim de região, incluindo regiões da mesma cor e intervalos. Sem capturar cliques. Limites da região atual/selecionada mais destacados; cards superiores permanecem.
3. No editor de regiões, arraste normal mantém as vizinhas conectadas. Shift pressionado ao iniciar o arraste de uma aresta move só a região escolhida, deixando a vizinha no lugar. Permitir abrir intervalo livre, impedir sobreposição, manter snap, desfazer/refazer e referências do Arranjo.
4. EQ/Gain e botão de três pontos do submenu com dimensões iguais: 38 × 25 CSS px, sem aumentar a largura das tracks. Aplicar às tracks especiais e comuns/grupos; não alterar o botão isolado do Master.

## Arquivos de referência

- `app/timeline-model.mjs`: funções puras de rolagem, limites e plano de redimensionamento.
- `app/timeline-navigation.js`: wrappers da interface para acompanhamento e linhas.
- `app/queue-280.js`: instalar navegação e suspender acompanhamento enquanto arrasta cursor.
- `app/region-editor.js`: plano ligado/independente, Shift no pointerdown e instrução traduzida.
- `app/repair.css`: divisórias e botões uniformes.
- `app/locales.mjs`: textos novos em pt-BR/en/es.
- `tests/timeline-navigation.test.cjs`: quatro testes adicionais.

Não substituir arquivos inteiros cegamente: o Mac pode ter diferenças próprias. Não importar a correção do dropdown Windows nem configurações de NSIS/ASIO. O hook de licenças Windows não precisa ser portado para a compilação Mac.

## Validação e publicação Mac solicitadas pelo usuário

Rodar testes/auditoria e testes reais de interface: reprodução com zoom acompanhando cursor; loop/salto para trás; pausa sem rolagem; divisórias da mesma cor; arestas com e sem Shift e desfazer; dimensões dos botões. Conferir traduções.

Gerar e validar pacotes Mac Intel/Apple Silicon 2.8.0 conforme o fluxo Mac existente. Substituir **somente** os dois ZIPs Mac e `SHA256SUMS-macOS.txt` na release v2.8.0 existente. Preservar instalador/checksum Windows, notas Windows, tag e Latest. Não criar release/tag/repositório. Atualizar apenas notas Mac com commit real, testes e limitações. Não declarar teste físico Apple Silicon sem executá-lo. Conferir downloads/checksums após publicar e respeitar confirmações obrigatórias da ferramenta.
