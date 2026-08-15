# Referências oficiais para a correção do player

Consultas realizadas em 15 de agosto de 2026.

## Documentação Android Media3

- HLS: https://developer.android.com/media/media3/exoplayer/hls
- Customização do ExoPlayer: https://developer.android.com/media/media3/exoplayer/customization
- Referência `DefaultHttpDataSource.Factory`: https://developer.android.com/reference/androidx/media3/datasource/DefaultHttpDataSource.Factory
- Guia inicial: https://developer.android.com/media/media3/exoplayer/hello-world
- Eventos do player: https://developer.android.com/media/media3/exoplayer/listening-to-player-events
- Solução de problemas: https://developer.android.com/media/media3/exoplayer/troubleshooting

## Pontos relevantes

A documentação oficial indica que o ExoPlayer pode reproduzir HLS por meio do módulo `media3-exoplayer-hls` e que `PlayerView` fornece a superfície de vídeo e controles de player.

Para fontes HTTP, a fábrica de `DataSource` permite configurar propriedades padrão de requisição. Um `HttpDataSource` pode enviar headers fixos em cada requisição HTTP à fonte, o que é o mecanismo apropriado para um CDN que exige headers como `Origin`, `Referer` ou `User-Agent`.

A implementação deve registrar `Player.Listener.onPlayerError` e capturar a causa/erro HTTP para evitar encerramento silencioso da Activity e mostrar uma mensagem de falha ao usuário.

## Trechos confirmados na documentação

A página oficial de HLS confirma que o módulo `androidx.media3:media3-exoplayer-hls` é necessário, que um `MediaItem` pode apontar para uma playlist HLS e que `MimeTypes.APPLICATION_M3U8` deve ser informado quando a URI não termina em `.m3u8`. Também documenta a criação de `HlsMediaSource.Factory` com uma `DataSource.Factory`.

A página oficial de customização mostra que um `DataSource.Factory` pode criar um `HttpDataSource` e definir headers via `setRequestProperty`, ou usar `ResolvingDataSource.Factory` para inserir headers por requisição com `dataSpec.withRequestHeaders(...)`. Essa é a base oficial para aplicar `Origin`, `Referer` e `User-Agent` às requisições da playlist e dos segmentos.

## Referências públicas e validação do CDN

A discussão oficial [androidx/media#2104](https://github.com/androidx/media/issues/2104) mostra a configuração de `DefaultHttpDataSource.Factory` com `setDefaultRequestProperties(...)` e `setUserAgent(...)` para servidores que exigem Referer e User-Agent. A discussão [androidx/media#1172](https://github.com/androidx/media/issues/1172) recomenda criar uma `HlsMediaSource.Factory` com a `DataSource.Factory` customizada quando os headers precisam acompanhar a fonte HLS. O plano público do [ZoneMinder Mobile Android](https://github.com/SteveGilvarry/zoneminder-mobile-android/blob/main/PLAN.md) também adota Media3 HLS e `DefaultHttpDataSource.Factory`/`ResolvingDataSource` para autenticação em playlists e segmentos.

Na validação local, os links atuais do carrossel e de `ultimos-filmes` retornaram HTTP 200 com `#EXTM3U`, enquanto uma rota histórica do catálogo retornou erro de origem 404. O manifesto atual de `drama/13792-hit-para-dois` foi obtido com sucesso, mas o primeiro segmento retornou HTTP 401 com `x-country: BD` e `x-reason: not-allowed-geo`. Assim, o sandbox está bloqueado por geolocalização do CDN; isso é diferente de ausência de headers e impede um teste end-to-end aqui. O aplicativo, entretanto, foi ajustado para enviar Origin, Referer, User-Agent e headers de navegação na mesma factory usada pelo HLS para manifesto e segmentos.
