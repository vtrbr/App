import { API_BASE, LIVE_API_BASE } from "./config.js";

/* Cache em memória + deduplicação de requisições.
   Os endpoints "/stream" devolvem o catálogo inteiro (megabytes), então
   nunca são baixados duas vezes na mesma sessão. */
const cache = new Map();
const emVoo = new Map();

const TTL_CURTO = 5 * 60 * 1000;
const TTL_LONGO = 30 * 60 * 1000;

async function getJSON(caminho, ttl = TTL_CURTO, base = API_BASE) {
  const chave = `${base}${caminho}`;
  const agora = Date.now();
  const guardado = cache.get(chave);
  if (guardado && agora - guardado.t < ttl) return guardado.v;
  if (emVoo.has(chave)) return emVoo.get(chave);

  const p = fetch(`${base}${caminho}`)
    .then((r) => {
      if (!r.ok) throw new Error(`Falha ao carregar (${r.status})`);
      return r.json();
    })
    .then((v) => {
      cache.set(chave, { t: Date.now(), v });
      emVoo.delete(chave);
      return v;
    })
    .catch((e) => {
      emVoo.delete(chave);
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

/* ---------------- CANAIS AO VIVO ---------------- */

function urlHttpValida(url) {
  try {
    const parsed = new URL(String(url || ""));
    return parsed.protocol === "http:" || parsed.protocol === "https:";
  } catch (_) {
    return false;
  }
}

export function normalizarCanal(item = {}) {
  const embeds = (Array.isArray(item.embeds) ? item.embeds : [])
    .map((embed) => ({
      provider: String(embed?.provider || "Servidor"),
      quality: String(embed?.quality || "Automático"),
      embed_url: String(embed?.embed_url || ""),
    }))
    .filter((embed) => urlHttpValida(embed.embed_url));
  return {
    id: String(item.id || ""),
    titulo: String(item.name || item.title || "Canal ao vivo"),
    descricao: String(item.description || ""),
    logo: String(item.logo_url || item.logo || ""),
    categoria: String(item.category || "Outros"),
    embeds,
    epg: item.epg || {},
    aoVivo: true,
  };
}

export const getCanais = (categoria = "") => {
  const caminho = categoria ? `/channels?category=${encodeURIComponent(categoria)}` : "/channels";
  return getJSON(caminho, TTL_CURTO, LIVE_API_BASE).then((d) =>
    (Array.isArray(d.data) ? d.data : []).map(normalizarCanal),
  );
};

export const getCanalCategorias = () =>
  getJSON("/channels/categories", TTL_LONGO, LIVE_API_BASE).then((d) =>
    (Array.isArray(d.data) ? d.data : []).map((item) => ({
      id: String(item.id || ""),
      nome: String(item.name || "Outros"),
    })),
  );

export const getCanal = (id) =>
  getJSON(`/channels/${encodeURIComponent(id)}`, TTL_CURTO, LIVE_API_BASE).then((d) =>
    normalizarCanal(d.data && !Array.isArray(d.data) ? d.data : d.channel || d),
  );

export const getEventosAoVivo = () =>
  getJSON("/sports?status=live", TTL_CURTO, LIVE_API_BASE).then((d) =>
    (Array.isArray(d.data) ? d.data : []).map((item) => ({
      ...item,
      id: String(item.id || ""),
      titulo: String(item.title || "Evento ao vivo"),
      descricao: String(item.description || ""),
      logo: String(item.poster || ""),
      categoria: String(item.category || "Esportes"),
      embeds: (Array.isArray(item.embeds) ? item.embeds : [])
        .map((embed) => ({
          provider: String(embed?.provider || "Servidor"),
          quality: String(embed?.quality || "Automático"),
          embed_url: String(embed?.embed_url || ""),
        }))
        .filter((embed) => urlHttpValida(embed.embed_url)),
      aoVivo: true,
    })),
  );

export const buscarCanais = (q) =>
  getJSON(`/search?q=${encodeURIComponent(q || "")}`, TTL_CURTO, LIVE_API_BASE).then((d) => ({
    canais: (Array.isArray(d.data?.channels) ? d.data.channels : []).map(normalizarCanal),
    eventos: Array.isArray(d.data?.events) ? d.data.events : [],
  }));

/* ---------------- PLAYER ---------------- */

/** Texto puro do manifest .m3u8 (reprodução direta pelo CDN). */
export async function getManifesto(categoria, slug) {
  const r = await fetch(`${API_BASE}/filme-player/${categoria}/${slug}`);
  if (!r.ok) throw new Error("Este título está indisponível no momento.");
  const texto = await r.text();
  if (!texto.includes("#EXTM3U")) throw new Error("Fonte de vídeo inválida.");
  return texto;
}
