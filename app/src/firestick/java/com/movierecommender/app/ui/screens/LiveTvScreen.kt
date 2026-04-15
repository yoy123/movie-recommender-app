package com.movierecommender.app.ui.screens.firestick

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.KeyEvent
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

private const val LIVE_TV_URL = "https://thetvapp.to"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiveTvScreen(
    onBackClick: () -> Unit
) {
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val isLoading = remember { mutableStateOf(true) }

    BackHandler {
        val wv = webViewRef.value
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onBackClick()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webViewRef.value = this

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading.value = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading.value = false
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            // Keep all navigation inside the WebView
                            return false
                        }
                    }

                    webChromeClient = WebChromeClient()

                    // Handle DPAD back key inside WebView
                    setOnKeyListener { _, keyCode, event ->
                        if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                            if (canGoBack()) {
                                goBack()
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    }

                    loadUrl(LIVE_TV_URL)
                }
            }
        )

        if (isLoading.value) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }
    }
}
