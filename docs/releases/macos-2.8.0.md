# Preparação macOS — ZLPlay 2.8.0

Esta preparação não publica release, tag, branch remota ou instalador Windows. A publicação conjunta com Windows será feita somente após solicitação do usuário.

## Arquivos para a release

- `ZLPlay-2.8.0-macOS-Intel.zip`
- `ZLPlay-2.8.0-macOS-Apple-Silicon.zip`
- Checksums SHA-256 e notas macOS.

Mínimo: macOS 15 Sequoia. Intel e Apple Silicon são pacotes separados. Ambos incluem áudio nativo, reconhecimento offline, modelo multilíngue, logo aprovada e ícones dos documentos.

## Compilar e verificar

```sh
npm ci
node scripts/build-macos.mjs --arch x64 --dir
node scripts/verify-macos-package.mjs dist/macos-x64/mac/ZLPlay.app --arch x64
node scripts/build-macos.mjs --arch arm64 --dir
node scripts/verify-macos-package.mjs dist/macos-arm64/mac-arm64/ZLPlay.app --arch arm64
npm test
npm run audit:publication
```

Os pacotes registram o commit e a arquitetura em `package.json` dentro do ASAR. A verificação compara os arquivos `app/` e `desktop/` com o código local, verifica arquitetura, mínimo do sistema, modelo offline, ícones e bibliotecas nativas.

## Notas macOS

- Suporte nativo a Intel x64 e Apple Silicon ARM64.
- Painéis de seleção de arquivos trazidos para a frente no macOS.
- Lista de tons exibida na camada do diálogo que a abriu.
- Salvamento de repertório na pasta principal das músicas e abertura limitada às músicas registradas.
- Mensagem visível quando o salvamento de repertório é recusado.
- Reconhecimento offline incluído no pacote.
- Nova identidade aprovada e ícones aplicados a projetos e repertórios ao salvar.

## Validação e limites conhecidos

A suíte de 49 testes e a transcrição de exemplo foram executadas no Mac Intel. O usuário confirmou reprodução e outras operações no Intel. ARM64 foi compilado no Intel e verificado estruturalmente; execução em Apple Silicon permanece pendente. Os ícones personalizados foram conferidos pelos metadados gravados nos arquivos, sem confirmação visual automatizada no Finder.

Os pacotes locais usam assinatura ad-hoc, cuja integridade é verificada. Não têm assinatura de distribuição Developer ID nem notarização. Devem ser identificados como pacotes de teste/pré-release até a avaliação final. Não considerar `macOS 15+` uma garantia de validação em toda versão futura do sistema.

## Integração com Windows

O novo salvamento nativo e os auxiliares de ícones são exclusivos do macOS. Existem alterações em arquivos compartilhados de interface, seleção de arquivos e scripts de build; o responsável pelo Windows deve revisá-las e rodar os testes após integrar o commit do Mac.

Para uma release conjunta, a tag deve apontar para o commit final que reúne Windows e macOS. Se a integração alterar arquivos usados pelo Mac, recompilar os dois pacotes e atualizar seus checksums. Não publicar executáveis antigos como se tivessem sido gerados do commit final.

Na etapa autorizada de publicação: integrar os commits, conferir os quatro executáveis/pacotes, consolidar as notas, criar a tag acordada e publicar os assets. Nenhuma dessas mutações remotas é feita pela preparação local.
