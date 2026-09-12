package br.com.bonamassa.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import br.com.bonamassa.core.*

@Preview(name = "Início · celular", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF101010)
@Preview(name = "Início · fonte ampliada", widthDp = 360, heightDp = 800, fontScale = 1.3f, showBackground = true, backgroundColor = 0xFF101010)
@Composable
fun HomePreview() = BonamassaTheme { HomeScreen(AppState(), {}, {}, {}, {}, {}) }

@Preview(name = "Montagem da pizza", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF101010)
@Composable
fun BuilderPreview() = BonamassaTheme { BuilderScreen(Catalog.require("bonamassa"), null, false, false, {}, {}) }

@Preview(name = "Cardápio · tablet", widthDp = 700, heightDp = 950, showBackground = true, backgroundColor = 0xFF101010)
@Composable
fun MenuPreview() = BonamassaTheme { MenuScreen(AppState(), {}) }
