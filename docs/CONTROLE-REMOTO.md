# Controle remoto — Android e Windows

O Mobile oferece Reprodutor local e Controle remoto. Esta primeira implementação está habilitada somente no Windows. O macOS permanece fora desta etapa.

## Conectar

1. Execute o ZLPlay Windows atualizado e abra um projeto ou repertório.
2. Clique em **Controle remoto → Gerar QR code**.
3. No Android, escolha **Configurações → Controle remoto → Ler QR code**.
4. Aponte a câmera para o QR do computador. A conexão é automática, sem digitar endereço, porta ou senha.

Os dois aparelhos precisam estar na mesma rede local. O QR inclui uma chave aleatória de 256 bits e o certificado TLS do computador, válidos por dez minutos. Gerar outro QR invalida o anterior para novos pareamentos, preservando sessões já conectadas. Outro celular também precisa ler o QR para obter acesso. A câmera processa os quadros localmente, sem salvar ou enviar imagens.

## Comportamento

O áudio, os arquivos e o motor de reprodução continuam no computador. O Android recebe o estado do projeto e envia comandos de transporte, regiões, loop, Click solo, Pad com seleção de nota, BPM e mixer. A troca de música pelo remoto requer a reprodução parada. A timeline compacta serve para tocar nos cards e saltar entre regiões; o cursor é apenas visual no remoto. O transporte ocupa a maior parte da área de controles. O mixer oferece volume e os estados M/S que existem no desktop.

O botão Reprodutor pede confirmação antes de encerrar a conexão e retornar ao modo local. Cancelar preserva a sessão. A tela de conexão integrada às configurações não repete esse botão. Desconectar o celular ou desligar o servidor remoto não interrompe a reprodução no computador. Após falha de rede, comandos pendentes são descartados; não se repetem automaticamente ao reconectar.

## Protocolo e limites

- HTTPS/TLS 1.2 ou superior, porta TCP escolhida automaticamente pelo sistema e incluída no QR, ativação manual e certificado temporário mantido em memória.
- O QR transporta o SHA-256 do certificado, fixado automaticamente pelo cliente antes de transmitir a chave.
- Leitura do QR autoriza o pareamento; o token de sessão fica em memória nativa do Android. O WebView recebe somente dados da interface.
- Estado autoritativo do desktop, atualização aproximadamente a cada 200 ms, identificação do projeto e deduplicação de comandos.
- Comandos permitidos e limites de tamanho, taxa de pareamento e sessões. Nenhum comando executa código, abre caminhos ou transfere gravações.
- Sem descoberta automática ou conexão pela internet. USB pode ser usado pelo teste automatizado com `adb reverse`; não é necessário no uso por Wi-Fi.

## Verificação

`npm test` cobre autenticação, autorização, certificado, comandos duplicados e rejeição de projeto desatualizado. A instrumentação Android `mode=remote` verifica o cliente TLS nativo com fixture sintética e argumentos de pareamento temporários. Um teste integrado deve usar perfil Electron isolado e áudio sintético; não use projetos pessoais como fixtures.

A validação por USB não comprova a configuração de firewall/Wi-Fi de cada rede. Não houve validação ou geração de pacote macOS nesta etapa.

## Interface e presença

O remoto usa transporte em uma linha e grupos Click/Pad abaixo. A timeline permite agendar saltos ao tocar na região; o cursor é animado localmente, com referências de tempo do PC, correção gradual de deriva e sincronização imediata em descontinuidades. O tom efetivo em cada card é informativo; a transposição da música não aparece na interface remota.

No Windows, um card com ponto verde informa quantos dispositivos estão ativos e permanece no cabeçalho da música em tela cheia. A presença expira após aproximadamente três segundos sem requisições, sem interromper o áudio. Gerar QR habilita o servidor, mas não conta como conexão; a janela distingue esses estados. A indicação Próxima mostra abaixo do nome o destino tonal Tocar em efetivo.
