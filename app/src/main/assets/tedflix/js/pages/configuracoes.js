import { el, svgIcone } from "../dom.js";
import { lerPrefs, salvarPrefs, OPCOES_BUFFER, OPCOES_PLAYER } from "../prefs.js";
import { medir, lerCache, nivel, ROTULO } from "../net.js";

const AVATAR_SEEDS = Array.from({ length: 15 }, (_, index) => `tedflix-avatar-${String(index + 1).padStart(2, "0")}`);
const DEFAULT_SEED = AVATAR_SEEDS[0];

function avatarUrl(seed, size = 256) {
  return `https://api.dicebear.com/10.x/fun-emoji/png?seed=${encodeURIComponent(seed || DEFAULT_SEED)}&size=${size}`;
}

function respostaAndroid(nome, ...args) {
  try {
    const fn = window.AndroidPlayer?.[nome];
    if (typeof fn !== "function") return { success: false, error: "Função indisponível." };
    const raw = fn(...args);
    if (typeof raw === "object") return raw;
    return JSON.parse(raw || "{}");
  } catch (error) {
    return { success: false, error: error?.message || "Não foi possível concluir a operação." };
  }
}

function icon(nome, classe = "") {
  return el("span", { class: `cfg-icon ${classe}`, html: svgIcone(nome) });
}

function imgAvatar(profile, classe = "cfg-avatar-img", size = 256) {
  const image = el("img", {
    class: classe,
    src: avatarUrl(profile?.avatarSeed || DEFAULT_SEED, size),
    alt: profile?.name || "Avatar",
    loading: "eager",
  });
  image.addEventListener("error", () => {
    if (!image.dataset.fallback) {
      image.dataset.fallback = "1";
      image.src = avatarUrl(DEFAULT_SEED, size);
    }
  });
  return image;
}

function button(label, classe = "btn ghost", onClick) {
  const node = el("button", { class: classe, type: "button" }, label);
  if (onClick) node.addEventListener("click", onClick);
  return node;
}

function perfilAtual(perfis, ativo) {
  return perfis.find((profile) => profile.id === ativo) || perfis[0] || {
    id: "",
    name: "Meu perfil",
    avatarSeed: DEFAULT_SEED,
    avatarStyle: "fun-emoji",
    email: "",
  };
}

function lerPerfis() {
  const resposta = respostaAndroid("getProfiles");
  const perfis = Array.isArray(resposta.profiles) ? resposta.profiles : [];
  const ativo = resposta.activeId || perfis[0]?.id || "";
  return { perfis, ativo };
}

function tituloStatus() {
  const resposta = respostaAndroid("getAccountStatus");
  if (!resposta.success) return "Status indisponível no momento.";
  const status = resposta.status || {};
  const situacao = status.status || status.accountStatus || "ativa";
  const validade = status.validade || status.expiresAt || status.expiraEm || "não informada";
  const dias = status.diasRestantes ?? status.daysRemaining;
  return `Status: ${situacao} · Validade: ${validade}${dias === undefined ? "" : ` · ${dias} dias restantes`}`;
}

function cabecalho(titulo, onBack, acao, textoAcao = "") {
  window.scrollTo({ top: 0, left: 0, behavior: "auto" });
  document.documentElement.scrollLeft = 0;
  const header = el("div", { class: "cfg-screen-head" });
  const left = el("div", { class: "cfg-screen-head-left" });
  if (onBack) {
    const back = button("←", "cfg-back", onBack);
    back.setAttribute("style", "display:grid;place-items:center;width:40px;min-width:40px;height:40px;padding:0;color:#ffffff!important;background:#171820;border:1px solid rgba(255,255,255,.10);border-radius:999px;font-family:Arial,sans-serif;font-size:27px;font-weight:400;line-height:1;text-indent:0;overflow:hidden;");
    back.setAttribute("aria-label", "Voltar para Configurações");
    back.setAttribute("title", "Voltar para Configurações");
    left.append(back);
  }
  left.append(el("h1", {}, titulo));
  header.append(left);
  if (acao) header.append(button(textoAcao, "cfg-head-action", acao));
  return header;
}

function linhaOpcao(nomeIcone, titulo, descricao, onClick, complemento = null) {
  const row = el("button", { class: "cfg-row", type: "button" });
  row.append(icon(nomeIcone, "cfg-row-icon"));
  row.append(el("span", { class: "cfg-row-copy" }, [
    el("strong", {}, titulo),
    el("small", {}, descricao),
  ]));
  if (complemento) row.append(complemento);
  row.append(icon("chevron", "cfg-chevron"));
  if (onClick) row.addEventListener("click", onClick);
  return row;
}

function grupoOpcoes(titulo, descricao, opcoes, atual, aoEscolher) {
  const lista = el("div", { class: "cfg-choice-list" });
  opcoes.forEach((opcao) => {
    const item = el("button", { class: `cfg-choice${opcao.valor === atual ? " selected" : ""}`, type: "button" }, [
      el("span", { class: "cfg-radio" }),
      el("span", { class: "cfg-choice-copy" }, [
        el("strong", {}, opcao.titulo),
        el("small", {}, opcao.desc),
      ]),
    ]);
    item.addEventListener("click", () => {
      [...lista.children].forEach((child) => child.classList.remove("selected"));
      item.classList.add("selected");
      aoEscolher(opcao.valor);
    });
    lista.append(item);
  });
  return el("section", { class: "cfg-settings-section" }, [
    el("h2", {}, titulo),
    el("p", { class: "cfg-section-sub" }, descricao),
    lista,
  ]);
}

function perfilCard(perfil, ativo, onClick) {
  const card = el("button", { class: `cfg-profile-mini${perfil.id === ativo ? " selected" : ""}`, type: "button" });
  card.append(imgAvatar(perfil, "cfg-profile-mini-avatar", 160));
  card.append(el("span", {}, perfil.name || "Meu perfil"));
  if (perfil.id === ativo) card.append(el("i", { class: "cfg-profile-check" }, "✓"));
  card.addEventListener("click", onClick);
  return card;
}

function toast(text, tipo = "") {
  const atual = document.querySelector(".cfg-toast");
  atual?.remove();
  const node = el("div", { class: `cfg-toast ${tipo}` }, text);
  document.body.append(node);
  setTimeout(() => node.remove(), 2800);
}

export default async function paginaConfiguracoes(raiz) {
  const page = el("div", { class: "page cfg cfg-shell" });
  const state = {
    screen: "home",
    perfis: [],
    ativo: "",
    perfilEditando: null,
    draftNome: "",
    draftAvatar: "",
  };

  function atualizarPerfis() {
    const dados = lerPerfis();
    state.perfis = dados.perfis;
    state.ativo = dados.ativo;
  }

  function renderHome() {
    page.innerHTML = "";
    const prefs = lerPrefs();
    const perfil = perfilAtual(state.perfis, state.ativo);
    const contaStatus = tituloStatus();
    const header = cabecalho("Configurações", null, null);
    page.append(header, el("p", { class: "cfg-lead" }, "Gerencie sua conta, perfil e preferências."));

    const profileHero = el("section", { class: "cfg-account-hero" });
    const profileButton = el("button", { class: "cfg-account-profile", type: "button" });
    const avatarWrap = el("span", { class: "cfg-account-avatar-wrap" }, [
      imgAvatar(perfil, "cfg-account-avatar", 256),
      el("span", { class: "cfg-camera" }, [icon("camera")]),
    ]);
    profileButton.append(avatarWrap, el("span", { class: "cfg-account-copy" }, [
      el("strong", {}, perfil.name || "Meu perfil"),
      el("small", { class: "cfg-pill" }, state.perfis.length > 1 ? "Perfil selecionado" : "Perfil principal"),
      el("small", {}, "Toque para editar nome e avatar"),
    ]), icon("chevron", "cfg-chevron"));
    profileButton.addEventListener("click", () => renderProfileEdit(perfil));
    profileHero.append(profileButton);
    profileHero.append(el("div", { class: "cfg-account-status" }, [
      el("div", { class: "cfg-account-status-title" }, [el("span", { class: "cfg-status-dot" }), "Minha conta"]),
      el("p", {}, contaStatus),
    ]));
    page.append(profileHero);

    const contaSection = el("section", { class: "cfg-card cfg-card-group" });
    contaSection.append(el("div", { class: "cfg-group-label" }, "Minha conta"));
    contaSection.append(linhaOpcao("user", "Perfil", "Editar nome, avatar e gerenciar perfis.", () => renderProfiles()));
    contaSection.append(linhaOpcao("lock", "Senha", "Alterar senha de acesso.", () => renderSecurity()));
    page.append(contaSection);

    const favoritosSection = el("section", { class: "cfg-card cfg-card-group" });
    favoritosSection.append(linhaOpcao("heart", "Favoritos", "Acesse filmes e séries salvos.", () => window.AndroidPlayer?.openFavorites?.()));
    favoritosSection.append(button("Abrir favoritos  ›", "cfg-wide-button", () => window.AndroidPlayer?.openFavorites?.()));
    page.append(favoritosSection);

    const networkCard = el("section", { class: "cfg-card cfg-network-card" });
    const networkValue = el("strong", { class: "cfg-network-value" }, "medindo...");
    const networkTag = el("span", { class: "vel-tag" }, "");
    const networkDescription = el("p", { class: "cfg-section-sub" }, "Testando a sua conexão agora.");
    networkCard.append(el("div", { class: "cfg-card-title-line" }, [icon("globe", "cfg-colored green"), el("div", {}, [el("h2", {}, "Conexão"), el("p", { class: "cfg-section-sub" }, "Sua velocidade de rede e qualidade.")]), el("span", { class: "cfg-network-badge" }, [networkValue, networkTag])]));
    networkCard.append(networkDescription, button("Medir novamente", "cfg-wide-button", async () => {
      networkValue.textContent = "medindo...";
      pintarRede(await medir({ forcar: true }), networkValue, networkTag, networkDescription);
    }));
    page.append(networkCard);

    const playback = el("section", { class: "cfg-card cfg-card-group" });
    playback.append(linhaOpcao("play", "Reprodução", `Player ${prefs.player === "auto" ? "automático" : prefs.player} · buffer ${prefs.buffer}.`, () => renderPlayback()));
    page.append(playback);

    const system = el("section", { class: "cfg-card cfg-card-group" });
    system.append(linhaOpcao("bell", "Notificações", "Gerencie avisos e atualizações da sua conta.", () => window.AndroidPlayer?.openNotifications?.()));
    system.append(linhaOpcao("gear", "Fontes de conteúdo", "Escolha o servidor principal ou uma fonte alternativa.", () => window.AndroidPlayer?.openSources?.()));
    system.append(linhaOpcao("logout", "Sair da conta", "Encerrar sessão neste dispositivo.", () => sair()));
    page.append(system);

    const cache = lerCache();
    if (cache && !cache.expirado) pintarRede(cache, networkValue, networkTag, networkDescription);
    medir().then((dados) => pintarRede(dados, networkValue, networkTag, networkDescription)).catch(() => pintarRede(null, networkValue, networkTag, networkDescription));
  }

  function pintarRede(dados, valor, tag, detalhe) {
    const nivelRede = nivel(dados?.mbps);
    valor.textContent = dados?.mbps ? `${dados.mbps.toFixed(2)} Mbps` : "indisponível";
    tag.textContent = ROTULO[nivelRede] || "";
    tag.className = `vel-tag ${nivelRede}`;
    detalhe.textContent = nivelRede === "lenta"
      ? "Conexão fraca: as capas vêm menores e o player começa em qualidade baixa."
      : nivelRede === "media"
        ? "Dá para assistir bem em qualidade média."
        : nivelRede === "desconhecida"
          ? "Não deu para medir agora."
          : "Conexão boa: qualidade alta liberada.";
  }

  function renderProfiles() {
    atualizarPerfis();
    page.innerHTML = "";
    page.append(cabecalho("Perfis", renderHome, () => renderManageProfiles(), "Editar"));
    page.append(el("p", { class: "cfg-lead" }, "Escolha quem está assistindo ou adicione um novo perfil."));
    const grid = el("div", { class: "cfg-profiles-grid" });
    state.perfis.forEach((perfil) => grid.append(perfilCard(perfil, state.ativo, () => selecionarPerfil(perfil))));
    const add = el("button", { class: "cfg-profile-add", type: "button" }, [el("span", { class: "cfg-add-circle" }, [icon("plus")]), el("strong", {}, "Adicionar perfil")]);
    add.addEventListener("click", () => renderAddProfile());
    grid.append(add);
    page.append(grid);
    page.append(el("section", { class: "cfg-card cfg-profile-manage" }, [
      linhaOpcao("people", "Gerenciar perfis", "Editar, excluir e organizar os perfis criados.", () => renderManageProfiles()),
    ]));
  }

  function selecionarPerfil(perfil) {
    const resposta = respostaAndroid("activateProfileFromSettings", perfil.id);
    if (!resposta.success) {
      toast(resposta.error || "Não foi possível selecionar o perfil.", "error");
      return;
    }
    atualizarPerfis();
    toast(`Perfil ${perfil.name} selecionado.`);
    renderProfiles();
  }

  function renderManageProfiles() {
    atualizarPerfis();
    page.innerHTML = "";
    page.append(cabecalho("Gerenciar perfis", renderProfiles));
    const list = el("section", { class: "cfg-card cfg-manage-list" });
    state.perfis.forEach((perfil) => {
      const row = el("div", { class: "cfg-manage-row" }, [
        imgAvatar(perfil, "cfg-manage-avatar", 128),
        el("div", { class: "cfg-manage-copy" }, [el("strong", {}, perfil.name), el("small", {}, perfil.id === state.ativo ? "Perfil principal" : "Perfil adicional")]),
        (() => {
          const edit = button("", "cfg-icon-button", () => renderProfileEdit(perfil));
          edit.append(icon("pencil"));
          return edit;
        })(),
      ]);
      list.append(row);
    });
    const addRow = el("button", { class: "cfg-manage-add", type: "button" }, [el("span", { class: "cfg-add-circle small" }, [icon("plus")]), el("strong", {}, "Adicionar perfil")]);
    addRow.addEventListener("click", () => renderAddProfile());
    list.append(addRow);
    page.append(list);
  }

  function renderAvatarPicker(currentSeed, onPick, onBack) {
    page.innerHTML = "";
    page.append(cabecalho("Escolher avatar", onBack || (() => renderProfileEdit(state.perfilEditando))));
    page.append(el("p", { class: "cfg-lead" }, "Escolha um avatar para este perfil."));
    const grid = el("div", { class: "cfg-avatar-grid" });
    AVATAR_SEEDS.forEach((seed) => {
      const item = el("button", { class: `cfg-avatar-option${seed === currentSeed ? " selected" : ""}`, type: "button" }, [
        imgAvatar({ avatarSeed: seed, name: "Avatar" }, "cfg-picker-avatar", 256),
        seed === currentSeed ? el("span", { class: "cfg-avatar-check" }, "✓") : null,
      ]);
      item.addEventListener("click", () => onPick(seed));
      grid.append(item);
    });
    page.append(grid, button("Cancelar", "cfg-cancel-button", onBack || (() => renderProfileEdit(state.perfilEditando))));
  }

  function renderProfileEdit(perfil, draftNome = perfil?.name || "", draftAvatar = perfil?.avatarSeed || DEFAULT_SEED) {
    state.perfilEditando = perfil;
    state.draftNome = draftNome;
    state.draftAvatar = draftAvatar;
    page.innerHTML = "";
    page.append(cabecalho("Editar perfil", renderProfiles, () => salvarPerfil(perfil, nameInput.value, state.draftAvatar), "Salvar"));
    const editor = el("section", { class: "cfg-profile-editor" });
    const preview = el("div", { class: "cfg-editor-preview" }, [
      imgAvatar({ avatarSeed: state.draftAvatar, name: perfil?.name || "Avatar" }, "cfg-editor-avatar", 320),
      el("span", { class: "cfg-editor-camera" }, [icon("camera")]),
    ]);
    preview.addEventListener("click", () => renderAvatarPicker(state.draftAvatar, (seed) => renderProfileEdit(perfil, nameInput.value, seed), () => renderProfileEdit(perfil, nameInput.value, state.draftAvatar)));
    editor.append(preview, el("label", { class: "cfg-field-label" }, "Nome do perfil"));
    const nameInput = el("input", { class: "input cfg-field", type: "text", value: draftNome, maxlength: "40", autocomplete: "nickname" });
    editor.append(nameInput, el("label", { class: "cfg-field-label" }, "Escolher avatar"));
    const strip = el("div", { class: "cfg-avatar-strip" });
    AVATAR_SEEDS.slice(0, 6).forEach((seed) => {
      const mini = el("button", { class: `cfg-strip-option${seed === state.draftAvatar ? " selected" : ""}`, type: "button" }, imgAvatar({ avatarSeed: seed, name: "Avatar" }, "cfg-strip-avatar", 128));
      mini.addEventListener("click", () => {
        state.draftAvatar = seed;
        [...strip.children].forEach((child) => child.classList.remove("selected"));
        mini.classList.add("selected");
        preview.querySelector("img").src = avatarUrl(seed, 320);
      });
      strip.append(mini);
    });
    const choose = button("Escolher outro avatar", "cfg-wide-button", () => renderAvatarPicker(state.draftAvatar, (seed) => renderProfileEdit(perfil, nameInput.value, seed), () => renderProfileEdit(perfil, nameInput.value, state.draftAvatar)));
    editor.append(strip, choose, button("Salvar alterações", "cfg-save-button", () => salvarPerfil(perfil, nameInput.value, state.draftAvatar)));
    if (perfil && state.perfis.length > 1) editor.append(button("Excluir perfil", "cfg-delete-link", () => confirmarExclusao(perfil)));
    page.append(editor);
  }

  function salvarPerfil(perfil, nome, avatarSeed) {
    const valor = String(nome || "").trim();
    if (valor.length < 2) {
      toast("Digite um nome de perfil válido.", "error");
      return;
    }
    const resposta = respostaAndroid("updateStoredProfile", perfil?.id || "", valor, avatarSeed || DEFAULT_SEED);
    if (!resposta.success) {
      toast(resposta.error || "Não foi possível salvar o perfil.", "error");
      return;
    }
    atualizarPerfis();
    toast("Perfil atualizado.");
    renderProfileEdit(state.perfis.find((item) => item.id === (perfil?.id || "")) || perfil, valor, avatarSeed);
  }

  function renderAddProfile(draft = {}) {
    state.perfilEditando = null;
    state.draftAvatar = draft.avatar || state.draftAvatar || AVATAR_SEEDS[Math.min(state.perfis.length, AVATAR_SEEDS.length - 1)];
    page.innerHTML = "";
    page.append(cabecalho("Adicionar perfil", renderProfiles));
    const editor = el("section", { class: "cfg-profile-editor cfg-add-editor" });
    const preview = el("div", { class: "cfg-editor-preview" }, [imgAvatar({ avatarSeed: state.draftAvatar, name: "Novo perfil" }, "cfg-editor-avatar", 320), el("span", { class: "cfg-editor-camera" }, [icon("camera")])]);
    const nameInput = el("input", { class: "input cfg-field", type: "text", placeholder: "Nome do perfil", maxlength: "40", value: draft.name || "" });
    const codeInput = el("input", { class: "input cfg-field", type: "text", placeholder: "Token ou código de acesso", value: draft.code || "" });
    const emailInput = el("input", { class: "input cfg-field", type: "email", placeholder: "E-mail", autocomplete: "email", value: draft.email || "" });
    const passwordInput = el("input", { class: "input cfg-field", type: "password", placeholder: "Senha", autocomplete: "new-password", value: draft.password || "" });
    const draftAtual = () => ({ name: nameInput.value, code: codeInput.value, email: emailInput.value, password: passwordInput.value, avatar: state.draftAvatar });
    const abrirAvatar = () => renderAvatarPicker(state.draftAvatar, (seed) => renderAddProfile({ ...draftAtual(), avatar: seed }), () => renderAddProfile(draftAtual()));
    const avatarButton = button("Escolher avatar", "cfg-wide-button", abrirAvatar);
    preview.addEventListener("click", abrirAvatar);
    editor.append(preview, el("label", { class: "cfg-field-label" }, "Nome do perfil"), nameInput, el("label", { class: "cfg-field-label" }, "Credenciais"), codeInput, emailInput, passwordInput, avatarButton, button("Validar e adicionar", "cfg-save-button", () => {
      if (!nameInput.value.trim() || !codeInput.value.trim() || !emailInput.value.trim() || !passwordInput.value) {
        toast("Preencha nome, código, e-mail e senha.", "error");
        return;
      }
      const resposta = respostaAndroid("createProfileFromSettings", codeInput.value.trim(), emailInput.value.trim(), passwordInput.value, nameInput.value.trim(), state.draftAvatar);
      if (!resposta.success) {
        toast(resposta.error || "Não foi possível adicionar o perfil.", "error");
        return;
      }
      atualizarPerfis();
      toast("Perfil adicionado.");
      renderProfiles();
    }));
    page.append(editor);
  }

  function confirmarExclusao(perfil) {
    const overlay = el("div", { class: "cfg-modal-backdrop" });
    const modal = el("div", { class: "cfg-confirm-modal" }, [
      el("div", { class: "cfg-danger-icon" }, [icon("trash")]),
      el("h2", {}, "Excluir perfil?"),
      el("p", {}, "Tem certeza que deseja excluir este perfil?"),
      el("div", { class: "cfg-modal-actions" }, [
        button("Cancelar", "cfg-modal-secondary", () => overlay.remove()),
        button("Excluir", "cfg-modal-danger", () => {
          const resposta = respostaAndroid("deleteProfileFromSettings", perfil.id);
          overlay.remove();
          if (!resposta.success) toast(resposta.error || "Não foi possível excluir o perfil.", "error");
          else { atualizarPerfis(); toast("Perfil excluído."); renderManageProfiles(); }
        }),
      ]),
    ]);
    overlay.append(modal);
    page.append(overlay);
  }

  function renderSecurity() {
    page.innerHTML = "";
    page.append(cabecalho("Senha", renderHome));
    page.append(el("p", { class: "cfg-lead" }, "Altere sua senha de acesso com segurança."));
    const current = el("input", { class: "input cfg-field", type: "password", placeholder: "Senha atual", autocomplete: "current-password" });
    const next = el("input", { class: "input cfg-field", type: "password", placeholder: "Nova senha", autocomplete: "new-password" });
    const feedback = el("p", { class: "cfg-feedback" }, "");
    page.append(el("section", { class: "cfg-card cfg-form-card" }, [
      el("div", { class: "cfg-form-heading" }, [icon("lock", "cfg-colored purple"), el("div", {}, [el("h2", {}, "Segurança"), el("p", { class: "cfg-section-sub" }, "Use pelo menos 6 caracteres na nova senha.")])]),
      current, next, button("Alterar senha", "cfg-save-button", () => {
        const resposta = respostaAndroid("changeProfilePassword", current.value, next.value);
        feedback.textContent = resposta.success ? "Senha alterada com sucesso." : (resposta.error || "Não foi possível alterar a senha.");
        feedback.className = `cfg-feedback${resposta.success ? " success" : " error"}`;
        if (resposta.success) { current.value = ""; next.value = ""; }
      }),
      feedback,
    ]));
  }

  function renderPlayback() {
    page.innerHTML = "";
    const prefs = lerPrefs();
    page.append(cabecalho("Reprodução", renderHome));
    page.append(el("p", { class: "cfg-lead" }, "Escolha como o Tedflix deve preparar e reproduzir seus vídeos."));
    page.append(grupoOpcoes("Player", "A interface atual mantém o player nativo HLS para filmes e séries.", OPCOES_PLAYER, prefs.player, (valor) => salvarPrefs({ player: valor })));
    page.append(grupoOpcoes("Buffer", "Quanto vídeo é carregado à frente. Mais buffer reduz travamentos em rede instável.", OPCOES_BUFFER, prefs.buffer, (valor) => {
      salvarPrefs({ buffer: valor });
      window.AndroidPlayer?.setBuffer?.(valor);
    }));
    page.append(el("section", { class: "cfg-card cfg-info-card" }, [icon("play", "cfg-colored purple"), el("p", {}, "O player Android continua sendo o responsável pela reprodução HLS em tela cheia horizontal." )]));
  }

  function sair() {
    const resposta = respostaAndroid("logoutFromSettings");
    if (!resposta.success) toast(resposta.error || "Não foi possível sair da conta.", "error");
  }

  atualizarPerfis();
  raiz.append(page);
  renderHome();
  return page;
}
