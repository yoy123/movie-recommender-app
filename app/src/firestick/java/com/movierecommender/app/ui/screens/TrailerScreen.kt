package com.movierecommender.app.ui.screens.firestick

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.movierecommender.app.ui.leanback.LeanbackTopBar

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
    var webView: WebView? by remember { mutableStateOf(null) }

    // If no video URL provided, show no trailer available immediately
    if (videoUrl.isBlank()) {
        NoTrailerAvailable(title, onBackClick)
        return
    }

    // Check if this is a YouTube video key (prefixed with "youtube:")
    val isYouTubeKey = videoUrl.startsWith("youtube:")
    val youtubeVideoId = if (isYouTubeKey) videoUrl.removePrefix("youtube:") else null

    // Hide system bars for fullscreen and cleanup
    DisposableEffect(Unit) {
        activity?.let { act ->
            WindowInsetsControllerCompat(act.window, act.window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            webView?.apply {
                loadUrl("about:blank")
                stopLoading()
                destroy()
            }
            activity?.let { act ->
                WindowInsetsControllerCompat(act.window, act.window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Handle back press
    BackHandler {
        webView?.apply {
            loadUrl("about:blank")
            stopLoading()
        }
        onBackClick()
    }

    if (youtubeVideoId != null) {
        // Use WebView with YouTube IFrame Player for YouTube videos
        YouTubeWebViewPlayer(
            videoId = youtubeVideoId,
            onWebViewCreated = { webView = it },
            onBackClick = onBackClick
        )
    } else {
        // Play direct MP4 URL via HTML5 video player in WebView
        DirectVideoPlayer(
            videoUrl = videoUrl,
            onWebViewCreated = { webView = it },
            onBackClick = onBackClick
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeWebViewPlayer(
    videoId: String,
    onWebViewCreated: (WebView) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    
    // Load the full YouTube watch page instead of iframe embed
    // This works better for videos that restrict embedding
    val youtubeUrl = "https://www.youtube.com/watch?v=$videoId"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(AndroidColor.BLACK)
                    
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        builtInZoomControls = false
                        displayZoomControls = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        allowFileAccess = true
                        allowContentAccess = true
                        setSupportMultipleWindows(false)
                        // Use desktop user agent for better YouTube experience on TV
                        userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    }
                    
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            // Keep YouTube content in the WebView
                            return url?.contains("youtube.com") != true && 
                                   url?.contains("googlevideo.com") != true
                        }
                    }
                    
                    webChromeClient = object : WebChromeClient() {
                        // Enable fullscreen video support
                    }
                    
                    // Load the full YouTube watch page directly
                    loadUrl(youtubeUrl)
                    
                    onWebViewCreated(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DirectVideoPlayer(
    videoUrl: String,
    onWebViewCreated: (WebView) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(AndroidColor.BLACK)
                    
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        builtInZoomControls = false
                        displayZoomControls = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        allowFileAccess = true
                        allowContentAccess = true
                        // Use desktop user agent for better video playback
                        userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    }
                    
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            // Keep video content in the WebView
                            return false
                        }
                    }
                    
                    webChromeClient = object : WebChromeClient() {
                        // Enable fullscreen video support
                    }
                    
                    // Load direct video URL wrapped in HTML5 video player
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
                    
                    onWebViewCreated(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoTrailerAvailable(title: String, onBackClick: () -> Unit) {
    Scaffold(
        topBar = {
            LeanbackTopBar(
                title = title,
                subtitle = "Trailer unavailable",
                onBackClick = onBackClick
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
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ErrorScreen(title: String, errorMessage: String, onBackClick: () -> Unit) {
    Scaffold(
        topBar = {
            LeanbackTopBar(
                title = title,
                subtitle = "Playback issue",
                onBackClick = onBackClick
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
                    text = "Video Error",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage,
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
            }
        }
    }
}
