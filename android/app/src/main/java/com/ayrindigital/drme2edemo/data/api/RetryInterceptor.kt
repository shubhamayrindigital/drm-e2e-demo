package com.ayrindigital.drme2edemo.data.api

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Retries failed requests once with a short backoff. Designed for Render free tier where the
 * service spins down after 15 min of inactivity and takes ~50s to spin back up — during which
 * upstream proxy errors (502/503/504) or read timeouts can sneak through.
 */
class RetryInterceptor(
    private val maxRetries: Int = 1,
    private val backoffMs: Long = 2_000L,
) : Interceptor {
    private val tag = "RetryInterceptor"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var attempt = 0
        var lastException: IOException? = null
        var lastResponse: Response? = null

        while (attempt <= maxRetries) {
            if (attempt > 0) {
                Log.w(tag, "Retry #$attempt for ${request.method} ${request.url} after ${backoffMs}ms backoff")
                try {
                    Thread.sleep(backoffMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            try {
                lastResponse?.close()
                lastResponse = chain.proceed(request)
                if (lastResponse.code !in RETRYABLE_STATUS) return lastResponse
                Log.w(tag, "Got ${lastResponse.code} for ${request.url}; will retry if attempts remain")
            } catch (e: IOException) {
                Log.w(tag, "IOException for ${request.url}: ${e.message}; will retry if attempts remain")
                lastException = e
            }
            attempt++
        }

        return lastResponse ?: throw (lastException ?: IOException("Request failed without response"))
    }

    companion object {
        private val RETRYABLE_STATUS = setOf(502, 503, 504)
    }
}
