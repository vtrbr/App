import { el } from "../dom.js";
import { CATEGORIAS } from "../config.js";

export default function paginaCategorias(raiz) {
  const page = el("div", { class: "page" });
  const grid = el("div", {
    class: "pad",
    style: "display:grid;grid-template-columns:repeat(2,1fr);gap:12px;margin-top:12px",
  });

  const categoriasDaTela = [{ slug: "lancamentos", label: "Lançamentos" }, ...CATEGORIAS];
  categoriasDaTela.forEach((c) => {
    grid.append(
      el(
        "a",
        {
          href: `#/categoria/${c.slug}`,
          style:
            "display:grid;place-items:center;height:84px;border-radius:14px;background:var(--surface);font-family:var(--font-display);font-size:17px;text-transform:uppercase",
        },
        c.label,
      ),
    );
  });

  page.append(el("h1", { class: "page-title" }, "Categorias"), grid);
  raiz.append(page);
  return page;
}
