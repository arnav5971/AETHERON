package com.aetheron.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.DownloadListener
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import android.widget.Toast
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        setContent { AetheronBrowser() }
    }
}

private val Night = Color(0xFF080D18)
private val Panel = Color(0xFF111B2D)
private val Cyan = Color(0xFF55DDF5)
private val Muted = Color(0xFF91A4BC)
private val TextPrimary = Color(0xFFEAF5FF)
private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AetheronBrowser() {
    var address by remember { mutableStateOf("https://www.google.com") }
    var title by remember { mutableStateOf("New tab") }
    var progress by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var backEnabled by remember { mutableStateOf(false) }
    var forwardEnabled by remember { mutableStateOf(false) }
    var browser by remember { mutableStateOf<WebView?>(null) }
    var desktopMode by remember { mutableStateOf(false) }

    fun go(input: String) {
        val target = BrowserAddress.resolve(input)
        address = target
        browser?.loadUrl(target)
    }

    BackHandler(backEnabled) { browser?.goBack() }
    LaunchedEffect(desktopMode) {
        browser?.let { view ->
            val originalAgent = view.tag as? String ?: view.settings.userAgentString
            if (view.tag == null) view.tag = originalAgent
            view.settings.userAgentString = if (desktopMode) DESKTOP_USER_AGENT else (view.tag as String)
            view.settings.useWideViewPort = desktopMode
            view.settings.loadWithOverviewMode = desktopMode
            view.reload()
        }
    }

    Surface(Modifier.fillMaxSize(), color = Night) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF172B46), Night)))
                    .padding(horizontal = 16.dp, vertical = 15.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).background(
                            Brush.linearGradient(listOf(Cyan, Color(0xFF5677F5))),
                            RoundedCornerShape(13.dp)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("A", color = Night, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("AETHERON", color = TextPrimary, fontWeight = FontWeight.Bold,
                            fontSize = 18.sp, letterSpacing = 2.sp)
                        Text("THE NEXT ERA OF BROWSING", color = Muted,
                            fontSize = 9.sp, letterSpacing = 1.2.sp)
                    }
                    Icon(Icons.Default.Shield, contentDescription = "Safe browsing", tint = Cyan)
                }
                Spacer(Modifier.height(15.dp))
                Row(
                    Modifier.fillMaxWidth().background(Panel, CircleShape)
                        .padding(start = 13.dp, end = 5.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Cyan)
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = address,
                        onValueChange = { address = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { go(address) }),
                        decorationBox = { inner ->
                            Box {
                                if (address.isEmpty()) Text("Search or enter address", color = Muted)
                                inner()
                            }
                        }
                    )
                    IconButton(onClick = { go(address) }) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Go", tint = Cyan)
                    }
                }
                if (loading) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        Modifier.fillMaxWidth().height(2.dp),
                        color = Cyan,
                        trackColor = Panel
                    )
                }
            }

            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.safeBrowsingEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        val mobileUserAgent = settings.userAgentString
                        settings.useWideViewPort = desktopMode
                        settings.loadWithOverviewMode = desktopMode
                        settings.userAgentString = if (desktopMode) DESKTOP_USER_AGENT else mobileUserAgent
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                url?.let { address = it }
                                loading = true
                                backEnabled = view.canGoBack()
                                forwardEnabled = view.canGoForward()
                            }
                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                url?.let { address = it }
                                title = view.title.orEmpty().ifBlank { "AETHERON" }
                                loading = false
                                backEnabled = view.canGoBack()
                                forwardEnabled = view.canGoForward()
                            }
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                !BrowserAddress.isWebUrl(request.url.toString())
                            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                                handler.cancel()
                                Toast.makeText(view.context, "Secure connection blocked", Toast.LENGTH_LONG).show()
                            }
                            override fun onSafeBrowsingHit(
                                view: WebView, request: WebResourceRequest, threatType: Int,
                                callback: SafeBrowsingResponse
                            ) {
                                callback.backToSafety(true)
                            }
                        }
                        setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                            try {
                                val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
                                val request = DownloadManager.Request(Uri.parse(url))
                                    .setMimeType(mimeType)
                                    .addRequestHeader("User-Agent", userAgent)
                                    .setTitle(filename)
                                    .setDescription("Downloading with AETHERON")
                                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                                (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                                Toast.makeText(ctx, "Download started", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {
                                Toast.makeText(ctx, "Could not start download", Toast.LENGTH_LONG).show()
                            }
                        })
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress
                                loading = newProgress < 100
                            }
                            override fun onReceivedTitle(view: WebView, newTitle: String?) {
                                title = newTitle.orEmpty().ifBlank { "AETHERON" }
                            }
                        }
                        browser = this
                        loadUrl(address)
                    }
                },
                update = { browser = it }
            )

            Column(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 14.dp, vertical = 7.dp)) {
                Text(title, color = TextPrimary, fontSize = 11.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { browser?.goBack() }, enabled = backEnabled) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back",
                            tint = if (backEnabled) TextPrimary else Muted)
                    }
                    IconButton(onClick = { browser?.goForward() }, enabled = forwardEnabled) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Forward",
                            tint = if (forwardEnabled) TextPrimary else Muted)
                    }
                    IconButton(onClick = { go("https://www.google.com") }) {
                        Icon(Icons.Default.Home, contentDescription = "Home", tint = Cyan)
                    }
                    IconButton(onClick = { browser?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = TextPrimary)
                    }
                    TextButton(onClick = { desktopMode = !desktopMode }) {
                        Text(if (desktopMode) "DESKTOP" else "MOBILE", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
