# Auditoria do lietrepoqg

## Escopo

O ZIP enviado contém um repositório de plugins para o ecossistema Cloudstream, não um backend Android pronto para ser importado diretamente no Tedflix. O README local informa que o projeto foi criado pelo usuário e autoriza o uso; não há outro arquivo de licença no ZIP. O conteúdo foi apenas lido e extraído para auditoria, sem executar JARs, Gradle ou artefatos compilados.

## Fontes encontradas

| Provider | Tipos declarados | Padrão técnico | Prioridade para Tedflix |
|---|---|---|---|
| StreamFlix | Filmes, séries | API JSON própria, catálogo completo, detalhes e `get_stream_url` direto | Alta |
| CineAgora | Filmes, séries, animes | HTML/POST, TMDB opcional, páginas e iframes | Média |
| MendigoFlix | Filmes, séries, animes | Múltiplos hosts, cookies e resposta criptografada | Baixa na primeira entrega |
| PobreFlix | Filmes, séries, animes, doramas | CSRF, sessões, endpoints de opções/source e HLS | Média, após adaptador dedicado |
| AnimeFire | Animes | Provider específico de anime | Opcional, conforme categorias do Tedflix |
| AniTube | Animes | Provider específico de anime | Opcional |
| Goyabu | Animes | Provider específico de anime | Opcional |
| DattebayoBR | Anime/dorama | Provider específico | Opcional |
| Doramogo | Doramas | Provider específico | Opcional |
| EmbedTV | TV ao vivo | Catálogo live e embeds | Fora do primeiro escopo de filmes/séries |
| ReiDosEmbeds | TV ao vivo | API de canais e iframe de player | Fora do primeiro escopo de filmes/séries |

A lista e os metadados foram conferidos em `builds/plugins.json`.

## Contrato relevante

Os providers usam os contratos `search`, `load` e `loadLinks` do Cloudstream. Para a integração nativa, o Tedflix precisa normalizar cada resposta para um modelo próprio contendo `sourceId`, título, tipo, poster, backdrop, ano, slug/ID da fonte, temporadas/episódios e uma lista de links de vídeo com URL, referer, origin, user-agent e MIME/qualidade.

## Provider direto: StreamFlix

`StreamFlix.kt` usa `https://streamflix.live/api_proxy.php` e tem um fluxo relativamente simples. O catálogo de filmes vem de `action=get_vod_streams`, o de séries de `action=get_series`, os detalhes usam `get_vod_info` e `get_series_info`, e a reprodução usa `get_stream_url&type=movie&id=...` ou `get_stream_url&type=series&id=...`. O provider trata o retorno como uma URL direta de vídeo e o `loadLinks` emite um único link com `referer = mainUrl`. Esse é o melhor candidato para a primeira implementação real de fonte alternativa.

## Provider com resolução HTML: CineAgora

`CineAgora.kt` faz busca por POST ou GET, lê listas HTML e pode descobrir iframes ou links `.m3u8`/`.mp4`. Para séries há uma rota auxiliar de dados de episódios. A integração exige parser HTML e resolução por página; não deve ser tratada como uma API JSON genérica.

## Providers com cadeia de extractor

`PobreFlixExtractor.kt` usa cookies de sessão, tokens CSRF e chamadas sequenciais a endpoints de opções/source, seguindo redirecionamentos até encontrar HLS. `MendigoFlixExtractor.kt` usa vários hosts, cookies fixos e decriptação de resposta AES-GCM antes de criar links HLS. Esses providers precisam de adaptadores isolados, telemetria de falhas e validação de segurança; não devem ser incluídos no modo agregado apenas por aparecerem no manifesto.

## Arquitetura recomendada

Manter a WebView atual para a fonte principal do Tedflix e criar uma camada nativa `ContentSource` apenas para fontes alternativas. A configuração deve persistir uma seleção em `SharedPreferences` ou DataStore com os modos `servidor_principal`, `fonte_individual` e `todas_as_fontes`. O modo agregado deve consultar as fontes em paralelo com limites de tempo, remover duplicatas por título/ano/tipo e preservar o `sourceId` no item para que detalhes e reprodução voltem ao provider correto.

A primeira entrega deve implementar de ponta a ponta o servidor principal e StreamFlix, exibir as demais fontes do manifesto com seu tipo e estado, e deixar providers de extractor complexo atrás de adaptadores próprios. O player HLS existente deve receber apenas um link normalizado; seus headers e a lógica de orientação horizontal não devem ser alterados.

## Limites de teste

É necessário validar cada endpoint no momento da compilação/teste, pois os sites e APIs podem mudar. Uma fonte pode aparecer como configurada e ainda assim retornar 404, captcha, bloqueio geográfico, HTML inesperado ou URL não-HLS. O app deve informar a fonte que falhou e continuar com as outras, em vez de derrubar a Activity.


## Probe de disponibilidade em 17/08/2026

O endpoint `https://ted.cryptitys.site/api/home/carousel` respondeu HTTP 200 com JSON válido. Já os endpoints testados de StreamFlix, CineAgora e PobreFlix não entregaram o contrato esperado neste ambiente: StreamFlix devolveu uma página HTML que redireciona para `/lander`, CineAgora falhou na conexão TLS durante o probe e PobreFlix redirecionou para uma página de domínio estacionado. O seguimento passivo do redirecionamento encontrou respostas 403 de uma página de venda de domínio. Portanto, a integração precisa tratar fonte indisponível como estado normal e não pode assumir que o manifesto do ZIP representa disponibilidade atual.
