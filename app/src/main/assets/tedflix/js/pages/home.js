import { el } from "../dom.js";
import { hero } from "../components/hero.js";
import { fileira } from "../components/row.js";
import {
  getCarousel,
  getUltimosFilmes,
  getLancamentos,
  getSeries,
  getGenero,
} from "../api.js";
import { CATEGORIAS } from "../config.js";

export default async function paginaInicio(raiz) {
  const page = el("div", { class: "page" });
  const topo = el("div", { class: "hero skel" });
  page.append(topo);
  raiz.append(page);

  page.append(
    fileira({ titulo: "Últimos filmes", carregar: getUltimosFilmes }),
    fileira({ titulo: "Lançamentos", verTudo: "#/filmes", carregar: getLancamentos }),
    fileira({ titulo: "Séries", verTudo: "#/series", carregar: async () => (await getSeries()).slice(0, 24) }),
  );

  CATEGORIAS.slice(0, 6).forEach((c) => {
    page.append(
      fileira({
        titulo: c.label,
        verTudo: `#/categoria/${c.slug}`,
        carregar: async () => (await getGenero(c.slug)).slice(0, 24),
      }),
    );
  });

  try {
    const destaques = await getCarousel();
    const h = hero(destaques);
    topo.replaceWith(h);
    page.destruir = () => h.destruir?.();
  } catch (e) {
    topo.classList.remove("skel");
    topo.append(el("div", { class: "center" }, "Não foi possível carregar os destaques."));
  }

  return page;
}
