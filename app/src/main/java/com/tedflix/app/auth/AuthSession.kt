package com.tedflix.app.auth

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Sessão do Tedflix. O bearer nunca é colocado em localStorage, URLs ou logs.
 * O token é cifrado com uma chave AES-GCM mantida no Android Keystore.
 */
object AuthSession {
    private const val AUTH_BASE = "https://auth.cryptitys.site"
    const val MOVIE_API_BASE = "https://ted.cryptitys.site/api"
    const val MOVIE_API_HOST = "ted.cryptitys.site"
    private const val PREFS = "tedflix_auth_session"
    private const val TOKEN_KEY = "encrypted_access_token"
    private const val KEY_ALIAS = "TedflixAuthBearerKey"
    private const val PROFILES_PREFS = "tedflix_saved_profiles"
    private const val PROFILES_KEY = "profiles_json"
    private const val ACTIVE_PROFILE_KEY = "active_profile_id"

    data class User(
        val id: String = "",
        val email: String = "",
        val username: String = "",
        val expiresAt: String = "",
        val accountStatus: String = "",
        val daysRemaining: Int? = null,
    )

    data class Profile(
        val id: String,
        val name: String,
        val avatarSeed: String,
        val avatarStyle: String = "fun-emoji",
        val email: String = "",
    )

    private data class StoredProfile(
        val id: String,
        val name: String,
        val avatarSeed: String,
        val avatarStyle: String,
        val email: String,
        val user: User,
        val encryptedToken: String,
    )

    data class Notification(
        val id: String,
        val type: String,
        val title: String,
        val body: String,
        val read: Boolean,
        val sentAt: String,
    )

    data class Favorite(
        val filmeId: String,
        val titulo: String,
        val thumb: String,
        val adicionadoEm: String,
        val categoria: String = "",
        val slug: String = "",
        val tipo: String = "",
    )

    data class CatalogMatch(
        val filmeId: String,
        val titulo: String,
        val thumb: String,
        val categoria: String,
        val slug: String,
        val tipo: String,
    )

    data class HistoryItem(
        val filmeId: String,
        val titulo: String,
        val tempo: String,
        val thumb: String,
        val ultimoAcesso: String,
        val categoria: String = "",
        val slug: String = "",
        val tipo: String = "",
        val serieCategoria: String = "",
        val serieSlug: String = "",
    )

    data class Result<T>(
        val ok: Boolean,
        val value: T? = null,
        val message: String = "",
        val statusCode: Int = 0,
    )

    data class RawResponse(
        val statusCode: Int,
        val body: String,
        val contentType: String,
    )

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun hasToken(): Boolean = token().isNullOrBlank().not()

    fun token(): String? {
        if (!::appContext.isInitialized) return null
        val stored = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(TOKEN_KEY, null) ?: return null
        return try {
            decrypt(stored)
        } catch (_: Throwable) {
            clear()
            null
        }
    }

    fun saveLogin(accessToken: String, user: User?) {
        require(accessToken.isNotBlank())
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(TOKEN_KEY, encrypt(accessToken))
            .putString("user_id", user?.id.orEmpty())
            .putString("user_email", user?.email.orEmpty())
            .putString("user_name", user?.username.orEmpty())
            .putString("expires_at", user?.expiresAt.orEmpty())
            .putString("account_status", user?.accountStatus.orEmpty())
            .putInt("days_remaining", user?.daysRemaining ?: -1)
            .apply()
    }

    fun cachedUser(): User? {
        if (!::appContext.isInitialized || !hasToken()) return null
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return User(
            id = prefs.getString("user_id", "").orEmpty(),
            email = prefs.getString("user_email", "").orEmpty(),
            username = prefs.getString("user_name", "").orEmpty(),
            expiresAt = prefs.getString("expires_at", "").orEmpty(),
            accountStatus = prefs.getString("account_status", "").orEmpty(),
            daysRemaining = prefs.getInt("days_remaining", -1).takeIf { it >= 0 },
        )
    }

    fun clear() {
        if (!::appContext.isInitialized) return
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        appContext.getSharedPreferences(PROFILES_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun profiles(): List<Profile> = readStoredProfiles().map { it.toPublic() }

    fun activeProfileId(): String = if (::appContext.isInitialized) {
        appContext.getSharedPreferences(PROFILES_PREFS, Context.MODE_PRIVATE)
            .getString(ACTIVE_PROFILE_KEY, "").orEmpty()
    } else ""

    fun ensureCurrentProfile(defaultName: String, defaultAvatarSeed: String): Profile? {
        val currentToken = token()?.takeIf { it.isNotBlank() } ?: return null
        val stored = readStoredProfiles()
        val existing = stored.firstOrNull { profile ->
            try { decrypt(profile.encryptedToken) == currentToken } catch (_: Throwable) { false }
        }
        if (existing != null) {
            saveStoredProfiles(stored, existing.id)
            return existing.toPublic()
        }
        val user = cachedUser() ?: User()
        val profile = StoredProfile(
            id = "profile-${java.util.UUID.randomUUID()}",
            name = defaultName.trim().ifBlank { user.username.ifBlank { user.email.ifBlank { "Meu perfil" } } },
            avatarSeed = defaultAvatarSeed,
            avatarStyle = "fun-emoji",
            email = user.email,
            user = user,
            encryptedToken = encrypt(currentToken),
        )
        saveStoredProfiles(stored + profile, profile.id)
        return profile.toPublic()
    }

    fun activateProfile(id: String): Result<Profile> {
        val profile = readStoredProfiles().firstOrNull { it.id == id }
            ?: return Result(false, message = "Perfil não encontrado.")
        return try {
            val accessToken = decrypt(profile.encryptedToken)
            saveLogin(accessToken, profile.user)
            appContext.getSharedPreferences(PROFILES_PREFS, Context.MODE_PRIVATE).edit()
                .putString(ACTIVE_PROFILE_KEY, profile.id).apply()
            Result(true, profile.toPublic(), statusCode = 200)
        } catch (error: Throwable) {
            Result(false, message = "Não foi possível ativar este perfil.")
        }
    }

    fun createProfile(code: String, email: String, password: String, name: String, avatarSeed: String): Result<Profile> {
        ensureCurrentProfile(cachedUser()?.username.orEmpty(), "tedflix-avatar-01")
        return try {
            val body = JSONObject()
                .put("code", code.trim())
                .put("email", email.trim())
                .put("password", password)
            val response = rawRequest("POST", "/auth/login", body.toString(), includeBearer = false)
            val json = parseObject(response.body)
            if (response.statusCode !in 200..299) {
                Result(false, message = serverMessage(json, response.statusCode), statusCode = response.statusCode)
            } else {
                val token = json.optString("token").trim()
                if (token.isBlank()) {
                    Result(false, message = "O servidor não devolveu uma sessão válida.", statusCode = response.statusCode)
                } else {
                    val user = parseUser(json.optJSONObject("user")) ?: User(email = email.trim())
                    val profile = StoredProfile(
                        id = "profile-${java.util.UUID.randomUUID()}",
                        name = name.trim().ifBlank { user.username.ifBlank { email.trim() } },
                        avatarSeed = avatarSeed.ifBlank { "tedflix-avatar-01" },
                        avatarStyle = "fun-emoji",
                        email = user.email.ifBlank { email.trim() },
                        user = user,
                        encryptedToken = encrypt(token),
                    )
                    saveStoredProfiles(readStoredProfiles() + profile, activeProfileId())
                    Result(true, profile.toPublic(), statusCode = response.statusCode)
                }
            }
        } catch (error: Throwable) {
            Result(false, message = friendlyNetworkError(error), statusCode = 0)
        }
    }

    fun updateProfile(id: String, name: String, avatarSeed: String): Boolean {
        val updated = readStoredProfiles().map { profile ->
            if (profile.id == id) profile.copy(
                name = name.trim().ifBlank { profile.name },
                avatarSeed = avatarSeed.ifBlank { profile.avatarSeed },
            ) else profile
        }
        if (updated.none { it.id == id }) return false
        saveStoredProfiles(updated, activeProfileId())
        return true
    }

    fun deleteProfile(id: String): Boolean {
        val stored = readStoredProfiles()
        if (stored.size <= 1 || stored.none { it.id == id }) return false
        val remaining = stored.filterNot { it.id == id }
        val active = activeProfileId()
        val nextId = if (active == id) remaining.first().id else active
        saveStoredProfiles(remaining, nextId)
        if (active == id) activateProfile(nextId)
        return true
    }

    fun login(code: String, email: String, password: String): Result<User> {
        return try {
            val body = JSONObject()
                .put("code", code.trim())
                .put("email", email.trim())
                .put("password", password)
            val response = rawRequest("POST", "/auth/login", body.toString(), includeBearer = false)
            val json = parseObject(response.body)
            if (response.statusCode !in 200..299) {
                Result(false, message = serverMessage(json, response.statusCode), statusCode = response.statusCode)
            } else {
                val token = json.optString("token").trim()
                if (token.isBlank()) {
                    Result(false, message = "O servidor não devolveu uma sessão válida.", statusCode = response.statusCode)
                } else {
                    val user = parseUser(json.optJSONObject("user"))
                    saveLogin(token, user)
                    Result(true, user, statusCode = response.statusCode)
                }
            }
        } catch (error: Throwable) {
            Result(false, message = friendlyNetworkError(error), statusCode = 0)
        }
    }

    fun verify(): Result<User> {
        return authenticatedJson("GET", "/auth/verify").map { json ->
            val user = parseUser(json.optJSONObject("user"))
            if (user != null) saveLogin(token().orEmpty(), user)
            user ?: cachedUser() ?: User()
        }
    }

    fun profile(): Result<User> = authenticatedJson("GET", "/users/me").map { json ->
        val user = parseUser(json)
        if (user != null) saveLogin(token().orEmpty(), user)
        user ?: User()
    }

    fun status(): Result<JSONObject> = authenticatedJson("GET", "/users/me/status")

    fun updateUsername(username: String): Result<JSONObject> {
        val clean = username.trim()
        if (clean.isBlank()) return Result(false, message = "Informe um nome para o perfil.")
        val result = authenticatedJson(
            "PATCH",
            "/users/me",
            JSONObject().put("username", clean).toString(),
        )
        if (result.ok) {
            val current = cachedUser()
            if (current != null) saveLogin(token().orEmpty(), current.copy(username = clean))
        }
        return result
    }

    fun changePassword(currentPassword: String, newPassword: String): Result<JSONObject> = authenticatedJson(
        "PATCH",
        "/auth/password",
        JSONObject()
            .put("currentPassword", currentPassword)
            .put("newPassword", newPassword)
            .toString(),
    )

    fun notifications(): Result<List<Notification>> = authenticatedJson("GET", "/users/me/notifications").map { json ->
        val list = mutableListOf<Notification>()
        val array = json.optJSONArray("notifications") ?: JSONArray()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            list += Notification(
                id = item.optString("id"),
                type = item.optString("type"),
                title = item.optString("title"),
                body = item.optString("body"),
                read = item.optBoolean("read", false),
                sentAt = item.optString("sentAt"),
            )
        }
        list
    }

    fun markNotificationRead(id: String): Result<JSONObject> = authenticatedJson(
        "PATCH",
        "/users/me/notifications/${Uri.encode(id)}/read",
    )

    fun listFavorites(): Result<List<Favorite>> = authenticatedJson("GET", "/api/favorites/list").map { json ->
        val array = json.optJSONArray("favoritos") ?: json.optJSONArray("favorites") ?: JSONArray()
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    Favorite(
                        filmeId = item.optString("filmeId"),
                        titulo = item.optString("titulo"),
                        thumb = item.optString("thumb").ifBlank { item.optString("imagem") },
                        adicionadoEm = item.optString("adicionadoEm"),
                        categoria = item.optString("categoria"),
                        slug = item.optString("slug"),
                        tipo = item.optString("tipo"),
                    ),
                )
            }
        }
    }

    fun resolveCatalogItem(query: String, filmeId: String = ""): Result<CatalogMatch?> {
        if (query.isBlank() && filmeId.isBlank()) return Result(true, null, statusCode = 200)
        return authenticatedJson("GET", "/api/buscar/${Uri.encode(query.ifBlank { filmeId })}").map { json ->
            val array = json.optJSONArray("resultados") ?: JSONArray()
            val normalizedQuery = query.trim().lowercase()
            var fallback: CatalogMatch? = null
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val title = item.optString("titulo").trim()
                val id = item.optString("filmeId").ifBlank { item.optString("id").ifBlank { item.optString("_id") } }
                val link = item.optString("link_assistir").replace(Regex("^https?://[^/]+"), "")
                val parts = link.split("/").filter { it.isNotBlank() }
                val category = item.optString("categoria").ifBlank { parts.getOrNull(parts.size - 2).orEmpty() }
                val slug = item.optString("slug").ifBlank { parts.lastOrNull().orEmpty() }
                val match = CatalogMatch(
                    filmeId = id,
                    titulo = title,
                    thumb = item.optString("thumb").ifBlank { item.optString("imagem") },
                    categoria = category,
                    slug = slug,
                    tipo = item.optString("tipo"),
                )
                if (fallback == null) fallback = match
                if ((filmeId.isNotBlank() && id == filmeId) || (normalizedQuery.isNotBlank() && title.lowercase() == normalizedQuery)) return@map match
            }
            fallback
        }
    }

    fun toggleFavorite(filmeId: String, titulo: String, thumb: String): Result<Boolean?> {
        return authenticatedJson(
            "POST",
            "/api/favorites/toggle",
            JSONObject().put("filmeId", filmeId).put("titulo", titulo).put("thumb", thumb).toString(),
        ).map { json ->
            when {
                json.has("favorito") -> json.optBoolean("favorito")
                json.has("favorited") -> json.optBoolean("favorited")
                json.has("isFavorite") -> json.optBoolean("isFavorite")
                json.has("isFavorito") -> json.optBoolean("isFavorito")
                else -> null
            }
        }
    }

    fun continueWatching(): Result<List<HistoryItem>> = authenticatedJson("GET", "/api/history/continue-watching").map { json ->
        val array = json.optJSONArray("continuarAssistindo") ?: json.optJSONArray("history") ?: JSONArray()
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    HistoryItem(
                        filmeId = item.optString("filmeId"),
                        titulo = item.optString("titulo"),
                        tempo = item.optString("tempo"),
                        thumb = item.optString("thumb"),
                        ultimoAcesso = item.optString("ultimoAcesso"),
                        categoria = item.optString("categoria"),
                        slug = item.optString("slug"),
                        tipo = item.optString("tipo"),
                        serieCategoria = item.optString("serieCategoria"),
                        serieSlug = item.optString("serieSlug"),
                    ),
                )
            }
        }
    }

    private fun StoredProfile.toPublic() = Profile(id, name, avatarSeed, avatarStyle, email)

    private fun readStoredProfiles(): List<StoredProfile> {
        if (!::appContext.isInitialized) return emptyList()
        val raw = appContext.getSharedPreferences(PROFILES_PREFS, Context.MODE_PRIVATE)
            .getString(PROFILES_KEY, "[]").orEmpty()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val encrypted = item.optString("encryptedToken").trim()
                    val id = item.optString("id").trim()
                    if (id.isBlank() || encrypted.isBlank()) continue
                    add(StoredProfile(
                        id = id,
                        name = item.optString("name").ifBlank { "Meu perfil" },
                        avatarSeed = item.optString("avatarSeed").ifBlank { "tedflix-avatar-01" },
                        avatarStyle = item.optString("avatarStyle").ifBlank { "fun-emoji" },
                        email = item.optString("email"),
                        user = User(
                            id = item.optString("userId"),
                            email = item.optString("userEmail").ifBlank { item.optString("email") },
                            username = item.optString("username"),
                            expiresAt = item.optString("expiresAt"),
                            accountStatus = item.optString("accountStatus"),
                            daysRemaining = item.optInt("daysRemaining", -1).takeIf { it >= 0 },
                        ),
                        encryptedToken = encrypted,
                    ))
                }
            }
        } catch (_: Throwable) { emptyList() }
    }

    private fun saveStoredProfiles(profiles: List<StoredProfile>, activeId: String) {
        if (!::appContext.isInitialized) return
        val array = JSONArray().apply {
            profiles.forEach { profile -> put(JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name)
                put("avatarSeed", profile.avatarSeed)
                put("avatarStyle", profile.avatarStyle)
                put("email", profile.email)
                put("userId", profile.user.id)
                put("userEmail", profile.user.email)
                put("username", profile.user.username)
                put("expiresAt", profile.user.expiresAt)
                put("accountStatus", profile.user.accountStatus)
                put("daysRemaining", profile.user.daysRemaining ?: -1)
                put("encryptedToken", profile.encryptedToken)
            }) }
        }
        appContext.getSharedPreferences(PROFILES_PREFS, Context.MODE_PRIVATE).edit()
            .putString(PROFILES_KEY, array.toString())
            .putString(ACTIVE_PROFILE_KEY, activeId)
            .apply()
    }

    fun saveProgress(
        filmeId: String,
        titulo: String,
        tempo: String,
        thumb: String,
        categoria: String = "",
        slug: String = "",
        tipo: String = "",
        serieCategoria: String = "",
        serieSlug: String = "",
    ): Result<JSONObject> = authenticatedJson(
        "POST",
        "/api/history/save-progress",
        JSONObject().apply {
            put("filmeId", filmeId)
            put("titulo", titulo)
            put("tempo", tempo)
            put("thumb", thumb)
            if (categoria.isNotBlank()) put("categoria", categoria)
            if (slug.isNotBlank()) put("slug", slug)
            if (tipo.isNotBlank()) put("tipo", tipo)
            if (serieCategoria.isNotBlank()) put("serieCategoria", serieCategoria)
            if (serieSlug.isNotBlank()) put("serieSlug", serieSlug)
        }.toString(),
    )

    fun logout(): Result<JSONObject> {
        return try {
            val response = rawRequest("POST", "/auth/logout", null, includeBearer = true)
            clear()
            val json = parseObject(response.body)
            if (response.statusCode in 200..299 || response.statusCode == 401) {
                Result(true, json, statusCode = response.statusCode)
            } else {
                Result(false, message = serverMessage(json, response.statusCode), statusCode = response.statusCode)
            }
        } catch (error: Throwable) {
            clear()
            Result(true, message = "Sessão removida localmente.")
        }
    }

    /** Proxy para fetch GET da WebView. O bearer fica fora do JavaScript. */
    fun proxyMovieRequest(request: WebResourceRequest): RawResponse? {
        val uri = request.url
        if (uri.host != MOVIE_API_HOST || !uri.path.orEmpty().startsWith("/api/")) return null
        if (!hasToken()) return null
        val path = uri.encodedPath.orEmpty() + uri.encodedQuery.orEmpty().let { if (it.isBlank()) "" else "?$it" }
        return try {
            rawRequest(request.method.ifBlank { "GET" }, path, null, includeBearer = true)
        } catch (error: Throwable) {
            RawResponse(599, JSONObject().put("error", friendlyNetworkError(error)).toString(), "application/json")
        }
    }

    fun toWebResourceResponse(raw: RawResponse): WebResourceResponse {
        val mime = raw.contentType.substringBefore(';').ifBlank { "application/json" }
        val charset = if (raw.contentType.contains("charset=", ignoreCase = true)) "UTF-8" else "UTF-8"
        val reason = when (raw.statusCode) {
            in 200..299 -> "OK"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            429 -> "Too Many Requests"
            else -> "Error"
        }
        return WebResourceResponse(
            mime,
            charset,
            raw.statusCode.coerceIn(100, 599),
            reason,
            mapOf("Cache-Control" to "no-store", "Pragma" to "no-cache"),
            ByteArrayInputStream(raw.body.toByteArray(StandardCharsets.UTF_8)),
        )
    }

    private fun <T> Result<JSONObject>.map(transform: (JSONObject) -> T): Result<T> {
        return if (ok) Result(true, transform(value ?: JSONObject()), statusCode = statusCode)
        else Result(false, message = message, statusCode = statusCode)
    }

    private fun authenticatedJson(method: String, path: String, body: String? = null): Result<JSONObject> {
        return try {
            val response = rawRequest(method, path, body, includeBearer = true)
            val json = parseObject(response.body)
            if (response.statusCode == 401 || response.statusCode == 403) {
                clear()
            }
            if (response.statusCode in 200..299) {
                Result(true, json, statusCode = response.statusCode)
            } else {
                Result(false, message = serverMessage(json, response.statusCode), statusCode = response.statusCode)
            }
        } catch (error: Throwable) {
            Result(false, message = friendlyNetworkError(error), statusCode = 0)
        }
    }

    fun rawRequest(method: String, path: String, body: String? = null, includeBearer: Boolean): RawResponse {
        val url = when {
            path.startsWith("http") -> path
            path.startsWith("/api/") -> "https://$MOVIE_API_HOST$path"
            else -> "$AUTH_BASE$path"
        }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method.uppercase()
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            useCaches = false
            doInput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-store")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            if (includeBearer) {
                token()?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
        }
        return try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..399) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            RawResponse(status, responseBody, connection.contentType ?: "application/json")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseObject(raw: String): JSONObject = try {
        if (raw.trim().startsWith("{")) JSONObject(raw) else JSONObject()
    } catch (_: Throwable) {
        JSONObject()
    }

    private fun parseUser(json: JSONObject?): User? {
        if (json == null) return null
        return User(
            id = json.optString("id"),
            email = json.optString("email"),
            username = json.optString("username"),
            expiresAt = json.optString("accountExpiresAt", json.optString("expiresAt")),
            accountStatus = json.optString("accountStatus", json.optString("status")),
            daysRemaining = if (json.has("daysRemaining")) json.optInt("daysRemaining") else null,
        )
    }

    private fun serverMessage(json: JSONObject, code: Int): String {
        val fromServer = json.optString("error").ifBlank { json.optString("message") }
        if (fromServer.isNotBlank()) return fromServer
        return when (code) {
            400 -> "Confira os dados informados."
            401 -> "Sessão inválida ou credenciais incorretas."
            403 -> "Sua conta não pode acessar o serviço."
            429 -> "Muitas tentativas. Aguarde alguns minutos."
            in 500..599 -> "Servidor indisponível no momento."
            else -> "Não foi possível concluir a operação."
        }
    }

    private fun friendlyNetworkError(error: Throwable): String {
        val message = error.message.orEmpty()
        return if (message.contains("timeout", true) || message.contains("timed out", true)) {
            "O servidor demorou para responder. Tente novamente."
        } else {
            "Não foi possível conectar ao servidor. Verifique sua internet."
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val parts = value.split(":", limit = 2)
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val payload = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(payload), StandardCharsets.UTF_8)
    }
}
