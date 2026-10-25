# Licenciamento / Licensing / Licencias

## Português

O código próprio do ZLPlay é AGPL-3.0-only. O projeto utiliza JUCE sob AGPLv3 e, no Windows, os headers do ASIO SDK sob a alternativa GPLv3 expressamente oferecida pela Steinberg. Não reivindicamos uma licença proprietária ASIO ou certificação Steinberg. A GPLv3, seção 13, permite a combinação com AGPLv3, mantendo as condições aplicáveis a cada parte.

O ASIO permite ao programa comunicar-se com drivers instalados separadamente. O ZLPlay não inclui ASIO4ALL nem drivers de fabricantes. No macOS, o motor utiliza CoreAudio; a integração ASIO é exclusiva do Windows.

ASIO is a registered trademark of Steinberg Media Technologies GmbH. A licença do código não concede direitos de marca. Não utilizamos o logotipo ASIO Compatible nem afirmamos aprovação da Steinberg. Qualquer uso futuro desse logotipo exige verificar suas diretrizes específicas.

O instalador Windows inclui `resources/licenses/`: AGPLv3, GPLv3, aviso original ASIO, avisos de terceiros, este documento e `SOURCE_CODE.txt`, com o commit completo usado no build e links dos fontes. Os arquivos automáticos “Source code” da tag podem não corresponder a um instalador atualizado; use o commit registrado. Quem redistribuir binários precisa cumprir as condições de fontes correspondentes da GPL/AGPL; um link genérico ao projeto não substitui essa obrigação. Disponibilize os fontes exatos e as instruções junto ao acesso aos binários, conforme a modalidade de distribuição.

## English

ZLPlay's own code is AGPL-3.0-only. JUCE is used under AGPLv3. On Windows, ASIO SDK headers are used under the GPLv3 alternative expressly offered by Steinberg. We do not claim a proprietary ASIO agreement or Steinberg certification. GPLv3 section 13 permits combining GPLv3 and AGPLv3 code while retaining the applicable terms for each part.

ASIO communicates with separately installed drivers. ZLPlay does not bundle ASIO4ALL or hardware drivers. macOS uses CoreAudio. ASIO is a registered trademark of Steinberg Media Technologies GmbH. Code licenses do not grant trademark rights; no ASIO Compatible logo or Steinberg endorsement is claimed.

The Windows installer includes `resources/licenses/`, containing AGPLv3, GPLv3, the original ASIO notice, third-party notices, this document and `SOURCE_CODE.txt` with the full build commit and source links. Automatic tag source archives may precede an updated installer. Redistributors must provide corresponding source and build instructions under GPL/AGPL requirements; a generic repository link is insufficient.

## Español

El código propio de ZLPlay es AGPL-3.0-only. JUCE se utiliza bajo AGPLv3 y los headers ASIO de Windows bajo la alternativa GPLv3 ofrecida expresamente por Steinberg. No afirmamos disponer de un contrato propietario ASIO ni de certificación Steinberg. La sección 13 de GPLv3 permite combinar código GPLv3 y AGPLv3 conservando las condiciones aplicables a cada parte.

Los drivers se instalan por separado. No se incluyen ASIO4ALL ni drivers de fabricantes. macOS utiliza CoreAudio. ASIO is a registered trademark of Steinberg Media Technologies GmbH. Las licencias del código no conceden derechos sobre marcas; no utilizamos el logotipo ASIO Compatible ni afirmamos aprobación de Steinberg.

El instalador Windows incluye `resources/licenses/`, con AGPLv3, GPLv3, aviso original ASIO, avisos de terceros, este documento y `SOURCE_CODE.txt` con el commit completo y enlaces a los fuentes. Los archivos automáticos de la etiqueta pueden ser anteriores al instalador actualizado. Al redistribuir binarios, se deben facilitar los fuentes correspondientes y las instrucciones exigidas por GPL/AGPL; un enlace genérico no basta.

## Fontes das dependências / Dependency sources / Fuentes

- JUCE 9.0.4 + ASIO: revision `3fb5cbc0340758596dac39f7c8de47d562b4896e`, fixed in `native/CMakeLists.txt`; archive SHA-256 `2bb7095497a7bb7269dbdf841a7c6aa0939eaeb2a2bc9724ac4758d3ae3565a7`. [Source](https://github.com/juce-framework/JUCE/tree/3fb5cbc0340758596dac39f7c8de47d562b4896e). Original ASIO license: `docs/licenses/ASIO-LICENSE.txt`; GPLv3: `docs/licenses/GPL-3.0.txt`.
- Signalsmith Stretch, Signalsmith Linear, libebur128: sources and notices included in `native/vendor/` at the ZLPlay build commit.
- whisper.cpp: revision `9386f239401074690479731c1e41683fbbeac557`, fixed in `scripts/build-speech.mjs`; this script records the small compatibility patch applied during preparation. [Source](https://github.com/ggml-org/whisper.cpp/tree/9386f239401074690479731c1e41683fbbeac557).
- Electron 44.9.2 and build dependencies: `package-lock.json`, Electron's bundled notices and [Electron source](https://github.com/electron/electron/tree/v44.9.2).
- Optional Whisper model: URL and expected SHA-256 in `scripts/download-model.mjs`; its MIT notice accompanies the model.

## Referências / References / Referencias

- [GNU GPLv3](https://www.gnu.org/licenses/gpl-3.0.html), sections 6 and 13.
- [GNU AGPLv3](https://www.gnu.org/licenses/agpl-3.0.html), sections 6 and 13.
- [Steinberg licensing FAQ](https://forums.steinberg.net/t/asio-sdk-proprietary-licensing-faq/1044024). Its proprietary-contract requirements must not be confused with the alternative GPLv3 terms in the actual SDK license.
