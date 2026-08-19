# Diagnóstico dos erros 500 do Tedflix

## Resultado

Sim, ficou muito mais fácil de corrigir. Os arquivos enviados confirmam a causa exata dos dois erros.

## 1. Favoritos

O log informa:

```text
Error: 9 FAILED_PRECONDITION: The query requires an index.
server.js:2171:8
```

A linha corresponde a esta consulta:

```js
const favoritesQuery = await db.collection(COLLECTIONS.FAVORITOS)
  .where('userId', '==', userId)
  .orderBy('adicionadoEm', 'desc')
  .get();
```

O Firestore exige um índice composto para `userId + adicionadoEm`. Por isso `POST /api/favorites/toggle` funciona, mas `GET /api/favorites/list` retorna 500: gravar e remover não usam a consulta composta; listar usa.

### Correção recomendada

A solução principal é abrir o link de criação de índice que aparece no log e criar o índice composto na coleção `favoritos`, com:

```text
userId       Ascending
adicionadoEm Descending
```

Como correção complementar, mantenha o handler com retorno vazio normalizado e tratamento de erro:

```js
app.get('/api/favorites/list', async (req, res) => {
  try {
    const userId = req.user.userId;

    const snapshot = await db.collection('favoritos')
      .where('userId', '==', userId)
      .orderBy('adicionadoEm', 'desc')
      .limit(100)
      .get();

    const favoritos = snapshot.docs.map((doc) => {
      const data = doc.data();
      return {
        filmeId: data.filmeId || '',
        titulo: data.titulo || 'Sem título',
        thumb: data.thumb || '',
        adicionadoEm: data.adicionadoEm || null
      };
    });

    return res.json({ success: true, favoritos });
  } catch (error) {
    console.error('Erro em /api/favorites/list:', error);
    return res.status(500).json({
      success: false,
      message: 'Erro interno do servidor'
    });
  }
});
```

O índice é a correção necessária; alterar o Android não resolverá esse erro.

## 2. Notificações

O handler enviado usa:

```js
const notificationsSnapshot = await db.collection('notifications')
  .where('userId', '==', userId)
  .orderBy('sentAt', 'desc')
  .limit(50)
  .get();
```

Essa consulta tem o mesmo padrão: filtro por `userId` combinado com ordenação por `sentAt`. Portanto, a rota `GET /users/me/notifications` provavelmente também precisa de um índice composto na coleção `notifications`:

```text
userId  Ascending
sentAt  Descending
```

O código de envio confirma que `sentAt` é gravado e que a coleção correta é `notifications`:

```js
const notification = {
  userId,
  type: 'manual',
  title,
  body,
  action: action || null,
  read: false,
  sentAt: new Date().toISOString(),
  sentVia: ['email']
};
```

Assim, a correção mais provável é criar esse segundo índice composto no Firestore do projeto `postagem-e3677`. O handler do servidor de autenticação está apontando para esse projeto, enquanto favoritos usam o projeto `tedflix-54f17`.

## Índices necessários

| Projeto Firestore | Coleção | Campo 1 | Campo 2 |
|---|---|---|---|
| `tedflix-54f17` | `favoritos` | `userId` ascendente | `adicionadoEm` descendente |
| `postagem-e3677` | `notifications` | `userId` ascendente | `sentAt` descendente |

Depois de criar os índices, aguarde o Firestore concluir o status `Building` antes de testar novamente. O aplicativo já está chamando as rotas corretas.

## 3. Atenção de segurança urgente

Os arquivos enviados contêm chaves privadas completas de contas de serviço Firebase de dois projetos, além de um valor padrão de `JWT_SECRET`. Essas chaves devem ser consideradas expostas. É necessário revogar e gerar novas chaves no Firebase/Google Cloud, atualizar as variáveis de ambiente do Render e remover as chaves embutidas no código.

Também altere a senha da conta de teste após a validação, pois ela foi compartilhada na conversa.

A configuração segura deve usar somente variáveis de ambiente:

```js
const serviceAccount = {
  project_id: process.env.FIREBASE_PROJECT_ID,
  private_key_id: process.env.FIREBASE_PRIVATE_KEY_ID,
  private_key: process.env.FIREBASE_PRIVATE_KEY.replace(/\\n/g, '\n'),
  client_email: process.env.FIREBASE_CLIENT_EMAIL
};
```

Não mantenha uma chave privada de fallback no arquivo-fonte nem use um JWT secreto previsível como valor padrão de produção.

## Conclusão

A causa dos favoritos está comprovada pelo log: índice composto ausente em `favoritos`. A causa das notificações é o mesmo padrão de consulta e deve ser corrigida criando o índice composto `userId + sentAt` no projeto de autenticação. O app Android não precisa ser alterado para corrigir esses dois 500.
