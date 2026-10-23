# Reconhecimento offline de Guia

Motor whisper.cpp v1.8.4 (MIT), modelo Base multilíngue quantizado Q5_1 (MIT). Áudio não é enviado à internet. Executáveis e modelo não são publicados no Git: execute `npm run setup:speech` na plataforma de desenvolvimento.

Fontes:
- https://github.com/ggml-org/whisper.cpp/tree/v1.8.4
- https://huggingface.co/ggerganov/whisper.cpp

Modelo ggml-base-q5_1.bin SHA256: 422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898

No Windows, o ambiente original disponibilizava fallback SSE4.2 e otimizado AVX2/FMA/F16C. O novo script público compila uma versão baseline para a arquitetura local. Apple Silicon usa o binário ARM nativo; não tenta executar uma versão x86. Uma variante otimizada opcional somente é escolhida quando existe e a CPU informa os recursos exigidos.

Build: LLVM MinGW UCRT, CMake 4.1.0 + Ninja 1.13.1. whisper.cpp v1.8.4 com include <algorithm> acrescentado a examples/common.cpp para libc++. BUILD_SHARED_LIBS=OFF, GGML_STATIC=ON, GGML_OPENMP=OFF, GGML_NATIVE=OFF, GGML_BMI2=OFF, WHISPER_BUILD_TESTS=OFF, WHISPER_SDL2=OFF, CMAKE_EXE_LINKER_FLAGS=-static. Fallback: AVX/AVX2/FMA/F16C OFF. Otimizado: AVX/AVX2/FMA/F16C ON. Compilar target whisper-cli. Licenças MIT, LLVM e MinGW acompanham os recursos.

Fluxo: referências autorizadas de Guia/Guide são renderizadas em worker separado, WAV mono 16 kHz temporário. Segmentação por energia isola anúncios com margens e normalização; cada trecho é transcrito preservando seu horário original. O motor é carregado uma vez por lote. Vocabulário musical em português orienta a decodificação e reduz confusões em palavras curtas. Progresso usa arquivos de transcrição efetivamente concluídos. Temporários removidos ao concluir/cancelar.

Regiões entram diretamente na timeline. Nomes e números seguem o anúncio: repetir Verso 1 não o transforma em Verso 3. Se a guia inicial não nomear uma parte, a introdução pode ser inferida até o primeiro comando. Posições são aproximadas: não se presume uma distância fixa entre anúncio e entrada musical. Usuário revisa na grade. Ctrl+Z e Descartar restauram o estado anterior.

O resultado e a duração da análise dependem da articulação, qualidade da Guia e capacidade da CPU. As gravações usadas em avaliações locais não fazem parte do repositório.
