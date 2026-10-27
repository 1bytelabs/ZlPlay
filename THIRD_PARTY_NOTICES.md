# Componentes e avisos de terceiros

A AGPLv3 escolhida para o código próprio não substitui as licenças das dependências. Preserve os avisos originais ao redistribuir.

| Componente | Uso | Licença / fonte |
| --- | --- | --- |
| JUCE 9.0.4 | Dispositivos, leitura e infraestrutura de áudio | AGPLv3 ou licença comercial. Este projeto adota o caminho AGPLv3. [Fonte e licença](https://github.com/juce-framework/JUCE/tree/9.0.4). Obtido pelo CMake, não incluído no Git. |
| Steinberg ASIO SDK (Windows) | Comunicação com drivers ASIO através do JUCE | Este projeto escolhe a alternativa GPLv3. Aviso original em `docs/licenses/ASIO-LICENSE.txt`; GPLv3 em `docs/licenses/GPL-3.0.txt`. Headers na revisão fixa do JUCE indicada em `native/CMakeLists.txt`. ASIO is a registered trademark of Steinberg Media Technologies GmbH. |
| Signalsmith Stretch | Transposição e compensação de duração | MIT; `native/vendor/signalsmith-stretch-main/LICENSE.txt`. |
| Signalsmith Linear | Rotinas usadas pelo Stretch | MIT; `native/vendor/linear-main/LICENSE.txt`. |
| libebur128 | Loudness e true peak | MIT; `native/vendor/libebur128/COPYING`. A implementação BSD de `sys/queue.h` contém seu aviso no próprio arquivo. |
| Electron | Aplicativo desktop | MIT e componentes Chromium/Node com seus avisos. Instalado por npm; conservar os arquivos de licença produzidos pelo empacotamento. |
| electron-builder | Empacotamento de desenvolvimento | MIT; instalado por npm, fora da distribuição do código próprio. |
| whisper.cpp v1.8.4 | Reconhecimento opcional local | MIT; `native/speech/WHISPER-LICENSE.txt` e [fonte](https://github.com/ggml-org/whisper.cpp/tree/v1.8.4). |
| Modelo Whisper Base Q5_1 | Pesos opcionais para reconhecimento | MIT; `native/speech/MODEL-LICENSE.txt` e [distribuição](https://huggingface.co/ggerganov/whisper.cpp). Não incluído no Git. |

Os avisos LLVM, MinGW e winpthreads mantidos em `native/speech/` correspondem aos binários Windows usados no ambiente de desenvolvimento anterior. Compilar o reconhecimento em outra plataforma pode envolver um runtime diferente, cujo aviso também deve acompanhar a distribuição quando exigido.

Os arquivos de áudio dos usuários e os Pads não estão incluídos nem licenciados por este repositório. Nome e logotipo do projeto identificam a 1Byte Labs; a licença do código não concede autorização de uso das marcas.

Modelo de licença, fontes e escopo por plataforma: [docs/LICENSING.md](docs/LICENSING.md).

## QR code generation (Windows remote)
node-qrcode 1.5.4, Copyright (c) 2012 Ryan Day, MIT. Full license in docs/licenses/QRCode-MIT.txt and packaged node_modules/qrcode/license. Sources: https://github.com/soldair/node-qrcode/tree/v1.5.4. Runtime dependencies retain their license files in the packaged node_modules tree.

