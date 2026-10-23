# Segurança e privacidade

Não publique senhas, tokens, certificados, `.env`, bancos de dados reais, gravações ou projetos de clientes em issues, commits ou pull requests.

Para um relato sensível, use o canal de contato publicado no perfil da organização [1bytelabs](https://github.com/1bytelabs), solicitando um meio privado. Não coloque o segredo em uma issue pública. Se um segredo já tiver sido exposto, revogue-o no serviço de origem antes de limpar o histórico; apagar o arquivo em um commit posterior não remove versões anteriores.

Antes de contribuir, execute `npm run audit:publication` e confira `git diff --cached`. Não use `git add -f` para contornar as proteções do `.gitignore` sem revisar o conteúdo.

O motor recebe referências temporárias de arquivos autorizados pelo processo principal; esses identificadores IPC não são chaves de serviços externos. O reconhecimento de Guia é local e os arquivos temporários são removidos ao terminar ou cancelar.
