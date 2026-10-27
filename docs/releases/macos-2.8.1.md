# macOS 2.8.1

Porte seletivo da referência compartilhada `4025815adc75812122cffcadc18369d7ae945a31`. O Mac mantém CoreAudio, seleção nativa de arquivos, salvamento de repertórios na pasta correta, ícones/UTIs, implantação mínima macOS 15 e isolamento dos recursos Intel/ARM64.

## Recursos

- Próxima música com nome e tom efetivo em bemóis, independente de habilitar o remoto.
- Protocolo Mobile v1 sobre HTTPS local com convite QR, chave temporária e fingerprint TLS; nenhuma porta/senha digitada manualmente. O servidor inicia desligado.
- Contagem de dispositivos ativos antes da navegação Repertório, inclusive em tela cheia; desconexão não comanda o áudio.
- Indicadores de Loop/Click solo e Salto agendado com símbolos e cancelamento.
- Textos pt-BR/en/es; descrição de acesso à rede local localizada nas três línguas.

## Compilar

```sh
source tools/env.sh
npm ci
npm test
npm run audit:publication
node scripts/build-macos.mjs --arch x64 --dir
node scripts/verify-macos-package.mjs dist/macos-x64/mac/ZLPlay.app --arch x64
node scripts/build-macos.mjs --arch arm64 --dir
node scripts/verify-macos-package.mjs dist/macos-arm64/mac-arm64/ZLPlay.app --arch arm64
```

Os scripts registram commit local, arquitetura e revisão dos fontes publicados. Quando Git e conector gerarem commits equivalentes diferentes, definir `ZLPLAY_SOURCE_COMMIT` com o snapshot publicado de mesmo tree antes do build. Não alterar arquivos durante o empacotamento. As licenças e SOURCE_CODE.txt ficam em Contents/Resources/licenses; licenças de Electron e runtimes nativos também permanecem no pacote.

## Rede local e validação

Pareamento é voluntário pelo botão Controle remoto; manter Mac e Mobile na mesma rede. Não modificar firewall/TCC automaticamente. Validar QR, certificado, transporte, saltos/cancelamento, Loop/Click solo, Pad/BPM/mixer e desconexão durante áudio. Teste por cliente de protocolo no próprio Mac não equivale a teste físico do Android ou da câmera/Wi-Fi entre aparelhos.

Publicar somente os dois ZIPs 2.8.1 e SHA256SUMS-macOS.txt na v2.8.1 existente. As notas finais devem registrar os testes e limitações realmente observados, o commit do build e os fontes correspondentes. Preservar Windows/Android, a classificação da release, a v2.8.0 e ambas as tags.
