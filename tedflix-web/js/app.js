import { el, svgIcone } from "./dom.js";
import { NAV, CATEGORIAS } from "./config.js";
import { getFilmes, getSeries, getGenero, getLancamentos, buscar, ehSerie, parseLink } from "./api.js";
import paginaInicio from "./pages/home.js";
import { paginaCatalogo } from "./pages/catalogo.js";
import paginaCategorias from "./pages/categorias.js";
import paginaAgenda from "./pages/agenda.js";
import paginaBusca from "./pages/busca.js";
import paginaDetalhe from "./pages/detalhes.js";
import paginaAssistir from "./pages/assistir.js";
import paginaConfiguracoes from "./pages/configuracoes.js";
import paginaFavoritos from "./pages/favoritos.js";
import { limparPlayer } from "./components/player.js";

const app = document.getElementById("app");
const tabbar = document.getElementById("tabbar");
let atual = null;

NAV.forEach((n) => {
  const a = el("a", { href: n.href, "data-href": n.href });
  a.innerHTML = `${svgIcone(n.icon)}<span>${n.label}</span>`;
  tabbar.append(a);
});

function marcarNav(rota) {
  const semQuery = rota.split("?")[0];
  const base = "#/" + (semQuery.split("/")[1] || "");
  [...tabbar.children].forEach((a) => a.classList.toggle("active", a.dataset.href === (base === "#/" ? "#/" : base)));
}

function resolver(hash) {
  const rota = (hash || "#/").replace(/^#/, "") || "/";
  const [caminho, queryString = ""] = rota.split("?");
  const p = caminho.split("/").filter(Boolean);
  if (!p.length) return (r) => paginaInicio(r);
  if (p[0] === "filmes") return (r) => paginaCatalogo(r, { titulo: "Filmes", carregar: getFilmes });
  if (p[0] === "lancamentos") return (r) => paginaCatalogo(r, { titulo: "Lançamentos", carregar: getLancamentos });
  if (p[0] === "series") return (r) => paginaCatalogo(r, { titulo: "Séries", carregar: getSeries });
  if (p[0] === "categorias") return (r) => paginaCategorias(r);
  if (p[0] === "categoria" && p[1]) {
    if (p[1] === "lancamentos") return (r) => paginaCatalogo(r, { titulo: "Lançamentos", carregar: getLancamentos });
    const cat = CATEGORIAS.find((c) => c.slug === p[1]);
    return (r) => paginaCatalogo(r, { titulo: cat ? cat.label : p[1], carregar: () => getGenero(p[1]) });
  }
  if (p[0] === "agenda") return (r) => paginaAgenda(r);
  if (p[0] === "busca") return (r) => paginaBusca(r);
  if (p[0] === "favoritos") return (r) => paginaFavoritos(r);
  if (p[0] === "favorito") {
    const params = new URLSearchParams(queryString); const titulo = params.get("titulo") || "";
    return async (r) => {
      if (!titulo) { r.append(el("p", { class: "center" }, "Favorito sem título disponível.")); return null; }
      try {
        const resposta = await buscar(titulo); const itens = resposta.resultados || [];
        const item = itens.find((i) => (i.titulo || "").trim().toLowerCase() === titulo.trim().toLowerCase()) || itens[0];
        if (!item) { r.append(el("p", { class: "center" }, "Não foi possível localizar este título no catálogo.")); return null; }
        const link = parseLink(item.link_assistir); const categoria = item.categoria || link.categoria; const slug = item.slug || link.slug;
        return paginaDetalhe(r, { tipo: ehSerie(item) ? "serie" : "filme", categoria, slug });
      } catch (_) { r.append(el("p", { class: "center" }, "Não foi possível abrir este favorito agora.")); return null; }
    };
  }
  if (p[0] === "titulo" && p.length >= 4) return (r) => paginaDetalhe(r, { tipo: p[1], categoria: p[2], slug: p[3] });
  if (p[0] === "assistir" && p.length >= 3) {
    const params = new URLSearchParams(queryString);
    const lerLista = (nome) => { try { const valor = params.get(nome); const lista = valor ? JSON.parse(valor) : []; return Array.isArray(lista) ? lista : []; } catch (_) { return []; } };
    return (r) => paginaAssistir(r, { categoria: p[1], slug: p[2], fila: lerLista("fila"), filmeId: params.get("filmeId") || "", thumb: params.get("thumb") || "", tipo: params.get("tipo") || "", serieCategoria: params.get("serieCategoria") || "", serieSlug: params.get("serieSlug") || "", episodios: lerLista("episodios"), recomendados: lerLista("recomendados") });
  }
  if (p[0] === "config") return (r) => paginaConfiguracoes(r);
  return (r) => r.append(el("p", { class: "center" }, "Página não encontrada."));
}

async function navegar() {
  const rota = location.hash || "#/";
  limparPlayer();
  atual?.destruir?.();
  app.innerHTML = "";
  window.scrollTo(0, 0);
  marcarNav(rota.replace(/^#/, ""));
  document.title = tituloDaRota(rota);
  atual = (await resolver(rota)(app)) || null;
}

function tituloDaRota(hash) {
  const p = hash.replace(/^#\//, "").split("?")[0].split("/").filter(Boolean);
  if (!p.length) return "TEDFLIX — Filmes e séries online";
  const mapa = { filmes: "Filmes", series: "Séries", categorias: "Categorias", agenda: "Agenda de episódios", busca: "Buscar", favoritos: "Favoritos", favorito: "Favorito", titulo: "Detalhes", assistir: "Assistir", config: "Configurações" };
  return `${mapa[p[0]] || "TEDFLIX"} — TEDFLIX`;
}

window.addEventListener("hashchange", navegar);
export { navegar };
window.__tedflixStartApp = navegar;
if (!window.__TEDFLIX_BROWSER_GATE__) navegar();
