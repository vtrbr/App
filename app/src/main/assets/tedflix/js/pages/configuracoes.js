import { el } from "../dom.js";
import { lerPrefs, salvarPrefs, OPCOES_BUFFER } from "../prefs.js";
import { medir, lerCache, nivel, ROTULO } from "../net.js";

function grupo(titulo, sub, opcoes, valorAtual, aoEscolher) {
  const lista = el("div", { class: "opcoes" });
  opcoes.forEach((o) => {
    const item = el("button", {
      class: `opcao${o.valor === valorAtual ? " sel" : ""}`,
      type: "button",
      onclick: () => {
        [...lista.children].forEach((c) => c.classList.remove("sel"));
        item.classList.add("sel");
        aoEscolher(o.valor);
      },
    }, [
      el("span", { class: "marca" }),
      el("span", {}, [el("strong", {}, o.titulo), el("small", {}, o.desc)]),
    ]);
    lista.append(item);
  });

  return el("section", { class: "cfg-bloco" }, [
    el("h2", {}, titulo),
    sub ? el("p", { class: "sub" }, sub) : null,
    lista,
  ]);
}

function respostaAndroid(nome, ...args) {
  try {
    const fn = window.AndroidPlayer?.[nome];
    return typeof fn === "function" ? JSON.parse(fn(...args) || "{}") : { success: false, error: "Função indisponível." };
  } catch (e) {
    return { success: false, error: e?.message || "Não foi possível concluir a operação." };
  }
}

export default async function paginaConfiguracoes(raiz) {
  const prefs = lerPrefs();
  const page = el("div", { class: "page cfg" });

  const valor = el("strong", { class: "vel-num" }, "medindo...");
  const rotulo = el("span", { class: "vel-tag" }, "");
  const detalhe = el("p", { class: "sub" }, "Testando a sua conexão agora.");
  const botao = el("button", { class: "btn ghost", type: "button" }, "Medir de novo");

  function pintar(d) {
    const n = nivel(d?.mbps);
    valor.textContent = d?.mbps ? `${d.mbps.toFixed(2)} Mbps` : "indisponível";
    rotulo.textContent = ROTULO[n];
    rotulo.className = `vel-tag ${n}`;
    detalhe.textContent = n === "lenta"
      ? "Conexão fraca: as capas vêm menores e o player começa em qualidade baixa."
      : n === "media"
        ? "Dá para assistir bem em qualidade média."
        : n === "desconhecida"
          ? "Não deu para medir agora."
          : "Conexão boa: qualidade alta liberada.";
  }
  botao.addEventListener("click", async () => {
    valor.textContent = "medindo...";
    pintar(await medir({ forcar: true }));
  });

  const blocoRede = el("section", { class: "cfg-bloco rede" }, [
    el("h2", {}, "Sua velocidade de rede é:"),
    el("div", { class: "vel" }, [valor, rotulo]),
    detalhe,
    botao,
  ]);

  const nomePerfil = window.AndroidPlayer?.getProfileName?.() || "Meu perfil";
  const statusConta = el("div", { class: "cfg-status", role: "status" }, "Carregando status da conta...");
  const nomeInput = el("input", { class: "input", type: "text", value: nomePerfil, maxlength: "40", autocomplete: "nickname" });
  const senhaAtual = el("input", { class: "input", type: "password", placeholder: "Senha atual", autocomplete: "current-password" });
  const senhaNova = el("input", { class: "input", type: "password", placeholder: "Nova senha", autocomplete: "new-password" });
  const contaAviso = el("p", { class: "cfg-feedback sub" }, "");

  const salvarNome = el("button", { class: "btn primary", type: "button" }, "Salvar nome");
  salvarNome.addEventListener("click", () => {
    salvarNome.disabled = true;
    const resposta = respostaAndroid("updateProfileName", nomeInput.value);
    salvarNome.disabled = false;
    contaAviso.textContent = resposta.success ? "Nome atualizado." : (resposta.error || "Não foi possível salvar o nome.");
    if (resposta.success) nomeInput.value = nomeInput.value.trim();
  });

  const alterarSenha = el("button", { class: "btn primary", type: "button" }, "Alterar senha");
  alterarSenha.addEventListener("click", () => {
    alterarSenha.disabled = true;
    const resposta = respostaAndroid("changeProfilePassword", senhaAtual.value, senhaNova.value);
    alterarSenha.disabled = false;
    contaAviso.textContent = resposta.success ? "Senha alterada com sucesso." : (resposta.error || "Não foi possível alterar a senha.");
    if (resposta.success) { senhaAtual.value = ""; senhaNova.value = ""; }
  });

  const blocoConta = el("section", { class: "cfg-bloco cfg-conta" }, [
    el("div", { class: "cfg-card-heading" }, [
      el("div", { class: "cfg-avatar" }, "T"),
      el("div", {}, [el("h2", {}, "Minha conta"), el("p", { class: "sub" }, "Tudo do seu perfil em um só lugar.")]),
    ]),
    statusConta,
    el("div", { class: "cfg-subsection" }, [
      el("h3", {}, "Perfil"),
      nomeInput,
      salvarNome,
    ]),
    el("div", { class: "cfg-subsection" }, [
      el("h3", {}, "Senha"),
      senhaAtual,
      senhaNova,
      alterarSenha,
    ]),
    el("div", { class: "cfg-actions" }, [
      el("button", { class: "btn ghost", type: "button", onclick: () => window.AndroidPlayer?.openNotifications?.() }, "Notificações"),
      el("button", { class: "btn ghost danger", type: "button", onclick: () => {
        const resposta = respostaAndroid("logoutFromSettings");
        if (!resposta.success) contaAviso.textContent = resposta.error || "Não foi possível sair.";
      } }, "Sair da conta"),
    ]),
    contaAviso,
  ]);

  const blocoPerfil = el("section", { class: "cfg-bloco cfg-perfil" }, [
    el("div", { class: "cfg-avatar" }, "T"),
    el("div", {}, [el("h2", {}, nomePerfil), el("p", { class: "sub" }, "Perfil único do Tedflix")]),
  ]);

  const blocoFavoritos = el("section", { class: "cfg-bloco" }, [
    el("h2", {}, "Favoritos"),
    el("p", { class: "sub" }, "Acesse filmes e séries salvos. A abertura respeita a rota normal do catálogo."),
    el("button", { class: "btn ghost", type: "button", onclick: () => window.AndroidPlayer?.openFavorites?.() }, "Abrir favoritos"),
  ]);

  const blocoFontes = el("section", { class: "cfg-bloco" }, [
    el("h2", {}, "Fontes de conteúdo"),
    el("p", { class: "sub" }, "Escolha o servidor principal ou uma fonte alternativa."),
    el("button", { class: "btn ghost", type: "button", onclick: () => window.AndroidPlayer?.openSources?.() }, "Gerenciar fontes"),
  ]);

  page.append(
    el("h1", { class: "cfg-titulo" }, "Configurações"),
    blocoPerfil,
    blocoConta,
    blocoFavoritos,
    blocoRede,
    grupo("Buffer", "Quanto vídeo é carregado à frente. Mais buffer = menos travadas em rede instável.", OPCOES_BUFFER, prefs.buffer, (v) => {
      salvarPrefs({ buffer: v });
      window.AndroidPlayer?.setBuffer?.(v);
    }),
    blocoFontes,
  );

  raiz.append(page);

  try {
    const bruto = window.AndroidPlayer?.getAccountStatus?.() || "{}";
    const resposta = JSON.parse(bruto);
    const status = resposta.status || {};
    statusConta.textContent = resposta.success
      ? `Status: ${status.status || status.accountStatus || "ativo"} · Validade: ${status.validade || status.expiresAt || status.expiraEm || "não informada"} · Dias restantes: ${status.diasRestantes ?? status.daysRemaining ?? "—"}`
      : (resposta.error || "Status indisponível.");
  } catch (_) {
    statusConta.textContent = "Status indisponível no momento.";
  }

  const cache = lerCache();
  if (cache && !cache.expirado) pintar(cache);
  medir().then(pintar).catch(() => pintar(null));
  return page;
}
