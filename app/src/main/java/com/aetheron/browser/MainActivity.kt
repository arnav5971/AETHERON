package com.aetheron.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Environment
import android.webkit.DownloadListener
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var previousSystemUiVisibility: Int = 0
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = fileChooserCallback
            fileChooserCallback = null
            if (callback != null) {
                val data = result.data
                val uris = if (result.resultCode == RESULT_OK && data != null) {
                    val clip = data.clipData
                    when {
                        clip != null -> Array(clip.itemCount) { index -> clip.getItemAt(index).uri }
                        data.data != null -> arrayOf(data.data!!)
                        else -> null
                    }
                } else null
                callback.onReceiveValue(uris)
            }
        }
        setContent { AetheronBrowser(this) }
    }

    fun openFileChooser(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = callback
        return try {
            fileChooserLauncher.launch(params.createIntent())
            true
        } catch (_: Exception) {
            fileChooserCallback = null
            callback.onReceiveValue(null)
            Toast.makeText(this, "Unable to open file picker", Toast.LENGTH_SHORT).show()
            false
        }
    }

    @Suppress("DEPRECATION")
    fun showVideoFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (fullscreenView != null) {
            callback.onCustomViewHidden()
            return
        }
        val decor = window.decorView as ViewGroup
        fullscreenView = view
        fullscreenCallback = callback
        previousSystemUiVisibility = decor.systemUiVisibility
        (view.parent as? ViewGroup)?.removeView(view)
        decor.addView(view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            decor.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
        }
    }

    @Suppress("DEPRECATION")
    fun hideVideoFullscreen() {
        val view = fullscreenView ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        fullscreenView = null
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.show(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
        } else {
            window.decorView.systemUiVisibility = previousSystemUiVisibility
        }
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
    }

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        if (fullscreenView != null) hideVideoFullscreen()
        super.onDestroy()
    }
}

private val Ink = Color(0xFF182235)
private val Blue = Color(0xFF3268D9)
private val Muted = Color(0xFF758295)
private val Line = Color(0xFFDCE4ED)
private val Pale = Color(0xFFEAF1FA)
private val Green = Color(0xFF218767)
private const val GOOGLE_HOME = "https://www.google.com"
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

/**
 * Switches the WebView request profile without losing its original Android
 * WebView user agent. Mobile mode removes WebView-specific markers so sites
 * are more likely to serve their normal mobile experience.
 */
private fun applySiteMode(view: WebView, desktopMode: Boolean) {
    val originalMobileUa = (view.tag as? String) ?: view.settings.userAgentString
    val chromeLikeMobileUa = originalMobileUa
        .replace("; wv)", ")")
        .replace("Version/4.0 ", "")
    view.settings.userAgentString = if (desktopMode) DESKTOP_USER_AGENT else chromeLikeMobileUa
    // Keep viewport-meta support enabled in both modes so responsive mobile sites
    // can honor width=device-width; only desktop mode zooms out to the wide layout.
    view.settings.useWideViewPort = true
    view.settings.loadWithOverviewMode = desktopMode
}

private fun openExternalUri(context: Context, uri: Uri): Boolean {
    val scheme = uri.scheme?.lowercase() ?: return true
    if (scheme == "http" || scheme == "https") return false
    return try {
        val intent = if (scheme == "intent") {
            Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).apply {
                component = null
                selector = null
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val fallback = intent.getStringExtra("browser_fallback_url")
            if (!fallback.isNullOrBlank() && BrowserAddress.isWebUrl(fallback)) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallback)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
        }
        true
    } catch (_: Exception) {
        Toast.makeText(context, "Unable to open this link", Toast.LENGTH_SHORT).show()
        true
    }
}

private data class SavedPage(val title: String, val url: String)

private class BrowserStore(context: Context) {
    private val prefs = context.getSharedPreferences("aetheron_store", Context.MODE_PRIVATE)
    fun read(key: String): List<SavedPage> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                SavedPage(item.optString("title"), item.optString("url"))
            }
        } catch (_: Exception) { emptyList() }
    }
    fun write(key: String, items: List<SavedPage>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("title", it.title).put("url", it.url)) }
        prefs.edit().putString(key, array.toString()).apply()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AetheronBrowser(context: Context) {
    val store = remember { BrowserStore(context) }
    var address by remember { mutableStateOf("") }
    var currentTitle by remember { mutableStateOf("New tab") }
    var currentUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var desktop by remember { mutableStateOf(false) }
    var isVideoFullscreen by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var page by remember { mutableStateOf("Home") }
    var menuOpen by remember { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var showAiInfo by remember { mutableStateOf(false) }
    var bookmarks by remember { mutableStateOf(store.read("bookmarks")) }
    var history by remember { mutableStateOf(store.read("history").take(100)) }
    var tabCount by remember { mutableIntStateOf(1) }
    var tabTitle by remember { mutableStateOf("New tab") }

    fun navigate(raw: String) {
        val target = BrowserAddress.resolve(raw)
        address = target
        currentUrl = target
        if (page == "Web") webView?.loadUrl(target) else page = "Web"
    }
    fun addHistory(url: String, title: String) {
        if (!BrowserAddress.isWebUrl(url)) return
        history = (listOf(SavedPage(title.ifBlank { url }, url)) + history.filterNot { it.url == url }).take(100)
        store.write("history", history)
    }
    fun addBookmark() {
        if (!BrowserAddress.isWebUrl(currentUrl)) return
        bookmarks = (listOf(SavedPage(currentTitle, currentUrl)) + bookmarks.filterNot { it.url == currentUrl })
        store.write("bookmarks", bookmarks)
        Toast.makeText(context, "Saved to bookmarks", Toast.LENGTH_SHORT).show()
    }

    BackHandler(enabled = isVideoFullscreen) {
        (context as? MainActivity)?.hideVideoFullscreen()
        isVideoFullscreen = false
    }
    BackHandler(page == "Web" && !isVideoFullscreen) {
        if (webView?.canGoBack() == true) webView?.goBack()
        else { page = "Home"; loading = false }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.let { view ->
                view.stopLoading()
                view.webChromeClient = null
                view.webViewClient = WebViewClient()
                (view.parent as? ViewGroup)?.removeView(view)
                view.removeAllViews()
                view.destroy()
            }
            webView = null
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Column(Modifier.fillMaxSize()) {
            if (page != "Web") {
            // Tab strip
            Row(
                Modifier.fillMaxWidth().height(56.dp).background(Color(0xFFF7F9FC))
                    .padding(start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier.weight(1f).fillMaxHeight().padding(top = 5.dp, bottom = 0.dp)
                        .background(Color.White, RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp))
                        .padding(horizontal = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Public, null, tint = Ink, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(tabTitle, color = Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { page = "Home"; tabTitle = "New tab"; address = ""; currentUrl = "" }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, "Close tab", tint = Muted)
                    }
                }
                IconButton(onClick = {
                    tabCount++
                    page = "Home"; tabTitle = "New tab"; address = ""; currentUrl = ""
                    webView?.stopLoading()
                }) { Icon(Icons.Default.Add, "New tab", tint = Muted) }
                IconButton(onClick = { showTabs = true }) {
                    Icon(Icons.Default.Tab, "Tabs", tint = Muted)
                }
                Text(tabCount.toString(), color = Muted, fontSize = 13.sp)
            }
            HorizontalDivider(color = Line)

            }

            // Browser toolbar
            Row(
                Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                IconButton(onClick = { if (webView?.canGoBack() == true) webView?.goBack() else page = "Home" }, enabled = canBack || page == "Web") {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Ink)
                }
                IconButton(onClick = { webView?.goForward() }, enabled = canForward) {
                    Icon(Icons.Default.ArrowForward, "Forward", tint = if (canForward) Ink else Muted)
                }
                IconButton(onClick = { webView?.reload() }) { Icon(Icons.Default.Refresh, "Reload", tint = Ink) }
                IconButton(onClick = { page = "Home"; tabTitle = "New tab"; address = ""; currentUrl = "" }) {
                    Icon(Icons.Default.Home, "Home", tint = Ink)
                }
                Row(
                    Modifier.weight(1f).height(48.dp).background(Pale, RoundedCornerShape(22.dp))
                        .padding(start = 13.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = Muted, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = address,
                        onValueChange = { address = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(color = Ink, fontSize = 14.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { navigate(address) }),
                        decorationBox = { inner ->
                            Box {
                                if (address.isBlank()) Text("Search or enter address", color = Muted, maxLines = 1)
                                inner()
                            }
                        }
                    )
                    IconButton(onClick = { addBookmark() }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.StarBorder, "Bookmark", tint = Muted)
                    }
                }
                IconButton(onClick = {
                    tabCount++
                    page = "Home"; tabTitle = "New tab"; address = ""; currentUrl = ""
                }) { Icon(Icons.Default.Add, "New tab", tint = Ink) }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreHoriz, "More", tint = Ink) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(if (desktop) "Switch to mobile site" else "Request desktop site") },
                            leadingIcon = { Icon(Icons.Default.DesktopWindows, null) },
                            onClick = {
                                val nextMode = !desktop
                                desktop = nextMode
                                webView?.let { view ->
                                    applySiteMode(view, nextMode)
                                    view.reload()
                                }
                                menuOpen = false
                            })
                        DropdownMenuItem(text = { Text("Bookmarks") }, leadingIcon = { Icon(Icons.Default.Bookmarks, null) }, onClick = { page = "Saved"; menuOpen = false })
                        DropdownMenuItem(text = { Text("History") }, leadingIcon = { Icon(Icons.Default.History, null) }, onClick = { page = "History"; menuOpen = false })
                        DropdownMenuItem(text = { Text("Downloads") }, leadingIcon = { Icon(Icons.Default.Download, null) }, onClick = {
                            menuOpen = false
                            try { context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) } catch (_: Exception) {
                                Toast.makeText(context, "Open Downloads from your device", Toast.LENGTH_SHORT).show()
                            }
                        })
                        DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { menuOpen = false; showAiInfo = true })
                    }
                }
            }
            if (loading && page == "Web") {
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp), color = Blue, trackColor = Pale)
            }

            // Main content
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (page == "Web") {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            (webView ?: WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadsImagesAutomatically = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = false
                                settings.textZoom = 100
                                settings.setSupportZoom(false)
                                settings.builtInZoomControls = false
                                settings.displayZoomControls = false
                                settings.mediaPlaybackRequiresUserGesture = false
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                settings.safeBrowsingEnabled = true
                                settings.javaScriptCanOpenWindowsAutomatically = false
                                settings.setSupportMultipleWindows(false)
                                settings.databaseEnabled = true
                                settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                                tag = settings.userAgentString
                                applySiteMode(this, desktop)
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                        loading = true
                                        url?.let { address = it; currentUrl = it }
                                        canBack = view.canGoBack(); canForward = view.canGoForward()
                                    }
                                    override fun onPageFinished(view: WebView, url: String?) {
                                        loading = false
                                        url?.let { address = it; currentUrl = it; addHistory(it, view.title.orEmpty()) }
                                        currentTitle = view.title.orEmpty().ifBlank { "AETHERON" }
                                        tabTitle = currentTitle
                                        canBack = view.canGoBack(); canForward = view.canGoForward()
                                    }
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                        val uri = request.url
                                        if (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) return false
                                        return openExternalUri(context, uri)
                                    }

                                    @Deprecated("Deprecated in Android")
                                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                                        val uri = Uri.parse(url)
                                        if (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) return false
                                        return openExternalUri(context, uri)
                                    }
                                    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                                        handler.cancel()
                                        Toast.makeText(view.context, "Secure connection blocked", Toast.LENGTH_LONG).show()
                                    }
                                    override fun onSafeBrowsingHit(view: WebView, request: WebResourceRequest, threatType: Int, callback: SafeBrowsingResponse) {
                                        callback.backToSafety(true)
                                    }
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                                        progress = newProgress
                                        loading = newProgress < 100
                                    }
                                    override fun onReceivedTitle(view: WebView, title: String?) {
                                        currentTitle = title.orEmpty()
                                    }
                                    override fun onShowFileChooser(
                                        webView: WebView,
                                        filePathCallback: ValueCallback<Array<Uri>>,
                                        fileChooserParams: FileChooserParams
                                    ): Boolean {
                                        return (context as? MainActivity)?.openFileChooser(filePathCallback, fileChooserParams)
                                            ?: run { filePathCallback.onReceiveValue(null); false }
                                    }
                                    override fun onShowCustomView(
                                        view: View,
                                        callback: CustomViewCallback
                                    ) {
                                        (context as? MainActivity)?.showVideoFullscreen(view, callback)
                                        isVideoFullscreen = true
                                    }
                                    override fun onHideCustomView() {
                                        (context as? MainActivity)?.hideVideoFullscreen()
                                        isVideoFullscreen = false
                                    }
                                }
                                setDownloadListener(DownloadListener { url, userAgent, disposition, mime, _ ->
                                    try {
                                        val filename = URLUtil.guessFileName(url, disposition, mime)
                                        val downloadRequest = DownloadManager.Request(Uri.parse(url)).setMimeType(mime)
                                            .addRequestHeader("User-Agent", userAgent).setTitle(filename)
                                        android.webkit.CookieManager.getInstance().getCookie(url)
                                            ?.takeIf { it.isNotBlank() }
                                            ?.let { downloadRequest.addRequestHeader("Cookie", it) }
                                        val request = downloadRequest
                                            .setDescription("Downloading with AETHERON")
                                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                                        (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                                        Toast.makeText(ctx, "Download started", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) { Toast.makeText(ctx, "Download failed to start", Toast.LENGTH_LONG).show() }
                                })
                                webView = this
                                if (currentUrl.isNotBlank()) loadUrl(currentUrl)
                            }).also { webView = it }
                        },
                        update = { view -> if (webView !== view) webView = view }
                    )
                } else if (page == "Home") {
                    HomeScreen(
                        onSearch = { navigate(it) },
                        onQuick = { navigate(it) },
                        onBookmarks = { page = "Saved" },
                        onAi = { showAiInfo = true }
                    )
                } else {
                    SavedScreen(
                        heading = if (page == "Saved") "Saved pages" else "Browsing history",
                        entries = if (page == "Saved") bookmarks else history,
                        emptyText = if (page == "Saved") "Your bookmarks will appear here." else "Pages you visit will appear here.",
                        onOpen = { navigate(it) },
                        onDelete = { item ->
                            if (page == "Saved") {
                                bookmarks = bookmarks.filterNot { it.url == item.url }; store.write("bookmarks", bookmarks)
                            } else {
                                history = history.filterNot { it.url == item.url }; store.write("history", history)
                            }
                        },
                        onClear = {
                            if (page == "Saved") { bookmarks = emptyList(); store.write("bookmarks", bookmarks) }
                            else { history = emptyList(); store.write("history", history) }
                        }
                    )
                }
            }

            if (page != "Web") {
            // Bottom navigation
            HorizontalDivider(color = Line)
            Row(
                Modifier.fillMaxWidth().height(72.dp).background(Color.White)
                    .navigationBarsPadding().padding(horizontal = 7.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomItem("Home", Icons.Default.Home, page == "Home") { page = "Home" }
                BottomItem("Saved", Icons.Default.BookmarkBorder, page == "Saved") { page = "Saved" }
                BottomItem("History", Icons.Default.History, page == "History") { page = "History" }
                BottomItem("AI", Icons.Default.AutoAwesome, false) { showAiInfo = true }
                BottomItem("Tabs", Icons.Default.Tab, false, badge = tabCount.toString()) { showTabs = true }
            }
            }
        }
    }

    if (showTabs) {
        AlertDialog(
            onDismissRequest = { showTabs = false },
            title = { Text("Tabs") },
            text = { Column {
                Text("Current tab", fontWeight = FontWeight.SemiBold)
                Text(tabTitle, color = Muted)
                Spacer(Modifier.height(12.dp))
                Text("Open tabs: $tabCount")
                Text("Tab switching and independent page sessions are coming in the next tab-engine milestone.", fontSize = 12.sp, color = Muted)
            } },
            confirmButton = { TextButton(onClick = {
                tabCount++; page = "Home"; tabTitle = "New tab"; address = ""; currentUrl = ""; showTabs = false
            }) { Text("New tab") } },
            dismissButton = { TextButton(onClick = { showTabs = false }) { Text("Close") } }
        )
    }
    if (showAiInfo) {
        AlertDialog(
            onDismissRequest = { showAiInfo = false },
            title = { Text("AETHERON AI") },
            text = { Text("The AI panel is designed for page summaries, explanations and browsing help. A secure AI service endpoint and provider credentials must be configured on a backend before requests can be enabled. No page content is sent anywhere in this build.") },
            confirmButton = { TextButton(onClick = { showAiInfo = false }) { Text("Got it") } }
        )
    }
}

@Composable
private fun HomeScreen(
    onSearch: (String) -> Unit,
    onQuick: (String) -> Unit,
    onBookmarks: () -> Unit,
    onAi: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val quick = listOf(
        Triple("Wikipedia", "https://www.wikipedia.org", Icons.Default.MenuBook),
        Triple("YouTube", "https://m.youtube.com", Icons.Default.PlayArrow),
        Triple("GitHub", "https://github.com", Icons.Default.Code),
        Triple("Reddit", "https://www.reddit.com", Icons.Default.Public)
    )
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 28.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(Green, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Text("YOUR SPACE, YOUR PACE", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.1.sp)
                }
                Spacer(Modifier.height(62.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(92.dp).background(Color(0xFFE0EAFE), RoundedCornerShape(30.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Public, null, tint = Blue, modifier = Modifier.size(52.dp))
                    }
                }
                Spacer(Modifier.height(36.dp))
                Text(
                    text = "A little more room\nto roam.",
                    modifier = Modifier.fillMaxWidth(),
                    color = Ink, fontSize = 36.sp, lineHeight = 43.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(22.dp))
                Text("A calmer, faster place to explore the web.", modifier = Modifier.fillMaxWidth(),
                    color = Muted, fontSize = 16.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(48.dp))
                Row(
                    Modifier.fillMaxWidth().height(76.dp).background(Color.White, RoundedCornerShape(22.dp))
                        .padding(horizontal = 17.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = Muted, modifier = Modifier.size(25.dp))
                    Spacer(Modifier.width(16.dp))
                    BasicTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.weight(1f), singleLine = true,
                        textStyle = TextStyle(color = Ink, fontSize = 16.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (query.isNotBlank()) onSearch(query) }),
                        decorationBox = { inner -> Box {
                            if (query.isBlank()) Text("What are you looking for?", color = Muted, fontSize = 16.sp)
                            inner()
                        } }
                    )
                    IconButton(onClick = { if (query.isNotBlank()) onSearch(query) },
                        modifier = Modifier.background(Pale, RoundedCornerShape(12.dp))) {
                        Icon(Icons.Default.ArrowForward, "Search", tint = Blue)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null, tint = Green, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Private by design", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.width(22.dp))
                    Icon(Icons.Default.KeyboardCommandKey, null, tint = Green, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Search or enter a URL", color = Muted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(76.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Quick access", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text("Your favorite corners of the internet.", color = Muted, fontSize = 13.sp)
                    }
                    TextButton(onClick = onBookmarks) {
                        Text("All bookmarks  →", color = Blue, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(22.dp))
            }
            items(quick.chunked(2)) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                    pair.forEach { (name, url, icon) ->
                        Column(
                            Modifier.weight(1f).height(126.dp).background(Color.White, RoundedCornerShape(22.dp))
                                .clickable { onQuick(url) }.padding(17.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(Modifier.size(48.dp).background(Pale, RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = Blue, modifier = Modifier.size(27.dp))
                            }
                            Spacer(Modifier.height(13.dp))
                            Text(name, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onAi, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Blue)
                    Spacer(Modifier.width(8.dp))
                    Text("Explore AETHERON AI", color = Blue)
                }
            }
        }
    }
}

@Composable
private fun SavedScreen(
    heading: String,
    entries: List<SavedPage>,
    emptyText: String,
    onOpen: (String) -> Unit,
    onDelete: (SavedPage) -> Unit,
    onClear: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(heading, modifier = Modifier.weight(1f), color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            if (entries.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear all", color = Blue) }
        }
        Spacer(Modifier.height(12.dp))
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyText, color = Muted, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it.url }) { item ->
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFFF7F9FC), RoundedCornerShape(15.dp))
                            .clickable { onOpen(item.url) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Language, null, tint = Blue)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.url, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onDelete(item) }) { Icon(Icons.Default.DeleteOutline, "Delete", tint = Muted) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, badge: String? = null, onClick: () -> Unit) {
    Column(
        Modifier.width(66.dp).fillMaxHeight().clickable(onClick = onClick).padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box {
            Icon(icon, label, tint = if (selected) Blue else Muted, modifier = Modifier.size(27.dp))
            if (badge != null) {
                Box(Modifier.offset(x = 14.dp, y = (-7).dp).size(19.dp).background(Blue, CircleShape), contentAlignment = Alignment.Center) {
                    Text(badge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (selected) Blue else Muted, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
