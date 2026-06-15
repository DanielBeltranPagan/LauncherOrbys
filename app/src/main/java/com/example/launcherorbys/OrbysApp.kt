package com.example.launcherorbys

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.launcherorbys.utils.AppIconFetcher
import com.example.launcherorbys.utils.AppIconKeyer

class OrbysApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(AppIconFetcher.Factory(this@OrbysApp))
                add(AppIconKeyer())
            }
            .crossfade(true)
            .build()
    }
}
