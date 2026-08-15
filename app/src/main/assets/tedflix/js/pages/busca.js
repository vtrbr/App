import { el, debounce, limpar } from "../dom.js";
import { buscar } from "../api.js";
import { cardTitulo, skeletonCard } from "../components/card.js";

export default function paginaBusca(raiz) {
  const page = el("div", { class: "page" });
  const campo = el("input", {
    class: "input",
    type: "search",
    placeholder: "Buscar filmes e séries...",
    "aria-label": "Buscar",
  });
  const grid = el("div", { class: "grid", style: "margin-top:16px" });
  const aviso = el("p", { class: "sub", style: "padding:16px" }, "Digite para buscar.");

  page.append(
    el("h1", { class: "page-title" }, "Busca"),
    el("div", { class: "pad", style: "margin-top:8px" }, [campo]),
    aviso,
    grid,
  );
  raiz.append(page);
  setTimeout(() => campo.focus(), 50);

  const rodar = debounce(async (q) => {
    if (q.trim().length < 2) {
      limpar(grid);
      aviso.textContent = "Digite ao menos 2 letras.";
      return;
    }
    aviso.textContent = "";
    limpar(grid);
    for (let i = 0; i < 6; i++) grid.append(skeletonCard());
    try {
      const { resultados } = await buscar(q.trim());
      limpar(grid);
      if (!resultados.length) {
        aviso.textContent = "Nenhum resultado encontrado.";
        return;
      }
      const frag = document.createDocumentFragment();
      resultados.forEach((r) => frag.append(cardTitulo(r, { size: "md" })));
      grid.append(frag);
    } catch (e) {
      limpar(grid);
      aviso.textContent = "Falha na busca. Tente novamente.";
    }
  }, 400);

  campo.addEventListener("input", () => rodar(campo.value));
  return page;
}
