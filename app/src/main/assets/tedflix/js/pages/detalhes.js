import { el, svgIcone, limpar, anoCurto } from "../dom.js";
import { getTitulo, getTemporadas, getEpisodios, parseLink } from "../api.js";
import { backdrop, poster } from "../img.js";
import { cardTitulo } from "../components/card.js";

export default async function paginaDetalhe(raiz, { tipo, categoria, slug }) {
  const page = el("div", { class: "page" });
  raiz.append(page);
  page.append(el("div", { class: "center" }, [el("div", { class: "spinner" })]));

  let dados;
  try {
    dados = await getTitulo(categoria, slug);
  } catch (e) {
    limpar(page);
    page.append(el("p", { class: "center" }, "Não foi possível carregar este título."));
    return page;
  }

  limpar(page);

  const voltar = el("button", {
    class: "back",
    "aria-label": "Voltar",
    html: `<svg viewBox="0 0 24 24">${{ back: "" }.back || ""}</svg>`,
    onclick: () => history.back(),
  });
  voltar.innerHTML = svgIcone("back");

  const capa = el("div", { class: "detail-hero" }, [
    dados.imagem
      ? el("img", { src: backdrop(dados.imagem, 780), alt: dados.titulo || "", fetchpriority: "high" })
      : null,
    el("div", { class: "veil" }),
    voltar,
  ]);

  const fatos = [
    dados.nota ? `★ ${Number(dados.nota).toFixed(1)}` : null,
    anoCurto(dados.ano),
    dados.tipo,
    dados.duracao ? `${dados.duracao} min` : null,
    ...(dados.generos || []).slice(0, 3),
  ].filter(Boolean);

  const filmeId = String(dados.filmeId || dados.id || dados._id || dados.tmdb_id || slug);
  const thumb = String(dados.imagem || dados.thumb || "");
  let favorito = lerFavorito(filmeId);
  const favoritoBtn = el("button", { class: `btn ghost favorito-btn${favorito ? " ativo" : ""}`, type: "button" }, favorito ? "♥ Favorito" : "♡ Favoritar");
  favoritoBtn.addEventListener("click", () => {
    try {
      const resposta = JSON.parse(window.AndroidPlayer?.toggleFavorite?.(filmeId, dados.titulo || "Tedflix", thumb) || "{}");
      if (!resposta.success) throw new Error(resposta.error || "Não foi possível atualizar o favorito.");
      favorito = typeof resposta.favorito === "boolean" ? resposta.favorito : !favorito;
      favoritoBtn.textContent = favorito ? "♥ Favorito" : "♡ Favoritar";
      favoritoBtn.classList.toggle("ativo", favorito);
    } catch (e) {
      favoritoBtn.textContent = e.message || "Erro ao salvar";
      setTimeout(() => { favoritoBtn.textContent = favorito ? "♥ Favorito" : "♡ Favoritar"; }, 1800);
    }
  });

  const acao = el("div", { style: "display:flex;gap:10px;margin-top:14px;flex-wrap:wrap" }, [favoritoBtn]);
  if (tipo !== "serie") {
    const params = new URLSearchParams({ filmeId, thumb, tipo: dados.tipo || tipo || "filme" });
    acao.prepend(
      el("a", {
        class: "btn primary",
        href: `#/assistir/${categoria}/${slug}?${params.toString()}`,
        html: '<svg viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>Assistir',
      }),
    );
  }

  const corpo = el("div", { class: "detail-body" }, [
    el("h1", {}, dados.titulo || ""),
    el("div", { class: "facts" }, fatos.map((f) => el("span", {}, f))),
    acao,
    dados.descricao ? el("p", { class: "desc" }, dados.descricao) : null,
  ]);

  page.append(capa, corpo);

  if (tipo === "serie") page.append(await blocoTemporadas(categoria, slug, thumb));

  if ((dados.recomendados || []).length) {
    const rail = el("div", { class: "rail" });
    dados.recomendados.slice(0, 20).forEach((r) => rail.append(cardTitulo(r)));
    page.append(
      el("section", { class: "section" }, [
        el("div", { class: "section-head" }, [el("h2", {}, "Recomendados")]),
        rail,
      ]),
    );
  }

  return page;
}

async function blocoTemporadas(categoria, slug, serieThumb = "") {
  // O mapa é por episódio (categoria:slug), não por série inteira.
  // A ponte Android retorna os registros locais já mesclados com o histórico remoto.
  const progresso = lerProgressoLocal();
  const bloco = el("section", { class: "section" });
  const chips = el("div", { class: "chips" });
  const lista = el("div", {});
  bloco.append(
    el("div", { class: "section-head" }, [el("h2", {}, "Episódios")]),
    chips,
    lista,
  );

  let temporadas = [];
  try {
    temporadas = await getTemporadas(categoria, slug);
  } catch (e) {
    bloco.append(el("p", { class: "center" }, "Temporadas indisponíveis."));
    return bloco;
  }
  if (!temporadas.length) {
    lista.append(el("p", { class: "center" }, "Nenhuma temporada encontrada."));
    return bloco;
  }

  async function carregar(num) {
    limpar(lista);
    lista.append(el("div", { class: "center" }, [el("div", { class: "spinner" })]));
    try {
      const eps = await getEpisodios(categoria, slug, num);
      limpar(lista);
      if (!eps.length) {
        lista.append(el("p", { class: "center" }, "Sem episódios nesta temporada."));
        return;
      }
      const frag = document.createDocumentFragment();
      const dadosEpisodio = (ep) => {
        const p = parseLink(ep.link || ep.link_assistir);
        const cat = ep.categoria || p.categoria || categoria;
        const sl = ep.slug || p.slug;
        if (!sl) return null;
        return {
          categoria: cat,
          slug: sl,
          filmeId: String(ep.filmeId || ep.id || ep._id || sl),
          thumb: ep.imagem || serieThumb,
          titulo: `${ep.numero ? `${ep.numero}. ` : ""}${ep.titulo || "Episódio"}`,
        };
      };
      eps.forEach((ep, index) => {
        const info = dadosEpisodio(ep);
        const fila = eps.slice(index + 1)
          .map(dadosEpisodio)
          .filter(Boolean)
          .map((item) => ({
            ...item,
            tipo: "episodio",
            serieCategoria: categoria,
            serieSlug: slug,
          }));
        const queryParams = new URLSearchParams({
          filmeId: info?.filmeId || info?.slug || "",
          thumb: info?.thumb || serieThumb,
          tipo: "episodio",
          serieCategoria: categoria,
          serieSlug: slug,
        });
        if (fila.length) queryParams.set("fila", JSON.stringify(fila));
        const query = info ? `?${queryParams.toString()}` : "";
        frag.append(
          el("a", { class: "ep", href: info ? `#/assistir/${info.categoria}/${info.slug}${query}` : "#" }, [
            el("div", { class: "cap" }, [
              ep.imagem
                ? el("img", {
                    src: poster(ep.imagem, "sm"),
                    alt: ep.titulo || "",
                    loading: "lazy",
                    decoding: "async",
                  })
                : null,
            ]),
            el("div", { style: "min-width:0" }, [
              el("p", { class: "t" }, `${ep.numero ? `${ep.numero}. ` : ""}${ep.titulo || "Episódio"}`),
              el("p", { class: "d" }, ep.duracao || ""),
              info && progresso.get(`${info.categoria}:${info.slug}`) ? el("div", { class: "ep-progress" }, [
                el("span", { style: `width:${progresso.get(`${info.categoria}:${info.slug}`)}%` }),
              ]) : null,
            ]),
          ]),
        );
      });
      lista.append(frag);
    } catch (e) {
      limpar(lista);
      lista.append(el("p", { class: "center" }, "Não foi possível carregar os episódios."));
    }
  }

  temporadas.forEach((t, i) => {
    const b = el("button", { class: `chip${i === 0 ? " on" : ""}` }, t.titulo || `T${t.numero}`);
    b.addEventListener("click", () => {
      [...chips.children].forEach((c) => c.classList.remove("on"));
      b.classList.add("on");
      carregar(t.numero);
    });
    chips.append(b);
  });

  carregar(temporadas[0].numero);
  return bloco;
}

function lerFavorito(filmeId) {
  try {
    const resposta = JSON.parse(window.AndroidPlayer?.getFavorites?.() || "{}");
    return Boolean(resposta.success && (resposta.favoritos || []).some((item) => String(item.filmeId) === String(filmeId)));
  } catch (_) { return false; }
}

function lerProgressoLocal() {
  const mapa = new Map();
  try {
    const itens = JSON.parse(window.AndroidPlayer?.getContinueWatching?.() || "[]");
    (Array.isArray(itens) ? itens : []).forEach((item) => {
      if (item?.categoria && item?.slug) mapa.set(`${item.categoria}:${item.slug}`, Number(item.percent || 0));
    });
  } catch (_) {}
  return mapa;
}
