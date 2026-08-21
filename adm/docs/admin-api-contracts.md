# Contratos administrativos confirmados

Fonte local analisada: `/home/ubuntu/upload/server(1).js`.

## Auth Server

Base URL usada pelo Studio: `https://authted.onrender.com`.

### Login administrativo

`POST /admin/login`

Body:

```json
{"email":"admin@dominio.com","password":"senha"}
```

Resposta de sucesso: `{ success, token, admin: { id, email, name } }`.

### Usuários

`GET /admin/users` retorna `{ users: [...] }`, com `id`, `code`, `email`, `username`, `accountStatus`, `accountExpiresAt`, `createdAt`, `lastUsedAt` e `daysRemaining`.

`GET /admin/users/:id` retorna o documento completo e `daysRemaining`.

`POST /admin/users` aceita `email`, `password` opcional, `days`, `expiresAt` e `neverExpires`.

`PATCH /admin/users/:id` aceita `email`, `username` e `password`.

`PATCH /admin/users/:id/block` aceita `{ "block": true|false }`.

`PATCH /admin/users/:id/expiration` aceita `days`, `expiresAt` ou `neverExpires`.

`POST /admin/users/:id/token` renova o token do usuário.

`DELETE /admin/users/:id` remove o usuário.

### Notificações

`POST /admin/notifications/send`

`POST /admin/notifications/broadcast`

`POST /admin/notifications/schedule`

`GET /admin/notifications/scheduled`

`DELETE /admin/notifications/scheduled/:id`

`GET /admin/notifications/history`

Todas as rotas administrativas, exceto `/admin/login` e `/admin/setup`, exigem Bearer de admin e `adminMiddleware`.
