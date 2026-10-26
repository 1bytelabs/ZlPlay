# Diário do ZLPlay e continuidade Android

Registro consolidado em **25/10/2026** (America/Sao_Paulo). Este documento preserva decisões e evidências da conversa. Relatos antigos sem confirmação técnica foram separados de trabalho validado. Atualizar este diário a cada etapa relevante, sem registrar credenciais, caminhos pessoais, músicas ou dados de usuários.

## Estado atual e ponto de retomada

- Projeto desktop Windows/macOS: Electron, interface web e motor de áudio nativo em C++ com JUCE. Código próprio sob AGPL-3.0-only.
- Repositório: https://github.com/1bytelabs/ZlPlay . Organização 1Byte Labs; não criar outro repositório automaticamente.
- Versão publicada: **2.8.0**. Windows somente instalador; portátil retirada por decisão do usuário.
- Release compartilhada: https://github.com/1bytelabs/ZlPlay/releases/tag/v2.8.0 . Preservar arquivos/notas de cada plataforma ao atualizar a outra.
- Tag v2.8.0 permanece em `18fa25bf0dc8bd1a12281bfcedc3a9474ad9f222`. Não mover a tag para atualizar anexos. A release está marcada como Latest por decisão posterior do usuário, substituindo a preferência inicial por pré-release.
- Último build Windows: commit `d18ff78419ae9c09f3c2cc5c77fc0f142ead33c6`. **60 testes aprovados**, auditoria de publicação sem achados; instalação isolada e testes do pacote concluídos.
- Instalador Windows atual: `ZLPlay-2.8.0-instalador.exe`, SHA-256 `39b9578a4c5472e62c5141fe550f510cbe83e00ed43634ee0c84125990e5a969`. O checksum publicado lista somente esse instalador. Ambos foram baixados e comparados após publicação.
- **Android ainda não tem implementação nesta conversa.** Android Studio instalado e depuração USB ativada no celular, conforme o usuário. A conexão ADB, SDK e toolchain ainda precisam ser confirmados.
- Próxima etapa: inventariar o ambiente Android e validar uma reprodução multitrack mínima no celular real antes de construir a interface completa.

## Trabalho desktop confirmado nesta etapa

### Identidade e publicação

O usuário aprovou a identidade de ondas verticais do ZLPlay. Ícones do aplicativo e dos documentos `.zlplay`/`.zlset` foram atualizados. A marca 1Byte Labs fica como identificação da organização, com destaque próprio do ZLPlay. Versões Windows e Mac têm fluxos de compilação separados.

O repositório público recebeu README, regras de contribuição/segurança, política de arquivos ignorados e avisos das dependências. A licença AGPLv3 foi escolhida expressamente pelo usuário. Arquivos pessoais de áudio, pads, ferramentas, modelos e executáveis não pertencem ao Git do código. A auditoria automatizada é uma verificação adicional, não garantia de ausência de segredos.

### Correções Windows e reconhecimento de tom

- Commit `d5129c29902ca4dce35bea940145aa7fdc17392c`: dropdown de tons passa a abrir dentro do contexto da janela/modal. Teste real e no pacote instalado confirmou os 12 tons clicáveis. Distribuição Windows limitada a NSIS instalável.
- Commit `21eba53fafc5caff64863534c5deda92a8160791`: reconhecimento de tom nos nomes dos RPPs e destaque visual breve no Tap, sem mudar o cálculo de BPM.
- Um único RPP usa o tom explícito do nome como Original. Exemplo: `É Ele (Bb).rpp`.
- Vários RPPs apresentam alternativas de arquivos e tons para o usuário escolher o Original. A palavra “Original” no nome não tem prioridade automática. Exemplo: `É Ele - Original (Bb).rpp` e `É Ele (C).rpp`.
- “Tocar em” começa no Original escolhido e preserva escolhas explícitas ou salvas. As notas são exibidas em bemóis; a representação interna compatível é mantida.
- Validação sintética: um arquivo preenche Bb/Bb; vários começam sem escolha; selecionar C escolhe o RPP em C e define C/C; escolher D como destino continua preservado após redesenhar a linha.
- O Tap reinicia sua animação em acionamentos rápidos. Não altera o cálculo do andamento.

### macOS — preservar o trabalho do outro chat

O chat Mac integrou Tap/RPP sem importar a correção de sobreposição exclusiva do Windows. Referência registrada em `9a8cdfe9d64191bc26ce88892a7bb6dcc3398a09` na main; snapshot Mac `ba3d5a1c6e7e0cf8bb23f61cb9a4f4bcdc8ff97b`, build local equivalente `82beb9c1cd000ee5ee4a84af331d55447d4988aa`, tree `7b6414b5c96f73a4eb2f27441fb270a91ed9067b`.

Pacotes atuais registrados: Intel SHA-256 `3e819c32770fba60443a1e8df6bb8e939699c459c1b9cc774352bf3c5fcee0a6`; Apple Silicon `87eac4856378dbe1e5d373fd4b3b0721cb545e43567c5d8f192c1bc24e63f57c`. O histórico Mac registra 52 testes aprovados. Apple Silicon ainda aguarda teste físico; assinatura ad-hoc, sem Developer ID/notarização; somente ZIPs. Não tratar tentativa de teste visual que não carregou a pasta sintética como aprovação visual.

### ASIO e modelo de licença

A decisão é **manter ASIO no Windows** para interfaces com saídas independentes. Instalar driver de fabricante ou ASIO4ALL não substitui o suporte ASIO no aplicativo. Windows Audio/WASAPI continua alternativa; não presumir que ele expõe os mesmos canais de toda interface. No Mac, o motor usa CoreAudio.

A revisão encontrou nos headers ASIO incluídos no JUCE a escolha expressa entre licença proprietária Steinberg e GPLv3. O ZLPlay documenta a alternativa GPLv3 para esses headers e AGPLv3 para seu código/JUCE; não afirma ter contrato proprietário ou certificação Steinberg. Não prometer ausência de risco jurídico.

Commit `82142626480f5cf2eedc38163317c85be81c0c5b`:

- `docs/LICENSING.md`: modelo, escopo por plataforma, marcas e acesso aos fontes, em português/inglês/espanhol.
- `docs/licenses/ASIO-LICENSE.txt`: aviso original do SDK; `GPL-3.0.txt`: texto integral GPLv3.
- `scripts/prepare-windows-licenses.cjs`: hook de empacotamento somente Windows.
- `resources/licenses/` no aplicativo instalado contém AGPLv3, GPLv3, aviso ASIO, avisos de terceiros, explicação e `SOURCE_CODE.txt` com commit e links exatos.
- README e instruções de publicação atualizados. Dois novos testes verificam os documentos e que o hook não age no Mac.
- Instalador substituído na mesma release com confirmação do usuário. Mac, notas Mac, tag e Latest preservados.
- Os arquivos automáticos “Source code” da tag podem ser anteriores ao instalador. As notas apontam para o commit efetivo e para os fontes das dependências. Quem redistribuir precisa manter o cumprimento das condições de fontes correspondentes; documentação não equivale a parecer jurídico.

## Decisões e solicitações históricas desktop

Esta lista conserva a intenção do usuário; **não significa que todos os itens abaixo foram revalidados ou concluídos nesta etapa**. Antes de retomar um item, conferir código, testes e comportamento atual para não repetir trabalho.

- Repertório: Original antes de Tocar em, ambos na mesma linha; dropdowns funcionais; rolagem automática durante arraste; lista mais compacta e elegante; escolha correta dos projetos de origem.
- Versões de tom: janela pequena, fechar ao clicar fora; corrigir original deve ficar à frente. Original na tela principal é informação sem hover que sugira interação.
- Títulos: exibir música e cantor quando disponíveis, retirar metadados de tom/BPM do título visual sem renomear arquivos. Mostrar próxima música com apresentação discreta e elegante em vez de abas completas.
- Transporte: reduzir atraso do Play; clicar para reposicionar parado não deve produzir medidores indevidos; arrastar cursor de reprodução em todas as waveforms; Espaço alterna Play/Pause fora de campos de edição; Play no editor de regiões não deve iniciar Pad.
- BPM: Enter aplica valor, botões menos/mais com passos inteiros; andamento/click deve persistir ao salvar e não voltar a 120 no Play. Tap com feedback visual está confirmado acima.
- Regiões: remover seletor de modelo redundante; permitir nome e cor; início/fim em segundos só informativos; trocar “trechos” por “regiões”; remover texto vazio “Sem regiões”; botão “Detectar Regiões”.
- Detecção pela Guia: usar a track Guia, melhorar cobertura, mostrar progresso/cancelamento, adicionar as regiões diretamente na timeline para edição; preservar exatamente os nomes e números anunciados (não numerar ocorrências automaticamente); não antecipar o início em relação à fala. As guias têm antecipações variáveis: audição e ajuste continuam necessários.
- Tracks: alinhamento conjunto pela waveform do click à grade, preservando relações; ajustar espaço inicial quando apropriado, com desfazer.
- Mixer: arrastar tracks para dentro/entre grupos; bloquear rolagem de fundo com submenu aberto; corrigir sobreposição dos três pontos; Alterar tom como controle de estado; botão único EQ/Gain na track; renomear/excluir grupos no submenu; investigar piscada ao trocar música já carregada.
- Click/Pad: medidor do click nativo; botão Pad único com estado; bemóis nos seletores e botões. Importar 12 pads e sons separados de click normal/acento para a pasta de dados do usuário; restaurar padrões apenas do click, pois não há Pad nativo.
- Salvamento: Salvar direto no destino existente; Salvar como escolhe destino/nome; primeiro Salvar pergunta destino. Ao salvar uma pasta importada, assumir imediatamente o projeto `.zlplay` na sessão.
- Todo texto novo/alterado na interface deve ter tradução pt-BR/en/es.

## Visão acordada para Android

O objetivo é um player para apresentações ao vivo, com identidade própria do ZLPlay. Playback é uma referência funcional; não copiar sua interface, marca ou recursos visuais. Audio Evolution foi citado como opção existente que não cobre o fluxo desejado de regiões e pads de forma simples.

### Escopo inicial

- Foco **Android primeiro**, com teste no celular real do usuário. Emulador pode ajudar no layout; não substitui testes de áudio, USB e estabilidade no aparelho.
- Celular e tablet em **horizontal**. Layout responsivo por espaço disponível.
- Projetos preparados no ZLPlay desktop; o celular reproduz. Não incluir inicialmente edição de tracks nem mapeamento/detecção de regiões.
- Importar projeto individual `.zlplay` e repertório `.zlset` com seus áudios, pads e configurações necessárias. O usuário citou “.ZL7” oralmente; a extensão desktop confirmada é `.zlset`, não criar outro formato apenas por essa fala.
- Preservar regiões e **automações do Arranjo que saltam entre regiões/seções**. O usuário distinguiu isso de automação de volume.
- Transporte: Play, Pause, Stop, Loop, Solo Click e Pad com estado.
- Click com BPM editável e uso independente da música. **Solo Click deve iniciar o click com a música parada**; permitir click e Pad juntos sem iniciar a música.
- Escolha de tom da música, com compensação adequada e persistência; escolha de tom do Pad. Não pressupor que mudar a nota do Pad transpõe a música.
- Mixer de volumes com faders estreitos, sem ícones de instrumentos e sem medidores de entrada/sinal na primeira proposta visual.
- Waveform e regiões como cards compactos, selecionáveis para navegação; interação deve ser confortável no toque.
- Salvamento automático das alterações no aplicativo, sem botão Salvar nem criar cópias desnecessárias de áudios por mudança de configuração.
- Saídas de áudio configuráveis, inclusive interfaces USB/OTG, **conforme suporte real do Android, hardware e driver/backend**. Não prometer acesso a todos os canais de uma X32 ou interface antes de testar.
- Preservar traduções pt-BR/en/es e acessibilidade de controles por toque.

### Layout acordado

Celular: waveform/regiões, transporte, Pad e seleção de tom acessíveis; botão abre mixer e controles adicionais sem sobrecarregar a tela.

Tablet: cards das músicas **acima da waveform**, com pouca altura. Waveform/regiões amplas e mixer abaixo, com faders estreitos. Não colocar uma coluna grande de músicas que reduza a waveform. Configurações e importação de sons em tela própria.

Mockups foram discutidos e ajustados na conversa; não assumir que esses desenhos são implementação ou que arquivos de mockup foram versionados. A tela de configurações ajudou a consolidar a identidade visual aprovada.

### Transferência desktop → Android

Foi discutido “Exportar para Android” para projeto ou repertório, incluindo dependências em caminhos relativos. **ZIP versus pasta ainda não foi fechado tecnicamente.** A preocupação do usuário é evitar duplicação permanente: um ZIP importado e uma extração completa ocupam espaço duas vezes. Projetar extração segura/transacional e opção de remover a cópia importada somente com autorização/sem perder o original externo; ou permitir pasta com todos os arquivos. Não afirmar que um `.zlplay` sozinho contém os áudios.

O pacote deve registrar formato/versão e incluir projetos, ordem do repertório, regiões, saltos do Arranjo, mix/roteamento compatível, tom, click/compasso e referências aos sons necessários. Validar caminhos relativos, arquivos ausentes e nomes entre plataformas. Sons/músicas pessoais não vão para Git ou fixtures públicas.

## Plano de execução Android — próximo trabalho

1. **Inventário e dispositivo:** localizar Android SDK, platform-tools/ADB, JDK e versões instaladas; verificar `adb devices` com o celular e autorização de depuração. Identificar versão Android, arquitetura e recursos USB/áudio do aparelho. Não instalar ferramentas duplicadas sem necessidade.
2. **Avaliar arquitetura:** ler o modelo atual `.zlplay`/`.zlset`, motor C++ e arranjo. Decidir e registrar a arquitetura Android após um protótipo, considerando interface nativa, reaproveitamento do motor e licenças. Electron desktop não roda diretamente no Android. Não assumir escolha de framework já aprovada.
3. **Prova de áudio no celular:** projeto sintético com múltiplas tracks sincronizadas; Play/Pause/Stop/seek, click independente e Pad, sem rede. Medir estabilidade, latência, uso de CPU/memória, duração e aquecimento. Validar transposição em etapa separada antes de prometer desempenho.
4. **Formato e importação:** definir exportação desktop e importação Android versionadas, preservando regiões e saltos do Arranjo; validação e tratamento de arquivos ausentes; considerar armazenamento e recuperação após interrupção.
5. **Primeira interface horizontal:** cards, waveform/regiões, transporte, Pad e mixer, com variantes de celular/tablet. Implementar traduções desde o início.
6. **Persistência automática:** salvar metadados com escrita atômica, recuperação após fechamento/queda e migração de formato; não modificar áudios originais para salvar apenas mix/tom.
7. **Áudio externo:** testar interface USB/OTG real, enumeração de canais e roteamento de click/guia/VS. Separar o que funciona nativamente do que exigiria solução adicional. ASIO é assunto do Windows; não transportar SDK ASIO como solução de áudio Android.
8. **Confiabilidade ao vivo:** mudanças de região e saltos sem falhas, troca de música, interrupções, desconexão USB, bloqueio/segundo plano, foco de áudio, bateria e sessões longas. Definir comportamento seguro com base em testes.
9. **APK de testes:** compilar pacote identificável para instalar no aparelho do usuário, com relatório de recursos/limitações. Publicação em loja, assinatura de distribuição e versão iOS não foram autorizadas nesta etapa.

## Regras de continuidade

- Não recomeçar o trabalho desktop nem publicar novas releases só porque o diário menciona pendências históricas.
- Atualizar `docs/releases/PUBLICACAO-WINDOWS.md` quando houver nova publicação Windows; preservar as instruções e evidências Mac existentes.
- Conferir branch/main remota antes de escrever: o outro chat pode atualizar documentação e código Mac em paralelo. Nunca sobrescrever esse trabalho.
- Antes de nova integração mobile, reler este diário e confirmar ambiente por evidências. A escolha de uma arquitetura ainda precisa ser feita; o que está aprovado é o objetivo e o escopo.
- Novas entradas devem informar data, decisão, arquivos/commits, validação real, limitações e próxima etapa. Não marcar um recurso como concluído apenas porque foi sugerido ou apareceu em um mockup.

## 25/10/2026 — retomada desktop antes do Android

Usuário solicitou e autorizou implementar: acompanhamento da reprodução na timeline principal com zoom; linhas finas nos limites das regiões, mais fortes na região atual; Shift + arraste de aresta independente sem mover vizinha, mantendo comportamento normal conectado; EQ/Gain e submenu com dimensões iguais (38 × 25 CSS px). Textos pt-BR/en/es. Validação local: 60 testes aprovados e auditoria sem achados; teste Electron sintético confirmou avanço/retorno da rolagem, ausência de rolagem parado, linhas [0,10,30], dimensões iguais e limites normais [0,8]/[8,30] versus Shift [0,6]/[8,30].

Publicação Windows solicitada na mesma v2.8.0, somente instalador/checksum; validar pacote e downloads antes de declarar concluída. Handoff Mac em `docs/releases/HANDOFF-MAC-TIMELINE.md`; o usuário também autorizou o outro chat a gerar e substituir somente os ZIPs/checksum Mac após portar e validar as quatro mudanças. Android continua como próxima fase, ainda sem implementação.

Publicação desta rodada concluída: instalador Windows do commit d18ff78419ae9c09f3c2cc5c77fc0f142ead33c6 e checksum substituídos com confirmação do usuário; downloads comparados por SHA-256, Mac/notas Mac/tag/Latest preservados. Testes repetidos no pacote instalado confirmaram as quatro mudanças e os recursos anteriores. O prompt de continuidade Mac foi entregue no chat com autorização para substituir somente os pacotes Mac após validação.
