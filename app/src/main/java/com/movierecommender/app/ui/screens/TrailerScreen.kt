package com.movierecommender.app.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TrailerScreen(
    title: String,
    videoUrl: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var fallbackTriggered by remember { mutableStateOf(false) }

    // If no video URL provided, show no trailer available immediately
    if (videoUrl.isBlank()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = title, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No trailer available",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "We couldn't find an official trailer for this movie. Try another recommendation or search for it yourself.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        var chromeClient: FullscreenableChromeClient? = null

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        // Use desktop user agent to bypass mobile embed restrictions
                        settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        val wc = FullscreenableChromeClient(activity)
                        chromeClient = wc
                        webChromeClient = wc
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                // Prevent external app launches - keep everything in WebView
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                // Set video to play inline without fullscreen
                                view?.evaluateJavascript(
                                    "document.querySelector('video')?.setAttribute('playsinline','1');"
                                ) { }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                // Trigger fallback on any error loading the video
                                if (request?.url?.toString()?.contains("imdb", ignoreCase = true) == true) {
                                    fallbackTriggered = true
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: WebResourceResponse?
                            ) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                // Trigger fallback on HTTP errors
                                if (request?.url?.toString()?.contains("imdb", ignoreCase = true) == true) {
                                    fallbackTriggered = true
                                }
                            }
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        setBackgroundColor(Color.Black.toArgb())
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        // Load video URL wrapped in HTML5 video player
                        val htmlContent = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                <style>
                                    body {
                                        margin: 0;
                                        padding: 0;
                                        background-color: black;
                                        display: flex;
                                        justify-content: center;
                                        align-items: center;
                                        height: 100vh;
                                    }
                                    video {
                                        width: 100%;
                                        height: 100%;
                                        max-width: 100vw;
                                        max-height: 100vh;
                                        object-fit: contain;
                                    }
                                </style>
                            </head>
                            <body>
                                <video controls autoplay playsinline>
                                    <source src="$videoUrl" type="video/mp4">
                                    Your browser does not support the video tag.
                                </video>
                            </body>
                            </html>
                        """.trimIndent()
                        loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier.matchParentSize(),
                update = { webView ->
                    CookieManager.getInstance().flush()
                    if (fallbackTriggered) {
                        if (webView.url != "about:blank") {
                            webView.loadUrl("about:blank")
                        }
                    }
                }
            )

            if (fallbackTriggered) {
                Surface(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surface),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Video Error",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Unable to load the trailer. The video may have expired or is temporarily unavailable.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Try another trailer from the recommendations list.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Some trailers may be restricted due to age or regional limitations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Intercept back to exit fullscreen first, if active
        BackHandler(enabled = !fallbackTriggered && chromeClient?.isInFullScreen() == true) {
            chromeClient?.exitFullScreen()
        }

        // Ensure the WebView gets destroyed when leaving the screen
        DisposableEffect(Unit) {
            onDispose {
                // Nothing to do here because AndroidView handles disposal,
                // but we keep this in case we want to manually clean up.
            }
        }
    }
}

private class FullscreenableChromeClient(private val activity: Activity?) : WebChromeClient() {
    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null
    private var insetsController: WindowInsetsControllerCompat? = null

    fun isInFullScreen(): Boolean = customView != null

    fun exitFullScreen() {
        onHideCustomView()
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        val act = activity ?: return
        val decor = act.window.decorView as ViewGroup
        customView = view
        customViewCallback = callback
        insetsController = WindowInsetsControllerCompat(act.window, decor).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        decor.addView(view, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    override fun onHideCustomView() {
        val act = activity ?: return
        val decor = act.window.decorView as ViewGroup
        customView?.let { decor.removeView(it) }
        insetsController?.show(WindowInsetsCompat.Type.systemBars())
        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
    }
}
