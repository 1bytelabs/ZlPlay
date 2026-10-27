# ZlPlay

Player de multitracks e performance ao vivo da **1Byte Labs**, com interface Electron e motor de áudio C++ separado. Inclui mixer, regiões, repertórios, transposição, click e Pads personalizados. A reprodução usa arquivos locais; a detecção de regiões pela Guia funciona offline após instalar o runtime opcional de reconhecimento.

## Instalar o aplicativo no Mac

Baixe o ZIP Intel ou Apple Silicon nas [releases](https://github.com/1bytelabs/ZlPlay/releases), extraia e arraste **ZLPlay.app** para **Aplicativos** no Finder. Abra pela pasta Aplicativos. Para atualizar, feche o ZLPlay e escolha **Substituir** ao copiar a nova versão. Não é necessário compilar nem instalar Node.js para usar os pacotes prontos.

[Guia de instalação e atualização em português, inglês e espanhol](docs/INSTALAR-macOS.txt), também incluído nos ZIPs como `LEIA-ME-INSTALACAO-macOS.txt`.

## Plataformas

| Plataforma | Situação |
| --- | --- |
| Windows x64 | Base original do aplicativo; motor e testes validados localmente. |
| macOS Intel / Apple Silicon | Caminhos, código de plataforma e compilação CMake preparados. A matriz de CI compila e testa o código no macOS. A reprodução em hardware e o empacotamento no Mac precisam de validação nessa plataforma. |

Use o mesmo repositório nos dois sistemas. Não compartilhe `node_modules`, `native/bin`, `.cache` ou `dist` entre Windows e macOS: cada máquina deve instalar e compilar suas dependências.

## Requisitos

- Node.js **22 ou superior**, npm e Git.
- CMake **3.24 ou superior**.
- Windows: Visual Studio 2022 Build Tools, workload **Desktop development with C++** e Windows SDK. Use um terminal de desenvolvimento x64 quando necessário.
- macOS: Xcode Command Line Tools (`xcode-select --install`) e CMake (`brew install cmake`, caso use Homebrew).
- Internet durante a instalação das dependências e downloads iniciais. A operação normal não requer serviços de nuvem nem chaves de API.

## Executar

```sh
git clone https://github.com/1bytelabs/ZlPlay.git
cd ZlPlay
npm ci
npm run build:native
npm start
```

O build baixa o **JUCE 9.0.4** com revisão e SHA-256 fixados no CMake. O executável fica em `native/bin/zlplay-audio.exe` no Windows e `native/bin/zlplay-audio` no macOS. O click padrão é copiado automaticamente para essa pasta.

Opcionalmente, `CMAKE` pode apontar para um executável CMake específico e `JUCE_SOURCE_DIR` para um checkout local completo do JUCE 9.0.4. São caminhos locais de ferramentas, não credenciais; não é necessário criar `.env` ou `.env.example`. O aplicativo não carrega arquivos `.env`.

## Reconhecimento offline de regiões

```sh
npm run setup:speech
```

Esse comando compila `whisper.cpp` na revisão fixada de **v1.8.4** e baixa o modelo multilíngue Base Q5_1, verificando seu SHA-256. Fonte e build temporários ficam em `.cache/`; runtime e modelo ficam em `native/speech/`. Esses arquivos não são versionados. Consulte [native/SPEECH.md](native/SPEECH.md).

Sem essa etapa, o restante do player pode ser desenvolvido normalmente; a detecção de regiões não estará disponível. As posições e palavras reconhecidas devem ser revisadas pelo usuário.

## Testes e revisão de publicação

```sh
npm test
npm run audit:publication
```

Compile o motor antes de rodar os testes. As fixtures de áudio são sintetizadas pelos testes, sem músicas, gravações ou projetos reais. O auditor verifica os arquivos candidatos ao Git e informa somente caminho, linha e tipo de achado, nunca o conteúdo de uma possível credencial. Para conferir o índice antes de um commit:

```sh
node scripts/audit-publication.cjs --staged
git diff --cached --stat
```

O auditor é uma proteção adicional, não uma garantia de ausência de segredos. Revise também arquivos novos e o diff.

## Gerar executáveis

Na máquina de destino, após compilar o motor e preparar o reconhecimento opcional:

```sh
# Windows x64: instalador e portátil
npm run dist:win

# macOS: DMG e ZIP para a arquitetura da máquina
npm run dist:mac
```

Não há certificados ou credenciais de assinatura neste repositório. Assinatura e notarização de releases macOS devem ser configuradas separadamente por quem distribui. Builds de desenvolvimento não equivalem a uma release assinada. Os artefatos ficam em `dist/`, fora do Git.

O script antigo `npm run build:native:legacy` permanece como alternativa exclusivamente Windows para o ambiente local que já possui o compilador extraído em `tools/`. Ele não é requisito do novo fluxo CMake.

## Estrutura e dados locais

- `app/`: interface e processamento de controle.
- `desktop/`: Electron, IPC, acesso autorizado aos arquivos e persistência.
- `native/`: motor, código de plataforma e dependências de áudio com seus avisos.
- `tests/`: testes com dados sintéticos.
- `scripts/`: compilação, runtime opcional, testes e auditoria.

Pads e sons importados ficam em `Sounds/` dentro da pasta de dados do usuário fornecida pelo Electron: AppData no Windows e Application Support no macOS. Projetos e áudios do usuário não pertencem ao repositório.

O `.gitignore` usa uma lista explícita de pastas de código na raiz. Novas pastas públicas devem ser adicionadas conscientemente. Ferramentas baixadas, notas de desenvolvimento locais, modelos, binários, `.env`, certificados, bancos e projetos de áudio ficam de fora. Essa regra não apaga arquivos locais.

## Licença e contribuições

Código próprio sob **GNU AGPL v3.0**, conforme [LICENSE](LICENSE). Dependências mantêm suas próprias licenças e avisos; veja [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). A licença de software não concede direitos de marca sobre o nome ou logotipo ZLPlay / 1Byte Labs.

Leia [CONTRIBUTING.md](CONTRIBUTING.md) e [SECURITY.md](SECURITY.md) antes de enviar contribuições ou relatar problemas sensíveis.
