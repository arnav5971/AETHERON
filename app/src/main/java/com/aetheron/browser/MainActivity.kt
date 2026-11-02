package com.aetheron.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.os.Environment
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
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
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.app.NotificationCompat
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
import androidx.compose.runtime.saveable.rememberSaveable
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
    internal var activeWebView: WebView? = null
    internal val popupWebViews = mutableSetOf<WebView>()
    private var previousSystemUiVisibility: Int = 0
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private lateinit var sitePermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>

    private companion object {
        const val APP_NOTIFICATION_CHANNEL = "aetheron_browser"
        const val APP_NOTIFICATION_ID = 4301
    }

    private fun createAppNotificationChannel() {
        val channel = NotificationChannel(
            APP_NOTIFICATION_CHANNEL,
            "AETHERON notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Browser alerts and download updates" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun enableAppNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            showNotificationsEnabled()
        }
    }

    private fun showNotificationsEnabled() {
        val notification = NotificationCompat.Builder(this, APP_NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("AETHERON notifications enabled")
            .setContentText("Browser notifications are allowed on this device.")
            .setAutoCancel(true)
            .build()
        try {
            getSystemService(NotificationManager::class.java).notify(APP_NOTIFICATION_ID, notification)
            Toast.makeText(this, "AETHERON app notification sent", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Allow notifications in Android Settings", Toast.LENGTH_LONG).show()
        }
    }
    private var pendingRuntimePermissionResult: ((Map<String, Boolean>) -> Unit)? = null
    private var pendingWebPermissionRequest: PermissionRequest? = null

    private fun requestSiteRuntimePermissions(
        permissions: Array<String>,
        onResult: (Map<String, Boolean>) -> Unit
    ) {
        pendingRuntimePermissionResult = onResult
        try {
            sitePermissionLauncher.launch(permissions)
        } catch (_: Exception) {
            pendingRuntimePermissionResult = null
            onResult(emptyMap())
        }
    }

    private fun showSitePermissionConsent(
        origin: Uri,
        purpose: String,
        permissions: Array<String>,
        requireAll: Boolean = true,
        onResult: (Boolean) -> Unit
    ) {
        val host = origin.host ?: "This website"
        AlertDialog.Builder(this)
            .setTitle("$host wants to use $purpose")
            .setMessage("Allow only if you trust this website. Android will ask for the required device permission next.")
            .setNegativeButton("Don't allow") { _, _ -> onResult(false) }
            .setPositiveButton("Continue") { _, _ ->
                val missing = permissions.filter {
                    checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
                }.toTypedArray()
                if (missing.isEmpty()) {
                    onResult(true)
                } else {
                    requestSiteRuntimePermissions(missing) { results ->
                        val granted = if (requireAll) {
                            missing.all { results[it] == true }
                        } else {
                            permissions.any { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
                        }
                        onResult(granted)
                    }
                }
            }
            .setOnCancelListener { onResult(false) }
            .show()
    }

    fun cancelWebPermissionRequest(request: PermissionRequest) {
        if (pendingWebPermissionRequest === request) pendingWebPermissionRequest = null
    }

    fun handleWebPermissionRequest(request: PermissionRequest) {
        if (!request.origin.scheme.equals("https", ignoreCase = true)) {
            request.deny()
            return
        }
        val resources = request.resources.toSet()
        if (resources.isEmpty() || resources.any {
                it != PermissionRequest.RESOURCE_VIDEO_CAPTURE &&
                    it != PermissionRequest.RESOURCE_AUDIO_CAPTURE
            }) {
            request.deny()
            return
        }
        val androidPermissions = buildList {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in resources) add(Manifest.permission.CAMERA)
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in resources) add(Manifest.permission.RECORD_AUDIO)
        }.toTypedArray()
        pendingWebPermissionRequest?.deny()
        pendingWebPermissionRequest = request
        showSitePermissionConsent(request.origin, when {
            resources.size == 2 -> "your camera and microphone"
            PermissionRequest.RESOURCE_VIDEO_CAPTURE in resources -> "your camera"
            else -> "your microphone"
        }, androidPermissions) { allowed ->
            if (pendingWebPermissionRequest !== request) return@showSitePermissionConsent
            pendingWebPermissionRequest = null
            if (allowed && androidPermissions.all {
                    checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
                }) {
                request.grant(resources.toTypedArray())
            } else {
                request.deny()
            }
        }
    }

    fun handleGeolocationPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback
    ) {
        val uri = runCatching { Uri.parse(origin) }.getOrNull()
        if (uri?.scheme.equals("https", ignoreCase = true).not() || uri?.host.isNullOrBlank()) {
            callback.invoke(origin, false, false)
            return
        }
        showSitePermissionConsent(
            uri!!,
            "your location",
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            requireAll = false
        ) { allowed ->
            val granted = allowed && (
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                )
            callback.invoke(origin, granted, false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        sitePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val callback = pendingRuntimePermissionResult
            pendingRuntimePermissionResult = null
            callback?.invoke(result)
        }
        notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) showNotificationsEnabled()
            else Toast.makeText(this, "Notification permission was not granted", Toast.LENGTH_LONG).show()
        }
        createAppNotificationChannel()
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
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
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

    override fun onPause() {
        activeWebView?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        activeWebView?.onResume()
    }

    fun destroyPopupWebView(popup: WebView) {
        if (!popupWebViews.remove(popup)) return
        popup.stopLoading()
        popup.webChromeClient = null
        popup.webViewClient = null
        (popup.parent as? ViewGroup)?.removeView(popup)
        popup.removeAllViews()
        popup.destroy()
    }

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        pendingWebPermissionRequest?.let { runCatching { it.deny() } }
        pendingWebPermissionRequest = null
        pendingRuntimePermissionResult = null
        popupWebViews.toList().forEach(::destroyPopupWebView)
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
private const val GOOGLE_AI_MODE = "https://www.google.com/ai"

/**
 * Google blocks OAuth inside embedded WebViews. Keep Google Search, AI Mode,
 * and Google account/consent pages in a browser-owned Custom Tab so sign-in
 * uses the user's real browser cookies and security surface.
 */
internal fun shouldOpenInTrustedBrowser(rawUrl: String): Boolean = runCatching {
    val uri = java.net.URI(rawUrl)
    val host = uri.host?.lowercase()?.trimEnd('.') ?: return@runCatching false
    uri.scheme.equals("https", ignoreCase = true) &&
        (uri.port == -1 || uri.port == 443) && host in setOf(
        "google.com",
        "www.google.com",
        "accounts.google.com",
        "consent.google.com",
        "myaccount.google.com"
    )
}.getOrDefault(false)

private fun openGoogleInCustomTab(context: Context, url: String) {
    try {
        val customTab = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setToolbarColor(android.graphics.Color.rgb(24, 34, 53))
            .build()
        customTab.launchUrl(context, Uri.parse(url))
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to open Google in a secure browser", Toast.LENGTH_LONG).show()
        }
    }
}
/**
 * Android WebView keeps the physical phone width as the CSS viewport when a
 * site declares width=device-width. A desktop User-Agent alone therefore does
 * not reliably activate desktop breakpoints. In desktop mode we replace only
 * the top-level viewport meta with a conventional desktop width (980 CSS px).
 */
private fun applyDesktopViewport(view: WebView) {
    val script = """
        (function() {
          var head = document.head || document.getElementsByTagName('head')[0];
          if (!head) return false;
          var target = 'width=980, initial-scale=1.0, minimum-scale=0.25, maximum-scale=5.0, user-scalable=yes';
          function applyDesktopViewport() {
            var meta = document.querySelector('meta[name="viewport"]');
            if (!meta) {
              meta = document.createElement('meta');
              meta.setAttribute('name', 'viewport');
              head.appendChild(meta);
            }
            if (meta.getAttribute('content') !== target) meta.setAttribute('content', target);
          }
          applyDesktopViewport();
          if (!window.__aetheronDesktopViewportObserver) {
            window.__aetheronDesktopViewportObserver = new MutationObserver(applyDesktopViewport);
            window.__aetheronDesktopViewportObserver.observe(head, {
              childList: true, subtree: true, attributes: true, attributeFilter: ['content']
            });
          }
          return true;
        })();
    """.trimIndent()
    val density = view.resources.displayMetrics.density.coerceAtLeast(1f)
    val measuredWidthPx = view.width.takeIf { it > 0 } ?: view.resources.displayMetrics.widthPixels
    val widthDp = (measuredWidthPx / density).toInt().coerceAtLeast(320)
    val initialScalePercent = (widthDp * 100 / 980).coerceIn(25, 100)
    view.evaluateJavascript(script) {
        // Fit the desktop CSS canvas to the phone width once, while retaining
        // pinch zoom so the user can inspect desktop-sized controls and text.
        view.setInitialScale(initialScalePercent)
    }
}

/**
 * Changes only the site request profile. Mobile mode retains the site's own
 * viewport; desktop mode gets a desktop UA plus a desktop-sized CSS viewport.
 */
private fun applySiteMode(view: WebView, desktopMode: Boolean) {
    val originalMobileUa = (view.tag as? String) ?: view.settings.userAgentString
    val chromeVersion = Regex("Chrome/[^ ]+").find(originalMobileUa)?.value
        ?: "Chrome/AndroidWebView"
    val mobileUa = originalMobileUa
        .replace("; wv)", ")")
        .replace("Version/4.0 ", "")
    val desktopUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) $chromeVersion Safari/537.36"

    view.settings.userAgentString = if (desktopMode) desktopUa else mobileUa
    view.settings.useWideViewPort = true
    view.settings.loadWithOverviewMode = false
    view.settings.setSupportZoom(desktopMode)
    view.settings.builtInZoomControls = desktopMode
    view.settings.displayZoomControls = false
    if (!desktopMode) view.setInitialScale(0)
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
    var address by rememberSaveable { mutableStateOf("") }
    var currentTitle by rememberSaveable { mutableStateOf("New tab") }
    var currentUrl by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var desktop by rememberSaveable { mutableStateOf(false) }
    var isVideoFullscreen by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var page by rememberSaveable { mutableStateOf("Home") }
    var menuOpen by remember { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var bookmarks by remember { mutableStateOf(store.read("bookmarks")) }
    var history by remember { mutableStateOf(store.read("history").take(100)) }
    val tabCount = 1
    var tabTitle by rememberSaveable { mutableStateOf("New tab") }

    fun navigate(raw: String) {
        val target = BrowserAddress.resolve(raw)
        if (shouldOpenInTrustedBrowser(target)) {
            openGoogleInCustomTab(context, target)
            return
        }
        address = target
        currentUrl = target
        page = "Web"
        webView?.loadUrl(target)
    }

    fun openHome() {
        webView?.stopLoading()
        webView?.loadUrl("about:blank")
        page = "Home"
        tabTitle = "New tab"
        address = ""
        currentUrl = ""
        currentTitle = "New tab"
        loading = false
        progress = 0
        canBack = false
        canForward = false
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
    BackHandler(enabled = page == "Resources" && !isVideoFullscreen) {
        page = "Home"
    }
    BackHandler(enabled = (page == "Saved" || page == "History") && !isVideoFullscreen) {
        page = "Home"
    }
    BackHandler(page == "Web" && !isVideoFullscreen) {
        if (webView?.canGoBack() == true) webView?.goBack()
        else { page = "Home"; loading = false }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.let { view ->
                if ((context as? MainActivity)?.activeWebView === view) {
                    (context as? MainActivity)?.activeWebView = null
                }
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
                    IconButton(onClick = { openHome() }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, "Close tab", tint = Muted)
                    }
                }
                IconButton(onClick = { openHome() }) { Icon(Icons.Default.Add, "New tab", tint = Muted) }
                IconButton(onClick = { showTabs = true }) {
                    Icon(Icons.Default.Tab, "Tabs", tint = Muted)
                }
                Text(tabCount.toString(), color = Muted, fontSize = 13.sp)
            }
            HorizontalDivider(color = Line)

            }

            // Browser toolbar
            BoxWithConstraints(
                Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp)
            ) {
                // On narrow phone widths, keep the address field usable by moving
                // Home/New Tab into the overflow menu instead of squeezing it.
                val compactToolbar = maxWidth < 400.dp
                Row(
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                IconButton(
                    onClick = {
                        if (page != "Web") page = "Home"
                        else if (webView?.canGoBack() == true) webView?.goBack()
                        else page = "Home"
                    },
                    enabled = page != "Home" || (page == "Web" && canBack)
                ) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Ink)
                }
                IconButton(onClick = { webView?.goForward() }, enabled = page == "Web" && canForward) {
                    Icon(Icons.Default.ArrowForward, "Forward", tint = if (canForward) Ink else Muted)
                }
                IconButton(onClick = { webView?.reload() }, enabled = page == "Web") { Icon(Icons.Default.Refresh, "Reload", tint = if (page == "Web") Ink else Muted) }
                if (!compactToolbar) {
                    IconButton(onClick = { openHome() }) {
                        Icon(Icons.Default.Home, "Home", tint = Ink)
                    }
                }
                Row(
                    Modifier.weight(1f).height(44.dp).background(Pale, RoundedCornerShape(22.dp))
                        .padding(start = 13.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = Muted, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(                        value = address,
                        onValueChange = { address = it },                        modifier = Modifier.weight(1f),
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
                    IconButton(onClick = { addBookmark() }, enabled = page == "Web" && BrowserAddress.isWebUrl(currentUrl), modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.StarBorder, "Bookmark", tint = Muted)
                    }
                }
                if (!compactToolbar) {
                    IconButton(onClick = { openHome() }) { Icon(Icons.Default.Add, "New tab", tint = Ink) }
                }
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
                        DropdownMenuItem(text = { Text("Test app notifications") }, leadingIcon = { Icon(Icons.Default.Notifications, null) }, onClick = {
                            menuOpen = false
                            (context as? MainActivity)?.enableAppNotifications()
                        })
                        DropdownMenuItem(text = { Text("Home") }, leadingIcon = { Icon(Icons.Default.Home, null) }, onClick = { openHome(); menuOpen = false })
                        DropdownMenuItem(text = { Text("New tab") }, leadingIcon = { Icon(Icons.Default.Add, null) }, onClick = { openHome(); menuOpen = false })
                        DropdownMenuItem(text = { Text("Bookmarks") }, leadingIcon = { Icon(Icons.Default.Bookmarks, null) }, onClick = { page = "Saved"; menuOpen = false })
                        DropdownMenuItem(text = { Text("History") }, leadingIcon = { Icon(Icons.Default.History, null) }, onClick = { page = "History"; menuOpen = false })
                        DropdownMenuItem(text = { Text("Downloads") }, leadingIcon = { Icon(Icons.Default.Download, null) }, onClick = {
                            menuOpen = false
                            try { context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) } catch (_: Exception) {
                                Toast.makeText(context, "Open Downloads from your device", Toast.LENGTH_SHORT).show()
                            }
                        })
                        DropdownMenuItem(text = { Text("AI Mode · Google") }, leadingIcon = { Icon(Icons.Default.AutoAwesome, null) }, onClick = { menuOpen = false; navigate(GOOGLE_AI_MODE) })
                    }
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
                                // Match the Compose content slot exactly; do not constrain the
                                // browser viewport to a video aspect ratio or a fixed pixel size.
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
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
                                // Allow user-initiated target=_blank / window.open flows.
                                // The popup handler below routes Google OAuth to a trusted browser
                                // and keeps ordinary web popups inside AETHERON.
                                settings.javaScriptCanOpenWindowsAutomatically = false
                                settings.setSupportMultipleWindows(true)
                                settings.databaseEnabled = true
                                settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                                tag = settings.userAgentString
                                applySiteMode(this, desktop)
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                        loading = true
                                        url?.takeIf { BrowserAddress.isWebUrl(it) }?.let {
                                            address = it
                                            currentUrl = it
                                        }
                                        canBack = view.canGoBack(); canForward = view.canGoForward()
                                    }
                                    override fun onPageFinished(view: WebView, url: String?) {
                                        loading = false
                                        if (url == "about:blank") {
                                            progress = 0
                                            canBack = false
                                            canForward = false
                                            return
                                        }
                                        if (desktop) applyDesktopViewport(view)
                                        url?.takeIf { BrowserAddress.isWebUrl(it) }?.let {
                                            address = it
                                            currentUrl = it
                                            addHistory(it, view.title.orEmpty())
                                        }
                                        currentTitle = view.title.orEmpty().ifBlank { "AETHERON" }
                                        tabTitle = currentTitle
                                        canBack = view.canGoBack(); canForward = view.canGoForward()
                                    }
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                        val uri = request.url
                                        if (shouldOpenInTrustedBrowser(uri.toString())) {
                                            openGoogleInCustomTab(context, uri.toString())
                                            return true
                                        }
                                        if (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) return false
                                        return openExternalUri(context, uri)
                                    }

                                    @Deprecated("Deprecated in Android")
                                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                                        val uri = Uri.parse(url)
                                        if (shouldOpenInTrustedBrowser(url)) {
                                            openGoogleInCustomTab(context, url)
                                            return true
                                        }
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
                                    override fun onCreateWindow(
                                        view: WebView,
                                        isDialog: Boolean,
                                        isUserGesture: Boolean,
                                        resultMsg: Message
                                    ): Boolean {
                                        // Do not allow unsolicited/scripted popups.
                                        if (!isUserGesture) return false

                                        val popup = WebView(view.context).apply {
                                            layoutParams = ViewGroup.LayoutParams(1, 1)
                                            alpha = 0f
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.javaScriptCanOpenWindowsAutomatically = false
                                            settings.setSupportMultipleWindows(false)
                                        }
                                        (context as? MainActivity)?.popupWebViews?.add(popup)
                                        var routed = false
                                        fun routePopup(url: String, popupView: WebView) {
                                            if (routed || url == "about:blank") return
                                            routed = true
                                            when {
                                                shouldOpenInTrustedBrowser(url) ->
                                                    openGoogleInCustomTab(context, url)
                                                Uri.parse(url).scheme.equals("http", true) ||
                                                    Uri.parse(url).scheme.equals("https", true) ->
                                                    view.loadUrl(url)
                                                else -> openExternalUri(context, Uri.parse(url))
                                            }
                                            popupView.post {
                                                (context as? MainActivity)?.destroyPopupWebView(popupView)
                                            }
                                        }
                                        popup.webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                popupView: WebView,
                                                request: WebResourceRequest
                                            ): Boolean {
                                                routePopup(request.url.toString(), popupView)
                                                return true
                                            }

                                            @Deprecated("Deprecated in Android")
                                            override fun shouldOverrideUrlLoading(
                                                popupView: WebView,
                                                url: String
                                            ): Boolean {
                                                routePopup(url, popupView)
                                                return true
                                            }

                                            override fun onPageStarted(
                                                popupView: WebView,
                                                url: String?,
                                                favicon: Bitmap?
                                            ) {
                                                if (!url.isNullOrBlank()) routePopup(url, popupView)
                                            }

                                            override fun onRenderProcessGone(
                                                popupView: WebView,
                                                detail: android.webkit.RenderProcessGoneDetail
                                            ): Boolean {
                                                (context as? MainActivity)?.destroyPopupWebView(popupView)
                                                    ?: run { popupView.destroy() }
                                                return true
                                            }
                                        }
                                        // Attach the temporary WebView so Chromium can complete
                                        // the popup handshake; it is removed as soon as its URL
                                        // is handed to the main tab or the trusted browser.
                                        view.addView(popup)
                                        val transport = resultMsg.obj as? WebView.WebViewTransport
                                            ?: run {
                                                (context as? MainActivity)?.destroyPopupWebView(popup)
                                                    ?: run {
                                                        view.removeView(popup)
                                                        popup.destroy()
                                                    }
                                                return false
                                            }
                                        transport.webView = popup
                                        resultMsg.sendToTarget()
                                        return true
                                    }

                                    override fun onCloseWindow(window: WebView) {
                                        (context as? MainActivity)?.destroyPopupWebView(window)
                                    }

                                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                                        progress = newProgress
                                        loading = newProgress < 100
                                    }
                                    override fun onReceivedTitle(view: WebView, title: String?) {
                                        currentTitle = title.orEmpty()
                                    }
                                    override fun onRenderProcessGone(
                                        view: WebView,
                                        detail: android.webkit.RenderProcessGoneDetail
                                    ): Boolean {
                                        if (webView === view) {
                                            webView = null
                                            (context as? MainActivity)?.activeWebView = null
                                            page = "Home"
                                            loading = false
                                            progress = 0
                                            canBack = false
                                            canForward = false
                                            Toast.makeText(
                                                context,
                                                "A web page stopped unexpectedly. Please reopen it.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        (view.parent as? ViewGroup)?.removeView(view)
                                        view.destroy()
                                        return true
                                    }
                                    override fun onPermissionRequest(request: PermissionRequest) {
                                        (context as? MainActivity)?.handleWebPermissionRequest(request)
                                            ?: request.deny()
                                    }
                                    override fun onPermissionRequestCanceled(request: PermissionRequest) {
                                        (context as? MainActivity)?.cancelWebPermissionRequest(request)
                                    }
                                    override fun onGeolocationPermissionsShowPrompt(
                                        origin: String,
                                        callback: GeolocationPermissions.Callback
                                    ) {
                                        (context as? MainActivity)?.handleGeolocationPrompt(origin, callback)
                                            ?: callback.invoke(origin, false, false)
                                    }
                                    override fun onGeolocationPermissionsHidePrompt() {
                                        // No persistent site-level grant is retained by AETHERON.
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
                                (ctx as? MainActivity)?.activeWebView = this
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
                        onAi = { navigate(GOOGLE_AI_MODE) },
                        onResources = { page = "Resources" }
                    )
                } else if (page == "Resources") {
                    DeveloperResourcesScreen(
                        onBack = { page = "Home" },
                        onOpenService = { url -> navigate(url) }
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
                BottomItem("AI", Icons.Default.AutoAwesome, false) { navigate(GOOGLE_AI_MODE) }
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
                Text("Open browsing session: 1")
                Text("Independent multi-tab sessions are not available yet.", fontSize = 12.sp, color = Muted)
            } },
            confirmButton = { TextButton(onClick = {
                openHome()
                showTabs = false
            }) { Text("New tab") } },
            dismissButton = { TextButton(onClick = { showTabs = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun HomeScreen(
    onSearch: (String) -> Unit,
    onQuick: (String) -> Unit,
    onBookmarks: () -> Unit,
    onAi: () -> Unit,
    onResources: () -> Unit
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
                    textAlign = TextAlign.Center                )
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
                    Text("Bookmarks & history stored locally", color = Muted, fontSize = 13.sp)
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
                    Text("AI Mode · Powered by Google", color = Blue)
                }
                TextButton(onClick = onResources, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Code, null, tint = Blue)
                    Spacer(Modifier.width(8.dp))
                    Text("Free Developer Resources", color = Blue)
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