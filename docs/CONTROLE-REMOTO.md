# Controle remoto — Android e Windows

O Mobile oferece Reprodutor local e Controle remoto. Esta primeira implementação está habilitada somente no Windows. O macOS permanece fora desta etapa.

## Conectar

1. Execute o ZLPlay Windows atualizado e abra um projeto ou repertório.
2. Clique em **Controle remoto**, depois **Ativar na rede local**.
3. No Android, em **Configurações → Geral → Modo de reprodução**, escolha **Controle remoto**.
4. Na mesma rede local, informe o endereço IP e a porta exibidos no computador, além do código de oito dígitos.
5. Compare a impressão digital do certificado nos dois aparelhos e confirme no Android. Autorize a conexão no computador.

O código expira em cinco minutos. Para gerar outro, desative e reative o servidor. O Windows pode solicitar permissão de rede ao executar o aplicativo. Use uma rede privada compartilhada pelos dois aparelhos; redes de convidados podem bloquear a comunicação entre dispositivos. Não é necessário encaminhar portas no roteador.

## Comportamento

O áudio, os arquivos e o motor de reprodução continuam no computador. O Android recebe o estado do projeto e envia comandos de transporte, regiões, loop, Clique solo, Pad, tonalidade, BPM e mixer. A troca de música pelo remoto requer a reprodução parada. Arrastar o cursor requer pausa. O mixer oferece volume e os estados M/S que existem no desktop.

O botão Reprodutor retorna ao modo local. Desconectar o celular ou desligar o servidor remoto não interrompe a reprodução no computador. Após falha de rede, comandos pendentes são descartados; não se repetem automaticamente ao reconectar.

## Protocolo e limites

- HTTPS/TLS 1.2 ou superior, porta TCP 47839, ativação manual e certificado temporário mantido em memória.
- Comparação do certificado no pareamento; conexões seguintes fixam seu SHA-256. O código só é transmitido após essa comparação.
- Aprovação explícita no PC e token de sessão em memória nativa do Android. O WebView recebe somente dados da interface.
- Estado autoritativo do desktop, atualização aproximadamente a cada 200 ms, identificação do projeto e deduplicação de comandos.
- Comandos permitidos e limites de tamanho, taxa de pareamento e sessões. Nenhum comando executa código, abre caminhos ou transfere gravações.
- Primeira versão sem descoberta automática, QR code ou conexão pela internet. USB pode ser usado pelo teste automatizado com `adb reverse`; não é necessário no uso por Wi-Fi.

## Verificação

`npm test` cobre autenticação, autorização, certificado, comandos duplicados e rejeição de projeto desatualizado. A instrumentação Android `mode=remote` verifica o cliente TLS nativo com fixture sintética e argumentos de pareamento temporários. Um teste integrado deve usar perfil Electron isolado e áudio sintético; não use projetos pessoais como fixtures.

A validação por USB não comprova a configuração de firewall/Wi-Fi de cada rede. Não houve validação ou geração de pacote macOS nesta etapa.
