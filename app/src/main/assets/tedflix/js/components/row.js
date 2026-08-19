import { el, limpar, aoAparecer } from "../dom.js";
import { cardTitulo, skeletonCard } from "./card.js";
import { prefetchImagens } from "../img.js";

/**
 * Fileira horizontal preguiçosa: busca os dados quando chega perto da
 * viewport, mostra os primeiros cards imediatamente e completa o resto
 * num segundo quadro — assim a rolagem nunca engasga.
 */
export function fileira({ titulo, verTudo, carregar, limite = 20 }) {
  const rail = el("div", { class: "rail" });
  let pressionado = false;
  let arrastando = false;
  let inicioX = 0;
  let scrollInicial = 0;
  let ponteiroAtivo = null;
  let suprimirCliqueAte = 0;

  const finalizarArraste = (evento) => {
    if (ponteiroAtivo !== null && evento?.pointerId != null && evento.pointerId !== ponteiroAtivo) return;
    if (arrastando) suprimirCliqueAte = Date.now() + 420;
    pressionado = false;
    ponteiroAtivo = null;
    rail.classList.remove("arrastando");
  };

  rail.addEventListener("pointerdown", (evento) => {
    if (evento.pointerType === "mouse" && evento.button !== 0) return;
    pressionado = true;
    arrastando = false;
    ponteiroAtivo = evento.pointerId;
    inicioX = evento.clientX;
    scrollInicial = rail.scrollLeft;
    try { rail.setPointerCapture(evento.pointerId); } catch (_) {}
  });

  rail.addEventListener("pointermove", (evento) => {
    if (!pressionado || evento.pointerId !== ponteiroAtivo) return;
    const delta = evento.clientX - inicioX;
    if (!arrastando && Math.abs(delta) > 8) {
      arrastando = true;
      rail.classList.add("arrastando");
    }
    if (arrastando) {
      evento.preventDefault();
      evento.stopPropagation();
      rail.scrollLeft = scrollInicial - delta;
    }
  }, { passive: false });

  rail.addEventListener("pointerup", finalizarArraste);
  rail.addEventListener("pointercancel", finalizarArraste);
  rail.addEventListener("lostpointercapture", () => finalizarArraste());
  rail.addEventListener("click", (evento) => {
    if (Date.now() < suprimirCliqueAte) {
      evento.preventDefault();
      evento.stopImmediatePropagation();
      suprimirCliqueAte = 0;
    }
  }, true);
  for (let i = 0; i < 6; i++) rail.append(skeletonCard());

  const secao = el("section", { class: "section" }, [
    el("div", { class: "section-head" }, [
      el("h2", {}, titulo),
      verTudo ? el("a", { href: verTudo }, "Ver tudo ›") : null,
    ]),
    rail,
  ]);

  aoAparecer(
    secao,
    async () => {
      try {
        const itens = (await carregar()) || [];
        limpar(rail);
        if (!itens.length) {
          rail.append(el("p", { class: "sub" }, "Nada por aqui agora."));
          return;
        }
        const lista = itens.slice(0, limite);
        const primeiros = document.createDocumentFragment();
        lista.slice(0, 6).forEach((item, i) => primeiros.append(cardTitulo(item, { eager: i < 3 })));
        rail.append(primeiros);

        // resto fora do caminho crítico
        const agenda = window.requestIdleCallback || ((f) => setTimeout(f, 60));
        agenda(() => {
          const frag = document.createDocumentFragment();
          lista.slice(6).forEach((item) => frag.append(cardTitulo(item)));
          rail.append(frag);
          prefetchImagens(itens.slice(limite, limite + 10).map((i) => i.imagem));
        });
      } catch (e) {
        limpar(rail);
        rail.append(el("p", { class: "sub" }, "Não foi possível carregar esta fileira."));
      }
    },
    "700px",
  );

  return secao;
}
