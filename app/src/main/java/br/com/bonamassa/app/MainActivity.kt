package br.com.bonamassa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.com.bonamassa.app.ui.BonamassaTheme
import br.com.bonamassa.app.ui.BonamassaApp
import br.com.bonamassa.app.connected.CustomerApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { BonamassaTheme { if (BuildConfig.DEMO_MODE) BonamassaApp() else CustomerApp() } }
    }
}
