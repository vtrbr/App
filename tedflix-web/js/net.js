/**
 * Medidor de velocidade de rede.
 * - Faz um download real (Cloudflare speed endpoint, com CORS) lendo o stream
 *   em pedaços, então funciona mesmo se a conexão cair no meio.
 * - Cai para navigator.connection.downlink quando o teste não é possível.
 * O resultado é usado em dois lugares: tamanho das capas e configuração do
 * player (buffer inicial / qualidade de partida).
 */

const CHAVE = "tedflix:rede";
const URL_TESTE = "https://speed.cloudflare.com/__down?bytes=1500000";
const VALIDADE = 5 * 60 * 1000;

let emVoo = null;
const ouvintes = new Set();

export function lerCache() {
  try {
    const bruto = JSON.parse(localStorage.getItem(CHAVE) || "null");
    if (bruto && Date.now() - bruto.quando < VALIDADE) return bruto;
    return bruto ? { ...bruto, expirado: true } : null;
  } catch (e) {
    return null;
  }
}

function salvar(dado) {
  try {
    localStorage.setItem(CHAVE, JSON.stringify(dado));
  } catch (e) {
    /* modo privado */
  }
  ouvintes.forEach((f) => {
    try {
      f(dado);
    } catch (e) {}
  });
}

export function aoMedir(fn) {
  ouvintes.add(fn);
  return () => ouvintes.delete(fn);
}

function doNavegador() {
  const c = navigator.connection || navigator.webkitConnection;
  if (c && typeof c.downlink === "number" && c.downlink > 0) {
    return { mbps: c.downlink, fonte: "navegador", quando: Date.now() };
  }
  return null;
}

/** Classificação usada pelo resto do app. */
export function nivel(mbps) {
  if (!mbps || mbps <= 0) return "desconhecida";
  if (mbps < 1.5) return "lenta";
  if (mbps < 5) return "media";
  if (mbps < 20) return "boa";
  return "rapida";
}

export const ROTULO = {
  lenta: "Lenta",
  media: "Moderada",
  boa: "Boa",
  rapida: "Rápida",
  desconhecida: "Desconhecida",
};

/** Tamanho de capa recomendado para a rede atual. */
export function tamanhoCapa() {
  const n = nivel((lerCache() || {}).mbps ?? doNavegador()?.mbps);
  if (n === "lenta") return "xs";
  if (n === "media") return "sm";
  return "md";
}

/** Se vale a pena baixar imagens extras/2x. */
export function redeGenerosa() {
  const n = nivel((lerCache() || {}).mbps ?? doNavegador()?.mbps);
  return n === "boa" || n === "rapida";
}

/** Executa a medição de verdade (~1,5 MB, no máximo 6s). */
export function medir({ forcar = false } = {}) {
  const cache = lerCache();
  if (!forcar && cache && !cache.expirado) return Promise.resolve(cache);
  if (emVoo) return emVoo;

  emVoo = (async () => {
    const ctrl = new AbortController();
    const limite = setTimeout(() => ctrl.abort(), 6000);
    const inicio = performance.now();
    let bytes = 0;
    try {
      const r = await fetch(`${URL_TESTE}&t=${Date.now()}`, {
        cache: "no-store",
        signal: ctrl.signal,
      });
      if (!r.ok || !r.body) throw new Error("sem corpo");
      const leitor = r.body.getReader();
      for (;;) {
        const { done, value } = await leitor.read();
        if (done) break;
        bytes += value.byteLength;
        if (performance.now() - inicio > 5000) {
          ctrl.abort();
          break;
        }
      }
    } catch (e) {
      /* usa o que conseguiu baixar */
    } finally {
      clearTimeout(limite);
    }

    const seg = (performance.now() - inicio) / 1000;
    let dado;
    if (bytes > 60_000 && seg > 0.15) {
      dado = { mbps: +((bytes * 8) / seg / 1e6).toFixed(2), fonte: "teste", quando: Date.now() };
    } else {
      dado = doNavegador() || { mbps: 0, fonte: "indisponível", quando: Date.now() };
    }
    salvar(dado);
    emVoo = null;
    return dado;
  })();

  return emVoo;
}

/** Mede uma vez sem atrapalhar o carregamento inicial. */
export function medirOcioso() {
  const agenda = window.requestIdleCallback || ((f) => setTimeout(f, 1500));
  agenda(() => medir().catch(() => {}));
}

/** Avisa quando a conexão volta, para o player retomar sozinho. */
export function aoVoltarInternet(fn) {
  window.addEventListener("online", fn);
  return () => window.removeEventListener("online", fn);
}
