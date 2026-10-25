# Identidade ZLPlay — ondas verticais

A identidade atual foi aprovada a partir do mockup de configurações Android: cinco ondas verticais arredondadas, com ZL branco e Play violeta. A marca da 1Byte Labs permanece em Sobre.

## Matrizes e exportações

- app/brand/zlplay-mark.svg: matriz vetorial do símbolo.
- app/favicon.svg: cópia do símbolo para o navegador.
- desktop/icon.png: 1024 × 1024 transparente para a janela do aplicativo.
- desktop/icon.ico: Windows, 16/24/32/48/64/128/256 px.
- desktop/icon.icns: ícone macOS com múltiplas resoluções, incluindo 1024 px.
- desktop/zlplay-document.ico e desktop/zlplay-document.icns: a mesma marca para .zlplay, .zlset e o repertório legado.
- approved-concept.png: conceito anterior, mantido apenas como histórico; não usar no build.

Paleta: #7C3AED, #8B5CF6 e #B88AF4; fundo de interface #0D111C. O wordmark na interface é texto, preservando a leitura e a nitidez.

## Windows

O NSIS registra os ícones explícitos de documentos definidos em build.fileAssociations. Atualizar/reinstalar o instalador atualiza as associações; a execução portátil não registra associações globais. O arquivo de projeto não precisa ser regravado para receber o ícone associado à extensão.

## Integração macOS

A branch codex/macos-setup tem código próprio de documentos e build. Integre este commit preservando esse código, sem substituir integralmente o package.json do Mac.

1. Atualize os arquivos da marca e defina mac.icon como desktop/icon.icns.
2. Acrescente icon: desktop/zlplay-document a todas as entradas de fileAssociations, preservando as demais opções do Mac.
3. Preserve os UTIs, CFBundleDocumentTypes, UTTypeIconFile e o helper nativo de documentos. Todos devem usar zlplay-document.icns novo. O helper lê esse arquivo em Resources/zlplay-document.icns; mantenha o extraResources existente.
4. Confira tanto Contents/Resources/icon.icns e Contents/Resources/zlplay-document.icns quanto o ícone usado pelo helper.
5. Aplique o novo ícone também aos documentos com ícone personalizado antigo, usando o helper existente com os caminhos exatos dos projetos/repertórios de teste. Alterar apenas a associação não substitui um ícone personalizado já gravado pelo Finder. Não percorra nem altere outros arquivos sem autorização.
6. Valide Finder/Dock, .zlplay/.zlset novos e existentes, abertura por duplo clique e os dois builds. Preserve traduções pt-BR/en/es.

Não é necessário mover a tag ou substituir silenciosamente assets publicados para alterar a marca no código. Os pacotes já publicados permanecem com a marca anterior até recompilação e publicação explicitamente autorizada.
