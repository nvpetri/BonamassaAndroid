package br.com.bonamassa.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.bonamassa.app.ui.*
import br.com.bonamassa.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MenuUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun categoryActuallyFiltersTheMenu() {
        compose.setContent { BonamassaTheme { MenuScreen(AppState()) {} } }
        compose.onNodeWithText("Bebidas").performClick()
        compose.onNodeWithText("Refrigerante cola").assertExists()
        compose.onNodeWithText("A Bonamassa").assertDoesNotExist()
    }
    @Test fun emptyFavoritesHaveRecoveryAction() {
        compose.setContent { BonamassaTheme { MenuScreen(AppState()) {} } }
        compose.onNodeWithText("Só favoritos").performClick()
        compose.onNodeWithText("Nada por aqui ainda").assertExists()
        compose.onNodeWithText("Limpar filtros").performClick()
        compose.onNodeWithText("A Bonamassa").assertExists()
    }
    @Test fun searchAndSelectionReachTheProductCallback() {
        var selected = ""
        compose.setContent { BonamassaTheme { MenuScreen(AppState()) { selected = it } } }
        compose.onNode(hasSetTextAction()).performTextInput("frango")
        compose.onNodeWithText("Frango cremoso").performClick()
        compose.runOnIdle { assertEquals("frango", selected) }
    }
}
