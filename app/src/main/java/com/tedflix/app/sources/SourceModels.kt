package com.tedflix.app.sources

/** Tipos de conteúdo declarados pelos plugins do projeto enviado. */
enum class SourceContentType {
    MOVIES,
    SERIES,
    ANIME,
    ASIAN_DRAMA,
    LIVE,
}

enum class SourceSelectionMode {
    PRIMARY,
    SINGLE,
    ALL,
}

data class SourceDefinition(
    val id: String,
    val name: String,
    val description: String,
    val baseUrl: String,
    val types: Set<SourceContentType>,
    val iconUrl: String? = null,
    val availableInManifest: Boolean = true,
)

enum class SourceProbeState {
    UNKNOWN,
    CHECKING,
    ONLINE,
    OFFLINE,
    INVALID,
}

data class SourceProbeResult(
    val sourceId: String,
    val state: SourceProbeState,
    val httpCode: Int? = null,
    val elapsedMs: Long? = null,
    val message: String,
)

object SourceRegistry {
    const val PRIMARY_ID = "tedflix"
    const val ALL_ID = "all"

    val definitions: List<SourceDefinition> = listOf(
        SourceDefinition(
            id = PRIMARY_ID,
            name = "Servidor Tedflix",
            description = "Catálogo principal e player HLS do seu servidor.",
            baseUrl = "https://tedtv.onrender.com/api",
            types = setOf(SourceContentType.MOVIES, SourceContentType.SERIES),
            availableInManifest = false,
        ),
        SourceDefinition(
            id = "streamflix",
            name = "StreamFlix",
            description = "Provider de filmes e séries com API JSON própria.",
            baseUrl = "https://streamflix.live",
            types = setOf(SourceContentType.MOVIES, SourceContentType.SERIES),
            iconUrl = "https://openclipart.org/image/2400px/svg_to_png/193323/-S.png",
        ),
        SourceDefinition(
            id = "cineagora",
            name = "CineAgora",
            description = "Provider de filmes, séries e animes baseado em páginas HTML.",
            baseUrl = "https://cineagora.net",
            types = setOf(SourceContentType.MOVIES, SourceContentType.SERIES, SourceContentType.ANIME),
            iconUrl = "https://cineagora.net/templates/cineagora/images/touch-icon-180x180.png",
        ),
        SourceDefinition(
            id = "mendigoflix",
            name = "MendigoFlix",
            description = "Provider de filmes, séries e animes com extractors próprios.",
            baseUrl = "https://mendigoflix.lol",
            types = setOf(SourceContentType.MOVIES, SourceContentType.SERIES, SourceContentType.ANIME),
            iconUrl = "https://mendigoflix.lol/assets/favicon.png",
        ),
        SourceDefinition(
            id = "pobreflix",
            name = "PobreFlix",
            description = "Provider de filmes, séries, animes e doramas.",
            baseUrl = "https://lospobreflix.site",
            types = setOf(
                SourceContentType.MOVIES,
                SourceContentType.SERIES,
                SourceContentType.ANIME,
                SourceContentType.ASIAN_DRAMA,
            ),
            iconUrl = "https://www.image2url.com/r2/default/images/1776018665375-eafe8c65-10f1-490c-9994-2f519402b6e3.png",
        ),
        SourceDefinition(
            id = "animefire",
            name = "AnimeFire",
            description = "Provider especializado em animes.",
            baseUrl = "https://animefire.io",
            types = setOf(SourceContentType.ANIME),
            iconUrl = "https://animefire.io/img/icons/favicon-192x192.png",
        ),
        SourceDefinition(
            id = "anitube",
            name = "AniTube",
            description = "Provider especializado em animes.",
            baseUrl = "https://www.anitube.news",
            types = setOf(SourceContentType.ANIME),
            iconUrl = "https://www.anitube.news/wp-content/uploads/cropped-Favicon6-192x192.png",
        ),
        SourceDefinition(
            id = "goyabu",
            name = "Goyabu",
            description = "Provider especializado em animes.",
            baseUrl = "https://goyabu.io",
            types = setOf(SourceContentType.ANIME),
            iconUrl = "https://goyabu.io/wp-content/uploads/2025/12/cropped-goyabu-favicon-192x192.png",
        ),
        SourceDefinition(
            id = "dattebayo",
            name = "DattebayoBR",
            description = "Provider de animes e doramas asiáticos.",
            baseUrl = "https://www.dattebayo-br.com",
            types = setOf(SourceContentType.ANIME, SourceContentType.ASIAN_DRAMA),
            iconUrl = "https://www.dattebayo-br.com/favicon.png",
        ),
        SourceDefinition(
            id = "doramogo",
            name = "Doramogo",
            description = "Provider especializado em doramas.",
            baseUrl = "https://www.doramogo.net",
            types = setOf(SourceContentType.ASIAN_DRAMA),
            iconUrl = "https://www.doramogo.net/assets/doramogo/images/apple-touch-icon.webp?version=200.50.10",
        ),
        SourceDefinition(
            id = "embedtv",
            name = "EmbedTV",
            description = "Provider de canais de TV ao vivo.",
            baseUrl = "https://www4.embedtv.cv",
            types = setOf(SourceContentType.LIVE),
            iconUrl = "https://embedtv.best/assets/icon.png",
        ),
        SourceDefinition(
            id = "reidosembeds",
            name = "ReiDosEmbeds",
            description = "Diretório de canais ao vivo.",
            baseUrl = "https://reidosembeds.com",
            types = setOf(SourceContentType.LIVE),
            iconUrl = "https://www.image2url.com/r2/default/images/1776423716151-20e5aa74-a764-4d60-8575-0851d8ab37df.png",
        ),
    )

    fun find(id: String): SourceDefinition? = definitions.firstOrNull { it.id == id }
}
