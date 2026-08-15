import { API_BASE } from "./config.js";

/* Cache em memória + deduplicação de requisições.
   Os endpoints "/stream" devolvem o catálogo inteiro (megabytes), então
   nunca são baixados duas vezes na mesma sessão. */
const cache = new Map();
const emVoo = new Map();

const TTL_CURTO = 5 * 60 * 1000;
const TTL_LONGO = 30 * 60 * 1000;

async function getJSON(caminho, ttl = TTL_CURTO) {
  const agora = Date.now();
  const guardado = cache.get(caminho);
  if (guardado && agora - guardado.t < ttl) return guardado.v;
  if (emVoo.has(caminho)) return emVoo.get(caminho);

  const p = fetch(`${API_BASE}${caminho}`)
    .then((r) => {
      if (!r.ok) throw new Error(`Falha ao carregar (${r.status})`);
      return r.json();
    })
    .then((v) => {
      cache.set(caminho, { t: Date.now(), v });
      emVoo.delete(caminho);
      return v;
    })
    .catch((e) => {
      emVoo.delete(caminho);
      throw e;
    });

  emVoo.set(caminho, p);
  return p;
}

/** O scraper às vezes duplica o título ("XyzXyz") — limpa isso. */
export function limparTitulo(t) {
  const s = (t || "").trim();
  if (!s) return "";
  const meio = s.length / 2;
  if (s.length % 2 === 0 && s.slice(0, meio) === s.slice(meio)) return s.slice(0, meio);
  return s.replace(/^Assistir\s+/i, "").replace(/\s+Online Grátis$/i, "");
}

export function normalizar(item, tipoPadrao) {
  const tipo = item.tipo || tipoPadrao;
  return { ...item, titulo: limparTitulo(item.titulo), ...(tipo ? { tipo } : {}) };
}

export function parseLink(link) {
  if (!link) return { categoria: "", slug: "" };
  const partes = link.replace(/^https?:\/\/[^/]+/, "").split("/").filter(Boolean);
  return { categoria: partes[partes.length - 2] || "", slug: partes[partes.length - 1] || "" };
}

export function ehSerie(item) {
  const t = (item.tipo || "").toLowerCase();
  return t.includes("séri") || t.includes("serie");
}

/** Rota de detalhes de um item da API. */
export function rotaDetalhe(item) {
  const p = parseLink(item.link_assistir);
  const tipo = ehSerie(item) ? "serie" : "filme";
  return `#/titulo/${tipo}/${item.categoria || p.categoria}/${item.slug || p.slug}`;
}

/* ---------------- HOME ---------------- */

export const getCarousel = () =>
  getJSON("/home/carousel", TTL_LONGO).then((d) => (d.itens || []).map((i) => normalizar(i)));

export const getUltimosFilmes = () =>
  getJSON("/ultimos-filmes").then((d) => (d.filmes || []).map((i) => normalizar(i, "Filme")));

export const getAgenda = () => getJSON("/home/agenda").then((d) => d.dias || []);

/* ---------------- CATÁLOGOS ---------------- */

export const getFilmes = () =>
  getJSON("/filmes/stream", TTL_LONGO).then((d) => (d.filmes || []).map((i) => normalizar(i, "Filme")));

export const getSeries = () =>
  getJSON("/series/stream", TTL_LONGO).then((d) => (d.series || []).map((i) => normalizar(i, "Série")));

export const getAnimacoes = () =>
  getJSON("/animacoes/stream", TTL_LONGO).then((d) =>
    (d.animacoes || d.resultados || []).map((i) => normalizar(i, "Filme")),
  );

export const getGenero = (slug) => {
  if (slug === "animacao") return getAnimacoes();
  return getJSON(`/genero/${slug}/stream`, TTL_LONGO).then((d) =>
    (d.resultados || []).map((i) => normalizar(i, "Filme")),
  );
};

/** "Lançamentos": o servidor não tem endpoint próprio (dava 404).
 *  Usamos o topo do catálogo de filmes, que já vem por novidade. */
export const getLancamentos = async () => (await getFilmes()).slice(0, 30);

/* ---------------- BUSCA ---------------- */

export const buscar = (q, pagina = 1) =>
  getJSON(`/buscar/${encodeURIComponent(q)}?pagina=${pagina}`).then((d) => ({
    resultados: (d.resultados || []).map((i) => normalizar(i)),
    totalPaginas: d.total_paginas || 1,
  }));

/* ---------------- DETALHES ---------------- */

export const getTitulo = (categoria, slug) =>
  getJSON(`/filme/${categoria}/${slug}`, TTL_LONGO).then((d) => ({
    ...d,
    titulo: limparTitulo(d.titulo),
    recomendados: (d.recomendados || []).map((i) => normalizar(i)),
  }));

export const getTemporadas = (categoria, slug) =>
  getJSON(`/serie/${categoria}/${slug}/temporadas`, TTL_LONGO).then((d) => d.temporadas || []);

export const getEpisodios = (categoria, slug, numero) =>
  getJSON(`/serie/${categoria}/${slug}/temporada/${numero}/episodios`, TTL_LONGO).then(
    (d) => d.episodios || [],
  );

/* ---------------- PLAYER ---------------- */

/** Texto puro do manifest .m3u8 (reprodução direta pelo CDN). */
export async function getManifesto(categoria, slug) {
  const r = await fetch(`${API_BASE}/filme-player/${categoria}/${slug}`);
  if (!r.ok) throw new Error("Este título está indisponível no momento.");
  const texto = await r.text();
  if (!texto.includes("#EXTM3U")) throw new Error("Fonte de vídeo inválida.");
  return texto;
}
