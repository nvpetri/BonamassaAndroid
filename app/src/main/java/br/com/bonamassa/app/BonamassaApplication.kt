package br.com.bonamassa.app

import android.app.Application
import br.com.bonamassa.client.BonamassaApi
import coil.ImageLoader
import coil.ImageLoaderFactory

/** One photo cache and connection pool for the whole menu. Photos never receive a token. */
class BonamassaApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(BonamassaApi.newHttpClient())
        .build()
}
