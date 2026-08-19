import { el, svgIcone } from "./dom.js";
import { NAV, CATEGORIAS } from "./config.js";
import { getFilmes, getSeries, getGenero } from "./api.js";
import paginaInicio from "./pages/home.js";
import { paginaCatalogo } from "./pages/catalogo.js";
import paginaCategorias from "./pages/categorias.js";
import paginaAgenda from "./pages/agenda.js";
import paginaBusca from "./pages/busca.js";
import paginaDetalhe from "./pages/detalhes.js";
import paginaAssistir from "./pages/assistir.js";
import paginaConfiguracoes from "./pages/configuracoes.js";
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
  const base = "#/" + (rota.split("/")[1] || "");
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
  if (p[0] === "series")
    return (r) => paginaCatalogo(r, { titulo: "Séries", carregar: getSeries });
  if (p[0] === "categorias") return (r) => paginaCategorias(r);
  if (p[0] === "categoria" && p[1]) {
    const cat = CATEGORIAS.find((c) => c.slug === p[1]);
    return (r) =>
      paginaCatalogo(r, { titulo: cat ? cat.label : p[1], carregar: () => getGenero(p[1]) });
  }
  if (p[0] === "agenda") return (r) => paginaAgenda(r);
  if (p[0] === "busca") return (r) => paginaBusca(r);
  if (p[0] === "titulo" && p.length >= 4)
    return (r) => paginaDetalhe(r, { tipo: p[1], categoria: p[2], slug: p[3] });
  if (p[0] === "assistir" && p.length >= 3) {
    const params = new URLSearchParams(queryString);
    let fila = [];
    try {
      const valor = params.get("fila");
      fila = valor ? JSON.parse(valor) : [];
      if (!Array.isArray(fila)) fila = [];
    } catch (e) {
      fila = [];
    }
    return (r) => paginaAssistir(r, {
      categoria: p[1],
      slug: p[2],
      fila,
      filmeId: params.get("filmeId") || "",
      thumb: params.get("thumb") || "",
      tipo: params.get("tipo") || "",
      serieCategoria: params.get("serieCategoria") || "",
      serieSlug: params.get("serieSlug") || "",
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
  const p = hash.replace(/^#\//, "").split("/").filter(Boolean);
  if (!p.length) return "TEDFLIX — Filmes e séries online";
  const mapa = {
    filmes: "Filmes",
    series: "Séries",
    categorias: "Categorias",
    agenda: "Agenda de episódios",
    busca: "Buscar",
    titulo: "Detalhes",
    assistir: "Assistir",
  };
  return `${mapa[p[0]] || "TEDFLIX"} — TEDFLIX`;
}

window.addEventListener("hashchange", navegar);
navegar();
