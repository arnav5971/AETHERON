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
import android.speech.RecognizerIntent
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
    private lateinit var speechInputLauncher: ActivityResultLauncher<Intent>
    private var pendingSpeechResult: ((String) -> Unit)? = null

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
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setOnExitAnimationListener { splashView ->
            splashView.iconView.animate()
                .alpha(0f)
                .scaleX(0.88f)
                .scaleY(0.88f)
                .setDuration(350L)
                .withEndAction { splashView.remove() }
                .start()
        }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        sitePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val callback = pendingRuntimePermissionResult
            pendingRuntimePermissionResult = null
            callback?.invoke(result)
        }
        speechInputLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val recognized = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            val callback = pendingSpeechResult
            pendingSpeechResult = null
            callback?.invoke(recognized)
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
        window.statusBarColor = android.graphics.Color.rgb(9, 7, 37)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            (window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN.inv()) and
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        setContent { AetheronBrowser(this) }
    }

    fun startVoiceSearch(onResult: (String) -> Unit) {
        pendingSpeechResult = onResult
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search with AETHERON")
        }
        try {
            speechInputLauncher.launch(intent)
        } catch (_: Exception) {
            pendingSpeechResult = null
            Toast.makeText(this, "Voice search is unavailable on this device", Toast.LENGTH_LONG).show()
        }
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
        popup.webViewClient = WebViewClient()
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
        pendingSpeechResult = null
        popupWebViews.toList().forEach(::destroyPopupWebView)
        if (fullscreenView != null) hideVideoFullscreen()
        super.onDestroy()
    }
}

private val Ink = Color(0xFFF2F3FF)
private val Blue = Color(0xFF9B8CFF)
private val Muted = Color(0xFFA1A8C2)
private val Line = Color(0xFF292E49)
private val Pale = Color(0xFF1A2037)
private val Green = Color(0xFF63E6C4)
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
    // Never hand browser-internal or local-resource schemes to another app.
    if (scheme in setOf("javascript", "vbscript", "data", "blob", "file", "content", "about", "chrome", "devtools")) {
        return true
    }
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
    fun readSetting(key: String, defaultValue: String): String =
        prefs.getString("setting_$key", defaultValue) ?: defaultValue
    fun writeSetting(key: String, value: String) {
        prefs.edit().putString("setting_$key", value).apply()
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
        val target = BrowserAddress.resolve(raw, store.readSetting("search_engine", "Google"))
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
    BackHandler(enabled = page in setOf("AI", "Privacy", "Settings") && !isVideoFullscreen) {
        page = "Home"
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

    Surface(Modifier.fillMaxSize(), color = Color(0xFF090C18)) {
        Column(Modifier.fillMaxSize()) {

            if (page != "Home") {
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
                    enabled = page != "Home"
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
                        DropdownMenuItem(text = { Text("Bookmarks") }, leadingIcon = { Icon(Icons.Default.Bookmarks, null) }, onClick = { page = "Saved"; menuOpen = false })
                        DropdownMenuItem(text = { Text("History") }, leadingIcon = { Icon(Icons.Default.History, null) }, onClick = { page = "History"; menuOpen = false })
                        DropdownMenuItem(text = { Text("Downloads") }, leadingIcon = { Icon(Icons.Default.Download, null) }, onClick = {
                            menuOpen = false
                            try { context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) } catch (_: Exception) {
                                Toast.makeText(context, "Open Downloads from your device", Toast.LENGTH_SHORT).show()
                            }
                        })
                        DropdownMenuItem(text = { Text("Privacy & Security") }, leadingIcon = { Icon(Icons.Default.Security, null) }, onClick = { page = "Privacy"; menuOpen = false })
                        DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { page = "Settings"; menuOpen = false })
                        DropdownMenuItem(text = { Text("AI Mode · Google") }, leadingIcon = { Icon(Icons.Default.Language, null) }, onClick = { menuOpen = false; navigate(GOOGLE_AI_MODE) })
                    }
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
                        onHome = { page = "Home" },
                        onVoiceSearch = {
                            (context as? MainActivity)?.startVoiceSearch { spoken ->
                                if (spoken.isNotBlank()) navigate(spoken)
                            }
                        },
                        onVisualSearch = { navigate("https://lens.google.com/") },
                        onQuick = { navigate(it) },
                        onBookmarks = { page = "Saved" },
                        onHistory = { page = "History" },
                        onPrivacy = { page = "Privacy" },
                        onDownloads = {
                            try { context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
                            catch (_: Exception) { Toast.makeText(context, "Downloads screen is unavailable on this device", Toast.LENGTH_SHORT).show() }
                        },
                        onSettings = { page = "Settings" }
                    )
                } else if (page == "AI" || page == "Privacy" || page == "Settings") {
                    AetheronFeatureScreen(
                        page = page,
                        onBack = { page = "Home" },
                        desktopMode = desktop,
                        searchEngine = store.readSetting("search_engine", "Google"),
                        onDesktopModeChange = { enabled ->
                            desktop = enabled
                            webView?.let { view ->
                                applySiteMode(view, enabled)
                                if (currentUrl.isNotBlank()) view.reload()
                            }
                        },
                        onSearchEngineChange = { store.writeSetting("search_engine", it) },
                        onOpenDownloads = {
                            try {
                                context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                            } catch (_: Exception) {
                                Toast.makeText(context, "Downloads screen is unavailable on this device", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onClearBrowsingData = {
                            history = emptyList()
                            store.write("history", emptyList())
                            webView?.apply { clearHistory(); clearCache(true) }
                            android.webkit.CookieManager.getInstance().removeAllCookies(null)
                            android.webkit.CookieManager.getInstance().flush()
                            android.webkit.WebStorage.getInstance().deleteAllData()
                            Toast.makeText(context, "History, cache and website data cleared. Bookmarks and downloads were kept.", Toast.LENGTH_LONG).show()
                        },
                        onOpenAppPermissions = {
                            try {
                                context.startActivity(Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                ))
                            } catch (_: Exception) {
                                Toast.makeText(context, "Unable to open Android app settings", Toast.LENGTH_SHORT).show()
                            }
                        }
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

            if (page != "Web" && page != "Home") {
            // Bottom navigation
            HorizontalDivider(color = Line)
            Row(
                Modifier.fillMaxWidth().height(72.dp).background(Color(0xFF101529))
                    .navigationBarsPadding().padding(horizontal = 7.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomItem("Home", Icons.Default.Home, page == "Home") { page = "Home" }
                BottomItem("Saved", Icons.Default.BookmarkBorder, page == "Saved") { page = "Saved" }
                BottomItem("History", Icons.Default.History, page == "History") { page = "History" }
                BottomItem("AI", Icons.Default.AutoAwesome, page == "AI") { page = "AI" }
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
    onHome: () -> Unit,
    onVoiceSearch: () -> Unit,
    onVisualSearch: () -> Unit,
    onQuick: (String) -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onPrivacy: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color(0xFF172A83))
    ) {
        // The supplied artwork already contains the exact logo, illustrations,
        // cards, labels and navigation artwork. Keep it intact; only add hit targets.
        Image(
            painter = painterResource(id = R.drawable.aetheron_home_art),
            contentDescription = "AETHERON home screen",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )

        fun hit(x: Float, y: Float, w: Float, h: Float, action: () -> Unit): Modifier =
            Modifier.offset(x = maxWidth * x, y = maxHeight * y)
                .size(width = maxWidth * w, height = maxHeight * h)
                // Invisible hit areas must not draw the default dark ripple/splash.
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = action
                )

        // Search field, microphone and visual scan control.
        Box(hit(0.035f, 0.252f, 0.70f, 0.065f) { showSearch = true })
        Box(hit(0.755f, 0.252f, 0.085f, 0.065f, onVoiceSearch))
        Box(hit(0.855f, 0.252f, 0.105f, 0.065f, onVisualSearch))

        // Quick websites.
        Box(hit(0.075f, 0.382f, 0.18f, 0.105f) { onQuick("https://www.google.com") })
        Box(hit(0.295f, 0.382f, 0.18f, 0.105f) { onQuick("https://m.youtube.com") })
        Box(hit(0.525f, 0.382f, 0.18f, 0.105f) { onQuick("https://github.com") })
        Box(hit(0.745f, 0.382f, 0.18f, 0.105f) { onQuick("https://www.wikipedia.org") })

        // Quick actions.
        Box(hit(0.075f, 0.552f, 0.19f, 0.105f, onBookmarks))
        Box(hit(0.295f, 0.552f, 0.19f, 0.105f, onHistory))
        Box(hit(0.515f, 0.552f, 0.19f, 0.105f, onDownloads))
        Box(hit(0.735f, 0.552f, 0.19f, 0.105f, onSettings))

        // Feature cards.
        Box(hit(0.035f, 0.682f, 0.465f, 0.145f, onPrivacy))
        Box(hit(0.525f, 0.682f, 0.44f, 0.145f, onSettings))

        // Bottom navigation labels/icons are part of the reference artwork.
        Box(hit(0.025f, 0.895f, 0.17f, 0.095f, onHome))
        Box(hit(0.205f, 0.895f, 0.19f, 0.095f, onBookmarks))
        Box(hit(0.405f, 0.895f, 0.19f, 0.095f, onHistory))
        Box(hit(0.605f, 0.895f, 0.19f, 0.095f, onDownloads))
        Box(hit(0.805f, 0.895f, 0.17f, 0.095f, onSettings))
    }

    if (showSearch) {
        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text("Search or enter a website") },
            text = {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color(0xFF17105F), fontSize = 16.sp),
                    modifier = Modifier.fillMaxWidth()
                        .background(Color(0xFFF0EEFA), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        if (query.isNotBlank()) {
                            showSearch = false
                            onSearch(query)
                        }
                    })
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (query.isNotBlank()) {
                        showSearch = false
                        onSearch(query)
                    }
                }) { Text("Go", color = Color(0xFF8B2BFA)) }
            },
            dismissButton = {
                TextButton(onClick = { showSearch = false }) {
                    Text("Cancel", color = Color(0xFF6567A6))
                }
            }
        )
    }
}
@Composable
private fun AetheronFeatureScreen(
    page: String,
    onBack: () -> Unit,
    desktopMode: Boolean = false,
    searchEngine: String = "Google",
    onDesktopModeChange: (Boolean) -> Unit = {},
    onSearchEngineChange: (String) -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onClearBrowsingData: () -> Unit = {},
    onOpenAppPermissions: () -> Unit = {}
) {
    val title = when (page) { "AI" -> "AETHERON AI"; "Privacy" -> "Privacy & Security"; else -> "Settings" }
    val subtitle = when (page) {
        "AI" -> "A thoughtful assistant for your browsing."
        "Privacy" -> "Understand and control your browser privacy."
        else -> "Make AETHERON work the way you like."
    }
    var showSearchPicker by remember { mutableStateOf(false) }
    var showClearDataConfirm by remember { mutableStateOf(false) }
    val items = when (page) {
        "AI" -> listOf(
            Triple("Summarize a page", "Turn long pages into key points", Icons.Default.AutoAwesome),
            Triple("Explain selected text", "Understand difficult topics", Icons.Default.Lightbulb),
            Triple("Translate content", "Explore pages in your language", Icons.Default.Translate),
            Triple("Writing assistant", "Draft and refine your text", Icons.Default.EditNote)
        )
        "Privacy" -> listOf(
            Triple("Safe Browsing", "Android WebView Safe Browsing is enabled", Icons.Default.Security),
            Triple("Site permissions", "Manage Android app permissions in system settings", Icons.Default.AdminPanelSettings),
            Triple("Clear browsing data", "History and cookies controls are being expanded", Icons.Default.DeleteSweep),
            Triple("Incognito mode", "Private isolated sessions are not implemented yet", Icons.Default.VisibilityOff)
        )
        else -> listOf(
            Triple("Appearance", "Futuristic dark theme (light theme not available yet)", Icons.Default.Palette),
            Triple("Search engine", "Current: $searchEngine · Tap to change", Icons.Default.Search),
            Triple("Downloads", "Open Android Download Manager", Icons.Default.Download),
            Triple("Desktop site", if (desktopMode) "Enabled for the active tab" else "Disabled for the active tab", Icons.Default.DesktopWindows),
            Triple("Account & Sync", "Cloud sync is not connected yet", Icons.Default.CloudSync),
            Triple("About AETHERON", "Your space, your pace · Android browser", Icons.Default.Info)
        )
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Ink) }
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        if (page == "AI") {
            Column(Modifier.fillMaxWidth().background(Color(0xFF211B48), RoundedCornerShape(20.dp)).padding(17.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFC5B8FF), modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(10.dp))
                Text("AI workspace preview", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("The secure Ktor AI service exists in the repository but is not deployed or connected to this app. These actions are design previews only.", color = Color(0xFFD2C9F4), fontSize = 12.sp, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(16.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items) { item ->
                val isDesktop = page == "Settings" && item.first == "Desktop site"
                val isSearch = page == "Settings" && item.first == "Search engine"
                val isDownloads = page == "Settings" && item.first == "Downloads"
                val isClearData = page == "Privacy" && item.first == "Clear browsing data"
                val isSitePermissions = page == "Privacy" && item.first == "Site permissions"
                val isAction = isDesktop || isSearch || isDownloads || isClearData || isSitePermissions
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFF151B2F), RoundedCornerShape(16.dp))
                        .then(if (isAction) Modifier.clickable {
                            when {
                                isDesktop -> onDesktopModeChange(!desktopMode)
                                isSearch -> showSearchPicker = true
                                isDownloads -> onOpenDownloads()
                                isClearData -> showClearDataConfirm = true
                                isSitePermissions -> onOpenAppPermissions()
                            }
                        } else Modifier).padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(42.dp).background(Color(0xFF25264A), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                        Icon(item.third, null, tint = Blue)
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.first, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(item.second, color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                    if (isDesktop) Switch(checked = desktopMode, onCheckedChange = onDesktopModeChange)
                    else if (isSearch || isDownloads || isClearData || isSitePermissions) Icon(Icons.Default.ChevronRight, null, tint = Muted)
                }
            }
        }
    }
    if (showClearDataConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirm = false },
            title = { Text("Clear browsing data?") },
            text = { Text("This clears saved browsing history, cached files, cookies and website storage. Bookmarks and downloaded files are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDataConfirm = false
                    onClearBrowsingData()
                }) { Text("Clear data") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataConfirm = false }) { Text("Cancel") }
            }
        )
    }
    if (showSearchPicker) {
        AlertDialog(
            onDismissRequest = { showSearchPicker = false },
            title = { Text("Default search engine") },
            text = {
                Column {
                    listOf("Google", "Bing", "DuckDuckGo").forEach { engine ->
                        Row(Modifier.fillMaxWidth().clickable {
                            onSearchEngineChange(engine)
                            showSearchPicker = false
                        }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = searchEngine == engine, onClick = {
                                onSearchEngineChange(engine)
                                showSearchPicker = false
                            })
                            Spacer(Modifier.width(8.dp))
                            Text(engine)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSearchPicker = false }) { Text("Close") } }
        )
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
                        Modifier.fillMaxWidth().background(Color(0xFF151B2F), RoundedCornerShape(15.dp))
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