import { API_BASE } from "./config.js";

/* Cache em memória + deduplicação de requisições.
   Os endpoints "/stream" devolvem o catálogo inteiro (megabytes), então
   nunca são baixados duas vezes na mesma sessão. */
const cache = new Map();
const emVoo = new Map();

const TTL_CURTO = 5 * 60 * 1000;
const TTL_LONGO = 30 * 60 * 1000;
const CACHE_PREFIX = "tedflix:catalog-cache:v2:";
const CACHE_MAX_BYTES = 1_800_000;

function lerCachePersistente(chave) {
  try {
    const item = JSON.parse(localStorage.getItem(CACHE_PREFIX + chave) || "null");
    return item && item.t && item.v !== undefined ? item : null;
  } catch (_) { return null; }
}

function salvarCachePersistente(chave, valor) {
  try {
    const texto = JSON.stringify({ t: Date.now(), v: valor });
    if (texto.length <= CACHE_MAX_BYTES) localStorage.setItem(CACHE_PREFIX + chave, texto);
  } catch (_) { /* quota cheia: o cache em memória continua válido */ }
}

async function getJSON(caminho, ttl = TTL_CURTO, base = API_BASE) {
  const chave = `${base}${caminho}`;
  const agora = Date.now();
  const guardado = cache.get(chave);
  if (guardado && agora - guardado.t < ttl) return guardado.v;
  const persistido = lerCachePersistente(chave);
  if (persistido && agora - persistido.t < ttl) {
    cache.set(chave, persistido);
    return persistido.v;
  }
  if (emVoo.has(chave)) return emVoo.get(chave);

  const p = fetch(`${base}${caminho}`)
    .then((r) => {
      if (!r.ok) throw new Error(`Falha ao carregar (${r.status})`);
      return r.json();
    })
    .then((v) => {
      cache.set(chave, { t: Date.now(), v });
      salvarCachePersistente(chave, v);
      emVoo.delete(chave);
      return v;
    })
    .catch((e) => {
      emVoo.delete(chave);
      if (persistido?.v !== undefined) return persistido.v;
      throw e;
    });

  emVoo.set(chave, p);
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
  // Alguns itens reconstruídos do histórico usam uma rota hash de fallback.
  // Respeite-a diretamente em vez de tentar interpretá-la como link de CDN.
  if (typeof item.link_assistir === "string" && item.link_assistir.startsWith("#/")) {
    return item.link_assistir;
  }
  const p = parseLink(item.link_assistir);
  const episodioDeSerie = Boolean(item.serieSlug || item.serieCategoria);
  const tipo = ehSerie(item) || episodioDeSerie ? "serie" : "filme";
  const categoria = episodioDeSerie ? item.serieCategoria : (item.categoria || p.categoria);
  const slug = episodioDeSerie ? item.serieSlug : (item.slug || p.slug);
  return `#/titulo/${tipo}/${categoria}/${slug}`;
}

/* ---------------- HOME ---------------- */

export const getCarousel = () =>
  getJSON("/home/carousel", TTL_LONGO).then((d) => (d.itens || []).map((i) => normalizar(i)));

export const getUltimosFilmes = async () => {
  try {
    const d = await getJSON("/ultimos-filmes");
    const filmes = Array.isArray(d.filmes) ? d.filmes : [];
    if (filmes.length) return filmes.map((i) => normalizar(i, "Filme"));
  } catch (_) {
    // O serviço pode falhar ao extrair esta rota; o catálogo continua disponível.
  }
  return (await getFilmes()).slice(0, 30);
};

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

/** Lançamentos oficiais da Home, retornados pelo endpoint dedicado. */
export const getLancamentos = () =>
  getJSON("/home/lancamentos", TTL_LONGO).then((d) => {
    const itens = d.lancamentos || d.filmes || d.resultados || d.itens || [];
    return (Array.isArray(itens) ? itens : []).map((item) => normalizar(item));
  });

/* ---------------- BUSCA ---------------- */

export const buscar = (q, pagina = 1) =>
  getJSON(`/buscar/${encodeURIComponent(q)}?pagina=${pagina}`).then((d) => ({
    resultados: (d.resultados || []).map((i) => normalizar(i)),
    totalPaginas: d.total_paginas || 1,
  }));

/* ---------------- DETALHES ---------------- */

export const getTitulo = async (categoria, slug, tipo = "filme") => {
  if (String(tipo).toLowerCase().includes("séri") || String(tipo).toLowerCase().includes("serie")) {
    const [temporadas, busca] = await Promise.all([
      getJSON(`/series/serie/${categoria}/${slug}/temporadas`, TTL_LONGO),
      buscar(slug),
    ]);
    const item = (busca.resultados || []).find((i) => String(i.link_assistir || "").includes(`/${slug}`)) || busca.resultados?.[0] || {};
    return {
      ...item,
      titulo: limparTitulo(item.titulo || temporadas.serie || slug),
      categoria,
      slug,
      tipo: "Série",
      temporadas: temporadas.temporadas || [],
      recomendados: (item.recomendados || []).map((i) => normalizar(i)),
    };
  }
  return getJSON(`/filmes/filme/${categoria}/${slug}`, TTL_LONGO).then((d) => ({
    ...d,
    titulo: limparTitulo(d.titulo),
    recomendados: (d.recomendados || []).map((i) => normalizar(i)),
  }));
};

export const getTemporadas = (categoria, slug) =>
  getJSON(`/serie/${categoria}/${slug}/temporadas`, TTL_LONGO).then((d) => d.temporadas || []);

export const getEpisodios = (categoria, slug, numero) =>
  getJSON(`/serie/${categoria}/${slug}/temporada/${numero}/episodios`, TTL_LONGO).then(
    (d) => d.episodios || [],
  );

/* ---------------- PLAYER ---------------- */

/** Texto puro do manifest .m3u8 (reprodução direta pelo CDN). */
export async function getManifesto(categoria, slug) {
  const r = await fetch(`${API_BASE}/filmes/filme-player/${categoria}/${slug}`);
  if (!r.ok) throw new Error("Este título está indisponível no momento.");
  const texto = await r.text();
  if (!texto.includes("#EXTM3U")) throw new Error("Fonte de vídeo inválida.");
  return texto;
}
