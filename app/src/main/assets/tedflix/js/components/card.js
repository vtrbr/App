import { el, anoCurto } from "../dom.js";
import { poster, posterSrcSet } from "../img.js";
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
    const srcset = posterSrcSet(item.imagem, size);
    const img = el("img", {
      src,
      ...(srcset ? { srcset, sizes: "(max-width: 640px) 33vw, 180px" } : {}),
      alt: item.titulo || "",
      width: "342",
      height: "513",
      loading: eager ? "eager" : "lazy",
      decoding: "async",
      fetchpriority: eager ? "high" : "low",
    });
    const pronto = () => img.classList.add("on");
    img.addEventListener("load", pronto, { once: true });
    img.addEventListener("error", pronto, { once: true });
    if (img.complete) pronto();
    thumb.append(img);
  }
  if (nota) thumb.append(el("span", { class: "badge", html: `${ESTRELA}${nota}` }));

  return el("a", { class: "card", href: rotaDetalhe(item), "aria-label": item.titulo }, [
    thumb,
    el("p", { class: "nome" }, item.titulo || ""),
    el("p", { class: "meta" }, [anoCurto(item.ano), item.tipo].filter(Boolean).join(" · ")),
  ]);
}

export function skeletonCard() {
  return el("div", {}, [el("div", { class: "skel skel-card" })]);
}
