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

export default async function paginaConfiguracoes(raiz) {
  const prefs = lerPrefs();
  const page = el("div", { class: "page cfg" });

  /* ---- velocidade de rede ---- */
  const valor = el("strong", { class: "vel-num" }, "medindo...");
  const rotulo = el("span", { class: "vel-tag" }, "");
  const detalhe = el("p", { class: "sub" }, "Testando a sua conexão agora.");

  const botao = el("button", { class: "btn ghost", type: "button" }, "Medir de novo");

  function pintar(d) {
    const n = nivel(d?.mbps);
    valor.textContent = d?.mbps ? `${d.mbps.toFixed(2)} Mbps` : "indisponível";
    rotulo.textContent = ROTULO[n];
    rotulo.className = `vel-tag ${n}`;
    detalhe.textContent =
      n === "lenta"
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
  const blocoPerfil = el("section", { class: "cfg-bloco cfg-perfil" }, [
    el("div", { class: "cfg-avatar" }, "T"),
    el("div", {}, [
      el("h2", {}, nomePerfil),
      el("p", { class: "sub" }, "Perfil único do Tedflix"),
    ]),
  ]);

  const blocoConta = el("section", { class: "cfg-bloco" }, [
    el("h2", {}, "Sua conta"),
    el("p", { class: "sub" }, "Gerencie seu perfil, validade, senha e notificações."),
    el("button", {
      class: "btn ghost",
      type: "button",
      onclick: () => {
        if (window.AndroidPlayer && typeof window.AndroidPlayer.openAccount === "function") {
          window.AndroidPlayer.openAccount();
        } else {
          alert("A configuração da conta está disponível no aplicativo Android.");
        }
      },
    }, "Abrir minha conta"),
  ]);

  const blocoFavoritos = el("section", { class: "cfg-bloco" }, [
    el("h2", {}, "Favoritos"),
    el("p", { class: "sub" }, "Acesse os filmes e séries que você salvou para assistir depois."),
    el("button", {
      class: "btn ghost",
      type: "button",
      onclick: () => {
        if (window.AndroidPlayer && typeof window.AndroidPlayer.openFavorites === "function") window.AndroidPlayer.openFavorites();
        else alert("Os favoritos estão disponíveis no aplicativo Android.");
      },
    }, "Abrir favoritos"),
  ]);

  const blocoFontes = el("section", { class: "cfg-bloco" }, [
    el("h2", {}, "Fontes de conteúdo"),
    el("p", { class: "sub" }, "Escolha o servidor principal, uma fonte alternativa ou consulte todas as fontes cadastradas."),
    el("button", {
      class: "btn ghost",
      type: "button",
      onclick: () => {
        if (window.AndroidPlayer && typeof window.AndroidPlayer.openSources === "function") {
          window.AndroidPlayer.openSources();
        } else {
          alert("O gerenciamento de fontes está disponível no aplicativo Android.");
        }
      },
    }, "Gerenciar fontes"),
  ]);

  page.append(
    el("h1", { class: "cfg-titulo" }, "Configurações"),
    blocoPerfil,
    blocoConta,
    blocoRede,
    blocoFavoritos,
    grupo(
      "Buffer",
      "Quanto vídeo é carregado à frente. Mais buffer = menos travadas em rede instável.",
      OPCOES_BUFFER,
      prefs.buffer,
      (v) => {
        salvarPrefs({ buffer: v });
        window.AndroidPlayer?.setBuffer?.(v);
      },
    ),
    blocoFontes,
  );

  raiz.append(page);

  const cache = lerCache();
  if (cache && !cache.expirado) pintar(cache);
  medir().then(pintar).catch(() => pintar(null));

  return page;
}
