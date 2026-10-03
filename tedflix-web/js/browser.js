(() => {
  const AUTH = "https://servidores-ted-auth.onrender.com";
  const CATALOG = "https://servidores-ted-auth-1.onrender.com/api";
  const TOKEN_KEY = "tedflix:web:token";
  const USER_KEY = "tedflix:web:user";
  const PROFILE_KEY = "tedflix:web:profile";
  const PROFILES_KEY = "tedflix:web:profiles";
  const FAVORITES_KEY = "tedflix:web:favorites";
  const HISTORY_KEY = "tedflix:web:history";
  const SEEDS = Array.from({ length: 15 }, (_, i) => `tedflix-avatar-${String(i + 1).padStart(2, "0")}`);

  const read = (key, fallback) => { try { return JSON.parse(localStorage.getItem(key) || JSON.stringify(fallback)); } catch (_) { return fallback; } };
  const write = (key, value) => { try { localStorage.setItem(key, JSON.stringify(value)); } catch (_) {} };
  const token = () => localStorage.getItem(TOKEN_KEY) || "";
  const activeId = () => localStorage.getItem(PROFILE_KEY) || "";
  const avatar = (seed, style = "fun-emoji", size = 256) => `https://api.dicebear.com/10.x/${encodeURIComponent(style)}/png?seed=${encodeURIComponent(seed || SEEDS[0])}&size=${size}`;
  const json = async (url, options = {}) => {
    const headers = { Accept: "application/json", ...(options.body ? { "Content-Type": "application/json" } : {}), ...(token() ? { Authorization: `Bearer ${token()}` } : {}) };
    const response = await fetch(url, { ...options, headers: { ...headers, ...(options.headers || {}) } });
    let body = {};
    try { body = await response.json(); } catch (_) {}
    if (!response.ok) throw new Error(body.error || body.message || `Falha ao carregar (${response.status})`);
    return body;
  };
  const result = (value, error = "") => JSON.stringify(error ? { success: false, error } : { success: true, ...value });
  const profilesFrom = (body) => body.profiles || body.data?.profiles || [];
  const normalizeProfile = (p, i) => ({ id: p.id || p.profileId || `profile-${i}`, name: p.name || "Meu perfil", username: p.username || "", isKids: Boolean(p.isKids), isPrimary: Boolean(p.isPrimary), avatarSeed: p.avatar?.seed || p.avatarSeed || SEEDS[i % SEEDS.length], avatarStyle: p.avatar?.style || p.avatarStyle || "fun-emoji" });

  async function loadProfiles() {
    const body = await json(`${AUTH}/api/profiles`);
    const profiles = profilesFrom(body).map(normalizeProfile);
    write(PROFILES_KEY, profiles);
    return profiles;
  }

  function getProfiles() {
    const profiles = read(PROFILES_KEY, []);
    return JSON.stringify({ profiles, activeId: activeId() || profiles[0]?.id || "" });
  }

  async function activateProfile(id) {
    await json(`${AUTH}/api/profiles/${encodeURIComponent(id)}/select`, { method: "POST", body: "{}" });
    const access = await json(`${AUTH}/api/profiles/${encodeURIComponent(id)}/access`);
    if (!(access.access?.allowed ?? access.allowed ?? false)) throw new Error("Este perfil não tem acesso liberado no momento.");
    localStorage.setItem(PROFILE_KEY, id);
    await refreshData();
  }

  function profilePath(suffix) { return `${AUTH}/api/profiles/${encodeURIComponent(activeId())}${suffix}`; }
  async function refreshFavorites() {
    if (!activeId()) return [];
    const body = await json(profilePath("/favorites"));
    const items = body.favorites || body.favoritos || [];
    write(FAVORITES_KEY, items);
    return items;
  }
  async function refreshHistory() {
    if (!activeId()) return [];
    const body = await json(profilePath("/history"));
    const items = body.history || body.items || body.resultados || [];
    write(HISTORY_KEY, items);
    return items;
  }
  async function refreshData() {
    try { await Promise.all([refreshFavorites(), refreshHistory()]); } catch (_) {}
    window.__tedflixHistoryReady?.();
    window.dispatchEvent(new CustomEvent("tedflix-data-ready"));
  }

  window.AndroidPlayer = {
    getProfiles,
    getFavorites: () => result({ favorites: read(FAVORITES_KEY, []) }),
    getContinueWatching: () => JSON.stringify(read(HISTORY_KEY, [])),
    refreshContinueWatching: () => { refreshHistory().then(() => { window.__tedflixHistoryReady?.(); window.dispatchEvent(new CustomEvent("tedflix-data-ready")); }).catch(() => window.__tedflixHistoryReady?.()); },
    openFavorites: () => { location.hash = "#/favoritos"; },
    activateProfileFromSettings: (id) => { localStorage.setItem(PROFILE_KEY, id); activateProfile(id).catch(() => {}); return result({}); },
    updateStoredProfile: (id, name, seed) => {
      const profiles = read(PROFILES_KEY, []).map((p) => p.id === id ? { ...p, name, avatarSeed: seed } : p);
      write(PROFILES_KEY, profiles);
      json(`${AUTH}/api/profiles/${encodeURIComponent(id)}`, { method: "PATCH", body: JSON.stringify({ name, avatar: { style: profiles.find((p) => p.id === id)?.avatarStyle || "fun-emoji", seed } }) }).then(loadProfiles).catch(() => {});
      return result({});
    },
    createProfileFromSettings: (_code, _email, _password, name, seed) => {
      json(`${AUTH}/api/profiles`, { method: "POST", body: JSON.stringify({ name, isKids: false, avatar: { style: "fun-emoji", seed: seed || SEEDS[read(PROFILES_KEY, []).length % SEEDS.length] } }) }).then(async (body) => { const next = normalizeProfile(body.profile || body, read(PROFILES_KEY, []).length); write(PROFILES_KEY, [...read(PROFILES_KEY, []), next]); window.dispatchEvent(new CustomEvent("tedflix-data-ready")); }).catch(() => {});
      return result({});
    },
    deleteProfileFromSettings: (id) => {
      if (read(PROFILES_KEY, []).length <= 1) return result({}, "O último perfil não pode ser excluído.");
      write(PROFILES_KEY, read(PROFILES_KEY, []).filter((p) => p.id !== id));
      json(`${AUTH}/api/profiles/${encodeURIComponent(id)}`, { method: "DELETE" }).catch(() => {});
      if (activeId() === id) localStorage.setItem(PROFILE_KEY, read(PROFILES_KEY, [])[0]?.id || "");
      return result({});
    },
    logoutFromSettings: () => { window.AndroidPlayer.logout(); return result({}); },
    setBuffer: () => {},
    getAccountProfile: () => result({ user: read(USER_KEY, {}) }),
    getAccountStatus: () => result({ status: read(USER_KEY, {}) }),
    toggleFavorite: (contentId, title, thumb, contentType = "movie") => {
      const current = read(FAVORITES_KEY, []);
      const id = String(contentId || "");
      const found = current.findIndex((x) => String(x.contentId || x.filmeId || "") === id);
      const next = found >= 0 ? current.filter((_, i) => i !== found) : [...current, { contentId: id, title, thumb, contentType }];
      write(FAVORITES_KEY, next);
      if (activeId()) json(profilePath("/favorites/toggle"), { method: "POST", body: JSON.stringify({ contentId: id, title, thumb, contentType }) }).then(refreshFavorites).catch(() => {});
      window.dispatchEvent(new CustomEvent("tedflix-data-ready"));
      return result({ favorited: found < 0 });
    },
    saveProgress: (contentId, title, tempo, thumb, durationSeconds = 0) => {
      if (!activeId()) return result({});
      const parts = String(tempo || "0:00").split(":").map(Number); const positionSeconds = parts.length === 3 ? parts[0] * 3600 + parts[1] * 60 + parts[2] : parts.length === 2 ? parts[0] * 60 + parts[1] : Number(parts[0] || 0);
      json(profilePath("/progress"), { method: "PUT", body: JSON.stringify({ contentId, title, thumb, contentType: "movie", positionSeconds, durationSeconds }) }).then(refreshHistory).catch(() => {});
      return result({});
    },
    logout: () => { [TOKEN_KEY, USER_KEY, PROFILE_KEY, PROFILES_KEY, FAVORITES_KEY, HISTORY_KEY].forEach((k) => localStorage.removeItem(k)); location.reload(); },
  };

  function styles() {
    const s = document.createElement("style"); s.textContent = `.web-gate{position:fixed;inset:0;z-index:999;background:#050609;color:#fff;display:grid;place-items:center;padding:24px;font-family:Manrope,system-ui,sans-serif}.web-gate-card{width:min(440px,100%);padding:30px 24px;border:1px solid #292b35;border-radius:22px;background:#101117;box-shadow:0 24px 80px #0008}.web-gate-logo{display:block;width:190px;height:58px;object-fit:contain;margin:0 auto 22px}.web-gate h1{text-align:center;font:700 27px Oswald,sans-serif;text-transform:uppercase;margin-bottom:8px}.web-gate p{text-align:center;color:#aaa;margin-bottom:22px}.web-gate form{display:grid;gap:12px}.web-gate input{width:100%;padding:13px 16px;border:1px solid #353846;border-radius:999px;background:#181a22;color:#fff;font:inherit}.web-gate button{padding:13px;border:0;border-radius:999px;background:#e50914;color:#fff;font-weight:800;cursor:pointer}.web-gate .secondary{background:#272a35}.web-gate .error{min-height:20px;color:#ff8d91;text-align:center;font-size:13px}.web-profiles{display:flex;flex-wrap:wrap;justify-content:center;gap:20px;margin:24px 0}.web-profile{width:112px;background:none;border:0;color:#ddd;cursor:pointer;font:700 14px Manrope}.web-profile img{width:96px;height:96px;display:block;margin:auto;border-radius:50%;background:#20222b;border:3px solid transparent}.web-profile:hover img{border-color:#e50914}.web-add{color:#aaa}.web-gate .logout{margin-top:12px;background:transparent;color:#aaa}`; document.head.append(s);
  }

  function gate() {
    if (document.getElementById("web-gate")) return;
    const root = document.createElement("div"); root.id = "web-gate"; root.className = "web-gate"; document.body.append(root);
    const renderAuth = (message = "") => { root.innerHTML = `<div class="web-gate-card"><img class="web-gate-logo" src="assets/logo/logo.png" alt="Tedflix"><h1>Entre no Tedflix</h1><p>Use sua conta para continuar.</p><form id="web-login"><input name="email" type="email" placeholder="E-mail" required><input name="password" type="password" placeholder="Senha" required><input name="name" placeholder="Nome (somente cadastro)" hidden><button>Entrar</button><button type="button" class="secondary" id="web-register">Criar conta</button><div class="error">${message}</div></form></div>`; const form = root.querySelector("form"); let cadastro = false; root.querySelector("#web-register").onclick = () => { cadastro = !cadastro; root.querySelector('[name="name"]').hidden = !cadastro; root.querySelector('[name="name"]').required = cadastro; root.querySelector("button:not(.secondary)").textContent = cadastro ? "Cadastrar" : "Entrar"; root.querySelector("#web-register").textContent = cadastro ? "Já tenho conta" : "Criar conta"; }; form.onsubmit = async (e) => { e.preventDefault(); const data = Object.fromEntries(new FormData(form)); try { const body = await json(`${AUTH}/api/auth/${cadastro ? "register" : "login"}`, { method: "POST", body: JSON.stringify(cadastro ? { name: data.name, email: data.email, password: data.password } : { email: data.email, password: data.password }) }); const auth = body.auth || body; const idToken = auth.idToken || body.idToken || body.token; if (!idToken) throw new Error("O servidor não devolveu uma sessão válida."); localStorage.setItem(TOKEN_KEY, idToken); write(USER_KEY, body.account || body.user || {}); await chooseProfile(); } catch (err) { renderAuth(err.message); } }; };
    const chooseProfile = async () => { try { const profiles = await loadProfiles(); if (!profiles.length) { root.innerHTML = `<div class="web-gate-card"><img class="web-gate-logo" src="assets/logo/logo.png" alt="Tedflix"><h1>Crie seu perfil</h1><p>Você ainda não possui um perfil.</p><button id="web-create">Criar perfil</button><div class="error"></div></div>`; root.querySelector("#web-create").onclick = async () => { try { const p = await json(`${AUTH}/api/profiles`, { method: "POST", body: JSON.stringify({ name: "Meu perfil", isKids: false, avatar: { style: "fun-emoji", seed: SEEDS[0] } }) }); const created = normalizeProfile(p.profile || p, 0); write(PROFILES_KEY, [created]); await activateProfile(created.id); start(); } catch (e) { root.querySelector(".error").textContent = e.message; } }; return; } root.innerHTML = `<div class="web-gate-card"><img class="web-gate-logo" src="assets/logo/logo.png" alt="Tedflix"><h1>Quem está assistindo?</h1><p>Escolha seu avatar para continuar.</p><div class="web-profiles">${profiles.map((p) => `<button class="web-profile" data-id="${p.id}"><img src="${avatar(p.avatarSeed, p.avatarStyle)}" alt=""><span>${p.name}</span></button>`).join("")}</div><button class="logout" id="web-logout">Sair</button><div class="error"></div></div>`; root.querySelectorAll(".web-profile").forEach((button) => { button.onclick = async () => { try { await activateProfile(button.dataset.id); start(); } catch (e) { root.querySelector(".error").textContent = e.message; } }; }); root.querySelector("#web-logout").onclick = () => window.AndroidPlayer.logout(); } catch (e) { renderAuth(e.message); } };
    const start = () => { root.remove(); window.__TEDFLIX_BROWSER_GATE__ = false; window.__tedflixStartApp?.(); };
    styles();
    if (token()) chooseProfile(); else renderAuth();
  }
  window.__TEDFLIX_BROWSER_GATE__ = true;
  window.__tedflixBrowserGate = gate;
  window.addEventListener("load", gate, { once: true });
})();
