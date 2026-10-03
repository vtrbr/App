import { el, debounce, limpar } from "../dom.js";
import { buscar } from "../api.js";
import { cardTitulo, skeletonCard } from "../components/card.js";

function chaveHistoricoBusca() {
  try {
    const dados = JSON.parse(window.AndroidPlayer?.getProfiles?.() || "{}");
    return `tedflix:search-history:${dados.activeId || "guest"}`;
  } catch (_) { return "tedflix:search-history:guest"; }
}

function lerHistoricoBusca() {
  try {
    const itens = JSON.parse(localStorage.getItem(chaveHistoricoBusca()) || "[]");
    return Array.isArray(itens) ? itens.filter(Boolean).slice(0, 10) : [];
  } catch (_) { return []; }
}

function salvarPesquisa(valor) {
  const termo = String(valor || "").trim();
  if (termo.length < 2) return;
  const itens = [termo, ...lerHistoricoBusca().filter((item) => item.toLowerCase() !== termo.toLowerCase())].slice(0, 10);
  try { localStorage.setItem(chaveHistoricoBusca(), JSON.stringify(itens)); } catch (_) {}
}

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
  const recentes = el("div", { class: "pad search-recent" });

  page.append(
    el("h1", { class: "page-title" }, "Busca"),
    el("div", { class: "pad", style: "margin-top:8px" }, [campo]),
    aviso,
    recentes,
    grid,
  );
  raiz.append(page);
  setTimeout(() => campo.focus(), 50);

  function renderRecentes() {
    recentes.innerHTML = "";
    const itens = lerHistoricoBusca();
    if (!itens.length) return;
    recentes.append(el("p", { class: "sub" }, "Pesquisas recentes"));
    itens.forEach((item) => {
      const botao = el("button", { class: "btn ghost", type: "button" }, item);
      botao.addEventListener("click", () => { campo.value = item; rodar(item); });
      recentes.append(botao);
    });
  }
  renderRecentes();

  const rodar = debounce(async (q) => {
    if (q.trim().length < 2) {
      limpar(grid);
      aviso.textContent = "Digite ao menos 2 letras.";
      renderRecentes();
      return;
    }
    salvarPesquisa(q);
    renderRecentes();
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
