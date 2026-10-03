# Tedflix Web

Versão web do Tedflix, criada dentro do repositório a partir do frontend visual do aplicativo Android.

## O que foi preservado

- Mesmas cores, tipografia, logo, cards, fileiras, navegação inferior, telas de catálogo, detalhes, configurações e player.
- Mesmo motor visual do player (`js/components/player.js`), executado diretamente no navegador com Video.js/HLS.
- Mesmas APIs de autenticação/perfis e catálogo.

## Adaptado para navegador

- Tela de login/cadastro e tela de seleção de perfil antes da Home.
- Sessão, perfil ativo, favoritos e histórico persistidos em `localStorage`.
- Favoritos e histórico sincronizados com a API User/Admin.
- Página web de favoritos em `#/favoritos`.
- Remoção da dependência obrigatória da ponte Android.
- `manus-routes.json` declara as rotas do site.

## Executar localmente

A pasta é estática e não foi publicada. Use qualquer servidor HTTP local, por exemplo:

```bash
cd tedflix-web
python3 -m http.server 4173
```

Depois abra `http://localhost:4173` no navegador.

Abrir o `index.html` diretamente via `file://` não é recomendado, porque módulos ES e chamadas HTTP da API precisam de uma origem HTTP.
