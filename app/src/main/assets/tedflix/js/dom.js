export function el(tag, attrs = {}, filhos) {
  const n = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v === undefined || v === null || v === false) continue;
    if (k === "class") n.className = v;
    else if (k === "html") n.innerHTML = v;
    else if (k.startsWith("on") && typeof v === "function") n.addEventListener(k.slice(2), v);
    else n.setAttribute(k, v);
  }
  if (filhos !== undefined) {
    for (const f of [].concat(filhos)) {
      if (f === null || f === undefined || f === false) continue;
      n.append(f.nodeType ? f : document.createTextNode(String(f)));
    }
  }
  return n;
}

export function limpar(no) {
  while (no.firstChild) no.removeChild(no.firstChild);
}

export function debounce(fn, ms = 350) {
  let id;
  return (...a) => {
    clearTimeout(id);
    id = setTimeout(() => fn(...a), ms);
  };
}

/** Executa o callback quando o elemento chega perto da viewport. */
export function aoAparecer(no, cb, margem = "400px") {
  if (!("IntersectionObserver" in window)) {
    cb();
    return () => {};
  }
  const io = new IntersectionObserver(
    (entradas) => {
      if (entradas.some((e) => e.isIntersecting)) {
        io.disconnect();
        cb();
      }
    },
    { rootMargin: margem },
  );
  io.observe(no);
  return () => io.disconnect();
}

export function anoCurto(ano) {
  const m = String(ano || "").match(/\d{4}/);
  return m ? m[0] : "";
}

export const ICONES = {
  home: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.6V21h14V9.6"/>',
  tv: '<rect x="2" y="6" width="20" height="13" rx="2"/><path d="M8 3l4 3 4-3"/>',
  film: '<rect x="3" y="3" width="18" height="18" rx="2"/><path d="M7 3v18M17 3v18M3 12h18"/>',
  grid: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
  calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M8 3v4M16 3v4M3 10h18"/>',
  back: '<path d="M15 5l-7 7 7 7"/>',
  gear: '<circle cx="12" cy="12" r="3.2"/><path d="M19.4 13.5a7.7 7.7 0 0 0 0-3l1.8-1.4-2-3.4-2.1.9a7.7 7.7 0 0 0-2.6-1.5L14.2 2h-4l-.3 2.3a7.7 7.7 0 0 0-2.6 1.5l-2.1-.9-2 3.4 1.8 1.4a7.7 7.7 0 0 0 0 3L3.2 14.9l2 3.4 2.1-.9a7.7 7.7 0 0 0 2.6 1.5l.3 2.3h4l.3-2.3a7.7 7.7 0 0 0 2.6-1.5l2.1.9 2-3.4z"/>',

};

export function svgIcone(nome) {
  return `<svg viewBox="0 0 24 24" aria-hidden="true">${ICONES[nome] || ""}</svg>`;
}
