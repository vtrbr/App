import { el, limpar } from "../dom.js";
import { cardTitulo } from "./card.js";

/**
 * Grade virtualizada: mesmo com milhares de títulos, só ficam no DOM as
 * linhas próximas da área visível. As demais são removidas, então o
 * navegador nunca segura milhares de <img>.
 */
export function gradeVirtual(itens, { min = 104, gapX = 10, gapY = 14, buffer = 3 } = {}) {
  const inner = el("div", { class: "vgrid-inner" });
  const raiz = el("div", { class: "vgrid" }, [inner]);

  let colunas = 2;
  let larguraCol = 0;
  let alturaLinha = 0;
  let primeira = -1;
  let ultima = -1;
  let ticking = false;

  function medir() {
    const largura = raiz.clientWidth || window.innerWidth - 32;
    colunas = Math.max(2, Math.floor((largura + gapX) / (min + gapX)));
    larguraCol = (largura - gapX * (colunas - 1)) / colunas;
    alturaLinha = larguraCol * 1.5 + 42 + gapY;
    const linhas = Math.ceil(itens.length / colunas);
    raiz.style.height = `${Math.max(0, linhas * alturaLinha - gapY)}px`;
    primeira = ultima = -1;
  }

  function desenhar() {
    if (!alturaLinha) return;
    const topoRaiz = raiz.getBoundingClientRect().top + window.scrollY;
    const inicio = Math.max(0, Math.floor((window.scrollY - topoRaiz) / alturaLinha) - buffer);
    const visiveis = Math.ceil(window.innerHeight / alturaLinha) + buffer * 2;
    const fim = Math.min(Math.ceil(itens.length / colunas), inicio + visiveis);
    if (inicio === primeira && fim === ultima) return;
    primeira = inicio;
    ultima = fim;

    limpar(inner);
    inner.style.transform = `translateY(${inicio * alturaLinha}px)`;
    const frag = document.createDocumentFragment();
    for (let linha = inicio; linha < fim; linha++) {
      const faixa = el("div", { class: "vgrid-row" });
      faixa.style.cssText = `display:grid;grid-template-columns:repeat(${colunas},1fr);gap:${gapY}px ${gapX}px;margin-bottom:${gapY}px`;
      for (let c = 0; c < colunas; c++) {
        const item = itens[linha * colunas + c];
        if (item) faixa.append(cardTitulo(item, { size: colunas > 4 ? "sm" : "md" }));
      }
      frag.append(faixa);
    }
    inner.append(frag);
  }

  function aoRolar() {
    if (ticking) return;
    ticking = true;
    requestAnimationFrame(() => {
      ticking = false;
      desenhar();
    });
  }

  function aoRedimensionar() {
    medir();
    desenhar();
  }

  requestAnimationFrame(() => {
    medir();
    desenhar();
  });

  window.addEventListener("scroll", aoRolar, { passive: true });
  window.addEventListener("resize", aoRedimensionar);

  raiz.destruir = () => {
    window.removeEventListener("scroll", aoRolar);
    window.removeEventListener("resize", aoRedimensionar);
  };

  return raiz;
}
