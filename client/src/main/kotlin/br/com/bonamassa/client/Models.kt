package br.com.bonamassa.client

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID

fun objectOf(vararg entries: Pair<String, Any?>) = JSONObject().apply {
    entries.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
}
fun JSONObject.textOrNull(key: String): String? = if (isNull(key)) null else getString(key)
fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
fun <T> JSONArray.objects(read: (JSONObject) -> T): List<T> = (0 until length()).map { read(getJSONObject(it)) }
fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

enum class Kind(val label: String) { PIZZA("Pizzas"), CRUST("Bordas"), DRINK("Bebidas"), COMBO("Combos") }
enum class Size(val label: String) { SMALL("Pequena · 4 fatias"), MEDIUM("Média · 6 fatias"), LARGE("Grande · 8 fatias") }
enum class Mode(val label: String) { DELIVERY("Entrega"), PICKUP("Retirada") }
enum class Method(val label: String) { CASH("Dinheiro"), CARD("Cartão no recebimento"), PREPAID("Pago antecipadamente") }
enum class Status(val label: String, val active: Boolean = true) {
    SCHEDULED("Pedido agendado"), NEW("Aguardando confirmação"), CONFIRMED("Pedido aceito"), PREPARING("Em preparo"),
    READY("Pronto"), OUT_FOR_DELIVERY("Saiu para entrega"), RETURNING("Entrega em devolução"),
    DELIVERED("Concluído", false), RETURNED("Devolvido", false), CANCELLED("Cancelado", false)
}
data class User(val id: String, val storeId: String, val name: String, val email: String, val phone: String, val role: String)
data class Session(val accessToken: String, val expiresAt: String, val user: User)
data class Address(
    val street: String = "", val number: String = "", val neighborhood: String = "",
    val city: String = "", val state: String = "", val postalCode: String = "", val reference: String = "",
    val complement: String = "", val noComplement: Boolean = false
) {
    fun json() = objectOf("street" to street.trim(), "number" to number.trim(), "neighborhood" to neighborhood.trim(),
        "city" to city.trim(), "state" to state.trim().uppercase(), "postalCode" to postalCode.filter(Char::isDigit), "reference" to reference.trim(),
        "complement" to complement.trim(), "noComplement" to noComplement)
    fun summary() = listOf("$street, $number", "$neighborhood · $city/$state", "CEP $postalCode",
        complement.takeIf { it.isNotBlank() }?.let { "Complemento: $it" }.orEmpty(),
        if (noComplement) "Sem complemento" else "", reference).filter { it.isNotBlank() }.joinToString(" · ")
    fun validate() {
        require(street.trim().length in 1..120 && number.trim().length in 1..20 && neighborhood.trim().length in 1..80 && city.trim().length in 1..80) { "Preencha rua, número, bairro e cidade." }
        require(state.trim().uppercase().matches(Regex("[A-Z]{2}"))) { "Informe a UF com duas letras." }
        require(postalCode.filter(Char::isDigit).length == 8) { "Informe o CEP com 8 números." }
        require(reference.length <= 240) { "A referência deve ter até 240 caracteres." }
        require(complement.length <= 240 && (!noComplement || complement.isBlank())) { "Confira o complemento do endereço." }
    }
}
data class DraftLine(
    val kind: Kind, val productId: String = "", val flavorIds: List<String> = emptyList(),
    val size: Size = Size.LARGE, val crust: String = "NONE", val quantity: Int = 1,
    val note: String = "", val localId: String = UUID.randomUUID().toString()
) {
    fun json(): JSONObject {
        require(kind != Kind.CRUST && quantity in 1..20 && note.length <= 240)
        return when (kind) {
            Kind.PIZZA -> {
                require(flavorIds.size in 1..2 && flavorIds.distinct().size == flavorIds.size)
                objectOf("kind" to kind.name, "flavorIds" to JSONArray(flavorIds), "size" to size.name,
                    "crust" to crust, "quantity" to quantity, "note" to note.trim())
            }
            Kind.DRINK -> objectOf("kind" to kind.name, "productId" to productId, "quantity" to quantity)
            else -> objectOf("kind" to kind.name, "productId" to productId, "quantity" to quantity, "note" to note.trim())
        }
    }
}
data class Checkout(
    val mode: Mode = Mode.DELIVERY, val address: Address = Address(), val payment: Method = Method.CARD,
    val cash: String = "", val note: String = "", val promotionId: String? = null
) {
    fun request(items: List<DraftLine>, allowScheduling: Boolean = false): JSONObject {
        require(items.size in 1..30) { "Sua sacola precisa ter de 1 a 30 itens." }
        require(payment != Method.PREPAID) { "Escolha cartão ou dinheiro no recebimento." }
        if (mode == Mode.DELIVERY) address.validate()
        require(note.length <= 240)
        return objectOf("items" to JSONArray(items.map { it.json() }), "mode" to mode.name,
            "address" to if (mode == Mode.DELIVERY) address.json() else null, "note" to note.trim(),
            "payment" to payment.name, "cashTendered" to if (payment == Method.CASH) parseCash(cash) else null,
            "promotionId" to promotionId).apply { if (allowScheduling) put("allowScheduling", true) }
    }
}
fun parseCash(text: String): Long? {
    if (text.isBlank()) return null
    val normalized = text.trim().replace(',', '.')
    require(normalized.matches(Regex("[0-9]{1,6}(\\.[0-9]{1,2})?"))) { "Informe o dinheiro como 100,00, sem separador de milhar." }
    val cents = BigDecimal(normalized).movePointRight(2).longValueExact()
    require(cents in 0..10_000_000) { "Valor em dinheiro acima do limite." }
    return cents
}
data class Product(
    val id: String, val name: String, val description: String, val kind: Kind, val group: String?,
    val prices: Map<Size, Long>, val available: Boolean, val photo: String?, val combo: List<DraftLine>,
    val individualTotal: Long?
) { fun price(size: Size = Size.LARGE) = prices.getValue(size) }
data class Promotion(val id: String, val name: String, val kind: String, val value: Long, val endsAt: String?, val remaining: Long?)
data class Catalog(
    val storeId: String, val name: String, val open: Boolean, val deliveryFee: Long,
    val products: List<Product>, val promotions: List<Promotion>, val serverTime: String,
    val reservationsAvailable: Boolean = false, val nextOpening: String? = null,
    val opensAt: String = "17:00", val closesAt: String = "03:00"
) {
    val canOrder: Boolean get() = open || (reservationsAvailable && nextOpening != null)
    fun product(id: String) = products.find { it.id == id && it.available }
    fun estimate(line: DraftLine): Long {
        line.json()
        fun requireProduct(id: String, kind: Kind): Product = requireNotNull(product(id)?.takeIf { it.kind == kind }) { "Um produto da sacola ficou indisponível. Edite ou remova o item." }
        return if (line.kind == Kind.PIZZA) line.flavorIds.maxOf { requireProduct(it, Kind.PIZZA).price(line.size) } +
            if (line.crust == "NONE") 0 else requireProduct(line.crust, Kind.CRUST).price()
        else requireProduct(line.productId, line.kind).price()
    }
    fun title(line: DraftLine) = if (line.kind == Kind.PIZZA) line.flavorIds.joinToString(" + ") { (if (line.flavorIds.size == 2) "½ " else "") + (product(it)?.name ?: "Sabor indisponível") }
        else product(line.productId)?.name ?: "Produto indisponível"
    fun detail(line: DraftLine) = if (line.kind == Kind.PIZZA) "${line.size.label} · ${if (line.crust == "NONE") "Sem borda recheada" else product(line.crust)?.name ?: "Borda indisponível"}" else ""
}
data class ReceiptLine(val name: String, val detail: String, val note: String, val quantity: Int, val unitPrice: Long, val components: List<ReceiptLine>)
data class Totals(val subtotal: Long, val fee: Long, val discount: Long, val total: Long, val promotion: String?, val discountedPizzas: Int = 0)
data class Quote(val id: String, val expiresAt: String, val items: List<ReceiptLine>, val totals: Totals, val scheduledFor: String? = null)
data class Event(val action: String, val version: Int, val createdAt: String)
data class Order(
    val id: String, val number: Int, val version: Int, val status: Status, val deliveryStatus: String?,
    val mode: Mode, val createdAt: String, val updatedAt: String, val items: List<ReceiptLine>,
    val totals: Totals, val customerName: String, val address: Address?, val payment: Method,
    val paymentRecorded: Boolean, val cashTendered: Long?, val change: Long, val note: String, val events: List<Event>, val scheduledFor: String? = null
) {
    val canCancel: Boolean get() = status == Status.NEW || status == Status.SCHEDULED
    val statusLabel: String get() = when {
        status == Status.READY && mode == Mode.PICKUP -> "Pronto para retirar"
        status == Status.DELIVERED && mode == Mode.PICKUP -> "Retirado"
        status == Status.DELIVERED -> "Entregue"
        else -> status.label
    }
}
data class Page(val items: List<Order>, val nextCursor: String?)

object Decode {
    fun user(j: JSONObject) = User(j.getString("id"), j.getString("storeId"), j.getString("name"), j.getString("email"), j.getString("phone"), j.getString("role"))
    fun session(j: JSONObject) = Session(j.getString("accessToken"), j.getString("expiresAt"), user(j.getJSONObject("user")))
    fun address(j: JSONObject) = Address(j.getString("street"), j.getString("number"), j.getString("neighborhood"), j.getString("city"), j.getString("state"), j.getString("postalCode"), j.getString("reference"),
        j.optString("complement", ""), j.optBoolean("noComplement", false))
    fun draft(j: JSONObject) = DraftLine(Kind.valueOf(j.getString("kind")), j.optString("productId", ""), j.optJSONArray("flavorIds")?.strings() ?: emptyList(),
        Size.valueOf(j.optString("size", "LARGE")), j.optString("crust", "NONE"), j.getInt("quantity"), j.optString("note", ""), j.optString("localId", UUID.randomUUID().toString()))
    fun catalog(j: JSONObject): Catalog {
        val store = j.getJSONObject("store")
        val rules = j.getJSONObject("rules")
        require(rules.getString("pizzaPrice") == "HIGHEST_FLAVOR" && rules.getInt("maxFlavors") == 2 && rules.getString("comboComposition") == "FIXED") { "Atualize o app para usar as novas regras do cardápio." }
        return Catalog(store.getString("id"), store.getString("name"), store.getBoolean("open"), store.getLong("deliveryFee"), j.getJSONArray("products").objects { p ->
            Product(p.getString("id"), p.getString("name"), p.getString("description"), Kind.valueOf(p.getString("category")), p.textOrNull("pizzaGroup"),
                Size.entries.associateWith { p.getJSONObject("prices").getLong(it.name) }, p.getBoolean("available"), p.textOrNull("photo"),
                p.optJSONArray("combo")?.objects(::draft) ?: emptyList(), p.optJSONObject("comparison")?.getLong("individualTotal"))
        }, j.getJSONArray("promotions").objects { p -> Promotion(p.getString("id"), p.getString("name"), p.getString("kind"), p.getLong("value"), p.textOrNull("endsAt"), p.longOrNull("remaining")) }, j.getString("serverTime"), store.optBoolean("reservationsAvailable", false), store.textOrNull("nextOpening"),
            store.optString("opensAt", "17:00"), store.optString("closesAt", "03:00"))
    }
    fun line(j: JSONObject): ReceiptLine = ReceiptLine(j.getString("name"), j.getString("detail"), j.getString("note"), j.getInt("quantity"), j.optLong("unitPrice", 0), j.optJSONArray("components")?.objects(::line) ?: emptyList())
    fun totals(j: JSONObject) = Totals(j.getLong("subtotal"), j.getLong("fee"), j.getLong("discount"), j.getLong("total"), j.optJSONObject("promotion")?.getString("name"), j.optJSONObject("promotion")?.getInt("pizzaQuantity") ?: 0)
    fun quote(j: JSONObject) = Quote(j.getString("quoteId"), j.getString("expiresAt"), j.getJSONArray("items").objects(::line), totals(j), j.textOrNull("scheduledFor"))
    fun order(j: JSONObject) = Order(j.getString("id"), j.getInt("number"), j.getInt("version"), Status.valueOf(j.getString("status")), j.textOrNull("deliveryStatus"),
        Mode.valueOf(j.getString("mode")), j.getString("createdAt"), j.getString("updatedAt"), j.getJSONArray("items").objects(::line), totals(j),
        j.getJSONObject("customer").getString("name"), j.optJSONObject("address")?.let(::address), Method.valueOf(j.getString("payment")), j.getBoolean("paymentRecorded"),
        j.longOrNull("cashTendered"), j.getLong("change"), j.getString("note"), j.getJSONArray("events").objects { Event(it.getString("action"), it.getInt("version"), it.getString("createdAt")) }, j.textOrNull("scheduledFor"))
    fun page(j: JSONObject) = Page(j.getJSONArray("items").objects(::order), j.textOrNull("nextCursor"))
}
