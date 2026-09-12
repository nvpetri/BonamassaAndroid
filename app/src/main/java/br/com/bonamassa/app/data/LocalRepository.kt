package br.com.bonamassa.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.com.bonamassa.core.AppState
import kotlinx.coroutines.flow.map

private val Context.bonamassaStore by preferencesDataStore(name = "bonamassa_demo_v2")

class LocalRepository(context: Context) {
    private val store = context.applicationContext.bonamassaStore
    private val key = stringPreferencesKey("state")
    val state = store.data.map { preferences -> preferences[key]?.let(StateCodec::decode) ?: AppState() }

    suspend fun update(transform: (AppState) -> AppState): AppState {
        var result = AppState()
        // DataStore serializes read-modify-write transactions to prevent lost updates.
        store.edit { preferences ->
            val before = preferences[key]?.let(StateCodec::decode) ?: AppState()
            result = transform(before)
            preferences[key] = StateCodec.encode(result)
        }
        return result
    }
    suspend fun reset() { store.edit { it.remove(key) } }
}
