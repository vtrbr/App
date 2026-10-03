import { el, limpar, debounce } from "../dom.js";
import { gradeVirtual } from "../components/virtual-grid.js";

/** Página de catálogo com filtro instantâneo + grade virtualizada. */
export function paginaCatalogo(raiz, { titulo, carregar, placeholder = "Filtrar títulos..." }) {
  const page = el("div", { class: "page" });
  const area = el("div", { class: "pad", style: "margin-top:12px" });
  const busca = el("input", { class: "input", type: "search", placeholder, "aria-label": placeholder });
  const info = el("p", { class: "sub", style: "padding:10px 16px 0" }, "Carregando catálogo...");

  page.append(
    el("h1", { class: "page-title" }, titulo),
    el("div", { class: "pad", style: "margin-top:8px" }, [busca]),
    info,
    area,
  );
  raiz.append(page);

  let todos = [];
  let grade = null;

  function render(itens) {
    if (grade) {
      grade.destruir?.();
      limpar(area);
    }
    info.textContent = `${itens.length} título${itens.length === 1 ? "" : "s"}`;
    grade = gradeVirtual(itens);
    area.append(grade);
  }

  const filtrar = debounce((q) => {
    const t = q.trim().toLowerCase();
    render(t ? todos.filter((i) => (i.titulo || "").toLowerCase().includes(t)) : todos);
  }, 250);

  busca.addEventListener("input", () => filtrar(busca.value));

  carregar()
    .then((itens) => {
      todos = itens;
      render(todos);
    })
    .catch(() => {
      info.textContent = "Não foi possível carregar o catálogo.";
    });

  page.destruir = () => grade?.destruir?.();
  return page;
}
