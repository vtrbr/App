package com.tedflix.app.sources

import java.net.HttpURLConnection
import java.net.URI

object SourceProbe {
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000
    private const val MAX_BODY_BYTES = 32 * 1024

    fun check(source: SourceDefinition): SourceProbeResult {
        val started = System.currentTimeMillis()
        val url = if (source.id == SourceRegistry.PRIMARY_ID) {
            "${source.baseUrl}/home/carousel"
        } else {
            source.baseUrl
        }

        val connection = runCatching {
            val uri = URI(url)
            require(uri.scheme == "http" || uri.scheme == "https") { "URL sem suporte" }
            (uri.toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("Accept", "application/json,text/html;q=0.9,*/*;q=0.8")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Referer", "${source.baseUrl}/")
            }
        }.getOrElse { error ->
            return SourceProbeResult(
                sourceId = source.id,
                state = SourceProbeState.INVALID,
                elapsedMs = System.currentTimeMillis() - started,
                message = error.message ?: "URL inválida",
            )
        }

        return try {
            val code = connection.responseCode
            val body = runCatching {
                val stream = if (code >= 400) connection.errorStream else connection.inputStream
                stream?.use { readPrefix(it, MAX_BODY_BYTES) }.orEmpty()
            }.getOrDefault("")
            val parked = body.contains("forsale.godaddy.com", ignoreCase = true) ||
                body.contains("Access Denied", ignoreCase = true) ||
                body.contains("domínio está à venda", ignoreCase = true)
            val elapsed = System.currentTimeMillis() - started
            when {
                code in 200..399 && !parked -> SourceProbeResult(
                    sourceId = source.id,
                    state = SourceProbeState.ONLINE,
                    httpCode = code,
                    elapsedMs = elapsed,
                    message = "Fonte respondeu corretamente",
                )
                parked -> SourceProbeResult(
                    sourceId = source.id,
                    state = SourceProbeState.OFFLINE,
                    httpCode = code,
                    elapsedMs = elapsed,
                    message = "Domínio estacionado ou indisponível",
                )
                else -> SourceProbeResult(
                    sourceId = source.id,
                    state = SourceProbeState.OFFLINE,
                    httpCode = code,
                    elapsedMs = elapsed,
                    message = "Resposta HTTP $code",
                )
            }
        } catch (error: Exception) {
            SourceProbeResult(
                sourceId = source.id,
                state = SourceProbeState.OFFLINE,
                elapsedMs = System.currentTimeMillis() - started,
                message = error.message ?: "Falha de conexão",
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun readPrefix(stream: java.io.InputStream, maxBytes: Int): String {
        val buffer = ByteArray(4 * 1024)
        val output = java.io.ByteArrayOutputStream()
        var remaining = maxBytes
        while (remaining > 0) {
            val read = stream.read(buffer, 0, minOf(buffer.size, remaining))
            if (read <= 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
        return output.toByteArray().toString(Charsets.UTF_8)
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
}
