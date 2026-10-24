# Desenvolvimento macOS

O alvo principal é Apple Silicon (`arm64`, linha M), com macOS 15 Sequoia ou superior. A compatibilidade com versões posteriores precisa de validação; o mínimo não equivale a suporte testado em todas elas.

```sh
npm ci
npm run dist:mac:arm64
```

O comando compila o motor, os auxiliares de associação/ícones e o whisper.cpp para ARM64, verifica a arquitetura, prepara o modelo offline e gera DMG/ZIP. O modelo é compartilhado entre arquiteturas; os executáveis não são.

Para gerar somente o aplicativo de teste:

```sh
node scripts/build-macos.mjs --arch arm64 --dir
```

O SDK da Apple permite compilar ARM64 em um Mac Intel. Isso confirma a compilação, mas não permite executar o aplicativo ARM64 no Intel. Áudio, importação, dispositivos, reconhecimento, salvamento e ícones precisam ser testados em um Mac Apple Silicon antes da distribuição.

Recursos ARM64 ficam em `native/bin-arm64` e `native/speech-arm64`. O aplicativo fica em `dist/macos-arm64`. As pastas tradicionais `native/bin`, `native/speech` e a instalação Intel existente são preservadas. A configuração Windows não é modificada pelo fluxo macOS.

Em um Mac Apple Silicon, valide também o fluxo local e a suíte:

```sh
npm run build:native
npm run setup:speech
npm test
npm run audit:publication
```

Assinatura e notarização exigem configuração própria de Apple Developer. O build local usa assinatura ad-hoc por padrão, destinada a testes. Ela não equivale a Developer ID ou notarização. Para assinar com uma identidade de distribuição já configurada, defina ZLPLAY_MAC_IDENTITY.
