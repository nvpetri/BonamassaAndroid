package br.com.bonamassa.client

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class PostalAddress(val postalCode: String, val street: String, val neighborhood: String, val city: String, val state: String)
fun interface PostalCodeLookup { fun lookup(postalCode: String): PostalAddress }
class PostalCodeNotFound : IOException("CEP não encontrado. Confira os números ou preencha o endereço manualmente.")

/** Dedicated public transport: sends only the CEP, never a Bonamassa session or customer data. */
class ViaCepLookup internal constructor(private val origin: HttpUrl, private val http: OkHttpClient) : PostalCodeLookup {
    constructor() : this("https://viacep.com.br/".toHttpUrl(), OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build())

    override fun lookup(postalCode: String): PostalAddress {
        require(postalCode.matches(Regex("[0-9]{8}"))) { "Informe o CEP com 8 números." }
        val url = origin.newBuilder().addPathSegments("ws/$postalCode/json/").build()
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Não foi possível consultar o CEP. Tente novamente ou preencha manualmente.")
            val body = response.peekBody(65_537).string()
            if (body.length > 65_536) throw IOException("Resposta inválida da consulta de CEP.")
            val data = try { JSONObject(body) } catch (_: Exception) { throw IOException("Resposta inválida da consulta de CEP.") }
            if (data.optBoolean("erro", false)) throw PostalCodeNotFound()
            val cep = data.optString("cep").replace("-", "")
            val city = data.optString("localidade").trim()
            val state = data.optString("uf").trim().uppercase()
            if (cep != postalCode || city.isEmpty() || city.length > 80 || !state.matches(Regex("[A-Z]{2}")))
                throw IOException("A consulta não retornou um endereço válido. Preencha manualmente.")
            // ViaCEP's 'complemento' describes the postal range, not the customer's apartment.
            return PostalAddress(cep, data.optString("logradouro").trim().take(120),
                data.optString("bairro").trim().take(80), city, state)
        }
    }
}
