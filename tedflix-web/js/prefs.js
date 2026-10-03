/** Preferências da conta, guardadas no navegador. */

const CHAVE = "tedflix:prefs";

const PADRAO = {
  player: "auto", // "vidstack" | "videojs" | "auto"
  autoQualidade: true,
  buffer: "equilibrado", // "economico" | "equilibrado" | "generoso"
};

export function lerPrefs() {
  try {
    return { ...PADRAO, ...(JSON.parse(localStorage.getItem(CHAVE) || "{}") || {}) };
  } catch (e) {
    return { ...PADRAO };
  }
}

export function salvarPrefs(parcial) {
  const novo = { ...lerPrefs(), ...parcial };
  try {
    localStorage.setItem(CHAVE, JSON.stringify(novo));
  } catch (e) {
    /* ignora */
  }
  return novo;
}

export const OPCOES_PLAYER = [
  {
    valor: "auto",
    titulo: "Automático (recomendado)",
    desc: "Começa no Video.js (melhor em rede lenta) e troca sozinho para o Vidstack se algo falhar.",
  },
  { valor: "videojs", titulo: "Video.js + VHS", desc: "ABR maduro, ótimo em conexões lentas ou instáveis." },
  { valor: "vidstack", titulo: "Vidstack", desc: "Interface moderna estilo Netflix, usando HLS.js." },
];

export const OPCOES_BUFFER = [
  { valor: "economico", titulo: "Econômico", desc: "Menos dados, começa mais rápido." },
  { valor: "equilibrado", titulo: "Equilibrado", desc: "30s de buffer — padrão." },
  { valor: "generoso", titulo: "Generoso", desc: "Até 90s à frente, quase não trava." },
];
