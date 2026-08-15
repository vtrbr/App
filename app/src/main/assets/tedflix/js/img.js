/**
 * Otimização de imagens — o motivo do travamento antigo era baixar capas
 * em tamanho original (PNG/JPG grandes) para slots de ~110px.
 *
 * - TMDB: troca o tamanho no caminho (/t/p/original -> /t/p/w154), via CDN.
 * - NovelasFlix (/resize/...webp): ajusta ?w=&h= (já entrega WebP).
 * - O tamanho pedido ainda cai um degrau quando a rede está lenta.
 */

import { tamanhoCapa, redeGenerosa } from "./net.js";

const TMDB = { xs: "w154", sm: "w185", md: "w342", lg: "w500" };
const LARG = { xs: 154, sm: 185, md: 342, lg: 500 };
const ORDEM = ["xs", "sm", "md", "lg"];

/** Nunca pede mais do que a rede aguenta. */
function ajustar(size) {
  const teto = tamanhoCapa(); // xs | sm | md
  return ORDEM[Math.min(ORDEM.indexOf(size), ORDEM.indexOf(teto))] || size;
}

export function poster(url, size = "sm", { respeitarRede = true } = {}) {
  if (!url) return "";
  const s = respeitarRede ? ajustar(size) : size;
  try {
    if (url.includes("image.tmdb.org")) {
      return url.replace(/\/t\/p\/(w\d+|original)\//, `/t/p/${TMDB[s]}/`);
    }
    if (url.includes("/resize/")) {
      const u = new URL(url);
      const w = LARG[s];
      u.searchParams.set("w", String(w));
      u.searchParams.set("h", String(Math.round(w * 1.5)));
      return u.toString();
    }
  } catch (e) {
    /* URL estranha: devolve como veio */
  }
  return url;
}

/** Só oferece 2x quando a conexão comporta — em rede fraca isso dobrava o peso. */
export function posterSrcSet(url, size = "sm") {
  if (!redeGenerosa()) return "";
  const base = poster(url, size);
  if (!base) return "";
  const prox = size === "xs" ? "sm" : size === "sm" ? "md" : "lg";
  return `${base} 1x, ${poster(url, prox)} 2x`;
}

/** Imagem larga (hero / backdrop) — sem proporção de pôster. */
export function backdrop(url, largura = 780) {
  if (!url) return "";
  const l = redeGenerosa() ? largura : Math.min(largura, 500);
  try {
    if (url.includes("image.tmdb.org")) {
      return url.replace(/\/t\/p\/(w\d+|original)\//, `/t/p/w${l}/`);
    }
    if (url.includes("/resize/")) {
      const u = new URL(url);
      u.searchParams.set("w", String(l));
      u.searchParams.delete("h");
      return u.toString();
    }
  } catch (e) {
    /* ignora */
  }
  return url;
}

/* --------- fila de pré-carregamento ---------
   Baixa capas das próximas fileiras em segundo plano, mas com no máximo
   3 downloads simultâneos para não competir com o que está na tela. */

const fila = [];
let ativos = 0;
const jaVistas = new Set();
const LIMITE = 3;

function bombear() {
  while (ativos < LIMITE && fila.length) {
    const src = fila.shift();
    ativos++;
    const img = new Image();
    const fim = () => {
      ativos--;
      bombear();
    };
    img.onload = fim;
    img.onerror = fim;
    img.decoding = "async";
    img.fetchPriority = "low";
    img.src = src;
  }
}

export function prefetchImagens(urls, size = "sm") {
  const agenda = window.requestIdleCallback || ((f) => setTimeout(f, 400));
  agenda(() => {
    urls.slice(0, 10).forEach((u) => {
      const src = poster(u, size);
      if (!src || jaVistas.has(src)) return;
      jaVistas.add(src);
      fila.push(src);
    });
    bombear();
  });
}
