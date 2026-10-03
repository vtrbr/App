import { el, anoCurto } from "../dom.js";
import { poster } from "../img.js";
import { rotaDetalhe } from "../api.js";

const ESTRELA =
  '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2l3 6.6 7 .8-5.2 4.7 1.5 7-6.3-3.6L5.7 21l1.5-7L2 9.4l7-.8z"/></svg>';

/**
 * Card de pôster. As capas já vêm redimensionadas pelo CDN e só são
 * baixadas quando entram (ou quase entram) na tela.
 */
export function cardTitulo(item, { size = "sm", eager = false } = {}) {
  const src = poster(item.imagem, size);
  const nota = typeof item.nota === "number" && item.nota > 0 ? item.nota.toFixed(1) : null;

  const thumb = el("div", { class: "thumb" });
  if (src) {
    const img = el("img", {
      src,
      alt: item.titulo || "",
      width: "342",
      height: "513",
      loading: eager ? "eager" : "lazy",
      decoding: "async",
      referrerpolicy: "no-referrer",
      fetchpriority: eager ? "high" : "low",
    });
    const pronto = () => img.classList.add("on");
    img.addEventListener("load", pronto, { once: true });
    img.addEventListener("error", pronto, { once: true });
    if (img.complete) pronto();
    thumb.append(img);
  }
  if (nota) thumb.append(el("span", { class: "badge", html: `${ESTRELA}${nota}` }));
  if (Number.isFinite(Number(item.progresso)) && Number(item.progresso) > 0) {
    const percentual = Math.max(0, Math.min(100, Number(item.progresso)));
    thumb.append(el("span", { class: "progress-track", aria: "progressbar", "aria-valuenow": String(percentual), "aria-valuemin": "0", "aria-valuemax": "100" }, [
      el("span", { class: "progress-value", style: `width:${percentual}%` }),
    ]));
  }

  return el("a", { class: "card", href: rotaDetalhe(item), "aria-label": item.titulo }, [
    thumb,
    el("p", { class: "nome" }, item.titulo || ""),
    el("p", { class: "meta" }, item.continuarTexto || [anoCurto(item.ano), item.tipo].filter(Boolean).join(" · ")),
  ]);
}

export function skeletonCard() {
  return el("div", {}, [el("div", { class: "skel skel-card" })]);
}
