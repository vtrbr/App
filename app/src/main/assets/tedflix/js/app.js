import { el, svgIcone } from "./dom.js";
import { NAV, CATEGORIAS } from "./config.js";
import { getFilmes, getSeries, getGenero, getLancamentos, buscar, ehSerie, parseLink } from "./api.js";
import paginaInicio from "./pages/home.js?v=20260831-cw2";
import { paginaCatalogo } from "./pages/catalogo.js";
import paginaCategorias from "./pages/categorias.js";
import paginaAgenda from "./pages/agenda.js";
import paginaBusca from "./pages/busca.js";
import paginaDetalhe from "./pages/detalhes.js";
import paginaAssistir from "./pages/assistir.js";
import paginaConfiguracoes from "./pages/configuracoes.js";
import paginaCanais, { paginaCanal, paginaEvento } from "./pages/canais.js";
import { limparPlayer } from "./components/player.js";
import { medirOcioso } from "./net.js";


const app = document.getElementById("app");
const tabbar = document.getElementById("tabbar");
let atual = null;

/* ---------- navegação inferior ---------- */
NAV.forEach((n) => {
  const a = el("a", { href: n.href, "data-href": n.href });
  a.innerHTML = `${svgIcone(n.icon)}<span>${n.label}</span>`;
  tabbar.append(a);
});

function marcarNav(rota) {
  const semQuery = rota.split("?")[0];
  const base = "#/" + (semQuery.split("/")[1] || "");
  [...tabbar.children].forEach((a) =>
    a.classList.toggle("active", a.dataset.href === (base === "#/" ? "#/" : base)),
  );
}

/* ---------- rotas ---------- */
function resolver(hash) {
  const rota = (hash || "#/").replace(/^#/, "") || "/";
  const [caminho, queryString = ""] = rota.split("?");
  const p = caminho.split("/").filter(Boolean);

  if (!p.length) return (r) => paginaInicio(r);
  if (p[0] === "filmes")
    return (r) => paginaCatalogo(r, { titulo: "Filmes", carregar: getFilmes });
  if (p[0] === "lancamentos")
    return (r) => paginaCatalogo(r, { titulo: "Lançamentos", carregar: getLancamentos });
  if (p[0] === "series")
    return (r) => paginaCatalogo(r, { titulo: "Séries", carregar: getSeries });
  if (p[0] === "categorias") return (r) => paginaCategorias(r);
  if (p[0] === "categoria" && p[1]) {
    if (p[1] === "lancamentos") return (r) => paginaCatalogo(r, { titulo: "Lançamentos", carregar: getLancamentos });
    const cat = CATEGORIAS.find((c) => c.slug === p[1]);
    return (r) =>
      paginaCatalogo(r, { titulo: cat ? cat.label : p[1], carregar: () => getGenero(p[1]) });
  }
  if (p[0] === "agenda") return (r) => paginaAgenda(r);
  if (p[0] === "canais") {
    const params = new URLSearchParams(queryString);
    return (r) => paginaCanais(r, { categoria: params.get("categoria") || "" });
  }
  if (p[0] === "canal" && p[1]) return (r) => paginaCanal(r, { id: decodeURIComponent(p[1]) });
  if (p[0] === "evento" && p[1]) return (r) => paginaEvento(r, { id: decodeURIComponent(p[1]) });
  if (p[0] === "busca") return (r) => paginaBusca(r);
  if (p[0] === "favorito") {
    const params = new URLSearchParams(queryString);
    const titulo = params.get("titulo") || "";
    return async (r) => {
      if (!titulo) {
        r.append(el("p", { class: "center" }, "Favorito sem título disponível."));
        return null;
      }
      try {
        const resposta = await buscar(titulo);
        const itens = resposta.resultados || [];
        const normalizado = (titulo || "").trim().toLowerCase();
        const item = itens.find((i) => (i.titulo || "").trim().toLowerCase() === normalizado) || itens[0];
        if (!item) {
          r.append(el("p", { class: "center" }, "Não foi possível localizar este título no catálogo."));
          return null;
        }
        const link = parseLink(item.link_assistir);
        const categoria = item.categoria || link.categoria;
        const slug = item.slug || link.slug;
        if (!categoria || !slug) {
          r.append(el("p", { class: "center" }, "Este favorito não possui uma rota de detalhe válida."));
          return null;
        }
        return paginaDetalhe(r, { tipo: ehSerie(item) ? "serie" : "filme", categoria, slug });
      } catch (error) {
        r.append(el("p", { class: "center" }, "Não foi possível abrir este favorito agora."));
        return null;
      }
    };
  }
  if (p[0] === "titulo" && p.length >= 4)
    return (r) => paginaDetalhe(r, { tipo: p[1], categoria: p[2], slug: p[3] });
  if (p[0] === "assistir" && p.length >= 3) {
    const params = new URLSearchParams(queryString);
    const lerLista = (nome) => {
      try {
        const valor = params.get(nome);
        const lista = valor ? JSON.parse(valor) : [];
        return Array.isArray(lista) ? lista : [];
      } catch (_) { return []; }
    };
    const fila = lerLista("fila");
    return (r) => paginaAssistir(r, {
      categoria: p[1],
      slug: p[2],
      fila,
      filmeId: params.get("filmeId") || "",
      thumb: params.get("thumb") || "",
      tipo: params.get("tipo") || "",
      serieCategoria: params.get("serieCategoria") || "",
      serieSlug: params.get("serieSlug") || "",
      episodios: lerLista("episodios"),
      recomendados: lerLista("recomendados"),
    });
  }
  if (p[0] === "config") return (r) => paginaConfiguracoes(r);


  return (r) => {
    r.append(el("p", { class: "center" }, "Página não encontrada."));
    return null;
  };
}

async function navegar() {
  const rota = location.hash || "#/";
  limparPlayer();
  if (atual && atual.destruir) atual.destruir();
  app.innerHTML = "";
  window.scrollTo(0, 0);
  marcarNav(rota.replace(/^#/, ""));
  document.title = tituloDaRota(rota);
  atual = (await resolver(rota)(app)) || null;
}

function tituloDaRota(hash) {
  const caminho = hash.replace(/^#\//, "").split("?")[0];
  const p = caminho.split("/").filter(Boolean);
  if (!p.length) return "TEDFLIX — Filmes e séries online";
  const mapa = {
    filmes: "Filmes",
    series: "Séries",
    categorias: "Categorias",
    agenda: "Agenda de episódios",
    canais: "Canais ao vivo",
    canal: "Canal ao vivo",
    evento: "Evento ao vivo",
    busca: "Buscar",
    favorito: "Favorito",
    titulo: "Detalhes",
    assistir: "Assistir",
  };
  return `${mapa[p[0]] || "TEDFLIX"} — TEDFLIX`;
}

window.addEventListener("hashchange", navegar);
navegar();
