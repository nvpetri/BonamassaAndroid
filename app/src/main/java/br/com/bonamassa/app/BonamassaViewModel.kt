package br.com.bonamassa.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.bonamassa.app.data.LocalRepository
import br.com.bonamassa.core.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScreenState(val data: AppState = AppState(), val loaded: Boolean = false, val busy: Boolean = false, val fatalError: Boolean = false, val generation: Int = 0)

class BonamassaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocalRepository(application)
    private val _ui = MutableStateFlow(ScreenState())
    val ui = _ui.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private var observeJob: Job? = null
    init { observe() }

    fun observe() {
        observeJob?.cancel()
        _ui.update { it.copy(fatalError = false) }
        observeJob = viewModelScope.launch {
            try {
                repository.state.collect { state -> _ui.update { it.copy(data = state, loaded = true, fatalError = false) } }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { _ui.update { it.copy(fatalError = true) } }
        }
    }
    fun clearMessage() { _message.value = null }
    fun notify(text: String) { _message.value = text }
    private fun mutate(onDone: (AppState) -> Unit = {}, block: (AppState) -> AppState) {
        if (!_ui.value.loaded || _ui.value.busy || _ui.value.fatalError) return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val state = repository.update(block)
                _ui.update { it.copy(data = state) }
                onDone(state)
            } catch (e: CancellationException) { throw e
            } catch (e: IllegalArgumentException) { notify(e.message ?: "Verifique os dados informados.")
            } catch (_: Exception) { notify("Não foi possível salvar. Seus dados anteriores foram mantidos; tente novamente.")
            } finally { _ui.update { it.copy(busy = false) } }
        }
    }
    fun favorite(id: String) = mutate { state -> state.copy(favorites = if (id in state.favorites) state.favorites - id else state.favorites + id) }
    fun putItem(item: CartItem, onDone: () -> Unit) = mutate(onDone = { onDone() }) { state ->
        OrderRules.unitPrice(item)
        val exists = state.cart.any { it.id == item.id }
        require(exists || state.cart.size < OrderRules.MAX_LINES) { "Seu carrinho atingiu o limite de itens." }
        state.copy(cart = if (exists) state.cart.map { if (it.id == item.id) item else it } else state.cart + item)
    }
    fun quantity(id: String, count: Int) = mutate { state ->
        require(count in 1..OrderRules.MAX_QUANTITY)
        state.copy(cart = state.cart.map { if (it.id == id) it.copy(quantity = count) else it })
    }
    fun remove(id: String) = mutate { it.copy(cart = it.cart.filterNot { item -> item.id == id }) }
    fun coupon(code: String) = mutate(onDone = { state -> notify(OrderRules.quote(state.cart, state.checkout.fulfillment, state.coupon).couponError ?: if (code.isBlank()) "Cupom removido." else "Cupom aplicado.") }) {
        it.copy(coupon = code.trim().uppercase().take(24))
    }
    fun fulfillment(value: Fulfillment) = mutate { it.copy(checkout = it.checkout.copy(fulfillment = value)) }
    fun checkout(value: Checkout, onDone: () -> Unit) = mutate(onDone = { onDone() }) { it.copy(checkout = value) }
    fun profile(customer: Customer, address: Address, onDone: () -> Unit) = mutate(onDone = { onDone() }) {
        it.copy(checkout = it.checkout.copy(customer = customer, address = address))
    }
    fun place(value: Checkout, onDone: (String) -> Unit) {
        val id = UUID.randomUUID().toString()
        mutate(onDone = { onDone(id) }) { OrderRules.place(it.copy(checkout = value), id, System.currentTimeMillis()) }
    }
    fun advance(id: String) = mutate { state -> state.copy(orders = state.orders.map { order ->
        if (order.id == id) order.copy(status = OrderRules.nextStatus(order.status, order.checkout.fulfillment) ?: order.status) else order
    }) }
    fun cancel(id: String) = mutate { state -> state.copy(orders = state.orders.map { order ->
        if (order.id == id && order.status == OrderStatus.RECEIVED) order.copy(status = OrderStatus.CANCELLED) else order
    }) }
    fun reorder(id: String, onDone: () -> Unit) = mutate(onDone = { onDone() }) { state ->
        val order = requireNotNull(state.orders.find { it.id == id })
        require(state.cart.size + order.sourceItems.size <= OrderRules.MAX_LINES) { "Não há espaço no carrinho. Remova alguns itens." }
        val items = order.sourceItems.map { it.copy(id = UUID.randomUUID().toString()).also { item -> OrderRules.unitPrice(item) } }
        state.copy(cart = state.cart + items)
    }
    fun reset(onDone: () -> Unit) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                repository.reset()
                _ui.value = ScreenState(AppState(), loaded = true, generation = _ui.value.generation + 1)
                observe()
                onDone()
                notify("Dados desta demonstração apagados do aparelho.")
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { notify("Não foi possível apagar os dados.")
            } finally { _ui.update { it.copy(busy = false) } }
        }
    }
}
