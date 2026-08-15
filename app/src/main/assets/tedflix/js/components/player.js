/**
 * ===================================================================
 * TEDFLIX — PLAYER WEB NATIVO (player.js)
 * VERSÃO: Video.js (Motor HLS Robusto)
 * ===================================================================
 */

import { el } from "../dom.js";
import { nivel, lerCache, aoVoltarInternet } from "../net.js";

/* ------------------------------------------------------------------
 * 0. Carregamento do Video.js e plugin de qualidade
 * ------------------------------------------------------------------ */

const VJS_CDN = "https://vjs.zencdn.net/8.10.0/video.min.js";
const VJS_CSS = "https://vjs.zencdn.net/8.10.0/video-js.min.css";
const VJS_QUALITIES = "https://cdn.jsdelivr.net/npm/videojs-contrib-quality-levels@4.1.0/dist/videojs-contrib-quality-levels.min.js";

let vjsPromise = null;

function carregarVideoJs() {
  if (window.videojs) return Promise.resolve(window.videojs);
  if (vjsPromise) return vjsPromise;
  
  vjsPromise = new Promise((ok, erro) => {
    // Carrega o CSS base (necessário para o wrapper do Video.js)
    if (!document.querySelector(`link[href="${VJS_CSS}"]`)) {
      const link = document.createElement("link");
      link.rel = "stylesheet";
      link.href = VJS_CSS;
      document.head.append(link);
    }

    const s = document.createElement("script");
    s.src = VJS_CDN;
    s.async = true;
    s.onload = () => {
      // Carrega o plugin de qualidades logo após o core
      const sq = document.createElement("script");
      sq.src = VJS_QUALITIES;
      sq.async = true;
      sq.onload = () => ok(window.videojs);
      sq.onerror = () => erro(new Error("Falha ao carregar plugin de qualidade."));
      document.head.append(sq);
    };
    s.onerror = () => erro(new Error("Falha ao carregar o motor Video.js."));
    document.head.append(s);
  });
  
  return vjsPromise;
}

/* ------------------------------------------------------------------
 * Utilidades
 * ------------------------------------------------------------------ */

function formatarTempo(s) {
  if (!isFinite(s) || s < 0) s = 0;
  s = Math.floor(s);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sg = s % 60;
  const mm = String(m).padStart(h ? 2 : 1, "0");
  const ss = String(sg).padStart(2, "0");
  return h ? `${h}:${String(m).padStart(2, "0")}:${ss}` : `${mm}:${ss}`;
}

function limparTagsVTT(texto) {
  return String(texto || "")
    .replace(/<[^>]+>/g, "")
    .replace(/\{[^}]*\}/g, "")
    .trim();
}

const svg = (viewBox, inner) => `<svg viewBox="${viewBox}" aria-hidden="true">${inner}</svg>`;

const ICONE = {
  play: svg("0 0 24 24", '<path d="M8 5v14l11-7z" fill="currentColor"/>'),
  pause: svg("0 0 24 24", '<path d="M7 5h4v14H7zM13 5h4v14h-4z" fill="currentColor"/>'),
  back: svg("0 0 24 24", '<path d="M15 5l-7 7 7 7" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>'),
  girar: svg("0 0 24 24", '<path d="M4 12a8 8 0 0 1 14-5M20 12a8 8 0 0 1-14 5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><path d="M18 3v5h-5M6 21v-5h5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>'),
  audio: svg("0 0 24 24", '<path d="M4 9v6h4l5 4V5L8 9H4z" fill="currentColor"/><path d="M17 8.5a5 5 0 0 1 0 7" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>'),
  legenda: svg("0 0 24 24", '<rect x="3" y="5" width="18" height="14" rx="2" fill="none" stroke="currentColor" stroke-width="2"/><path d="M7 10.5h3M7 13.5h5M14 10.5h3M14 13.5h3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>'),
  qualidade: svg("0 0 24 24", '<circle cx="12" cy="12" r="3.2" fill="none" stroke="currentColor" stroke-width="2"/><path d="M19.4 13.5a7.7 7.7 0 0 0 0-3l1.8-1.4-2-3.4-2.1.9a7.7 7.7 0 0 0-2.6-1.5L14.2 2h-4l-.3 2.3a7.7 7.7 0 0 0-2.6 1.5l-2.1-.9-2 3.4 1.8 1.4a7.7 7.7 0 0 0 0 3L3.2 14.9l2 3.4 2.1-.9a7.7 7.7 0 0 0 2.6 1.5l.3 2.3h4l.3-2.3a7.7 7.7 0 0 0 2.6-1.5l2.1.9 2-3.4z" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/>'),
  velocidade: svg("0 0 24 24", '<circle cx="12" cy="13" r="8" fill="none" stroke="currentColor" stroke-width="2"/><path d="M12 13l4-3.2M8.5 5.3h7" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>'),
  check: svg("0 0 24 24", '<path d="M5 12.5l4.5 4.5L19 7" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>'),
};

const ESTADO = Object.freeze({
  IDLE: "idle", LOADING: "loading", READY: "ready", PLAYING: "playing",
  PAUSED: "paused", BUFFERING: "buffering", SEEKING: "seeking",
  RECOVERING: "recovering", ENDED: "ended", ERROR: "error", DESTROYED: "destroyed",
});

let atual = null;

export function limparPlayer() {
  if (!atual) return;
  try { atual._destruirInterno(); } catch (e) {}
  atual = null;
}

export function caixaPlayer() {
  return el("div", { class: "player-wrap" });
}

export function montarPlayer(container, opts) {
  limparPlayer();
  atual = criarInstancia(container, opts);
  return { destruir: () => limparPlayer() };
}

/* ------------------------------------------------------------------
 * Instância principal
 * ------------------------------------------------------------------ */

function criarInstancia(container, { m3u8, titulo, imagem, aoStatus = () => {}, aoGirar = null, aoVoltar = null }) {
  container.innerHTML = "";

  // O Video.js envolve o <video> em uma div, então aplicamos as classes nele
  const video = el("video", {
    class: "video-js vjs-default-skin tp-video",
    playsinline: "",
    "webkit-playsinline": "",
    poster: imagem || "",
    crossorigin: "anonymous",
  });

  const legendaEl = el("div", { class: "tp-legenda" });
  const bufferingEl = el("div", { class: "tp-buffering" }, [el("div", { class: "spinner" })]);
  const erroEl = el("div", { class: "tp-erro oculto" });

  const btnVoltar = el("button", { class: "tp-btn tp-back", type: "button", "data-focus": "", "aria-label": "Voltar" });
  btnVoltar.innerHTML = ICONE.back;

  const tituloEl = el("div", { class: "tp-titulo" }, titulo || "");

  const extraTopo = el("div", { class: "tp-topo-extra" });
  if (aoGirar) {
    const btnGirar = el("button", { class: "tp-btn tp-girar", type: "button", "data-focus": "", "aria-label": "Girar tela" });
    btnGirar.innerHTML = ICONE.girar;
    btnGirar.addEventListener("click", () => aoGirar());
    extraTopo.append(btnGirar);
  }

  const topo = el("div", { class: "tp-topo" }, [btnVoltar, tituloEl, extraTopo]);

  const btnRw = el("button", { class: "tp-btn tp-rw", type: "button", "data-focus": "", "aria-label": "Retroceder 10 segundos" }, [
    el("span", { class: "tp-skiptxt" }, "↺10"),
  ]);
  const btnPlay = el("button", { class: "tp-btn tp-play grande", type: "button", "data-focus": "", "aria-label": "Reproduzir" });
  btnPlay.innerHTML = ICONE.play;
  const btnFw = el("button", { class: "tp-btn tp-fw", type: "button", "data-focus": "", "aria-label": "Avançar 10 segundos" }, [
    el("span", { class: "tp-skiptxt" }, "10↻"),
  ]);
  const centro = el("div", { class: "tp-centro" }, [btnRw, btnPlay, btnFw]);

  const tempoAtualEl = el("span", { class: "tp-tempo" }, "0:00");
  const tempoDurEl = el("span", { class: "tp-tempo" }, "0:00");
  const progresso = el("div", { class: "tp-progresso", "data-focus": "", role: "slider", tabindex: "-1" }, [
    el("div", { class: "tp-progresso-buf" }),
    el("div", { class: "tp-progresso-tocado" }),
    el("div", { class: "tp-progresso-alca" }),
  ]);
  const linhaProgresso = el("div", { class: "tp-linha-progresso" }, [tempoAtualEl, progresso, tempoDurEl]);

  function botaoMenu(tipo, label, icone) {
    const b = el("button", { class: "tp-btn tp-menu-btn", type: "button", "data-focus": "", "data-menu": tipo });
    b.innerHTML = `${icone}<span>${label}</span>`;
    return b;
  }
  const btnAudio = botaoMenu("audio", "Áudio", ICONE.audio);
  const btnLegenda = botaoMenu("legenda", "Legendas", ICONE.legenda);
  const btnQualidade = botaoMenu("qualidade", "Qualidade", ICONE.qualidade);
  const btnVelocidade = botaoMenu("velocidade", "Velocidade", ICONE.velocidade);
  const linhaMenus = el("div", { class: "tp-linha-menus" }, [btnAudio, btnLegenda, btnQualidade, btnVelocidade]);

  const rodape = el("div", { class: "tp-rodape" }, [linhaProgresso, linhaMenus]);

  const popupLista = el("div", { class: "tp-popup-lista" });
  const popupTitulo = el("div", { class: "tp-popup-titulo" }, "");
  const popup = el("div", { class: "tp-popup oculto" }, [popupTitulo, popupLista]);

  const ui = el("div", { class: "tp-ui" }, [topo, centro, rodape, popup]);

  const raiz = el("div", { class: "tp", tabindex: "0" }, [video, legendaEl, bufferingEl, erroEl, ui]);
  container.append(raiz);

  /* ---------------- estado ---------------- */

  let estado = ESTADO.IDLE;
  let morto = false;
  let vjs = null; // Instância do Video.js
  let blobUrlAtual = null;

  let qualidadeManual = -1;
  let ultimaVelocidade = 1;
  let menuAberto = null;
  let pararOnline = null;

  // Helpers de acesso seguro ao estado do player
  const getCurTime = () => vjs ? vjs.currentTime() : 0;
  const getDur = () => vjs ? vjs.duration() || 0 : 0;

  function setEstado(novo) {
    if (morto) return;
    estado = novo;
    raiz.dataset.estado = novo;
    bufferingEl.classList.toggle("on", novo === ESTADO.BUFFERING || novo === ESTADO.RECOVERING);
  }

  /* ---------------- controles ---------------- */

  let timerControles = null;
  function mostrarControles() {
    ui.classList.add("on");
    raiz.classList.remove("tp-sem-cursor");
    clearTimeout(timerControles);
    if ((vjs && vjs.paused()) || menuAberto) return;
    timerControles = setTimeout(esconderControles, 3800);
  }
  function esconderControles() {
    if (menuAberto) return;
    ui.classList.remove("on");
    raiz.classList.add("tp-sem-cursor");
  }
  raiz.addEventListener("pointermove", mostrarControles);
  raiz.addEventListener("pointerdown", mostrarControles);
  raiz.addEventListener("keydown", mostrarControles);

  function alternarPlayPause() {
    if (!vjs) return;
    if (vjs.paused() || vjs.ended()) vjs.play().catch(() => {});
    else vjs.pause();
  }
  btnPlay.addEventListener("click", alternarPlayPause);
  btnRw.addEventListener("click", () => pular(-10));
  btnFw.addEventListener("click", () => pular(10));
  btnVoltar.addEventListener("click", () => {
    if (aoVoltar) aoVoltar();
    else history.back();
  });

  function pular(delta) {
    const dur = getDur();
    if (!isFinite(dur) || dur === 0) return;
    seekPara(clamp(getCurTime() + delta, 0, dur));
  }

  /* ---------------- seek robusto ---------------- */

  let seekPendente = null;
  let seekTimer = null;
  function seekPara(tempo) {
    seekPendente = tempo;
    atualizarProgressoVisual(tempo);
    clearTimeout(seekTimer);
    setEstado(ESTADO.SEEKING);
    seekTimer = setTimeout(() => {
      if (seekPendente == null || morto || !vjs) return;
      try { vjs.currentTime(seekPendente); } catch (e) {}
      seekPendente = null;
    }, 120);
  }

  /* ---------------- progresso ---------------- */

  let arrastando = false;
  function clamp(v, min, max) { return Math.max(min, Math.min(max, v)); }
  function fracaoNoEvento(ev) {
    const r = progresso.getBoundingClientRect();
    const x = (ev.touches ? ev.touches[0].clientX : ev.clientX) - r.left;
    return clamp(x / r.width, 0, 1);
  }
  function iniciarArraste(ev) {
    arrastando = true;
    moverArraste(ev);
    mostrarControles();
  }
  function moverArraste(ev) {
    if (!arrastando) return;
    const dur = getDur();
    if (!isFinite(dur)) return;
    seekPara(fracaoNoEvento(ev) * dur);
  }
  function soltar() { arrastando = false; }
  
  progresso.addEventListener("pointerdown", iniciarArraste);
  window.addEventListener("pointermove", moverArraste);
  window.addEventListener("pointerup", soltar);
  progresso.addEventListener("click", (ev) => {
    const dur = getDur();
    if (!isFinite(dur)) return;
    seekPara(fracaoNoEvento(ev) * dur);
  });

  function atualizarProgressoVisual(tempoForcado) {
    const dur = getDur();
    const cur = tempoForcado != null ? tempoForcado : getCurTime();
    const fracao = dur ? clamp(cur / dur, 0, 1) : 0;
    
    progresso.querySelector(".tp-progresso-tocado").style.width = (fracao * 100) + "%";
    progresso.querySelector(".tp-progresso-alca").style.left = (fracao * 100) + "%";
    tempoAtualEl.textContent = formatarTempo(cur);
    tempoDurEl.textContent = formatarTempo(dur);

    if (!vjs) return;
    let bufFim = 0;
    const b = vjs.buffered();
    if (b) {
      for (let i = 0; i < b.length; i++) {
        if (b.start(i) <= cur + 0.5 && b.end(i) >= bufFim) bufFim = b.end(i);
      }
    }
    const fracaoBuf = dur ? clamp(bufFim / dur, 0, 1) : 0;
    progresso.querySelector(".tp-progresso-buf").style.width = (fracaoBuf * 100) + "%";
  }

  /* ---------------- buffer monitor ---------------- */

  function bufferAdiante() {
    if (!vjs) return 0;
    const b = vjs.buffered();
    const t = getCurTime();
    if (!b) return 0;
    for (let i = 0; i < b.length; i++) {
      if (t >= b.start(i) - 0.25 && t <= b.end(i) + 0.25) return Math.max(0, b.end(i) - t);
    }
    return 0;
  }

  let monitorId = null;
  function iniciarMonitor() {
    monitorId = setInterval(() => {
      if (morto || !vjs) return;
      atualizarProgressoVisual();
      const ahead = bufferAdiante();
      if (!vjs.paused() && !vjs.seeking()) {
        if (ahead < 0.6 && estado !== ESTADO.RECOVERING) setEstado(ESTADO.BUFFERING);
        else if (ahead > 2 && estado === ESTADO.BUFFERING) setEstado(ESTADO.PLAYING);
      }
    }, 500);
  }

  /* ---------------- Menus (Qualidade / Áudio / Legenda) ---------------- */

  function montarListaQualidade() {
    if (!vjs || !vjs.qualityLevels) return [{ id: -1, label: "Automático" }];
    const niveis = vjs.qualityLevels();
    const lista = [];
    for (let i = 0; i < niveis.length; i++) {
      const lvl = niveis[i];
      lista.push({ id: lvl.id, label: lvl.height ? `${lvl.height}p` : `Nível ${i + 1}`, index: i });
    }
    // Remove duplicatas se houver (mesma resolução)
    const filtrada = lista.filter((v, i, a) => a.findIndex(t => t.label === v.label) === i)
                        .sort((a, b) => parseInt(b.label) - parseInt(a.label));
    return [{ id: -1, label: "Automático" }, ...filtrada];
  }

  function definirQualidade(id) {
    if (!vjs || !vjs.qualityLevels) return;
    qualidadeManual = id;
    const niveis = vjs.qualityLevels();
    for (let i = 0; i < niveis.length; i++) {
      niveis[i].enabled = (id === -1 || niveis[i].id === id);
    }
  }

  function audioAtual() {
    if (!vjs || !vjs.audioTracks) return 0;
    const tracks = vjs.audioTracks();
    for (let i = 0; i < tracks.length; i++) {
      if (tracks[i].enabled) return i;
    }
    return 0;
  }

  function definirAudio(idx) {
    if (!vjs || !vjs.audioTracks) return;
    const tracks = vjs.audioTracks();
    for (let i = 0; i < tracks.length; i++) {
      tracks[i].enabled = (i === idx);
    }
  }

  function legendaAtual() {
    if (!vjs || !vjs.textTracks) return -1;
    const tracks = vjs.textTracks();
    for (let i = 0; i < tracks.length; i++) {
      if (tracks[i].mode === "showing") return i;
    }
    return -1;
  }

  function definirLegenda(id) {
    if (!vjs || !vjs.textTracks) return;
    const tracks = vjs.textTracks();
    for (let i = 0; i < tracks.length; i++) {
      tracks[i].mode = (i === id) ? "showing" : "disabled";
    }
  }

  function fecharMenu() {
    menuAberto = null;
    popup.classList.add("oculto");
    [btnAudio, btnLegenda, btnQualidade, btnVelocidade].forEach(b => b.classList.remove("ativo"));
    mostrarControles();
  }

  function abrirMenu(tipo) {
    let itens = [];
    let titulo = "";
    
    if (tipo === "qualidade") {
      titulo = "Qualidade";
      itens = montarListaQualidade();
    } else if (tipo === "audio") {
      titulo = "Áudio";
      const tracks = vjs ? vjs.audioTracks() : [];
      for (let i = 0; i < tracks.length; i++) {
        itens.push({ valor: i, label: tracks[i].label || tracks[i].language || `Faixa ${i + 1}` });
      }
      if (!itens.length) itens = [{ valor: 0, label: "Padrão" }];
    } else if (tipo === "legenda") {
      titulo = "Legendas";
      itens = [{ valor: -1, label: "Desativadas" }];
      const tracks = vjs ? vjs.textTracks() : [];
      for (let i = 0; i < tracks.length; i++) {
        // Ignora metadados do motor
        if (tracks[i].kind === "subtitles" || tracks[i].kind === "captions") {
          itens.push({ valor: i, label: tracks[i].label || tracks[i].language || `Faixa ${i + 1}` });
        }
      }
    } else if (tipo === "velocidade") {
      titulo = "Velocidade";
      itens = [0.5, 0.75, 1, 1.25, 1.5, 2].map(v => ({ valor: v, label: v === 1 ? "Normal" : `${v}x` }));
    }

    const valorAtual =
      tipo === "qualidade" ? qualidadeManual :
      tipo === "audio" ? audioAtual() :
      tipo === "legenda" ? legendaAtual() :
      ultimaVelocidade;

    popupTitulo.textContent = titulo;
    popupLista.innerHTML = "";
    
    itens.forEach(it => {
      const b = el("button", { class: "tp-popup-item", type: "button", "data-focus": "" }, [
        el("span", {}, it.label || "Desconhecido")
      ]);
      // A key no objeto de qualidade foi mapeada como 'id', nos outros como 'valor'
      const val = tipo === "qualidade" ? it.id : it.valor;
      if (val === valorAtual) {
        const chk = el("span", { class: "tp-check" });
        chk.innerHTML = ICONE.check;
        b.append(chk);
        b.classList.add("selecionado");
      }
      b.addEventListener("click", () => {
        if (tipo === "qualidade") definirQualidade(val);
        else if (tipo === "audio") definirAudio(val);
        else if (tipo === "legenda") definirLegenda(val);
        else if (tipo === "velocidade") {
          ultimaVelocidade = val;
          if (vjs) vjs.playbackRate(val);
        }
        fecharMenu();
      });
      popupLista.append(b);
    });

    menuAberto = tipo;
    popup.classList.remove("oculto");
    [btnAudio, btnLegenda, btnQualidade, btnVelocidade].forEach(b => b.classList.toggle("ativo", b.dataset.menu === tipo));
    mostrarControles();
  }

  [btnAudio, btnLegenda, btnQualidade, btnVelocidade].forEach(b => {
    b.addEventListener("click", () => {
      if (menuAberto === b.dataset.menu) fecharMenu();
      else abrirMenu(b.dataset.menu);
    });
  });

  /* ---------------- erros e recuperação ---------------- */

  function mostrarErro(msg, comRetry = true) {
    setEstado(ESTADO.ERROR);
    erroEl.innerHTML = "";
    erroEl.append(el("p", {}, msg));
    if (comRetry) {
      const btn = el("button", { class: "btn primary" }, "Tentar novamente");
      btn.addEventListener("click", () => {
        erroEl.classList.add("oculto");
        iniciarFonte(getCurTime(), true);
      });
      erroEl.append(btn);
    }
    erroEl.classList.remove("oculto");
    aoStatus(msg, true);
  }

  /* ---------------- Inicialização do Motor ---------------- */

  function destruirMotor() {
    if (vjs) {
      try { vjs.dispose(); } catch (e) {}
      vjs = null;
    }
    if (blobUrlAtual) {
      URL.revokeObjectURL(blobUrlAtual);
      blobUrlAtual = null;
    }
  }

  function iniciarFonte(tempoInicial = 0, autoplayApósCarregar = true) {
    setEstado(ESTADO.LOADING);
    aoStatus("Preparando transmissão...");

    // CORREÇÃO CRÍTICA: Se for uma string bruta (m3u8), cria um Blob URL real
    let srcUrl = m3u8;
    if (m3u8 && typeof m3u8 === "string" && m3u8.trim().startsWith("#EXTM3U")) {
      const blob = new Blob([m3u8], { type: "application/vnd.apple.mpegurl" });
      srcUrl = URL.createObjectURL(blob);
      blobUrlAtual = srcUrl;
    }

    carregarVideoJs().then(videojs => {
      if (morto) return;
      
      // O vjs substitui a tag video por um div.video-js, mantendo nossa estrutura intacta
      vjs = videojs(video, {
        controls: false,
        autoplay: autoplayApósCarregar,
        preload: "auto",
        fill: true, // Faz o player herdar 100% de largura/altura da div `.tp`
        html5: {
          vhs: {
            overrideNative: true,
            maxPlaylistRetries: 6
          }
        }
      });

      // Binds de Eventos Originais mapeados para o Video.js
      vjs.on("loadedmetadata", () => {
        atualizarProgressoVisual();
        setEstado(ESTADO.READY);
        aoStatus("");
      });
      vjs.on("play", () => {
        btnPlay.innerHTML = ICONE.pause;
        btnPlay.setAttribute("aria-label", "Pausar");
        setEstado(ESTADO.PLAYING);
        mostrarControles();
      });
      vjs.on("pause", () => {
        btnPlay.innerHTML = ICONE.play;
        btnPlay.setAttribute("aria-label", "Reproduzir");
        if (estado !== ESTADO.ENDED) setEstado(ESTADO.PAUSED);
        mostrarControles();
      });
      vjs.on("waiting", () => {
        if (!vjs.seeking()) setEstado(ESTADO.BUFFERING);
      });
      vjs.on("playing", () => setEstado(ESTADO.PLAYING));
      vjs.on("seeking", () => setEstado(ESTADO.SEEKING));
      vjs.on("seeked", () => setEstado(vjs.paused() ? ESTADO.PAUSED : ESTADO.PLAYING));
      vjs.on("ended", () => {
        setEstado(ESTADO.ENDED);
        ui.classList.add("on");
        clearTimeout(timerControles);
      });
      vjs.on("timeupdate", () => {
        if (!arrastando) atualizarProgressoVisual();
      });
      
      // Quando clicar no palco do vídeo real
      vjs.on("click", () => {
        alternarPlayPause();
        mostrarControles();
      });

      vjs.on("error", () => {
        if (morto) return;
        mostrarErro("Não foi possível carregar o vídeo. Verifique sua conexão.", true);
      });

      // Carrega a fonte (segura via Blob ou URL direta)
      vjs.src({ src: srcUrl, type: "application/vnd.apple.mpegurl" });
      vjs.currentTime(tempoInicial);

    }).catch(e => {
      console.error(e);
      if (!morto) mostrarErro("Falha ao carregar o motor Video.js.");
    });
  }

  /* ---------------- Inicialização e Destruição ---------------- */

  iniciarMonitor();
  iniciarFonte(0, true);
  mostrarControles();
  raiz.focus();

  function _destruirInterno() {
    morto = true;
    clearTimeout(timerControles);
    clearTimeout(seekTimer);
    clearInterval(monitorId);
    if (pararOnline) pararOnline();
    window.removeEventListener("pointermove", moverArraste);
    window.removeEventListener("pointerup", soltar);
    
    destruirMotor();
    
    if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
    try { raiz.remove(); } catch (e) {}
    setEstado(ESTADO.DESTROYED);
  }

  return { _destruirInterno };
}
