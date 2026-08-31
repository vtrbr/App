import { el, limpar, aoAparecer } from "../dom.js";
import { cardTitulo, skeletonCard } from "./card.js";
import { prefetchImagens } from "../img.js";

/**
 * Fileira horizontal preguiçosa. A rolagem fica a cargo do overflow-x nativo
 * do WebView, que é mais confiável no toque do Android do que capturar
 * ponteiros manualmente.
 */
export function fileira({ titulo, verTudo, carregar, limite = 20, emptyText = "Nada por aqui agora." }) {
  const rail = el("div", { class: "rail", role: "region", "aria-label": titulo });
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
          rail.append(el("p", { class: "sub continue-empty" }, emptyText));
          return;
        }
        const lista = itens.slice(0, limite);
        const primeiros = document.createDocumentFragment();
        lista.slice(0, 6).forEach((item, i) => primeiros.append(cardTitulo(item, { eager: i < 3 })));
        rail.append(primeiros);

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
