# Motor nativo ZLPlay

Processo C++ separado da interface Electron, usando JUCE 9.0.4 e Signalsmith Stretch/Linear. O código do Trilha e do REAPER não é usado.

## Reprodução

Arquivos locais são selecionados pelo usuário. O preload obtém seus caminhos com `webUtils.getPathForFile`; o processo principal registra identificadores aleatórios e aceita somente esses identificadores nas referências de áudio. A interface recebe duração, canais e taxa, em vez de um PCM de toda a música.

O motor tem três partes: leitura antecipada com `BufferingAudioReader` e `TimeSliceThread`; leitura dos trechos, conversão de taxa e mixagem em uma thread de processamento; entrega de blocos de uma fila limitada ao dispositivo. A saída usa um relógio comum para todas as tracks. O callback do dispositivo não lê arquivos nem cria objetos de processamento.

Cada item mantém posição na timeline, início no arquivo e comprimento. A leitura mantém duas janelas limitadas por item para preparar o início do loop. Áudio tonal passa por grupos Signalsmith; Click/Guia e instrumentos excluídos do tom recebem compensação de atraso quando há transposição. O arquivo original não é regravado.

O motor tenta a taxa da fonte; se a saída atual não a aceitar, usa a taxa do dispositivo e converte somente os blocos em reprodução. Isso não implica decodificar a música inteira nessa taxa.

O Click nativo usa o mesmo `Meuclick.wav` do Solo Click, incluído junto do executável pelo script de compilação. Apenas essa pequena amostra fixa é carregada integralmente; as músicas continuam sendo lidas em blocos. Acento e sobreposição de caudas seguem o comportamento do Solo Click.

As prévias de formas de onda são calculadas por outro processo nativo, depois de abrir o mixer. Os dados ficam em memória; não é escrito um cache de áudio ao lado dos WAVs.

## Verificação

`tests/native.test.cjs`: leitura de metadados, soma das tracks, silêncio, pan, posição/offset de um impulso, repetição exata e transposição de um tom de 440 para 880 Hz. Todos esses testes são offline.

Os testes públicos usam fixtures sintéticas. Testes e resultados locais com músicas reais não são publicados.

`tests/native-protocol.test.cjs` verifica a inicialização, respostas sucessivas,
recuperação após um comando inválido e encerramento por EOF, sem abrir arquivos
ou dispositivos de áudio. No macOS, o processo de console cria primeiro a
aplicação AppKit para que a fila de comandos do JUCE seja efetivamente atendida.

Em uma carga `offline: true`, somente os comandos de renderização avançam o
processamento; a thread de reprodução não completa essa fila em segundo plano.
`tests/offline-render.test.cjs` intercala esperas e mudanças de ganho para verificar
que a rampa depende da quantidade de samples renderizados, não do tempo entre
comandos ou da velocidade da máquina.

## Limites desta versão de teste

- A interface usa Electron. Tracks locais, Pad e Solo Click usam o mesmo motor/dispositivo nativo. Os WAVs curtos dos Pads são carregados sob demanda; músicas continuam por streaming.
- ZIP ainda usa o importador anterior em memória. Para leitura por blocos, extraia os áudios e use Abrir pasta / projeto.
- WAV, MP3, FLAC, OGG e AIFF dependem dos formatos compilados no JUCE; AAC/M4A não estão habilitados neste motor. A interface informa falha de formato quando o leitor nativo não aceita o arquivo.
- Há testes automáticos e saída real silenciada, mas ainda falta avaliação auditiva humana com a interface de áudio usada em apresentações.
- ASIO foi habilitado no Windows com os headers incluídos no JUCE. Dispositivo, taxa e buffer são configuráveis; o painel do driver é aberto quando o dispositivo o oferece. Compatibilidade com uma interface física específica requer validação no hardware.
- O tamanho da fila de mixagem limita o consumo, mas pode acrescentar até aproximadamente 0,17–0,36 s de resposta a controles de track, conforme a taxa. O master é aplicado no callback apenas aos envios para o Master. Saídas diretas não são duplicadas nele. Não é uma entrega validada para shows.

## Compilar

Use `npm run build:native`, que executa o CMake em Windows e macOS. A revisão e o hash do JUCE estão fixados em `CMakeLists.txt`. `platform-support.h` contém as chamadas específicas de sistema. Consulte os requisitos de compilador no README da raiz.

`native/build.ps1` é uma alternativa antiga exclusiva de Windows para um ambiente com MSVC e Windows SDK já extraídos em `tools/`. Essas ferramentas não fazem parte do repositório. Depois de compilar, o empacotamento inclui `native/bin/` nos recursos do aplicativo.

As ferramentas e dependências de compilação ficam locais ao projeto. Nenhuma alteração no programa REAPER é necessária.

## Licenças

JUCE é oferecido sob AGPLv3 ou licença comercial: https://juce.com/legal/juce-9-licence/. O ZlPlay adota AGPLv3. Signalsmith Stretch e Linear usam MIT; os avisos originais são incluídos junto ao motor. Electron e as bibliotecas de codecs têm seus próprios avisos. Consulte `../THIRD_PARTY_NOTICES.md`.

## Roteamento 1.9.0

Fila de até 64 saídas físicas e dois canais internos de Master. Grupos de DSP são separados por transposição e destino para não misturar canais de saídas diferentes. Destinos mono somam L/R com fator 0,5. Configurações de dispositivo são globais ao computador; rotas são armazenadas no projeto. A UI permite herança track → grupo → Bus VS → Master ou saída direta.

Testes novos em tests/audio-routing.test.cjs: isolamento de quatro saídas, mono, par estéreo, Master zerado sem afetar envios diretos, Master em outro par, transposição, click e Pad/Solo Click no callback. Verificação de UI inclui aplicação do dispositivo atual, salvar/reabrir rotas e janela em tela cheia.
