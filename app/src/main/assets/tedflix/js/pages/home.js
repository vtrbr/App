import { el } from "../dom.js";
import { hero } from "../components/hero.js";
import { fileira } from "../components/row.js";
import {
  getCarousel,
  getUltimosFilmes,
  buscar,
  parseLink,
  getLancamentos,
  getSeries,
  getGenero,
  getTitulo,
} from "../api.js";
import { CATEGORIAS } from "../config.js";

export default async function paginaInicio(raiz) {
  const page = el("div", { class: "page" });
  const topo = el("div", { class: "hero skel" });
  page.append(topo);
  raiz.append(page);

  // O histórico precisa ser carregado antes de montar as fileiras. A versão
  // lazy anterior podia executar a fileira enquanto o refresh remoto ainda
  // estava em andamento e fixá-la em "Nada por aqui agora".
  const continuar = await carregarContinuarAssistindo();
  if (continuar.length) {
    page.append(fileira({ titulo: "Continuar Assistindo", carregar: async () => continuar, limite: 20 }));
  }

  page.append(
    fileira({ titulo: "Últimos filmes", carregar: getUltimosFilmes }),
    fileira({ titulo: "Lançamentos", verTudo: "#/categoria/lancamentos", carregar: getLancamentos }),
    fileira({ titulo: "Séries", verTudo: "#/series", carregar: async () => (await getSeries()).slice(0, 24) }),
  );

  CATEGORIAS.slice(0, 6).forEach((c) => {
    page.append(
      fileira({
        titulo: c.label,
        verTudo: `#/categoria/${c.slug}`,
        carregar: async () => (await getGenero(c.slug)).slice(0, 24),
      }),
    );
  });

  try {
    const destaques = await getCarousel();
    const h = hero(destaques);
    topo.replaceWith(h);
    page.destruir = () => h.destruir?.();
  } catch (e) {
    topo.classList.remove("skel");
    topo.append(el("div", { class: "center" }, "Não foi possível carregar os destaques."));
  }

  return page;
}

async function carregarContinuarAssistindo() {
  try {
    if (!window.AndroidPlayer?.getContinueWatching) return [];

    const lerSnapshot = () => {
      try {
        const valor = JSON.parse(window.AndroidPlayer.getContinueWatching() || "[]");
        return Array.isArray(valor) ? valor : [];
      } catch (_) {
        return [];
      }
    };

    // O cache local é a resposta imediata. A API é acionada em paralelo para
    // atualizar/mesclar o histórico remoto sem fazer a Home parecer vazia
    // enquanto o servidor responde.
    const localSnapshot = lerSnapshot();
    let itens = localSnapshot;
    if (window.AndroidPlayer.refreshContinueWatching) {
      await new Promise((resolve) => {
        let finalizado = false;
        const concluir = () => {
          if (finalizado) return;
          finalizado = true;
          window.__tedflixHistoryReady = null;
          resolve();
        };
        window.__tedflixHistoryReady = concluir;
        try { window.AndroidPlayer.refreshContinueWatching(); } catch (_) { concluir(); }
        // Aguarde a mesma janela usada pela versão funcional para o backend
        // responder; o snapshot local continua sendo preservado enquanto isso.
        setTimeout(concluir, 5000);
      });
      const remotoMesclado = lerSnapshot();
      if (remotoMesclado.length) itens = remotoMesclado;
    }

    if (!itens.length) return [];
    const cards = await Promise.all(itens
      .filter((item) => item && (item.categoria || item.slug || item.titulo || item.filmeId))
      .map(async (item) => {
        let categoria = String(item.categoria || "").trim();
        let slug = String(item.slug || "").trim();
        let detalhe = null;

        // Registros antigos do endpoint remoto podem trazer apenas filmeId,
        // titulo e thumb. Resolve o link pelo catálogo sem descartar o item.
        if ((!categoria || !slug) && (item.titulo || item.filmeId)) {
          try {
            const busca = await buscar(item.titulo || item.filmeId);
            const resultados = Array.isArray(busca?.resultados) ? busca.resultados : [];
            const id = String(item.filmeId || "").trim();
            const titulo = String(item.titulo || "").trim().toLowerCase();
            const encontrado = resultados.find((candidato) => {
              const candidatoId = String(candidato.filmeId || candidato.id || candidato._id || "").trim();
              return (id && candidatoId === id) || (titulo && String(candidato.titulo || "").trim().toLowerCase() === titulo);
            }) || resultados[0];
            if (encontrado) {
              const partes = parseLink(encontrado.link_assistir);
              categoria = categoria || encontrado.categoria || partes.categoria;
              slug = slug || encontrado.slug || partes.slug;
              detalhe = encontrado;
            }
          } catch (_) {}
        }

        // A rota conhecida já é suficiente para renderizar o histórico local.
        // O detalhe completo é opcional e não pode bloquear a fileira.
        if (categoria && slug && !detalhe) {
          try {
            const detalhePromise = getTitulo(categoria, slug);
            detalhe = await Promise.race([
              detalhePromise,
              new Promise((resolve) => setTimeout(() => resolve(null), 900)),
            ]);
          } catch (_) {}
        }

        const tipo = item.tipo || detalhe?.tipo || "Filme";
        const ehEpisodio = Boolean(item.serieSlug || item.serieCategoria || String(item.tipo || "").toLowerCase().includes("epis"));
        const detalheCategoria = String(item.serieCategoria || categoria).trim();
        const detalheSlug = String(item.serieSlug || slug).trim();
        let detalhePai = detalhe;
        if (ehEpisodio && detalheCategoria && detalheSlug) {
          try { detalhePai = await getTitulo(detalheCategoria, detalheSlug); } catch (_) {}
        }
        // Mesmo sem metadados completos, não escondemos o registro salvo.
        // O link de favorito permite que a rota faça uma nova busca pelo título.
        const tipoCard = ehEpisodio ? (detalhePai?.tipo || "Série") : tipo;
        const tituloCard = ehEpisodio
          ? (detalhePai?.titulo || item.serieTitulo || item.titulo || "Série")
          : (item.titulo || detalhe?.titulo || "Tedflix");
        const imagemCard = ehEpisodio
          ? (item.serieThumb || detalhePai?.imagem || item.thumb || detalhe?.imagem || "")
          : (item.thumb || item.imagem || detalhe?.imagem || "");
        const rota = detalheCategoria && detalheSlug
          ? `/titulo/${ehEpisodio ? "serie" : (tipo.toLowerCase() === "serie" ? "serie" : "filme")}/${detalheCategoria}/${detalheSlug}`
          : `#/favorito?titulo=${encodeURIComponent(tituloCard)}`;
        const progressoDaApi = calcularProgressoDaApi(item.tempo, detalhe?.duracao || detalhePai?.duracao);
        const episodioTitulo = String(item.titulo || detalhe?.titulo || "Episódio").trim();
        return {
          ...detalhePai,
          ...detalhe,
          ...item,
          categoria,
          slug,
          tipo: tipoCard,
          titulo: tituloCard,
          imagem: imagemCard,
          link_assistir: rota,
          // A API de histórico pode informar apenas o tempo atual; quando o
          // catálogo fornece duração, a barra é calculada sem depender do login.
          progresso: Number(item.percent || progressoDaApi || 0),
          continuarTexto: ehEpisodio
            ? `Você está assistindo: ${tituloCard} — ${episodioTitulo}`
            : (item.tempo ? `Continuar em ${item.tempo}` : "Continuar assistindo"),
        };
      })).filter(Boolean);
    const seriesJaExibidas = new Set();
    return cards.filter((item) => {
      const ehEpisodio = Boolean(item.serieSlug || item.serieCategoria || String(item.tipo || "").toLowerCase().includes("epis"));
      const chave = ehEpisodio
        ? `serie:${item.serieCategoria || item.categoria}:${item.serieSlug || item.slug}`
        : `titulo:${item.categoria}:${item.slug}`;
      if (seriesJaExibidas.has(chave)) return false;
      seriesJaExibidas.add(chave);
      return true;
    });
  } catch (e) {
    return [];
  }
}

function calcularProgressoDaApi(tempo, duracao) {
  const atual = tempoParaSegundos(tempo);
  const total = duracaoParaSegundos(duracao);
  if (!atual || !total) return 0;
  return Math.max(0, Math.min(99, Math.floor((atual * 100) / total)));
}

function tempoParaSegundos(valor) {
  const partes = String(valor || "").trim().split(":").map(Number);
  if (partes.length === 3 && partes.every(Number.isFinite)) return partes[0] * 3600 + partes[1] * 60 + partes[2];
  if (partes.length === 2 && partes.every(Number.isFinite)) return partes[0] * 60 + partes[1];
  return Number.isFinite(Number(valor)) ? Number(valor) : 0;
}

function duracaoParaSegundos(valor) {
  const texto = String(valor || "").trim().toLowerCase();
  const numero = Number((texto.match(/[\d.,]+/) || [""])[0].replace(",", "."));
  if (!Number.isFinite(numero) || numero <= 0) return 0;
  return texto.includes("h") ? numero * 3600 : numero * 60;
}
