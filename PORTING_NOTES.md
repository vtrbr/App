# Porting notes — Tedflix Android

## Contrato do site fornecido

- API base declarada em `js/config.js`: `https://ted.cryptitys.site/api`.
- O catálogo usa `/home/carousel`, `/ultimos-filmes`, `/filmes/stream`, `/series/stream`, `/animacoes/stream`, `/genero/{slug}/stream`, `/buscar/{q}?pagina={n}`.
- Detalhes usam `/filme/{categoria}/{slug}`.
- Séries usam `/serie/{categoria}/{slug}/temporadas` e `/serie/{categoria}/{slug}/temporada/{numero}/episodios`.
- Reprodução usa `GET /filme-player/{categoria}/{slug}` e espera texto bruto contendo `#EXTM3U`.
- O servidor do site retorna o manifesto com `Content-Type: application/vnd.apple.mpegurl` e os segmentos são referenciados pela playlist.

## Porting decisions

- O frontend do site foi empacotado em `app/src/main/assets/tedflix`, preservando a interface e os dados visuais.
- `assistir.js` delega a rota de reprodução ao bridge `AndroidPlayer.openPlayer(categoria, slug, titulo)` quando executado no Android.
- A reprodução Android usa `androidx.media3:media3-exoplayer-hls` com `ExoPlayer`, sem Video.js, Vidstack ou WebView para vídeo.
- `PlayerActivity` força orientação horizontal, modo imersivo e expõe controles de voltar, play/pause, +/-10 s, progresso, áudio, legendas, qualidade, velocidade e recarregar.
- Configurações web foram reduzidas a medição de conexão e buffer; o buffer selecionado é persistido e lido pelo player nativo.

## Fontes

- Site fornecido pelo usuário: `/home/ubuntu/tedtv-main/tedtv-main/tedflix-final/tedflix`.
- Backend fornecido pelo usuário: `/home/ubuntu/tedtv-main/tedtv-main/server/server.js`, rotas de player nas linhas 1812–1889.
