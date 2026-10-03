# Tedflix

Aplicativo Android e versão web do Tedflix.

## APIs atuais

A versão atual utiliza **dois servidores**:

| Serviço | URL base | Uso |
|---|---|---|
| User/Admin/Auth | `https://servidores-ted-auth.onrender.com` | Cadastro, login, sessão, perfis, seleção de perfil, autorização de acesso, conta, favoritos, histórico e progresso |
| Catálogo/Player | `https://servidores-ted-auth-1.onrender.com/api` | Filmes, séries, gêneros, busca, detalhes, temporadas, episódios e dados necessários para reprodução |

### Regras de integração

- Chamadas de autenticação e dados do usuário usam `servidores-ted-auth.onrender.com`.
- Chamadas de catálogo e reprodução usam `servidores-ted-auth-1.onrender.com/api`.
- O cliente não usa mais o servidor antigo de canais ao vivo nem rotas sem suporte nos contratos atuais.
- O fluxo de entrada é: **login/cadastro → perfis → seleção do perfil → validação de acesso → catálogo**.

## Estrutura

- `app/`: projeto Android.
- `tedflix-web/`: versão web com a mesma interface visual, telas e player.
- `artifacts/`: artefatos locais de build; não fazem parte do código-fonte versionado.

## Validação

Para verificar a versão web localmente:

```bash
cd tedflix-web
python3 -m http.server 4173
```

Depois abra `http://localhost:4173` no navegador. O site não é publicado por este repositório automaticamente.
