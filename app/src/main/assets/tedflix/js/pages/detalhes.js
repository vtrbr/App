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

  const acao = el("div", { style: "display:flex;gap:10px;margin-top:14px;flex-wrap:wrap" });
  if (tipo !== "serie") {
    acao.append(
      el("a", {
        class: "btn primary",
        href: `#/assistir/${categoria}/${slug}`,
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

  if (tipo === "serie") page.append(await blocoTemporadas(categoria, slug));

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

async function blocoTemporadas(categoria, slug) {
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
      eps.forEach((ep) => {
        const p = parseLink(ep.link || ep.link_assistir);
        const cat = ep.categoria || p.categoria || categoria;
        const sl = ep.slug || p.slug;
        frag.append(
          el("a", { class: "ep", href: sl ? `#/assistir/${cat}/${sl}` : "#" }, [
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
