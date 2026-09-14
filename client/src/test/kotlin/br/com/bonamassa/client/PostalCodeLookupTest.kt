package br.com.bonamassa.client

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PostalCodeLookupTest {
    private fun response(cep: String = "01001-000", street: String = "Praça da Sé", neighborhood: String = "Sé") = objectOf(
        "cep" to cep, "logradouro" to street, "bairro" to neighborhood, "localidade" to "São Paulo", "uf" to "SP", "complemento" to "lado ímpar").toString()

    @Test fun validCepFillsOnlyPostalFieldsWithoutBonamassaCredentials() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(response()))
            val result = ViaCepLookup(server.url("/"), OkHttpClient()).lookup("01001000")
            assertEquals(PostalAddress("01001000", "Praça da Sé", "Sé", "São Paulo", "SP"), result)
            val request = server.takeRequest()
            assertEquals("/ws/01001000/json/", request.path)
            assertNull(request.getHeader("Authorization")); assertNull(request.getHeader("Cookie"))
            assertEquals(0L, request.bodySize)
        }
    }
    @Test fun invalidCepNeverMakesAnHttpRequest() {
        MockWebServer().use { server ->
            val lookup = ViaCepLookup(server.url("/"), OkHttpClient())
            for (cep in listOf("", "123", "01001-000", "abcdefgh", "010010000")) {
                assertThrows(IllegalArgumentException::class.java) { lookup.lookup(cep) }
            }
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun notFoundAndUnavailableAndMalformedResponsesAreRecoverable() {
        MockWebServer().use { server ->
            val lookup = ViaCepLookup(server.url("/"), OkHttpClient.Builder().followRedirects(false).build())
            for (body in listOf("{\"erro\":true}", "{\"erro\":\"true\"}")) {
                server.enqueue(MockResponse().setBody(body))
                assertThrows(PostalCodeNotFound::class.java) { lookup.lookup("01001000") }
            }
            for (reply in listOf(MockResponse().setResponseCode(503), MockResponse().setBody("<html>erro</html>"), MockResponse().setBody(response("02002-000")), MockResponse().setResponseCode(302).setHeader("Location", "https://example.com/"))) {
                server.enqueue(reply)
                assertThrows(IOException::class.java) { lookup.lookup("01001000") }
            }
            server.enqueue(MockResponse().setBody(response(street = "", neighborhood = "")))
            assertEquals("", lookup.lookup("01001000").street)
        }
    }
    @Test fun addressAndNoComplementRoundTripWithoutLosingLegacyReference() {
        val address = Address("Rua", "10A", "Centro", "São Paulo", "SP", "01001000", "Portão azul", "Apto 12")
        address.validate()
        assertEquals(address, Decode.address(address.json()))
        assertThrows(IllegalArgumentException::class.java) { address.copy(number = " ").validate() }
        assertThrows(IllegalArgumentException::class.java) { address.copy(noComplement = true).validate() }
        val absent = address.copy(complement = "", noComplement = true)
        absent.validate()
        val saved = SavedState(checkout = Checkout(address = absent))
        assertEquals(saved, SavedCodec.decode(SavedCodec.encode(saved)))
        val old = JSONObject(address.json().toString()).apply { remove("complement"); remove("noComplement") }
        assertEquals("Portão azul", Decode.address(old).reference)
        assertFalse(Decode.address(old).noComplement)
    }
}
