# Diagnóstico do crash ao tocar em Assistir

Data da investigação: 15 de agosto de 2026.

## Evidências externas

Os endpoints usados pela Home são definidos em `app/src/main/assets/tedflix/js/api.js`:

- `GET https://ted.cryptitys.site/api/home/carousel`
- `GET https://ted.cryptitys.site/api/ultimos-filmes`
- A fileira "Lançamentos" usa `GET https://ted.cryptitys.site/api/filmes/stream` e seleciona os primeiros itens.
- O player monta `GET https://ted.cryptitys.site/api/filme-player/{categoria}/{slug}`.

Em 15 de agosto de 2026, `home/carousel` respondeu HTTP 200 com 2968 bytes e `ultimos-filmes` respondeu HTTP 200 com 4713 bytes, usando Origin `https://novelasflix.video`, Referer `https://novelasflix.video/` e User-Agent Chrome 150. Foram testados 16 candidatos do carrossel e de últimos filmes; todos responderam HTTP 200 e iniciaram com `#EXTM3U`, Content-Type `application/vnd.apple.mpegurl; charset=utf-8`.

O primeiro segmento do manifesto de `drama/13792-hit-para-dois` foi:

`https://v-br-cinquantadue.printhouse.casa/a1/out/6/13792/13792_hit-para-dois-2026-web-dl-1080p-x264-dual-5.10.ts?md5=09r1nCQhUP599FXNJzaQhw&expires=1786838559&ref=https://novelasflix.video/drama/13792-hit-para-dois/`

Com os mesmos headers, o segmento respondeu HTTP 401 com `x-reason: not-allowed-geo` e `x-country: US`. Isso confirma que, neste ambiente, o CDN bloqueia a etapa de segmento por geolocalização, embora a API entregue uma playlist HLS válida. A validação no APK deve exibir esse 401 na tela, não encerrar a Activity.

## Hipóteses de crash auditadas

`PlayerActivity` está declarada como `.PlayerActivity`, `exported=false`, `screenOrientation=landscape`, com `Theme.Tedflix.Player`; `MainActivity` está exportada como launcher. A permissão INTERNET existe no Manifesto. O bridge chamava uma Activity explícita e passava `categoria`, `slug` e `titulo`, mas os parâmetros Kotlin originalmente eram `String` não anuláveis. JavaScript pode fornecer `undefined`/`null` em rotas malformadas, e a ponte poderia lançar uma exceção antes do corpo de `openPlayer`. A instrumentação tornou esses parâmetros `String?` e validau-os antes de construir o Intent.

`detalhes.js` cria links de filme como `#/assistir/${categoria}/${slug}` e de episódio como `#/assistir/${cat}/${sl}`. `assistir.js` envia esses valores diretamente ao bridge. O novo código também valida a URL, o status HTTP, o Content-Type e o prefixo `#EXTM3U` antes de criar o MediaItem/HlsMediaSource.

## Instrumentação adicionada

O PlayerActivity registra as etapas `PlayerActivity criada`, `Intent recebido`, `streamUrl montada`, `validando manifesto HTTP`, `URL validada`, `criando MediaItem`, `criando HlsMediaSource`, `criando ExoPlayer`, `preparando ExoPlayer`, `reprodução solicitada`, `STATE_READY` e erros de playback. Falhas apresentam uma tela técnica com etapa, Activity, tipo, mensagem, causa, URL, status/Content-Type, prévia de resposta e stack trace resumido, com botões de tentar novamente e voltar.

## Confirmação da documentação oficial

A documentação atual do Android Media3 confirma que `media3-exoplayer-hls` é o módulo necessário para HLS, que `MimeTypes.APPLICATION_M3U8` é apropriado quando a URL não termina em `.m3u8`, e que a forma oficial de usar uma fonte HLS customizada é `HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)` seguida de `player.setMediaSource(...)` e `prepare()`.

A documentação de customização confirma que `DefaultHttpDataSource.Factory.setDefaultRequestProperties(...)` insere headers em cada `HttpDataSource` criado pela factory, e também apresenta `ResolvingDataSource` para headers just-in-time. A documentação de eventos confirma que `Player.Listener.onPlayerError(PlaybackException)` é o callback correto para erros de playback e que a causa pode ser inspecionada como `HttpDataSourceException`/`InvalidResponseCodeException`.

Referências: https://developer.android.com/media/media3/exoplayer/hls ; https://developer.android.com/media/media3/exoplayer/customization ; https://developer.android.com/reference/androidx/media3/datasource/DefaultHttpDataSource.Factory ; https://developer.android.com/media/media3/exoplayer/listening-to-player-events.

## Auditoria estrutural

O Manifesto declara `android.permission.INTERNET` e `android.permission.ACCESS_NETWORK_STATE`. `MainActivity` é a Activity launcher exportada; `PlayerActivity` está declarada como `.PlayerActivity`, `exported="false"`, `screenOrientation="landscape"`, com `configChanges` para a rotação e tema próprio. A abertura usa `Intent(activity, PlayerActivity::class.java)`, portanto é explícita e não depende de resolução implícita.

Os extras são `EXTRA_CATEGORIA`, `EXTRA_SLUG` e `EXTRA_TITULO`; a ponte agora aceita `String?`, normaliza com `orEmpty().trim()` e impede o Intent quando categoria ou slug estão vazios. `PlayerActivity` repete essa validação e mostra diagnóstico caso os extras não cheguem.

O projeto usa `androidx.media3:media3-exoplayer`, `media3-exoplayer-hls` e `media3-ui`, todos na versão 1.6.1; `minSdk=24`, `targetSdk=35`, `compileSdk=35`. O build release está com `isMinifyEnabled=false`, então R8/ProGuard não está removendo classes do player. `lintDebug`, `assembleDebug` e `assembleRelease` concluíram com sucesso; apenas avisos de APIs Android depreciadas foram emitidos.

A captura global `TedflixApplication` agora persiste qualquer exceção não tratada antes de o processo morrer. No próximo lançamento, `MainActivity` mostra a thread, tipo, mensagem e stack trace, com botão para continuar para a WebView. Isso cobre o caso em que o crash acontece fora do `try/catch` da PlayerActivity.

## Inspeção do APK instrumentado

A inspeção do `AndroidManifest.xml` compilado em `tedflix-diagnostic-debug.apk` confirmou `minSdkVersion=24`, `targetSdkVersion=35`, `android.permission.INTERNET`, `android.permission.ACCESS_NETWORK_STATE`, `com.tedflix.app.TedflixApplication`, `com.tedflix.app.PlayerActivity` com `exported=false`, `screenOrientation=0` (landscape) e `configChanges` de orientação/tamanho. `MainActivity` permanece `exported=true` com os filtros MAIN/LAUNCHER. Não há indício de `ActivityNotFoundException` ou de Activity ausente no APK.

Hashes dos APKs instrumentados:

- `tedflix-diagnostic-debug.apk`: `ccac825ff406d3130a9b466470d759d5c196891ba9b1813b036de2b11519a4d6`
- `tedflix-diagnostic-release-unsigned.apk`: `b1aa66cc16c87feb12082d9f52d3a670b844cbddf6ec6d853eff556abe97d3d4`

## Correção da primeira abertura

O stack trace fornecido pelo aparelho apontou para `PlayerActivity.showStartupError(PlayerActivity.kt:549)`, chamado por `onCreate(PlayerActivity.kt:102)`. Essa linha não era a causa original: `showStartupError()` fabricava uma nova `IllegalStateException("A interface do player não pôde ser criada")` e descartava a exceção que havia acontecido dentro de `buildUi()`. Por isso o diagnóstico aparecia como `Activity: StringBuilder` e não mostrava a instrução que falhou.

A correção agora passa o `Throwable` original para `showDiagnosticScreen`, registra subetapas de `buildUi()` no Logcat com a tag `TedflixPlayer` e mostra a última subetapa real na tela. Também removi a atribuição repetida de `requestedOrientation` do `onCreate`: a orientação landscape já está declarada no Manifesto, e repetir a troca durante a criação da UI podia provocar uma recriação/estado intermediário na primeira abertura. O player HLS Media3, `HlsMediaSource`, headers e preflight não foram substituídos.

Novo build:

- `tedflix-first-open-debug.apk`: `38c57854808624b824ca4b125900940f6ef266115835024f22c318f913eb22d2`
- `tedflix-first-open-release-unsigned.apk`: `4bdbc49b5284294695d7e90a1ad86e5778603ef6ca3eaca6019466a1b192e8d3`

O build `assembleDebug`, `assembleRelease` e `lintDebug` foi concluído com sucesso. O ambiente não possui aparelho Android conectado; portanto, a validação final da primeira abertura deve ser feita instalando este APK no mesmo celular. Se ainda falhar, a tela agora exibirá a exceção real e a última subetapa específica, em vez do wrapper genérico.
