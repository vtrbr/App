# Tedflix Studio

Aplicativo Android administrativo do ecossistema Tedflix, desenvolvido em Kotlin com uma interface WebView responsiva e identidade visual compartilhada com o app principal.

## Escopo

O Studio usa o servidor de autenticação do Tedflix para login administrativo, mantém a sessão em cache seguro no Android Keystore e apresenta as áreas administrativas previstas: dashboard, usuários, criação de acesso, notificações, agendamentos, histórico, logs, estatísticas e configurações.

A interface utiliza o tema escuro do Tedflix, vermelho de destaque, cards, badges, gradientes, avatares e navegação adaptada para telas de celular e tablet.

## API

```text
https://authted.onrender.com
```

As chamadas administrativas são feitas pela ponte nativa `AndroidStudio`. O Bearer Token não é exposto ao JavaScript; ele permanece armazenado no armazenamento seguro da sessão Android.

## Estrutura

```text
adm/
├── app/
│   ├── src/main/java/com/tedflix/studio/
│   │   ├── MainActivity.kt
│   │   └── auth/StudioSession.kt
│   └── src/main/assets/studio/
│       ├── index.html
│       ├── css/style.css
│       └── js/app.js
├── docs/admin-api-contracts.md
├── artifacts/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Compilação

No Linux ou macOS:

```bash
cd adm
./gradlew assembleDebug
```

No Windows:

```bat
cd adm
gradlew.bat assembleDebug
```

O APK de desenvolvimento é gerado em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Instalação para teste

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

O APK entregue no repositório fica em `adm/artifacts/tedflix-studio-debug.apk`. O arquivo `.sha256` ao lado contém o checksum para conferência de integridade.

## Observações de segurança

O arquivo `local.properties` é específico do computador e não deve ser versionado. A sessão administrativa deve ser encerrada pelo botão de sair quando o dispositivo for compartilhado. Nunca inclua credenciais reais, tokens ou arquivos de serviço no repositório.
