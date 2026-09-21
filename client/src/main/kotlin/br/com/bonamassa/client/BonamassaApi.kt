package br.com.bonamassa.client

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class ApiFailure(val status: Int, val code: String, override val message: String, val requestId: String?) : IOException(message) {
    // An ambiguous response must never discard a pending order/cancellation.
    val definitive get() = status in setOf(400, 403, 404, 409, 410, 422) && code != "IDEMPOTENCY_CONFLICT"
}
data class Endpoint(val origin: String, val storeSlug: String) {
    companion object {
        fun parse(origin: String, slug: String, allowHttp: Boolean): Endpoint {
            val url = try { origin.trim().toHttpUrl() } catch (_: Exception) { throw IllegalArgumentException("Informe a URL completa da API, por exemplo http://192.168.1.10:3001.") }
            require(url.scheme == "https" || allowHttp) { "Esta versão exige uma API com HTTPS." }
            require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null && url.encodedPath == "/") { "Informe só o endereço e a porta da API, sem /v1, credenciais ou parâmetros." }
            require(slug.matches(Regex("[a-z0-9-]{1,60}"))) { "Identificador da loja inválido." }
            return Endpoint(url.toString().removeSuffix("/"), slug)
        }
    }
    fun photo(path: String?): String? = path?.takeIf { it.matches(Regex("/v1/stores/${Regex.escape(storeSlug)}/images/[a-fA-F0-9-]{36}")) }?.let { origin + it }
}

/** No redirects, automatic write retries, cookies or HTTP credential logging. */
class BonamassaApi(val endpoint: Endpoint, private val http: OkHttpClient = newHttpClient()) {
    private val reads = http.newBuilder().retryOnConnectionFailure(true).build()
    companion object {
        fun newHttpClient() = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            // Node closes idle HTTP/1 connections after a few seconds. Retire ours first.
            .connectionPool(ConnectionPool(5, 2, TimeUnit.SECONDS))
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    }
    fun request(method: String, path: String, token: String? = null, body: JSONObject? = null, key: String? = null): JSONObject {
        require(path.startsWith("/v1/") && !path.contains(".."))
        val request = Request.Builder().url(endpoint.origin + path).header("Accept", "application/json")
            .method(method, body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType()))
        token?.let { request.header("Authorization", "Bearer $it") }
        key?.let { request.header("Idempotency-Key", it) }
        (if (method == "GET") reads else http).newCall(request.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val error = runCatching { JSONObject(raw) }.getOrNull()
                throw ApiFailure(response.code, error?.optString("code") ?: "HTTP_ERROR",
                    error?.optString("message")?.takeIf { it.isNotBlank() }?.take(400) ?: "O serviço não respondeu como esperado (${response.code}).",
                    error?.textOrNull("requestId"))
            }
            return if (raw.isBlank()) JSONObject() else try { JSONObject(raw) } catch (_: Exception) { throw IOException("Resposta inválida do serviço. Tente atualizar.") }
        }
    }
    fun catalog() = Decode.catalog(request("GET", "/v1/stores/${endpoint.storeSlug}/catalog"))
    private fun emailBody(email: String) = objectOf("storeSlug" to endpoint.storeSlug, "email" to email.trim().lowercase())
    private fun customer(session: Session): Session {
        if (session.user.role != "CUSTOMER") {
            runCatching { logout(session.accessToken) }
            throw ApiFailure(403, "CUSTOMER_ONLY", "Use uma conta de cliente. Contas da equipe entram pelo painel ou app de entregas.", null)
        }
        return session
    }
    fun signIn(email: String, password: String): Session =
        customer(Decode.session(request("POST", "/v1/sessions", body = emailBody(email).put("password", password))))
    @Deprecated("Cadastros novos exigem confirmação de e-mail; use register e confirmEmail.")
    fun signIn(email: String, password: String, name: String, phone: String): Session {
        register(email, password, name, phone)
        return confirmEmail(email, "123456")
    }
    fun register(email: String, password: String, name: String, phone: String) {
        request("POST", "/v1/customers", body = emailBody(email).put("password", password).put("name", name.trim()).put("phone", phone.filter(Char::isDigit)))
    }
    fun requestEmailVerification(email: String) {
        request("POST", "/v1/auth/email-verification/request", body = emailBody(email))
    }
    fun confirmEmail(email: String, code: String): Session =
        customer(Decode.session(request("POST", "/v1/auth/email-verification/confirm", body = emailBody(email).put("code", code.filter(Char::isDigit)))))
    fun requestPasswordReset(email: String) {
        request("POST", "/v1/auth/password-reset/request", body = emailBody(email))
    }
    fun resetPassword(email: String, code: String, newPassword: String) {
        request("POST", "/v1/auth/password-reset/confirm", body = emailBody(email).put("code", code.filter(Char::isDigit)).put("newPassword", newPassword))
    }
    fun me(token: String) = Decode.user(request("GET", "/v1/me", token))
    fun logout(token: String) { request("DELETE", "/v1/sessions/current", token) }
    fun quote(token: String, lines: List<DraftLine>, checkout: Checkout, allowScheduling: Boolean = false) = Decode.quote(request("POST", "/v1/orders/quote", token, checkout.request(lines, allowScheduling), UUID.randomUUID().toString()))
    fun send(token: String, pending: Pending) = Decode.order(request("POST", pending.path, token, JSONObject(pending.body), pending.key))
    fun order(token: String, id: String): Order {
        UUID.fromString(id)
        return Decode.order(request("GET", "/v1/orders/$id", token))
    }
    fun orders(token: String, cursor: String? = null, status: Status? = null): Page {
        val url = (endpoint.origin + "/v1/me/orders").toHttpUrl().newBuilder().addQueryParameter("limit", "30")
        cursor?.let { url.addQueryParameter("cursor", it) }; status?.let { url.addQueryParameter("status", it.name) }
        return Decode.page(request("GET", "/v1/me/orders?${url.build().encodedQuery}", token))
    }
}
