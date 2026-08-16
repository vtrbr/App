import { el } from "../dom.js";
import { getManifesto } from "../api.js";
import { montarPlayer, limparPlayer } from "../components/player.js";

/** Entra em tela cheia horizontal (cinema) — precisa de gesto do usuário. */
async function modoCinema(alvo) {
  try {
    if (!document.fullscreenElement) {
      await (alvo.requestFullscreen?.() || alvo.webkitRequestFullscreen?.());
    } else {
      await document.exitFullscreen();
      return;
    }
  } catch (e) {
    /* navegador pode recusar */
  }
  try {
    await screen.orientation?.lock?.("landscape");
  } catch (e) {
    /* desktop / iOS não permitem */
  }
}

export default async function paginaAssistir(raiz, { categoria, slug, fila = [] }) {
  document.body.classList.add("imersivo");

  const page = el("div", { class: "page assistir" });
  const palco = el("div", { class: "cinema" });
  const caixa = el("div", { class: "player-wrap" });

  palco.append(caixa);
  page.append(palco);
  raiz.append(page);

  const carregando = el("div", { class: "center" }, [
    el("div", { class: "spinner" }),
    el("p", {}, "Preparando transmissão..."),
  ]);
  caixa.append(carregando);

  const voltar = () => history.back();
  let instancia = null;

  // Registra a limpeza antes do handoff nativo. Sem isso, o retorno do
  // PlayerActivity deixava a página Android sem o hook de destruição.
  page.destruir = () => {
    document.body.classList.remove("imersivo");
    try {
      screen.orientation?.unlock?.();
    } catch (err) {}
    instancia?.destruir?.();
    limparPlayer();
  };

  try {
    const nome = decodeURIComponent(slug).replace(/^\d+-/, "").replace(/-/g, " ");

    // No Android, a interface permanece igual, mas a reprodução é delegada
    // ao player nativo para suportar HLS e tela cheia horizontal.
    if (window.AndroidPlayer && typeof window.AndroidPlayer.openPlayer === "function") {
      window.AndroidPlayer.openPlayer(categoria, slug, nome, JSON.stringify(Array.isArray(fila) ? fila : []));
      return page;
    }

    const m3u8 = await getManifesto(categoria, slug);
    caixa.innerHTML = "";
    instancia = montarPlayer(caixa, {
      m3u8,
      titulo: nome,
      aoStatus: () => {}, // o player agora mostra seu próprio overlay de status/erro
      aoVoltar: voltar,
      aoGirar: () => modoCinema(palco),
    });
    // primeiro toque na tela já joga em tela cheia horizontal (mobile)
    caixa.addEventListener("click", function once(ev) {
      if (ev.target.closest(".tp-back, .tp-btn, .tp-progresso, .tp-menu-btn")) return;
      caixa.removeEventListener("click", once);
      if (window.matchMedia("(max-width: 900px)").matches) modoCinema(palco);
    });
  } catch (e) {
    caixa.innerHTML = "";
    const retry = el("button", { class: "btn primary" }, "Tentar novamente");
    retry.addEventListener("click", () => {
      raiz.innerHTML = "";
      paginaAssistir(raiz, { categoria, slug, fila });
    });
    caixa.append(
      el("div", { class: "center" }, [
        el("p", {}, e.message || "Não foi possível carregar o vídeo."),
        retry,
      ]),
    );
  }

  return page;
}
