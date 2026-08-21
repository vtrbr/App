(() => {
  'use strict';

  const bridge = window.AndroidStudio;
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));
  const app = {
    page: 'dashboard',
    users: [],
    admin: null,
    busy: false
  };

  function parse(value, fallback = {}) {
    try { return JSON.parse(value); } catch (_) { return fallback; }
  }

  function native(name, ...args) {
    if (!bridge || typeof bridge[name] !== 'function') {
      return { ok: false, status: 0, body: { error: 'Ponte nativa indisponível' } };
    }
    try {
      const value = bridge[name](...args);
      return typeof value === 'string' ? parse(value, { ok: false, status: 0, body: { error: 'Resposta inválida' } }) : value;
    } catch (error) {
      return { ok: false, status: 0, body: { error: error.message || 'Falha na ponte nativa' } };
    }
  }

  function request(method, path, body) {
    const raw = body === undefined || body === null ? '' : JSON.stringify(body);
    return native('apiRequest', method, path, raw);
  }

  function bodyOf(response) { return response && response.body ? response.body : {}; }
  function errorOf(response) {
    const body = bodyOf(response);
    return body.error || body.message || body.raw || `Falha HTTP ${response && response.status ? response.status : ''}`.trim();
  }

  function escapeHtml(value) {
    return String(value === null || value === undefined ? '' : value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#039;');
  }

  function initials(name, email = '') {
    const source = String(name || email || 'A').trim().split(/\s+/).filter(Boolean);
    return (source.length > 1 ? source[0][0] + source[1][0] : source[0][0]).toUpperCase();
  }

  function date(value, withTime = false) {
    if (!value) return '—';
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) return String(value);
    return new Intl.DateTimeFormat('pt-BR', withTime ? { dateStyle: 'short', timeStyle: 'short' } : { dateStyle: 'short' }).format(parsed);
  }

  function statusLabel(value) {
    const map = { active: 'Ativo', expired: 'Expirado', blocked: 'Bloqueado', expiring: 'Expirando', pending: 'Pendente', cancelled: 'Cancelado' };
    return map[value] || value || 'Desconhecido';
  }

  function statusClass(value) {
    if (value === 'active') return 'active';
    if (value === 'expiring' || value === 'pending') return 'expiring';
    if (value === 'expired' || value === 'blocked' || value === 'cancelled') return 'expired';
    return 'neutral';
  }

  function showToast(message, kind = 'normal') {
    const toast = $('#toast');
    if (!toast) return;
    toast.textContent = message;
    toast.style.borderLeftColor = kind === 'error' ? 'var(--red)' : kind === 'success' ? 'var(--green)' : 'var(--blue)';
    toast.classList.add('show');
    clearTimeout(showToast.timer);
    showToast.timer = setTimeout(() => toast.classList.remove('show'), 3600);
  }

  function setBusy(button, busy, label = 'Processando...') {
    if (!button) return;
    if (busy) {
      button.dataset.originalText = button.textContent;
      button.disabled = true;
      button.textContent = label;
    } else {
      button.disabled = false;
      button.textContent = button.dataset.originalText || button.textContent;
    }
  }

  function showLogin() {
    $('#login-screen').classList.remove('hidden');
    $('#studio-screen').classList.add('hidden');
    $('#login-password').value = '';
    $('#login-email').focus();
  }

  function showStudio(admin) {
    app.admin = admin || app.admin || {};
    $('#login-screen').classList.add('hidden');
    $('#studio-screen').classList.remove('hidden');
    const name = app.admin.name || 'Administrador';
    const email = app.admin.email || '';
    $('#admin-name').textContent = name;
    $('#admin-email').textContent = email;
    $('#admin-avatar').textContent = initials(name, email);
    $('#top-admin-avatar').textContent = initials(name, email);
    renderPage(app.page);
  }

  function pageHead(title, subtitle, actions = '') {
    return `<div class="page-head"><div><h1>${escapeHtml(title)}</h1><p>${escapeHtml(subtitle || '')}</p></div><div class="page-actions">${actions}</div></div>`;
  }

  function panelLoading() { return '<div class="panel loading">Carregando dados do Studio...</div>'; }
  function panelError(message, retry = '') { return `<div class="panel error-box"><strong>Não foi possível carregar</strong><p>${escapeHtml(message)}</p>${retry ? `<button class="secondary-button" data-retry="${retry}">Tentar novamente</button>` : ''}</div>`; }

  async function loadUsers(force = false) {
    if (app.users.length && !force) return app.users;
    const response = request('GET', '/admin/users');
    if (!response.ok) throw new Error(errorOf(response));
    app.users = Array.isArray(bodyOf(response).users) ? bodyOf(response).users : [];
    return app.users;
  }

  function statCard(label, value, accent, note) {
    return `<div class="stat-card ${accent || ''}"><span class="stat-label">${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong><small>${escapeHtml(note || '')}</small></div>`;
  }

  function userCounts(users) {
    const active = users.filter(u => u.accountStatus === 'active');
    const expiring = users.filter(u => u.accountStatus === 'active' && Number.isFinite(Number(u.daysRemaining)) && Number(u.daysRemaining) >= 0 && Number(u.daysRemaining) <= 3);
    const expired = users.filter(u => u.accountStatus === 'expired');
    const blocked = users.filter(u => u.accountStatus === 'blocked');
    return { total: users.length, active: active.length, expiring: expiring.length, expired: expired.length, blocked: blocked.length };
  }

  async function renderDashboard() {
    const root = $('#page-root');
    root.innerHTML = panelLoading();
    try {
      const users = await loadUsers(true);
      const counts = userCounts(users);
      const recent = users.slice().sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0)).slice(0, 5);
      const bars = Array.from({ length: 10 }, (_, index) => {
        const value = Math.max(16, Math.round((users.length ? (index + 4) / 14 : 1) * 80));
        return `<i class="bar" style="height:${value}%"></i>`;
      }).join('');
      root.innerHTML = `${pageHead('Dashboard', 'Visão geral da plataforma e dos acessos.', '<button class="primary-button" data-page-action="access">+ Criar acesso</button>')}
        <div class="stats-grid">
          ${statCard('Total de usuários', counts.total, '', 'Base cadastrada')}
          ${statCard('Usuários ativos', counts.active, 'green', 'Acesso liberado')}
          ${statCard('Expirando em até 3 dias', counts.expiring, 'orange', 'Acompanhar renovação')}
          ${statCard('Bloqueados', counts.blocked, 'purple', `${counts.expired} expirados`)}
        </div>
        <div class="two-col">
          <section class="panel"><h2>Novos acessos</h2><div class="chart">${bars}</div><p class="muted">Resumo visual da base carregada do servidor.</p></section>
          <section class="panel"><h2>Distribuição de status</h2><div class="quick-list">
            <div class="quick-item"><span class="status active">Ativos</span><b>${counts.active} usuários</b></div>
            <div class="quick-item"><span class="status expiring">Expirando</span><b>${counts.expiring} usuários</b></div>
            <div class="quick-item"><span class="status expired">Expirados</span><b>${counts.expired} usuários</b></div>
            <div class="quick-item"><span class="status blocked">Bloqueados</span><b>${counts.blocked} usuários</b></div>
          </div></section>
        </div>
        <div class="two-col">
          <section class="panel"><h2>Usuários recentes</h2><div class="table-wrap"><table class="data-table"><thead><tr><th>Usuário</th><th>E-mail</th><th>Código</th><th>Criação</th><th>Status</th></tr></thead><tbody>${recent.length ? recent.map(userRow).join('') : '<tr><td colspan="5" class="empty">Nenhum usuário encontrado.</td></tr>'}</tbody></table></div></section>
          <section class="panel"><h2>Ações rápidas</h2><div class="quick-list">
            <button class="quick-item" data-page-action="users"><span>⌕</span><div><b>Buscar usuários</b><span>Consultar acesso, status e validade.</span></div></button>
            <button class="quick-item" data-page-action="access"><span>＋</span><div><b>Criar novo acesso</b><span>Gerar código, senha e validade.</span></div></button>
            <button class="quick-item" data-page-action="notifications"><span>♧</span><div><b>Enviar notificação</b><span>Mensagem individual ou broadcast.</span></div></button>
          </div></section>
        </div>`;
      bindPageActions();
    } catch (error) {
      root.innerHTML = panelError(error.message, 'dashboard');
    }
  }

  function userRow(user) {
    return `<tr data-user-row="${escapeHtml(user.id)}"><td><strong>${escapeHtml(user.username || 'Sem nome')}</strong></td><td>${escapeHtml(user.email)}</td><td>${escapeHtml(user.code || '—')}</td><td>${date(user.createdAt)}</td><td><span class="status ${statusClass(user.accountStatus)}">${escapeHtml(statusLabel(user.accountStatus))}</span></td></tr>`;
  }

  async function renderUsers() {
    const root = $('#page-root');
    root.innerHTML = panelLoading();
    try {
      const users = await loadUsers(true);
      root.innerHTML = `${pageHead('Usuários', 'Consulte e gerencie todos os acessos cadastrados.', '<button class="primary-button" data-page-action="access">+ Criar acesso</button>')}
        <section class="panel"><div class="toolbar"><input id="user-search" placeholder="Buscar por nome, e-mail ou código..."><select id="user-status"><option value="">Todos os status</option><option value="active">Ativos</option><option value="expiring">Expirando</option><option value="expired">Expirados</option><option value="blocked">Bloqueados</option></select><button class="secondary-button" id="user-filter">Filtrar</button></div><div id="users-table"></div></section>`;
      renderUserTable(users);
      $('#user-search').addEventListener('input', filterUsers);
      $('#user-status').addEventListener('change', filterUsers);
      bindPageActions();
    } catch (error) { root.innerHTML = panelError(error.message, 'users'); }
  }

  function filterUsers() {
    const query = ($('#user-search')?.value || '').trim().toLowerCase();
    const status = $('#user-status')?.value || '';
    const filtered = app.users.filter(user => {
      const text = `${user.username || ''} ${user.email || ''} ${user.code || ''}`.toLowerCase();
      return (!query || text.includes(query)) && (!status || user.accountStatus === status || (status === 'expiring' && Number(user.daysRemaining) >= 0 && Number(user.daysRemaining) <= 3));
    });
    renderUserTable(filtered);
  }

  function renderUserTable(users) {
    const target = $('#users-table');
    if (!target) return;
    target.innerHTML = `<div class="table-wrap"><table class="data-table"><thead><tr><th>Usuário</th><th>E-mail</th><th>Código</th><th>Validade</th><th>Status</th><th>Dias</th><th>Ações</th></tr></thead><tbody>${users.length ? users.map(user => `<tr><td><strong>${escapeHtml(user.username || 'Sem nome')}</strong></td><td>${escapeHtml(user.email)}</td><td>${escapeHtml(user.code || '—')}</td><td>${date(user.accountExpiresAt)}</td><td><span class="status ${statusClass(user.accountStatus)}">${escapeHtml(statusLabel(user.accountStatus))}</span></td><td>${escapeHtml(user.daysRemaining === null || user.daysRemaining === undefined ? '∞' : user.daysRemaining)}</td><td><div class="row-actions"><button class="mini-button" data-user-action="view" data-user-id="${escapeHtml(user.id)}">Ver</button><button class="mini-button" data-user-action="edit" data-user-id="${escapeHtml(user.id)}">Editar</button></div></td></tr>`).join('') : '<tr><td colspan="7" class="empty">Nenhum usuário encontrado.</td></tr>'}</tbody></table></div><p class="muted">Exibindo ${users.length} de ${app.users.length} usuários carregados.</p>`;
    $$('[data-user-action]', target).forEach(button => button.addEventListener('click', () => {
      const id = button.dataset.userId;
      if (button.dataset.userAction === 'view') renderUserDetail(id);
      else renderEditUser(id);
    }));
  }

  function renderAccess() {
    $('#page-root').innerHTML = `${pageHead('Criar acesso', 'Gere um novo acesso para um usuário Tedflix.')}
      <div class="two-col"><section class="panel"><h2>Novo acesso</h2><form id="create-user-form"><div class="form-grid"><label>E-mail *<input name="email" type="email" required placeholder="usuario@email.com"></label><label>Senha opcional<input name="password" type="text" placeholder="Gerar automaticamente"></label><label>Dias de validade<input name="days" type="number" min="1" value="30"></label><label>Data específica<input name="expiresAt" type="datetime-local"></label><label class="full"><span><input name="neverExpires" type="checkbox" style="width:auto;margin-right:8px"> Acesso sem expiração</span></label></div><div class="form-actions"><button class="secondary-button" type="reset">Limpar</button><button class="primary-button" type="submit">Criar e gerar acesso</button></div><p id="access-error" class="form-error"></p></form></section><section class="panel"><h2>Prévia do acesso</h2><div class="preview-card"><img class="preview-logo" src="assets/tedflix_logo.png" alt="Tedflix"><h3 id="access-preview-title">Seu acesso será criado com sucesso.</h3><p id="access-preview-text">O código, a senha e a validade aparecerão aqui depois da criação.</p></div></section></div>`;
    const form = $('#create-user-form');
    form.addEventListener('input', () => { $('#access-preview-text').textContent = `E-mail: ${form.email.value || '—'}\nValidade: ${form.neverExpires.checked ? 'Ilimitada' : form.days.value ? `${form.days.value} dias` : 'Data específica'}`; });
    form.addEventListener('submit', async event => {
      event.preventDefault();
      const button = $('button[type="submit"]', form); setBusy(button, true);
      const data = { email: form.email.value.trim(), password: form.password.value.trim() || undefined, days: form.neverExpires.checked ? undefined : Number(form.days.value) || undefined, expiresAt: form.neverExpires.checked ? undefined : (form.expiresAt.value ? new Date(form.expiresAt.value).toISOString() : undefined), neverExpires: form.neverExpires.checked };
      const response = request('POST', '/admin/users', data); setBusy(button, false);
      if (!response.ok) { $('#access-error').textContent = errorOf(response); return; }
      const result = bodyOf(response); $('#access-error').textContent = '';
      $('#access-preview-title').textContent = 'Acesso criado com sucesso.';
      $('#access-preview-text').textContent = `Código: ${result.code || '—'}\nE-mail: ${result.email || data.email}\nSenha: ${result.password || '—'}\nValidade: ${result.expiresAt ? date(result.expiresAt) : 'Ilimitada'}`;
      showToast('Novo acesso criado.', 'success'); app.users = []; 
    });
  }

  async function renderUserDetail(id) {
    const root = $('#page-root'); root.innerHTML = panelLoading();
    const response = request('GET', `/admin/users/${encodeURIComponent(id)}`);
    if (!response.ok) { root.innerHTML = panelError(errorOf(response), 'users'); return; }
    const user = bodyOf(response);
    root.innerHTML = `${pageHead('Detalhes do usuário', 'Informações pessoais, acesso e situação da conta.', '<button class="secondary-button" data-page-action="users">← Voltar</button>')}
      <section class="panel user-hero"><div class="avatar large">${escapeHtml(initials(user.username, user.email))}</div><div><h2>${escapeHtml(user.username || 'Sem nome')}</h2><p class="muted">${escapeHtml(user.email)} · ${escapeHtml(user.code || 'Sem código')}</p><span class="status ${statusClass(user.accountStatus)}">${escapeHtml(statusLabel(user.accountStatus))}</span></div></section>
      <div class="two-col"><section class="panel"><h2>Informações da conta</h2><div class="detail-list"><p><span>ID</span><b>${escapeHtml(user.id)}</b></p><p><span>Validade</span><b>${date(user.accountExpiresAt, true)}</b></p><p><span>Dias restantes</span><b>${user.daysRemaining === null || user.daysRemaining === undefined ? 'Ilimitado' : escapeHtml(user.daysRemaining)}</b></p><p><span>Criado em</span><b>${date(user.createdAt, true)}</b></p><p><span>Último acesso</span><b>${date(user.lastUsedAt, true)}</b></p></div></section><section class="panel"><h2>Ações</h2><div class="quick-list"><button class="primary-button" data-user-action="edit" data-user-id="${escapeHtml(id)}">Editar dados</button><button class="secondary-button" data-user-action="expiration" data-user-id="${escapeHtml(id)}">Alterar validade</button><button class="secondary-button" data-user-action="block" data-user-id="${escapeHtml(id)}">${user.accountStatus === 'blocked' ? 'Desbloquear usuário' : 'Bloquear usuário'}</button><button class="danger-button" data-user-action="delete" data-user-id="${escapeHtml(id)}">Excluir usuário</button></div></section></div>`;
    bindPageActions();
    $$('[data-user-action]').forEach(button => button.addEventListener('click', () => handleUserAction(button.dataset.userAction, id, user)));
  }

  function renderEditUser(id) {
    const user = app.users.find(item => item.id === id) || {};
    $('#page-root').innerHTML = `${pageHead('Editar usuário', 'Atualize os dados pessoais e de acesso.', '<button class="secondary-button" data-page-action="users">← Voltar</button>')}
      <section class="panel"><form id="edit-user-form"><div class="form-grid"><label>Nome do usuário<input name="username" value="${escapeHtml(user.username || '')}" placeholder="Nome opcional"></label><label>E-mail<input name="email" type="email" value="${escapeHtml(user.email || '')}" required></label><label>Nova senha<input name="password" type="password" placeholder="Deixe vazio para manter"></label></div><div class="form-actions"><button class="secondary-button" type="button" data-page-action="users">Cancelar</button><button class="primary-button" type="submit">Salvar alterações</button></div><p id="edit-error" class="form-error"></p></form></section>`;
    $('#edit-user-form').addEventListener('submit', event => {
      event.preventDefault();
      const form = event.currentTarget; const button = $('button[type="submit"]', form); setBusy(button, true);
      const response = request('PATCH', `/admin/users/${encodeURIComponent(id)}`, { email: form.email.value.trim(), username: form.username.value.trim(), password: form.password.value || undefined }); setBusy(button, false);
      if (!response.ok) { $('#edit-error').textContent = errorOf(response); return; }
      app.users = []; showToast('Usuário atualizado.', 'success'); renderUserDetail(id);
    });
    bindPageActions();
  }

  function handleUserAction(action, id, user) {
    if (action === 'edit') return renderEditUser(id);
    if (action === 'expiration') return renderExpiration(id, user);
    if (action === 'block') {
      const block = user.accountStatus !== 'blocked';
      if (!confirm(`${block ? 'Bloquear' : 'Desbloquear'} este usuário?`)) return;
      const response = request('PATCH', `/admin/users/${encodeURIComponent(id)}/block`, { block });
      if (!response.ok) return showToast(errorOf(response), 'error');
      app.users = []; showToast(block ? 'Usuário bloqueado.' : 'Usuário desbloqueado.', 'success'); renderUserDetail(id);
    }
    if (action === 'delete') {
      if (!confirm('Excluir este usuário definitivamente?')) return;
      const response = request('DELETE', `/admin/users/${encodeURIComponent(id)}`);
      if (!response.ok) return showToast(errorOf(response), 'error');
      app.users = []; showToast('Usuário excluído.', 'success'); renderUsers();
    }
  }

  function renderExpiration(id, user) {
    $('#page-root').innerHTML = `${pageHead('Definir validade', 'Altere a data de expiração do usuário.', '<button class="secondary-button" data-page-action="users">← Voltar</button>')}
      <section class="panel"><div class="user-hero"><div class="avatar large">${escapeHtml(initials(user.username, user.email))}</div><div><h2>${escapeHtml(user.username || 'Sem nome')}</h2><p class="muted">${escapeHtml(user.email)}</p></div></div><form id="expiration-form"><div class="form-grid"><label>Dias para adicionar<input name="days" type="number" min="1" placeholder="30"></label><label>Definir data específica<input name="expiresAt" type="datetime-local"></label><label class="full"><span><input name="neverExpires" type="checkbox" style="width:auto;margin-right:8px"> Tornar acesso ilimitado</span></label></div><div class="form-actions"><button class="primary-button" type="submit">Aplicar validade</button></div><p id="expiration-error" class="form-error"></p></form></section>`;
    $('#expiration-form').addEventListener('submit', event => { event.preventDefault(); const form = event.currentTarget; const response = request('PATCH', `/admin/users/${encodeURIComponent(id)}/expiration`, { days: form.days.value ? Number(form.days.value) : undefined, expiresAt: form.expiresAt.value ? new Date(form.expiresAt.value).toISOString() : undefined, neverExpires: form.neverExpires.checked }); if (!response.ok) { $('#expiration-error').textContent = errorOf(response); return; } app.users = []; showToast('Validade atualizada.', 'success'); renderUserDetail(id); });
    bindPageActions();
  }

  function notificationForm(type) {
    if (type === 'individual') return `<form id="notification-individual-form"><div class="form-grid"><label>Id do usuário *<input name="userId" required placeholder="ID do documento"></label><label>Ação opcional<input name="action" placeholder="Ex.: abrir favoritos"></label><label class="full">Título *<input name="title" required placeholder="Aviso de renovação"></label><label class="full">Mensagem *<textarea name="body" required placeholder="Digite a mensagem..."></textarea></label></div><div class="form-actions"><button class="primary-button" type="submit">Enviar notificação</button></div></form>`;
    if (type === 'broadcast') return `<form id="notification-broadcast-form"><div class="form-grid"><label>Destinatários<select name="filter"><option value="all">Todos</option><option value="active">Ativos</option><option value="expiring">Expirando</option><option value="blocked">Bloqueados</option></select></label><label>Ação opcional<input name="action"></label><label class="full">Título *<input name="title" required></label><label class="full">Mensagem *<textarea name="body" required></textarea></label></div><div class="form-actions"><button class="primary-button" type="submit">Enviar em massa</button></div></form>`;
    return `<form id="notification-schedule-form"><div class="form-grid"><label>Enviar para<select name="filter"><option value="all">Todos</option><option value="active">Ativos</option><option value="expiring">Expirando</option><option value="blocked">Bloqueados</option></select></label><label>Data e hora *<input name="scheduledFor" type="datetime-local" required></label><label class="full">Título *<input name="title" required></label><label class="full">Mensagem *<textarea name="body" required></textarea></label></div><div class="form-actions"><button class="primary-button" type="submit">Agendar notificação</button></div></form>`;
  }

  async function renderNotifications() {
    const root = $('#page-root'); root.innerHTML = `${pageHead('Notificações', 'Envie mensagens individuais, em massa ou agendadas.')}
      <section class="panel"><div class="toolbar"><button class="primary-button notification-tab" data-tab="individual">Para usuário</button><button class="secondary-button notification-tab" data-tab="broadcast">Em massa</button><button class="secondary-button notification-tab" data-tab="schedule">Agendar</button><button class="secondary-button notification-tab" data-tab="history">Histórico</button></div><div id="notification-content"></div></section>`;
    $$('[data-tab]').forEach(button => button.addEventListener('click', () => showNotificationTab(button.dataset.tab)));
    showNotificationTab('individual');
  }

  function showNotificationTab(tab) {
    const target = $('#notification-content');
    if (tab === 'history') return loadNotificationHistory(target);
    target.innerHTML = notificationForm(tab);
    const form = target.querySelector('form');
    form.addEventListener('submit', event => {
      event.preventDefault(); const data = Object.fromEntries(new FormData(form).entries());
      const path = tab === 'individual' ? '/admin/notifications/send' : tab === 'broadcast' ? '/admin/notifications/broadcast' : '/admin/notifications/schedule';
      const response = request('POST', path, tab === 'individual' ? data : data);
      if (!response.ok) return showToast(errorOf(response), 'error');
      form.reset(); showToast(bodyOf(response).message || 'Operação concluída.', 'success');
    });
  }

  function loadNotificationHistory(target) {
    target.innerHTML = panelLoading();
    const response = request('GET', '/admin/notifications/history');
    if (!response.ok) { target.innerHTML = panelError(errorOf(response)); return; }
    const rows = bodyOf(response).notifications || [];
    target.innerHTML = `<div class="table-wrap"><table class="data-table"><thead><tr><th>Título</th><th>Tipo</th><th>Usuário</th><th>Enviado em</th><th>Status</th></tr></thead><tbody>${rows.length ? rows.map(item => `<tr><td><strong>${escapeHtml(item.title)}</strong><br><small>${escapeHtml(item.body || '')}</small></td><td>${escapeHtml(item.type || '—')}</td><td>${escapeHtml(item.userId || '—')}</td><td>${date(item.sentAt, true)}</td><td><span class="status active">Enviada</span></td></tr>`).join('') : '<tr><td colspan="5" class="empty">Nenhuma notificação enviada.</td></tr>'}</tbody></table></div>`;
  }

  function renderHistory() {
    $('#page-root').innerHTML = `${pageHead('Histórico', 'Consulte o histórico de notificações enviadas pelo Studio.')}
      <section class="panel"><div id="history-content" class="loading">Carregando histórico...</div></section>`;
    loadNotificationHistory($('#history-content'));
  }

  function renderLogs() {
    $('#page-root').innerHTML = `${pageHead('Logs de ações', 'Área reservada para os registros administrativos do servidor.')}
      <section class="panel"><div class="empty"><h2>Logs administrativos</h2><p>O auth server atual ainda não expõe uma rota <code>/admin/logs</code>. A tela está preparada e será preenchida assim que o endpoint for disponibilizado.</p></div></section>`;
  }

  function renderSettings() {
    const admin = app.admin || {};
    $('#page-root').innerHTML = `${pageHead('Configurações', 'Preferências e sessão do Tedflix Studio.')}
      <div class="two-col"><section class="panel"><h2>Administrador conectado</h2><div class="user-hero"><div class="avatar large">${escapeHtml(initials(admin.name, admin.email))}</div><div><h2>${escapeHtml(admin.name || 'Administrador')}</h2><p class="muted">${escapeHtml(admin.email || '')}</p><span class="status active">Sessão ativa</span></div></div></section><section class="panel"><h2>Segurança</h2><p class="muted">O token administrativo fica protegido pelo Android Keystore e permanece em cache para evitar novo login a cada abertura.</p><button class="danger-button" id="settings-logout">Encerrar sessão</button></section></div>`;
    $('#settings-logout').addEventListener('click', () => { native('logout'); showLogin(); });
  }

  function renderPage(page) {
    app.page = page || 'dashboard';
    $$('.nav-item').forEach(button => button.classList.toggle('active', button.dataset.page === app.page));
    if (app.page === 'dashboard') return renderDashboard();
    if (app.page === 'users') return renderUsers();
    if (app.page === 'access') return renderAccess();
    if (app.page === 'notifications') return renderNotifications();
    if (app.page === 'history') return renderHistory();
    if (app.page === 'logs') return renderLogs();
    if (app.page === 'settings') return renderSettings();
    return renderDashboard();
  }

  function bindPageActions() {
    $$('[data-page-action]').forEach(button => button.addEventListener('click', () => renderPage(button.dataset.pageAction)));
    $$('[data-retry]').forEach(button => button.addEventListener('click', () => renderPage(button.dataset.retry)));
  }

  function refresh() { app.users = []; renderPage(app.page); }

  $('#login-form').addEventListener('submit', event => {
    event.preventDefault();
    const form = event.currentTarget; const button = $('button[type="submit"]', form); const error = $('#login-error'); error.textContent = ''; setBusy(button, true, 'Validando...');
    const response = native('login', $('#login-email').value.trim(), $('#login-password').value); setBusy(button, false);
    if (!response.ok || !bodyOf(response).success) { error.textContent = errorOf(response) || 'Credenciais inválidas'; return; }
    showToast('Login administrativo realizado.', 'success'); showStudio(bodyOf(response).admin);
  });

  $$('.nav-item').forEach(button => button.addEventListener('click', () => renderPage(button.dataset.page)));
  $('#logout-button').addEventListener('click', () => { native('logout'); showLogin(); });
  $('#refresh-button').addEventListener('click', refresh);
  $('#top-notifications').addEventListener('click', () => { renderPage('notifications'); });

  const state = native('sessionState');
  if (state && state.ok !== false && state.loggedIn) showStudio(state.admin || {}); else showLogin();
})();
