package br.com.bonamassa.app.connected

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.bonamassa.app.BuildConfig
import br.com.bonamassa.client.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

data class Review(val quote: Quote, val checkout: Checkout)
data class CustomerUi(
    val saved: SavedState = SavedState(), val loaded: Boolean = false, val fatal: Boolean = false,
    val busy: Boolean = false, val refreshing: Boolean = false, val catalog: Catalog? = null,
    val orders: List<Order> = emptyList(), val cursor: String? = null, val review: Review? = null,
    val error: String? = null, val syncError: String? = null, val updatedAt: Long? = null, val selectedOrder: String? = null
)

class CustomerViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SecureStore(application)
    private val storageMutex = Mutex()
    private val _ui = MutableStateFlow(CustomerUi())
    val ui = _ui.asStateFlow()
    private var refreshJob: Job? = null
    private var epoch = 0
    private var historyLoaded = false
    init { load() }

    fun endpoint(): Endpoint = Endpoint.parse(_ui.value.saved.origin, _ui.value.saved.slug, BuildConfig.DEBUG)
    private fun api() = BonamassaApi(endpoint())
    fun clearError() { _ui.update { it.copy(error = null) } }
    fun load() {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, fatal = false) }
        viewModelScope.launch {
            try {
                val configured = Endpoint.parse(
                    BuildConfig.API_URL.ifBlank { if (BuildConfig.DEBUG) "http://10.0.2.2:3001" else "" },
                    BuildConfig.STORE_SLUG,
                    BuildConfig.DEBUG
                )
                val stored = withContext(Dispatchers.IO) { store.read() }
                val saved = when {
                    stored == null -> SavedState(origin = configured.origin, slug = configured.storeSlug)
                    stored.origin == configured.origin && stored.slug == configured.storeSlug -> stored
                    stored.pending != null -> stored
                    else -> SavedState(origin = configured.origin, slug = configured.storeSlug)
                }
                if (saved !== stored) withContext(Dispatchers.IO) { store.write(saved) }
                _ui.update { it.copy(saved = saved, loaded = true, busy = false) }
                refresh()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _ui.update { it.copy(fatal = true, busy = false, error = "Não foi possível ler os dados protegidos. Seus dados foram preservados. Tente novamente.") } }
        }
    }
    private suspend fun change(transform: (SavedState) -> SavedState) = storageMutex.withLock {
        val next = transform(_ui.value.saved)
        withContext(Dispatchers.IO) { store.write(next) }
        _ui.update { it.copy(saved = next) }
    }
    private suspend fun failure(e: Exception, token: String? = null) {
        if (e is CancellationException) throw e
        if (e is ApiFailure && e.status == 401 && token != null && _ui.value.saved.session?.accessToken == token) {
            change { it.copy(session = null) }
            epoch++
            historyLoaded = false
            _ui.update { it.copy(orders = emptyList(), cursor = null, review = null, selectedOrder = null) }
        }
        _ui.update { it.copy(error = when (e) {
            is ApiFailure -> if (e.status == 401 && token != null) "Sua sessão expirou. Entre novamente para continuar." else e.message
            is IllegalArgumentException -> e.message ?: "Verifique os dados informados."
            is IOException -> "Não foi possível conectar. Confira a internet e tente novamente."
            else -> "Não foi possível concluir. Seus dados anteriores foram mantidos; tente novamente."
        }) }
    }
    private fun action(block: suspend () -> Unit) {
        if (!_ui.value.loaded || _ui.value.fatal || _ui.value.busy) return
        val token = _ui.value.saved.session?.accessToken
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() } catch (e: Exception) { failure(e, token) }
            finally { _ui.update { it.copy(busy = false) } }
        }
    }
    private fun editable() { require(_ui.value.saved.pending == null) { "Verifique o envio pendente antes de alterar a sacola ou sair." } }
    private fun session() = requireNotNull(_ui.value.saved.session) { "Entre na sua conta para continuar." }
    private fun merge(orders: List<Order>) {
        _ui.update { s ->
            val all = (s.orders + orders).groupBy { it.id }.values.map { versions -> versions.maxBy { it.version } }.sortedByDescending { it.number }
            s.copy(orders = all)
        }
    }
    fun signIn(email: String, password: String, name: String?, phone: String?, done: () -> Unit) = action {
        require(email.trim().isNotEmpty() && password.isNotEmpty()) { "Informe e-mail e senha." }
        if (name != null) {
            require(name.trim().length in 1..80 && password.length in 12..128) { "Informe seu nome e uma senha de 12 a 128 caracteres." }
            require(phone.orEmpty().filter(Char::isDigit).matches(Regex("[1-9][0-9]{9,14}"))) { "Informe o telefone com DDD." }
        }
        val client = api()
        val next = withContext(Dispatchers.IO) { client.signIn(email, password, name, phone) }
        try { change { it.signedIn(next) } }
        catch (e: Exception) { withContext(Dispatchers.IO) { runCatching { client.logout(next.accessToken) } }; throw e }
        epoch++
        historyLoaded = false
        _ui.update { it.copy(orders = emptyList(), cursor = null, review = null, selectedOrder = null) }
        done()
        refresh(force = true)
    }
    fun logout() = action {
        editable()
        val old = _ui.value.saved.session
        val client = api()
        change { SavedState(origin = it.origin, slug = it.slug) }
        epoch++
        historyLoaded = false
        _ui.update { it.copy(orders = emptyList(), cursor = null, review = null, selectedOrder = null) }
        if (old != null) withContext(Dispatchers.IO) { runCatching { client.logout(old.accessToken) } }
    }
    fun favorite(id: String) = action {
        change { it.copy(favorites = if (id in it.favorites) it.favorites - id else it.favorites + id) }
    }
    fun put(line: DraftLine, done: () -> Unit) = action {
        editable()
        requireNotNull(_ui.value.catalog).estimate(line)
        change { saved ->
            val exists = saved.cart.any { it.localId == line.localId }
            require(exists || saved.cart.size < 30) { "O limite da sacola é de 30 itens." }
            saved.copy(cart = if (exists) saved.cart.map { if (it.localId == line.localId) line else it } else saved.cart + line)
        }
        _ui.update { it.copy(review = null) }; done()
    }
    fun quantity(id: String, quantity: Int) = action {
        editable(); require(quantity in 1..20)
        change { it.copy(cart = it.cart.map { line -> if (line.localId == id) line.copy(quantity = quantity) else line }) }
        _ui.update { it.copy(review = null) }
    }
    fun remove(id: String) = action {
        editable(); change { it.copy(cart = it.cart.filterNot { line -> line.localId == id }) }; _ui.update { it.copy(review = null) }
    }
    fun quote(checkout: Checkout, done: () -> Unit) = action {
        editable()
        val token = session().accessToken
        val cart = _ui.value.saved.cart
        checkout.request(cart)
        change { it.copy(checkout = checkout) }
        _ui.update { it.copy(review = null) }
        val client = api()
        val scheduling = _ui.value.catalog?.reservationsAvailable == true
        val quote = withContext(Dispatchers.IO) { client.quote(token, cart, checkout, scheduling) }
        _ui.update { it.copy(review = Review(quote, checkout)) }; done()
    }
    fun discardReview() { _ui.update { it.copy(review = null) } }
    fun place(done: (String) -> Unit) = action {
        editable()
        val user = session().user
        val review = requireNotNull(_ui.value.review) { "Confira os valores novamente antes de enviar." }
        val pending = Pending.order(review.quote, endpoint(), user)
        change { it.copy(pending = pending) } // Durable BEFORE any HTTP write.
        sendPending(done)
    }
    fun cancel(order: Order, reason: String, done: () -> Unit) = action {
        editable()
        val pending = Pending.cancel(order, reason, endpoint(), session().user)
        change { it.copy(pending = pending) }
        sendPending { done() }
    }
    fun retryPending(done: (String) -> Unit) = action { sendPending(done) }
    private suspend fun sendPending(done: (String) -> Unit) {
        val pending = requireNotNull(_ui.value.saved.pending)
        val session = session()
        val client = api()
        require(pending.belongsTo(client.endpoint, session.user)) { "Entre na conta e no servidor originais para verificar este envio." }
        try {
            val order = withContext(Dispatchers.IO) { client.send(session.accessToken, pending) }
            change { it.acknowledge(order) } // Clear cart and pending in the same disk transaction.
            merge(listOf(order))
            _ui.update { it.copy(review = null, selectedOrder = order.id) }
            done(order.id)
            refresh(force = true)
        } catch (e: ApiFailure) {
            if (e.definitive) { change { it.copy(pending = null) }; _ui.update { it.copy(review = null) }; refresh(force = true) }
            throw e
        }
    }
    fun selectOrder(id: String) { _ui.update { it.copy(selectedOrder = id) }; refresh() }
    fun moreOrders() = action {
        val cursor = _ui.value.cursor ?: return@action
        val client = api(); val token = session().accessToken
        val page = withContext(Dispatchers.IO) { client.orders(token, cursor) }
        merge(page.items); _ui.update { it.copy(cursor = page.nextCursor) }
    }
    /** Called by repeatOnLifecycle(STARTED). No simulated advances or background polling. */
    fun refresh(force: Boolean = false) {
        if (!_ui.value.loaded || _ui.value.fatal || (!force && _ui.value.busy) || refreshJob?.isActive == true) return
        val generation = epoch
        val saved = _ui.value.saved
        val previouslyKnown = _ui.value.orders
        val selected = _ui.value.selectedOrder ?: saved.lastOrderId
        val firstHistoryLoad = !historyLoaded
        val client = try { api() } catch (e: IllegalArgumentException) { _ui.update { it.copy(syncError = e.message) }; return }
        _ui.update { it.copy(refreshing = true) }
        refreshJob = viewModelScope.launch {
            try {
                val catalog = withContext(Dispatchers.IO) { client.catalog() }
                if (generation != epoch) return@launch
                _ui.update { it.copy(catalog = catalog) }
                saved.session?.let { auth ->
                    require(catalog.storeId == auth.user.storeId) { "A conta não corresponde à loja configurada." }
                    val page = withContext(Dispatchers.IO) { client.orders(auth.accessToken) }
                    if (generation != epoch) return@launch
                    merge(page.items)
                    // The first page's cursor is independent of locally acknowledged orders.
                    if (firstHistoryLoad || (page.nextCursor != null && previouslyKnown.none { old -> page.items.any { it.id == old.id } })) {
                        _ui.update { it.copy(cursor = page.nextCursor) }
                    }
                    // Find older active orders even when the newest page is full of completed ones.
                    if (firstHistoryLoad && page.nextCursor != null) {
                        for (status in Status.entries.filter { it.active && (it != Status.SCHEDULED || catalog.reservationsAvailable) }) {
                            var cursor: String? = null
                            do {
                                val active = withContext(Dispatchers.IO) { client.orders(auth.accessToken, cursor, status) }
                                if (generation != epoch) return@launch
                                merge(active.items)
                                cursor = active.nextCursor
                            } while (cursor != null)
                        }
                    }
                    val ids = (previouslyKnown.filter { it.status.active }.map { it.id } + listOfNotNull(selected)).distinct().filterNot { id -> page.items.any { it.id == id } }
                    for (id in ids) {
                        val order = withContext(Dispatchers.IO) { client.order(auth.accessToken, id) }
                        if (generation != epoch) return@launch
                        merge(listOf(order))
                    }
                    historyLoaded = true
                }
                _ui.update { it.copy(syncError = null, updatedAt = System.currentTimeMillis()) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == epoch) {
                    if (e is ApiFailure && e.status == 401) failure(e, saved.session?.accessToken)
                    _ui.update { it.copy(syncError = if (e is ApiFailure) e.message else "Sem atualização. Confira a conexão; as informações exibidas podem estar desatualizadas.") }
                }
            } finally { _ui.update { it.copy(refreshing = false) } }
        }
    }
    fun stopRefreshing() { refreshJob?.cancel() }
}
