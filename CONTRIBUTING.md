# Contribuir com ZlPlay

1. Instale as dependências e compile o motor conforme o README na sua plataforma.
2. Crie uma branch para a mudança e mantenha os textos da interface em pt-BR, en e es.
3. Use `path.join`, as funções de `desktop/runtime-paths.cjs` e diretórios de dados do Electron. Não acrescente caminhos absolutos de uma máquina ou dependências obrigatórias de PowerShell ao fluxo comum.
4. Proteja trechos de sistema em `native/platform-support.h`. Mudanças no callback de áudio não devem introduzir leitura de disco ou alocações na thread de áudio.
5. Rode `npm test`, a auditoria e revise o diff. Use somente fixtures sintéticas.
6. Abra um pull request descrevendo o comportamento, a validação e as plataformas efetivamente testadas. A revisão em Windows não substitui a avaliação de áudio em um Mac.

Contribuições ao código próprio são recebidas sob a AGPLv3 do projeto. Preserve as licenças de arquivos de terceiros.

Diários de trabalho, resumos de conversas e planejamento privado devem permanecer em local-notes/ (ignorada pelo Git). Publique somente documentação técnica necessária ao projeto; não adicione diários ao repositório.
