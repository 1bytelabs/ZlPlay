# Publicar Windows na release existente do ZLPlay 2.8.0

O usuário autorizou publicar primeiro o macOS e acrescentar o Windows depois, na mesma release.

## Destino

- Repositório existente: `1bytelabs/ZlPlay`.
- Release: [v2.8.0](https://github.com/1bytelabs/ZlPlay/releases/tag/v2.8.0).
- Não criar outro repositório, outra release ou outra tag ao acrescentar o Windows.
- A solicitação futura do usuário para publicar a versão Windows autoriza executar esta etapa. Até essa solicitação, apenas preparar.

## Preparação no computador Windows

1. Preserve o trabalho local e use a versão Windows final aprovada pelo usuário. Confira a versão em `package.json`; não renomeie um executável de outra versão para parecer 2.8.0.
2. Compile o motor e prepare o reconhecimento offline com os scripts do projeto. Confirme que os runtimes e o modelo estão nos recursos do aplicativo.
3. Rode `npm test` e `npm run audit:publication`. Gere os pacotes com `npm run dist:win` e valide a instalação e a versão portátil no Windows.
4. Separe estes arquivos, com a versão consistente:
   - `ZLPlay-2.8.0-instalador.exe`
   - `ZLPlay-2.8.0-portatil.exe`
   - `SHA256SUMS-Windows.txt`, com SHA-256 dos dois executáveis.
5. Registre o commit real do código Windows usado no build. A release reúne plataformas; não diga que um executável foi gerado de um commit diferente daquele efetivamente utilizado.

## Publicar quando o usuário pedir

1. Leia a release existente e seus assets antes de alterar.
2. Acrescente somente os dois executáveis Windows e `SHA256SUMS-Windows.txt`.
3. Preserve os ZIPs do Mac, `SHA256SUMS-macOS.txt` e as notas existentes. O usuário decidiu manter o bundle dos fontes somente como backup local; não o recoloque nos anexos da release.
4. Se um asset Windows de mesmo nome já existir, compare o hash: reutilize se for idêntico. Não sobrescreva uma versão diferente silenciosamente.
5. Atualize a descrição acrescentando uma seção Windows com os downloads, a validação e o commit do build. Preserve a seção macOS.
6. Não mova a tag `v2.8.0` nem troque seu commit para simplesmente anexar os arquivos Windows.
7. Mantenha a classificação de pré-release e os avisos do Mac enquanto as pendências persistirem. Não apresente os pacotes como assinados por Developer ID/notarizados: os builds Mac têm assinatura ad-hoc e ainda precisam de validação em hardware Apple Silicon.
8. Confira no GitHub os arquivos anexados e informe ao usuário o link da mesma release.

## Código macOS

A branch `codex/macos-setup` contém o snapshot Mac publicado. Seu conteúdo corresponde ao Git tree `907c4e934c570e518c51c155f0509f3900b5048c`, o mesmo do build local `6c0682ee52c0172e49ab0532f6e2f6c118156486`. O snapshot remoto tem commit próprio porque foi enviado pelo conector; os arquivos são idênticos.

Não é necessário substituir o trabalho Windows pelo Mac para anexar assets. Se integrar mudanças compartilhadas, revise o diff e rode novamente os testes Windows. Preserve as correções específicas de cada plataforma.
