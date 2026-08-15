import { el, anoCurto } from "../dom.js";
import { backdrop } from "../img.js";
import { rotaDetalhe } from "../api.js";

/** Carrossel de destaques com troca automática (pausa quando fora da tela). */
export function hero(itens) {
  const raiz = el("div", { class: "hero" });
  if (!itens.length) {
    raiz.classList.add("skel");
    return raiz;
  }

  const img = el("img", { alt: "", decoding: "async", fetchpriority: "high" });
  const h1 = el("h1");
  const tags = el("div", { class: "tags" });
  const desc = el("p");
  const btn = el("a", {
    class: "btn",
    html: '<svg viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>Assistir',
  });
  const dots = el("div", { class: "dots" });

  raiz.append(
    img,
    el("div", { class: "veil" }),
    el("div", { class: "info" }, [tags, h1, desc, el("div", { style: "margin-top:12px" }, [btn])]),
    dots,
  );

  itens.forEach(() => dots.append(el("i")));

  let atual = 0;
  function mostrar(i) {
    atual = i % itens.length;
    const it = itens[atual];
    img.src = backdrop(it.imagem, 780);
    img.alt = it.titulo || "";
    h1.textContent = it.titulo || "";
    desc.textContent = it.descricao || "";
    btn.href = rotaDetalhe(it);
    tags.innerHTML = "";
    const partes = [
      it.nota ? `★ ${Number(it.nota).toFixed(1)}` : null,
      it.tipo,
      anoCurto(it.ano),
    ].filter(Boolean);
    partes.forEach((p) => tags.append(el("span", {}, p)));
    [...dots.children].forEach((d, k) => d.classList.toggle("on", k === atual));
    // pré-carrega o próximo slide
    const prox = itens[(atual + 1) % itens.length];
    if (prox) new Image().src = backdrop(prox.imagem, 780);
  }

  mostrar(0);

  let timer = null;
  const rodar = () => {
    parar();
    timer = setInterval(() => mostrar(atual + 1), 6000);
  };
  const parar = () => {
    if (timer) clearInterval(timer);
    timer = null;
  };

  const io = new IntersectionObserver((e) => (e[0].isIntersecting ? rodar() : parar()));
  io.observe(raiz);
  raiz.destruir = () => {
    parar();
    io.disconnect();
  };

  return raiz;
}
