package br.com.bonamassa.app.data

import br.com.bonamassa.core.*
import org.json.JSONArray
import org.json.JSONObject

/** Explicit, versioned codec: no reflection, no serialized Java objects or credentials. */
object StateCodec {
    private fun json(block: JSONObject.() -> Unit) = JSONObject().apply(block)
    private fun <T> array(values: Iterable<T>, encode: (T) -> Any): JSONArray = JSONArray().apply { values.forEach { put(encode(it)) } }
    private fun <T> JSONArray.objects(decode: (JSONObject) -> T) = (0 until length()).map { decode(getJSONObject(it)) }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
    private fun item(value: CartItem) = json {
        put("id", value.id); put("product", value.productId); put("second", value.secondFlavorId ?: "")
        put("size", value.size.name); put("crust", value.crust.name); put("quantity", value.quantity)
        put("extras", array(value.extras) { it.name }); put("note", value.note)
    }
    private fun readItem(value: JSONObject) = CartItem(
        value.getString("id"), value.getString("product"), value.optString("second").ifBlank { null },
        PizzaSize.valueOf(value.getString("size")), Crust.valueOf(value.getString("crust")),
        value.getJSONArray("extras").strings().map(Extra::valueOf).toSet(), value.getInt("quantity"), value.optString("note")
    ).also { OrderRules.unitPrice(it) }
    private fun checkout(value: Checkout) = json {
        put("name", value.customer.name); put("phone", value.customer.phone)
        put("street", value.address.street); put("number", value.address.number)
        put("district", value.address.district); put("city", value.address.city)
        put("cep", value.address.cep); put("complement", value.address.complement)
        put("fulfillment", value.fulfillment.name); put("payment", value.payment.name); put("change", value.changeFor)
    }
    private fun readCheckout(v: JSONObject) = Checkout(
        Customer(v.optString("name"), v.optString("phone")),
        Address(v.optString("street"), v.optString("number"), v.optString("district"), v.optString("city"), v.optString("cep"), v.optString("complement")),
        Fulfillment.valueOf(v.getString("fulfillment")), Payment.valueOf(v.getString("payment")), v.optString("change")
    )
    fun encode(state: AppState): String = json {
        put("schema", 2); put("nextOrderNumber", state.nextOrderNumber)
        put("cart", array(state.cart, ::item)); put("favorites", array(state.favorites) { it })
        put("checkout", checkout(state.checkout)); put("coupon", state.coupon)
        put("orders", array(state.orders) { order -> json {
            put("id", order.id); put("number", order.number); put("createdAt", order.createdAt); put("status", order.status.name)
            put("checkout", checkout(order.checkout)); put("subtotal", order.quote.subtotal)
            put("discount", order.quote.discount); put("delivery", order.quote.delivery)
            put("sourceItems", array(order.sourceItems, ::item))
            put("items", array(order.items) { line -> json {
                put("title", line.title); put("details", line.details); put("quantity", line.quantity)
                put("unitPrice", line.unitPrice); put("note", line.note)
            } })
        } })
    }.toString()
    fun decode(text: String): AppState {
        val v = JSONObject(text)
        require(v.getInt("schema") == 2) { "Versão de dados não suportada." }
        val cart = v.getJSONArray("cart").objects(::readItem)
        require(cart.size <= OrderRules.MAX_LINES && cart.map { it.id }.distinct().size == cart.size)
        val orders = v.getJSONArray("orders").objects { o ->
            val quote = Quote(o.getLong("subtotal"), o.getLong("discount"), o.getLong("delivery"))
            val lines = o.getJSONArray("items").objects { line ->
                OrderLine(line.getString("title"), line.getString("details"), line.getInt("quantity"), line.getLong("unitPrice"), line.optString("note"))
            }
            require(lines.isNotEmpty() && lines.all { it.quantity in 1..OrderRules.MAX_QUANTITY && it.unitPrice >= 0 })
            require(quote.subtotal == lines.sumOf { it.quantity * it.unitPrice })
            require(quote.discount in 0..quote.subtotal && quote.delivery >= 0)
            Order(o.getString("id"), o.getInt("number"), o.getLong("createdAt"), lines, readCheckout(o.getJSONObject("checkout")),
                quote, OrderStatus.valueOf(o.getString("status")), o.getJSONArray("sourceItems").objects(::readItem))
        }
        val next = v.getInt("nextOrderNumber")
        require(orders.size <= 100 && next >= 1001 && orders.all { it.number < next })
        return AppState(cart, v.getJSONArray("favorites").strings().filter { Catalog.find(it) != null }.toSet(),
            readCheckout(v.getJSONObject("checkout")), v.optString("coupon"), orders, next)
    }
}
