# Testes do Tedflix Android

Data do teste: 2026-08-15.

## API pública

A API respondeu com HTTP 200 para:

- Catálogo: `https://ted.cryptitys.site/api/home/carousel`
- Manifesto: `https://ted.cryptitys.site/api/filme-player/drama/6068-a-casa-do-dragao-noads`

O manifesto retornado contém `#EXTM3U`, `#EXT-X-VERSION:3`, `#EXT-X-TARGETDURATION`, `#EXTINF` e URLs HTTPS para segmentos `.ts` com parâmetros `md5`, `expires` e `ref`.

## Resultado do teste do segmento

Um GET com `Range: bytes=0-1023` para o primeiro segmento retornou HTTP 401, tanto sem quanto com o header `Referer: https://novelasflix.video/drama/6068-a-casa-do-dragao-noads/`. O teste utilizou a URL assinada retornada pelo manifesto no momento da execução.

Isso indica que o player Android precisa ser testado com a autorização exigida pelo CDN, que pode depender da validade/renovação da assinatura, de headers adicionais, de cookies ou de uma regra específica do provedor. O app não deve considerar a reprodução validada enquanto um segmento real não puder ser lido pelo cliente Android.

## Verificações que passaram

- Sintaxe de todos os arquivos JavaScript empacotados: passou.
- `lintDebug`: passou após correções.
- `assembleDebug`: passou.
- `assembleRelease`: passou e gerou APK unsigned.
- Integridade do APK debug via `unzip -t`: passou.
- `assembleRelease`: passou e gerou `app-release-unsigned.apk`.
- Suíte Gradle: passou; não há testes unitários definidos (`NO-SOURCE`).
- Manifesto da aplicação: `com.tedflix.app`, `MainActivity` como launcher, permissões de internet e estado de rede.

## Diagnóstico do CDN

O teste de segmento continua retornando HTTP 401 com `x-reason: not-allowed-geo` e `x-country: UZ`. Isso é uma restrição geográfica do CDN no ambiente de build, não um erro de parsing HLS ou CORS do app. A reprodução deve ser validada em um dispositivo/rede autorizada pelo CDN.
