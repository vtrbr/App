import { el, svgIcone, debounce } from "../dom.js";
import { getCanais, getCanalCategorias, getEventosAoVivo, getCanal } from "../api.js";

function imagem(src, alt) {
  const img = el("img", { alt, loading: "lazy" });
  if (src) {
    img.src = src;
    img.addEventListener("load", () => img.classList.add("on"), { once: true });
    img.addEventListener("error", () => img.remove(), { once: true });
  }
  return img;
}

function programaAtual(item) {
  return item?.epg?.current?.title || item?.description || "Transmissão ao vivo";
}

function abrirAoVivo(item) {
  const embeds = Array.isArray(item.embeds) ? item.embeds : [];
  if (!embeds.length) return false;
  const payload = JSON.stringify(embeds);
  if (window.AndroidPlayer?.openLivePlayer) {
    window.AndroidPlayer.openLivePlayer(
      item.id,
      item.titulo,
      item.logo,
      payload,
      programaAtual(item),
      item.categoria || "",
    );
    return true;
  }
  return false;
}

function cardCanal(item, tipo = "canal") {
  const href = tipo === "evento" ? `#/evento/${encodeURIComponent(item.id)}` : `#/canal/${encodeURIComponent(item.id)}`;
  const card = el("a", { class: "live-card", href, "aria-label": `Assistir ${item.titulo}` });
  card.append(
    el("div", { class: "live-thumb" }, [
      imagem(item.logo, item.titulo),
      el("span", { class: "live-badge", html: `${svgIcone("play")} AO VIVO` }),
    ]),
    el("div", { class: "live-card-body" }, [
      el("strong", {}, item.titulo),
      el("span", { class: "live-meta" }, programaAtual(item)),
      el("span", { class: "live-provider" }, `${item.embeds?.length || 0} servidor(es)`),
    ]),
  );
  card.addEventListener("click", (event) => {
    if (abrirAoVivo(item)) event.preventDefault();
  });
  return card;
}

function destaque(item) {
  const card = el("a", { class: "live-feature", href: `#/canal/${encodeURIComponent(item.id)}` });
  card.append(
    el("div", { class: "live-feature-art" }, [
      imagem(item.logo, item.titulo),
      el("div", { class: "live-feature-veil" }),
      el("div", { class: "live-feature-copy" }, [
        el("span", { class: "live-badge live-badge-large", html: `${svgIcone("play")} AO VIVO` }),
        el("h2", {}, item.titulo),
        el("p", {}, programaAtual(item)),
      ]),
    ]),
  );
  card.addEventListener("click", (event) => {
    if (abrirAoVivo(item)) event.preventDefault();
  });
  return card;
}

function estado(texto, erro = false) {
  return el("div", { class: `center live-state${erro ? " is-error" : ""}` }, [
    !erro && el("div", { class: "spinner" }),
    el("p", {}, texto),
  ]);
}

function normalizarEvento(item) {
  return {
    ...item,
    titulo: item.titulo || item.title || "Evento ao vivo",
    logo: item.logo || item.poster || "",
    categoria: item.categoria || item.category || "Esportes",
  };
}

export default async function paginaCanais(raiz, { categoria = "" } = {}) {
  const page = el("div", { class: "page live-page" });
  raiz.append(page);
  page.append(
    el("div", { class: "live-heading" }, [
      el("div", {}, [el("span", { class: "eyebrow" }, "TRANSMISSÃO AO VIVO"), el("h1", { class: "page-title" }, "Canais")]),
    ]),
    el("p", { class: "sub live-sub" }, "Escolha um canal para abrir o player em tela cheia. O EPG é atualizado automaticamente."),
  );

  const search = el("input", { class: "input live-search", type: "search", placeholder: "Buscar canal, programa ou categoria...", "aria-label": "Buscar canais" });
  page.append(search);
  const chips = el("div", { class: "chips live-chips" });
  page.append(chips);

  const content = el("div", { class: "live-content" });
  page.append(content);
  content.append(estado("Carregando canais e programação..."));

  try {
    const [canais, categorias, eventos] = await Promise.all([
      getCanais(categoria),
      getCanalCategorias(),
      getEventosAoVivo(),
    ]);
    const eventosNormalizados = eventos.map(normalizarEvento);
    const render = (filtro = "") => {
      content.innerHTML = "";
      const termo = filtro.trim().toLowerCase();
      const filtrados = canais.filter((item) => [item.titulo, item.categoria, programaAtual(item)].join(" ").toLowerCase().includes(termo));
      const eventosFiltrados = eventosNormalizados.filter((item) => [item.titulo, item.categoria].join(" ").toLowerCase().includes(termo));
      if (!termo && eventosFiltrados.length) {
        content.append(el("section", { class: "live-section" }, [
          el("div", { class: "section-head" }, [el("h2", {}, "Eventos ao vivo"), el("span", { class: "sub" }, `${eventosFiltrados.length} agora`)]),
          el("div", { class: "live-grid live-grid-events" }, eventosFiltrados.map((item) => cardCanal(item, "evento"))),
        ]));
      }
      if (!termo && filtrados[0]) content.append(el("section", { class: "live-section" }, [el("div", { class: "section-head" }, [el("h2", {}, "Em destaque")]), destaque(filtrados[0])]));
      const title = categoria ? `Categoria: ${categoria}` : "Todos os canais";
      const section = el("section", { class: "live-section" }, [
        el("div", { class: "section-head" }, [el("h2", {}, title), el("span", { class: "sub" }, `${filtrados.length} canais`)]),
        el("div", { class: "live-grid" }),
      ]);
      const grid = section.querySelector(".live-grid");
      filtrados.forEach((item) => grid.append(cardCanal(item)));
      content.append(section);
      if (!filtrados.length && !eventosFiltrados.length) content.replaceChildren(estado("Nenhum canal encontrado."));
    };

    chips.append(el("a", { class: `chip${!categoria ? " on" : ""}`, href: "#/canais" }, "Todos"));
    categorias.forEach((item) => chips.append(el("a", { class: `chip${item.nome === categoria ? " on" : ""}`, href: `#/canais?categoria=${encodeURIComponent(item.nome)}` }, item.nome)));
    search.addEventListener("input", debounce(() => render(search.value), 180));
    render();
  } catch (error) {
    content.replaceChildren(estado(error.message || "Não foi possível carregar os canais agora.", true));
    const retry = el("button", { class: "btn primary" }, "Tentar novamente");
    retry.addEventListener("click", () => location.reload());
    content.append(retry);
  }
  return page;
}

export async function paginaCanal(raiz, { id, tipo = "canal" }) {
  const page = el("div", { class: "page live-page" });
  raiz.append(page);
  page.append(estado("Preparando transmissão..."));
  try {
    const item = tipo === "evento" ? null : await getCanal(id);
    if (!item || !item.id) throw new Error("Canal não encontrado.");
    page.innerHTML = "";
    page.append(
      el("a", { class: "back", href: "#/canais", "aria-label": "Voltar", html: svgIcone("back") }),
      el("div", { class: "live-detail-art" }, [imagem(item.logo, item.titulo), el("div", { class: "live-detail-veil" })]),
      el("div", { class: "live-detail-body" }, [
        el("span", { class: "live-badge", html: `${svgIcone("play")} AO VIVO` }),
        el("h1", {}, item.titulo),
        el("p", { class: "sub" }, programaAtual(item)),
        el("p", { class: "desc" }, item.descricao),
        el("button", { class: "btn primary live-start", html: `${svgIcone("play")} Abrir transmissão` }),
      ]),
    );
    page.querySelector(".live-start").addEventListener("click", () => {
      if (!abrirAoVivo(item)) page.append(estado("Este canal não possui um servidor disponível.", true));
    });
  } catch (error) {
    page.replaceChildren(estado(error.message || "Não foi possível abrir o canal.", true), el("a", { class: "btn ghost", href: "#/canais" }, "Voltar aos canais"));
  }
  return page;
}

export async function paginaEvento(raiz, { id }) {
  const page = el("div", { class: "page live-page" });
  raiz.append(page);
  page.append(estado("Carregando evento..."));
  try {
    const eventos = await getEventosAoVivo();
    const item = eventos.map(normalizarEvento).find((evento) => evento.id === id);
    if (!item) throw new Error("Este evento não está ao vivo neste momento.");
    page.innerHTML = "";
    page.append(
      el("a", { class: "back", href: "#/canais", "aria-label": "Voltar", html: svgIcone("back") }),
      el("div", { class: "live-detail-art" }, [imagem(item.logo, item.titulo), el("div", { class: "live-detail-veil" })]),
      el("div", { class: "live-detail-body" }, [
        el("span", { class: "live-badge", html: `${svgIcone("play")} AO VIVO` }),
        el("h1", {}, item.titulo),
        el("p", { class: "sub" }, item.categoria),
        el("p", { class: "desc" }, item.descricao),
        el("button", { class: "btn primary live-start", html: `${svgIcone("play")} Abrir transmissão` }),
      ]),
    );
    page.querySelector(".live-start").addEventListener("click", () => {
      if (!abrirAoVivo(item)) page.append(estado("Abra este evento no aplicativo Android para iniciar a transmissão.", true));
    });
  } catch (error) {
    page.replaceChildren(estado(error.message || "Não foi possível abrir o evento.", true), el("a", { class: "btn ghost", href: "#/canais" }, "Voltar aos canais"));
  }
  return page;
}
