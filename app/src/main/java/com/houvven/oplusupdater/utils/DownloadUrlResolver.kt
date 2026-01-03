package com.houvven.oplusupdater.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Utility for resolving Oplus download URLs.
 * If URL contains /downloadCheck, it needs to be resolved via 302 redirect.
 */
object DownloadUrlResolver {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false) // Don't follow redirects, we need the Location header
        .build()
    
    /**
     * Check if URL needs resolution (contains /downloadCheck)
     */
    fun needsResolution(url: String): Boolean {
        return url.contains("/downloadCheck")
    }
    
    /**
     * Resolve download URL.
     * If URL contains /downloadCheck, request it with special header to get actual URL from 302 Location.
     * Otherwise return the URL as-is.
     */
    suspend fun resolveUrl(url: String): ResolvedUrl {
        if (!needsResolution(url)) {
            return ResolvedUrl(originalUrl = url, resolvedUrl = null, needsResolution = false)
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("userid", "oplus-ota|")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.code == 302 || response.code == 301) {
                        val location = response.header("Location")
                        if (!location.isNullOrEmpty()) {
                            ResolvedUrl(
                                originalUrl = url,
                                resolvedUrl = location,
                                needsResolution = true
                            )
                        } else {
                            ResolvedUrl(originalUrl = url, resolvedUrl = null, needsResolution = true, error = "No Location header in redirect")
                        }
                    } else {
                        ResolvedUrl(originalUrl = url, resolvedUrl = null, needsResolution = true, error = "Expected 302, got ${response.code}")
                    }
                }
            } catch (e: Exception) {
                ResolvedUrl(originalUrl = url, resolvedUrl = null, needsResolution = true, error = e.message)
            }
        }
    }
    
    data class ResolvedUrl(
        val originalUrl: String,
        val resolvedUrl: String?,
        val needsResolution: Boolean,
        val error: String? = null
    ) {
        /**
         * Get the actual download URL (resolved if available, otherwise original)
         */
        fun getDownloadUrl(): String {
            return resolvedUrl ?: originalUrl
        }
    }
}
