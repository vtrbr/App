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
  user: '<circle cx="12" cy="8" r="3.5"/><path d="M4.5 21a7.5 7.5 0 0 1 15 0"/>',
  lock: '<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3"/>',
  heart: '<path d="M20.8 8.8c0 5.1-8.8 10.4-8.8 10.4S3.2 13.9 3.2 8.8A4.8 4.8 0 0 1 12 6.2a4.8 4.8 0 0 1 8.8 2.6Z"/>',
  globe: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18"/>',
  play: '<circle cx="12" cy="12" r="9"/><path d="m10 8 6 4-6 4z"/>',
  bell: '<path d="M18 9a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9ZM10 21h4"/>',
  logout: '<path d="M10 17l5-5-5-5M15 12H3M21 19V5a2 2 0 0 0-2-2h-5"/>',
  chevron: '<path d="m9 5 7 7-7 7"/>',
  pencil: '<path d="m4 16.5-.8 4.3 4.3-.8L19 8.5a2.8 2.8 0 0 0-4-4L4 16.5Z"/><path d="m13.5 6.5 4 4"/>',
  camera: '<path d="M4 7h3l1.5-2h7L17 7h3a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2Z"/><circle cx="12" cy="13" r="3.5"/>',
  people: '<circle cx="9" cy="9" r="3"/><circle cx="17" cy="10" r="2.5"/><path d="M3 20a6 6 0 0 1 12 0M15 16a5 5 0 0 1 6 4"/>',
  trash: '<path d="M5 7h14M10 11v6M14 11v6M7 7l1 14h8l1-14M9 7V4h6v3"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  refresh: '<path d="M20 11a8 8 0 0 0-14.7-4L3 10M3 5v5h5M4 13a8 8 0 0 0 14.7 4L21 14m0 5v-5h-5"/>',

};

export function svgIcone(nome) {
  return `<svg viewBox="0 0 24 24" aria-hidden="true">${ICONES[nome] || ""}</svg>`;
}
