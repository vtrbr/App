# Auditoria inicial da autenticação Tedflix

## Origem

As informações abaixo foram extraídas do arquivo enviado pelo usuário `prompt.txt` e devem ser confirmadas contra o servidor real antes da implementação.

## Autenticação

Servidor informado: `https://authted.onrender.com/`.

Login: `POST /auth/login`, corpo JSON com `code`, `email` e `password`. A resposta de sucesso esperada contém `success`, `token`, `user` e `firstLogin`. O token deve ser usado como `Authorization: Bearer {token}`.

Verificação: `GET /auth/verify` com bearer. Perfil: `GET /users/me`. Status da assinatura: `GET /users/me/status`. Logout: `POST /auth/logout`. Alteração de senha: `PATCH /auth/password` com `currentPassword` e `newPassword`. Atualização de nome: `PATCH /users/me` com `username`. Notificações: `GET /users/me/notifications`; marcar como lida: `PATCH /users/me/notifications/:id/read`.

Erros esperados: `400` validação, `401` credenciais/token inválidos, `403` conta bloqueada ou expirada, `404` usuário/recurso inexistente, `429` excesso de tentativas e `500` erro interno. Ao receber token inválido ou expirado, o app deve limpar a sessão e voltar à tela de login.

## Servidor de filmes

O prompt informa que todas as chamadas devem incluir o mesmo header bearer. Foram documentadas rotas de catálogo, detalhes, busca, temporadas, episódios, player HLS, histórico, continuar assistindo e favoritos. A rota de player informada é `GET /api/filme-player/{categoria}/{slug}` e retorna uma playlist HLS/M3U8.

## Fluxo de tela

A tela usa código de acesso, e-mail e senha; não deve exibir uma opção separada de primeiro acesso porque o servidor decide esse fluxo. O HTML enviado usa fundo com banner, logo Tedflix, cartão escuro, campos com borda vermelha, botão vermelho `ENTRAR` e links auxiliares.

## Pontos a confirmar

É necessário confirmar por requisição passiva o caminho real da API, o comportamento de CORS/OPTIONS, a resposta de login sem credenciais reais, se o endpoint de filmes é o mesmo domínio atual e se a playlist HLS exige o bearer em cada segmento ou somente na obtenção da playlist. O app não deve registrar token, senha ou cookies em logs e deve armazenar o token em armazenamento protegido do Android, não no cache comum do WebView.

## Probe passivo do servidor em 18/08/2026

`GET https://authted.onrender.com/` respondeu `200` com `Tedflix Auth Server`, versão `1.0.0`, estado `online` e grupos de endpoints `/auth/*`, `/users/*` e `/admin/*`.

`POST /auth/login` com corpo vazio respondeu `400` e `{"error":"Código, e-mail e senha são obrigatórios"}`. `GET /auth/verify` sem Authorization respondeu `401` e `{"error":"Token não fornecido"}`. `POST /auth/logout` sem Authorization respondeu `401` e a mesma mensagem. A resposta incluiu `Access-Control-Allow-Origin: *`, HSTS e cabeçalhos de segurança; nenhum token real foi enviado ou obtido durante o teste.

Arquivo bruto do probe: `audit/auth-endpoint-probe.txt`.

## Implementação no Tedflix

A tela nativa `AuthActivity` usa o banner e o logo enviados, solicita código de acesso, e-mail e senha e não exibe uma opção separada de primeiro acesso. O login chama `POST /auth/login` em segundo plano, salva apenas o bearer cifrado com AES-GCM usando uma chave do Android Keystore e abre a `MainActivity` somente depois de uma resposta de sucesso.

A `MainActivity` exige sessão antes de carregar a WebView. Chamadas GET da WebView para `https://ted.cryptitys.site/api/...` são encaminhadas pelo bridge nativo com `Authorization: Bearer ...`, sem colocar o token no JavaScript, em URLs, localStorage ou logs. Respostas autenticadas usam `Cache-Control: no-store` e `Pragma: no-cache`. Respostas 401/403 limpam a sessão e retornam à tela de login.

A `PlayerActivity` mantém Media3 ExoPlayer/HlsMediaSource, preflight e controles existentes. O bearer é aplicado na validação inicial da playlist e no `DefaultHttpDataSource.Factory`, portanto também acompanha as requisições dos segmentos HLS. A tela de conta usa os endpoints de perfil, status, notificações, atualização de nome, troca de senha e logout documentados no prompt.

A validação automatizada sem credenciais reais confirmou `assembleDebug`, `lintDebug`, manifesto, assets e ausência da opção “Primeiro acesso”. O login real e o playback autenticado ainda precisam ser exercitados no aparelho com uma conta/token válidos do usuário.

## Troca do servidor de filmes

Por solicitação do usuário, o servidor de filmes foi alterado de `https://ted.cryptitys.site` para `https://tedtv.onrender.com`. O servidor de autenticação continua em `https://authted.onrender.com`.

As referências ativas atualizadas foram: `config.js` da WebView, `AuthSession.MOVIE_API_BASE`, `AuthSession.MOVIE_API_HOST`, `PlayerActivity.API_BASE`, `SourceRegistry` e o `preconnect` do HTML. O probe sem credenciais confirmou que `https://tedtv.onrender.com/api/` e `/api/home` estão online e respondem HTTP 401 com `Token não fornecido`, indicando que o novo servidor está protegido pelo bearer conforme esperado. O root `/` retorna 404 por não possuir rota pública, o que não impede o uso das rotas `/api/...`.
