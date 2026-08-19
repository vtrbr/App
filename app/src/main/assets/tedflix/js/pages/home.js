import { el } from "../dom.js";
import { hero } from "../components/hero.js";
import { fileira } from "../components/row.js";
import {
  getCarousel,
  getUltimosFilmes,
  getLancamentos,
  getSeries,
  getGenero,
  getTitulo,
} from "../api.js";
import { CATEGORIAS } from "../config.js";

export default async function paginaInicio(raiz) {
  const page = el("div", { class: "page" });
  const topo = el("div", { class: "hero skel" });
  page.append(topo);
  raiz.append(page);

  const continuar = await carregarContinuarAssistindo();
  if (continuar.length) {
    page.append(fileira({ titulo: "Continuar Assistindo", carregar: async () => continuar, limite: 20 }));
  }

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

async function carregarContinuarAssistindo() {
  try {
    if (!window.AndroidPlayer?.getContinueWatching) return [];
    const itens = JSON.parse(window.AndroidPlayer.getContinueWatching() || "[]");
    if (!Array.isArray(itens)) return [];
    return (await Promise.all(itens
      .filter((item) => item && item.categoria && item.slug)
      .map(async (item) => {
        let detalhe = null;
        try { detalhe = await getTitulo(item.categoria, item.slug); } catch (_) {}
        const tipo = item.tipo || detalhe?.tipo || "Filme";
        const ehEpisodio = Boolean(item.serieSlug || item.serieCategoria || String(item.tipo || "").toLowerCase().includes("epis"));
        const detalheCategoria = item.serieCategoria || item.categoria;
        const detalheSlug = item.serieSlug || item.slug;
        return {
          ...detalhe,
          ...item,
          tipo,
          titulo: item.titulo || detalhe?.titulo || "Tedflix",
          imagem: item.thumb || item.imagem || detalhe?.imagem || "",
          link_assistir: `/titulo/${ehEpisodio ? "serie" : (tipo.toLowerCase() === "serie" ? "serie" : "filme")}/${detalheCategoria}/${detalheSlug}`,
          progresso: Number(item.percent || 0),
        };
      }))).filter(Boolean);
  } catch (e) {
    return [];
  }
}
