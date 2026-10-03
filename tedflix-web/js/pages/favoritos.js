import { el } from "../dom.js";
import { buscar, ehSerie, normalizar, parseLink } from "../api.js";
import { cardTitulo } from "../components/card.js";

export default async function paginaFavoritos(raiz) {
  const page = el("div", { class: "page" });
  page.append(el("h1", { class: "page-title" }, "Favoritos"));
  const grid = el("div", { class: "grid" });
  page.append(grid); raiz.append(page);
  try {
    const resposta = JSON.parse(window.AndroidPlayer?.getFavorites?.() || "{}");
    const favoritos = resposta.favorites || resposta.favoritos || [];
    if (!favoritos.length) { grid.append(el("p", { class: "center" }, "Você ainda não salvou nenhum título.")); return page; }
    const itens = await Promise.all(favoritos.map(async (favorito) => {
      const titulo = favorito.title || favorito.titulo || "";
      let item = { ...favorito, titulo, imagem: favorito.thumb || favorito.imagem, tipo: favorito.contentType || favorito.tipo || "Filme" };
      try {
        const r = await buscar(titulo); const candidatos = r.resultados || [];
        item = candidatos.find((x) => (x.titulo || "").toLowerCase() === titulo.toLowerCase()) || candidatos[0] || item;
      } catch (_) {}
      const link = parseLink(item.link_assistir);
      return normalizar({ ...item, categoria: item.categoria || link.categoria, slug: item.slug || link.slug }, item.tipo);
    }));
    itens.filter(Boolean).forEach((item) => grid.append(cardTitulo(item, { eager: true })));
  } catch (_) { grid.append(el("p", { class: "center" }, "Não foi possível carregar seus favoritos agora.")); }
  return page;
}
