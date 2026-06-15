package com.example.launcherorbys.utils

import android.content.Context
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options

class AppIconFetcher(
    private val packageName: String,
    private val context: Context
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val pm = context.packageManager
        val icon = pm.getApplicationIcon(packageName)
        return DrawableResult(
            drawable = icon,
            isSampled = false,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme == "appicon") {
                val packageName = data.authority ?: return null
                return AppIconFetcher(packageName, context)
            }
            return null
        }
    }
}

class AppIconKeyer : Keyer<Uri> {
    override fun key(data: Uri, options: Options): String? {
        return if (data.scheme == "appicon") data.toString() else null
    }
}
