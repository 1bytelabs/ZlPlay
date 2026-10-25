# Publicar Windows na release existente do ZLPlay 2.8.0

Em 25/10/2026, o usuário escolheu atualizar a release v2.8.0 existente com a nova logo de ondas verticais. O Mac está sendo atualizado primeiro; o chat Windows deve atualizar somente o instalador Windows e seu checksum quando o usuário solicitar. Não criar release v2.8.1 ou duplicar a v2.8.0.

## Destino

- Repositório existente: `1bytelabs/ZlPlay`.
- Release: [v2.8.0](https://github.com/1bytelabs/ZlPlay/releases/tag/v2.8.0).
- Não criar outro repositório, outra release ou outra tag ao acrescentar o Windows.
- A solicitação futura do usuário para publicar a versão Windows autoriza executar esta etapa. Até essa solicitação, apenas preparar.

## Preparação no computador Windows

1. Preserve o trabalho local e use a versão Windows final aprovada pelo usuário. Confira a versão em `package.json`; não renomeie um executável de outra versão para parecer 2.8.0.
2. Compile o motor e prepare o reconhecimento offline com os scripts do projeto. Confirme que os runtimes e o modelo estão nos recursos do aplicativo.
3. Rode `npm test` e `npm run audit:publication`. Gere os pacotes com `npm run dist:win` e valide a instalação no Windows.
4. Separe estes arquivos, com a versão consistente:
   - `ZLPlay-2.8.0-instalador.exe`
   - `SHA256SUMS-Windows.txt`, com SHA-256 do instalador.
5. Registre o commit real do código Windows usado no build. A release reúne plataformas; não diga que um executável foi gerado de um commit diferente daquele efetivamente utilizado.

## Publicar quando o usuário pedir

1. Leia a release existente e seus assets antes de alterar.
2. Atualize somente o instalador Windows e `SHA256SUMS-Windows.txt` na release v2.8.0 existente. A solicitação do usuário para publicar o Windows autoriza a atualização; respeite eventuais confirmações obrigatórias da ferramenta.
3. Preserve os ZIPs do Mac, `SHA256SUMS-macOS.txt` e as notas existentes. O usuário decidiu manter o bundle dos fontes somente como backup local; não o recoloque nos anexos da release.
4. Se um asset Windows de mesmo nome já existir, compare o hash: reutilize se for idêntico. Não sobrescreva uma versão diferente silenciosamente.
5. Atualize a descrição acrescentando uma seção Windows com os downloads, a validação e o commit do build. Preserve a seção macOS.
6. Não mova a tag `v2.8.0` nem troque seu commit para simplesmente anexar os arquivos Windows.
7. Por solicitação do usuário, a v2.8.0 agora está marcada como Latest para aparecer na página inicial do repositório. Preserve essa configuração e os avisos técnicos nas notas; não restaure a marcação de pré-release automaticamente. Apple Silicon ainda aguarda teste em hardware, e os pacotes Mac não têm Developer ID/notarização.
8. Confira no GitHub os arquivos anexados e informe ao usuário o link da mesma release.

## Código macOS

A nova marca está no commit `a2aefa71f63da0dcc3db97cd5678e801982a8e8b` de `codex/macos-setup`. O build local foi gerado do commit `6c022b181f94cc661e4dd13e6678a019be3676ee`; ambos têm o Git tree `a6cd234675b8d433aa408fd21e18b432d670d9d1`. A tag v2.8.0 continua no snapshot original e não deve ser movida.

Pacotes Mac com ondas verticais (confira estes hashes antes de concluir a atualização Windows):
- `ZLPlay-2.8.0-macOS-Intel.zip`: `41e4fab9bde7cb46635e98c1279544362a2a98a58471c3c7fb98969aab730eb8`.
- `ZLPlay-2.8.0-macOS-Apple-Silicon.zip`: `d89e26a25891bdac133721f8cf8a4f20edb81b2eec66e53e6f1c143988b26b37`.

49 testes passaram e a auditoria não teve achados. Os dois apps passaram na verificação de arquitetura, 77 arquivos, modelo offline e assinatura ad-hoc. Finder/Sobre, documentos e duplo clique foram conferidos em Intel. Apple Silicon ainda não executado em hardware M; Dock sem confirmação visual; DMGs falharam no hdiutil, portanto apenas ZIPs. Preserve estes limites nas notas.

Não é necessário substituir o trabalho Windows pelo Mac para anexar assets. Se integrar mudanças compartilhadas, revise o diff e rode novamente os testes Windows. Preserve as correções específicas de cada plataforma.

## Distribuição Windows somente instalável

Em 25/10/2026, o usuário pediu corrigir o dropdown do tom original no Windows, substituir o instalador da v2.8.0 e retirar a versão portátil da release. Não gerar nem publicar portátil. O arquivo SHA256SUMS-Windows.txt deve listar somente o instalador. Preservar os assets/notas do Mac, Latest e a tag.
