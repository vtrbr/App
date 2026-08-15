import { el } from "../dom.js";
import { getAgenda } from "../api.js";
import { backdrop } from "../img.js";

export default async function paginaAgenda(raiz) {
  const page = el("div", { class: "page" });
  const corpo = el("div", {});
  page.append(el("h1", { class: "page-title" }, "Agenda"), corpo);
  raiz.append(page);

  corpo.append(el("div", { class: "center" }, [el("div", { class: "spinner" })]));

  try {
    const dias = await getAgenda();
    corpo.innerHTML = "";
    if (!dias.length) {
      corpo.append(el("p", { class: "center" }, "Nenhum episódio agendado."));
      return page;
    }
    dias.forEach((dia) => {
      const bloco = el("section", { class: "section" }, [
        el("div", { class: "section-head" }, [
          el("h2", {}, dia.nome || dia.data || ""),
          el("a", {}, `${(dia.episodios || []).length} ep.`),
        ]),
      ]);
      (dia.episodios || []).forEach((ep) => {
        bloco.append(
          el("div", { class: "ep" }, [
            el("div", { class: "cap" }, [
              ep.imagem
                ? el("img", {
                    src: backdrop(ep.imagem, 342),
                    alt: ep.titulo || "",
                    loading: "lazy",
                    decoding: "async",
                  })
                : null,
            ]),
            el("div", { style: "min-width:0" }, [
              el("p", { class: "t" }, ep.serie || ep.titulo || ""),
              el("p", { class: "d" }, [ep.episodio, ep.horario].filter(Boolean).join(" · ")),
            ]),
          ]),
        );
      });
      corpo.append(bloco);
    });
  } catch (e) {
    corpo.innerHTML = "";
    corpo.append(el("p", { class: "center" }, "Não foi possível carregar a agenda."));
  }

  return page;
}
