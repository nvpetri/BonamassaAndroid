package br.com.bonamassa.client

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Pending(val path: String, val body: String, val key: String, val ownerId: String, val storeId: String, val origin: String) {
    val isOrder get() = path == "/v1/orders"
    fun belongsTo(endpoint: Endpoint, user: User) = ownerId == user.id && storeId == user.storeId && origin == endpoint.origin
    companion object {
        fun order(quote: Quote, endpoint: Endpoint, user: User) = Pending("/v1/orders", objectOf("quoteId" to quote.id).toString(), UUID.randomUUID().toString(), user.id, user.storeId, endpoint.origin)
        fun cancel(order: Order, reason: String, endpoint: Endpoint, user: User): Pending {
            require(order.status == Status.NEW && reason.trim().length in 1..240) { "Informe o motivo. Só é possível cancelar antes de o pedido ser aceito." }
            return Pending("/v1/orders/${order.id}/cancel", objectOf("expectedVersion" to order.version, "reason" to reason.trim()).toString(), UUID.randomUUID().toString(), user.id, user.storeId, endpoint.origin)
        }
    }
}
data class SavedState(
    val origin: String = "", val slug: String = "bonamassa", val account: User? = null, val session: Session? = null,
    val cart: List<DraftLine> = emptyList(), val favorites: Set<String> = emptySet(), val checkout: Checkout = Checkout(),
    val pending: Pending? = null, val lastOrderId: String? = null
) {
    fun signedIn(next: Session): SavedState {
        pending?.let { require(it.ownerId == next.user.id && it.storeId == next.user.storeId) { "Entre na mesma conta para verificar o envio pendente antes de trocar de cliente." } }
        return if (account == null || account.id == next.user.id) copy(account = next.user, session = next)
        else SavedState(origin = origin, slug = slug, account = next.user, session = next)
    }
    fun acknowledge(order: Order) = copy(pending = null, cart = if (pending?.isOrder == true) emptyList() else cart,
        checkout = if (pending?.isOrder == true) checkout.copy(promotionId = null, note = "", cash = "") else checkout, lastOrderId = order.id)
}

/** Versioned plaintext codec; Android encrypts the entire result before atomic persistence. */
object SavedCodec {
    private fun user(u: User) = objectOf("id" to u.id, "storeId" to u.storeId, "name" to u.name, "email" to u.email, "phone" to u.phone, "role" to u.role)
    fun encode(s: SavedState): String = objectOf(
        "version" to 1, "origin" to s.origin, "slug" to s.slug, "account" to s.account?.let(::user),
        "session" to s.session?.let { objectOf("accessToken" to it.accessToken, "expiresAt" to it.expiresAt, "user" to user(it.user)) },
        "cart" to JSONArray(s.cart.map { it.json().put("localId", it.localId) }), "favorites" to JSONArray(s.favorites.toList()),
        "checkout" to objectOf("mode" to s.checkout.mode.name, "address" to s.checkout.address.json(), "payment" to s.checkout.payment.name, "cash" to s.checkout.cash, "note" to s.checkout.note, "promotionId" to s.checkout.promotionId),
        "pending" to s.pending?.let { objectOf("path" to it.path, "body" to it.body, "key" to it.key, "ownerId" to it.ownerId, "storeId" to it.storeId, "origin" to it.origin) },
        "lastOrderId" to s.lastOrderId
    ).toString()
    fun decode(text: String): SavedState {
        val j = JSONObject(text)
        require(j.getInt("version") == 1) { "Versão de dados locais incompatível. Atualize o app." }
        val c = j.getJSONObject("checkout")
        return SavedState(j.getString("origin"), j.getString("slug"), j.optJSONObject("account")?.let(Decode::user), j.optJSONObject("session")?.let(Decode::session),
            j.getJSONArray("cart").objects(Decode::draft), j.getJSONArray("favorites").strings().toSet(),
            Checkout(Mode.valueOf(c.getString("mode")), Decode.address(c.getJSONObject("address")), Method.valueOf(c.getString("payment")), c.getString("cash"), c.getString("note"), c.textOrNull("promotionId")),
            j.optJSONObject("pending")?.let { Pending(it.getString("path"), it.getString("body"), it.getString("key"), it.getString("ownerId"), it.getString("storeId"), it.getString("origin")) }, j.textOrNull("lastOrderId"))
    }
}
