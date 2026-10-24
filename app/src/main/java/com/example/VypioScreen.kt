package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.ui.theme.VypioAccent
import com.example.ui.theme.VypioBackgroundDark
import com.example.ui.theme.VypioBorderDark
import com.example.ui.theme.VypioPrimary
import com.example.ui.theme.VypioSecondary
import com.example.ui.theme.VypioSurfaceDark
import com.example.ui.theme.VypioSurfaceVariantDark
import com.example.ui.theme.VypioTextPrimaryDark
import com.example.ui.theme.VypioTextSecondaryDark

const val VYPIO_HOME_URL = "https://vypio.xyz/"
private const val PREFS_NAME = "vypio_offline_prefs"
private const val KEY_DRAFTS = "saved_drafts"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VypioScreen(
  networkMonitor: NetworkMonitor,
  deepLinkUrl: String? = null,
  onDeepLinkHandled: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val isOnline by networkMonitor.isOnline.collectAsState(initial = networkMonitor.isCurrentlyOnline())

  var webViewInstance by remember { mutableStateOf<WebView?>(null) }
  var swipeRefreshLayoutInstance by remember { mutableStateOf<SwipeRefreshLayout?>(null) }
  var canGoBack by remember { mutableStateOf(false) }
  var pageProgress by remember { mutableIntStateOf(0) }
  var isLoading by remember { mutableStateOf(true) }
  val animatedProgress by animateFloatAsState(
    targetValue = if (isLoading) pageProgress.coerceIn(8, 100) / 100f else 1f,
    animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
    label = "webview_load_progress"
  )
  var isInitialLoading by remember { mutableStateOf(true) }
  var hasLoadError by remember { mutableStateOf(false) }
  var currentUrl by remember { mutableStateOf(VYPIO_HOME_URL) }
  var customFullscreenView by remember { mutableStateOf<View?>(null) }
  var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
  var showOnlineRestoredBanner by remember { mutableStateOf(false) }

  // Notification runtime permission launcher (Android 13+)
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      Toast.makeText(context, "Push notifications enabled", Toast.LENGTH_SHORT).show()
    }
  }

  // Request notification permission on first launch if not granted
  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
      if (!hasPermission) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  // Handle deep link when notification is clicked
  LaunchedEffect(deepLinkUrl) {
    if (!deepLinkUrl.isNullOrBlank()) {
      webViewInstance?.loadUrl(deepLinkUrl)
      onDeepLinkHandled()
    }
  }

  // File upload chooser callback reference
  var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    val uris: Array<Uri>? = if (result.resultCode == android.app.Activity.RESULT_OK) {
      val data = result.data
      when {
        data?.clipData != null -> {
          val clip = data.clipData!!
          Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
        }
        data?.data != null -> arrayOf(data.data!!)
        else -> null
      }
    } else {
      null
    }
    fileUploadCallback?.onReceiveValue(uris)
    fileUploadCallback = null
  }

  // Handle system back navigation inside WebView or closing fullscreen
  BackHandler(enabled = customFullscreenView != null || canGoBack) {
    if (customFullscreenView != null) {
      customViewCallback?.onCustomViewHidden()
      customFullscreenView = null
      customViewCallback = null
    } else if (webViewInstance?.canGoBack() == true) {
      webViewInstance?.goBack()
    }
  }

  // React dynamically to network changes: switch cache mode and notify user
  LaunchedEffect(isOnline) {
    webViewInstance?.settings?.cacheMode = if (isOnline) {
      WebSettings.LOAD_DEFAULT
    } else {
      WebSettings.LOAD_CACHE_ELSE_NETWORK
    }

    if (isOnline) {
      if (hasLoadError) {
        hasLoadError = false
        webViewInstance?.reload()
      } else {
        showOnlineRestoredBanner = true
      }
    } else {
      showOnlineRestoredBanner = false
    }
  }

  LaunchedEffect(showOnlineRestoredBanner) {
    if (showOnlineRestoredBanner) {
      kotlinx.coroutines.delay(4000)
      showOnlineRestoredBanner = false
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(VypioBackgroundDark)
  ) {
    // SwipeRefreshLayout wrapping WebView for smooth pull-to-refresh
    AndroidView(
      modifier = Modifier
        .fillMaxSize()
        .testTag("vypio_pull_to_refresh"),
      factory = { ctx ->
        val webView = WebView(ctx).apply {
          layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
          )
          setBackgroundColor(android.graphics.Color.parseColor("#09090B"))

          settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = if (networkMonitor.isCurrentlyOnline()) {
              WebSettings.LOAD_DEFAULT
            } else {
              WebSettings.LOAD_CACHE_ELSE_NETWORK
            }
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            userAgentString = "$userAgentString VypioAndroidApp/1.0"
          }

          val cookieManager = CookieManager.getInstance()
          cookieManager.setAcceptCookie(true)
          cookieManager.setAcceptThirdPartyCookies(this, true)

          webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
              super.onPageStarted(view, url, favicon)
              isLoading = true
              hasLoadError = false
              pageProgress = 10
              url?.let { currentUrl = it }
              canGoBack = view?.canGoBack() ?: false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
              super.onPageFinished(view, url)
              isLoading = false
              isInitialLoading = false
              swipeRefreshLayoutInstance?.isRefreshing = false
              url?.let { currentUrl = it }
              canGoBack = view?.canGoBack() ?: false
            }

            override fun onReceivedError(
              view: WebView?,
              request: WebResourceRequest?,
              error: WebResourceError?
            ) {
              super.onReceivedError(view, request, error)
              if (request?.isForMainFrame == true) {
                if (!networkMonitor.isCurrentlyOnline()) {
                  view?.settings?.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                }
                hasLoadError = true
                isLoading = false
                isInitialLoading = false
                swipeRefreshLayoutInstance?.isRefreshing = false
              }
            }

            override fun onRenderProcessGone(
              view: WebView?,
              detail: RenderProcessGoneDetail?
            ): Boolean {
              hasLoadError = true
              isLoading = false
              isInitialLoading = false
              swipeRefreshLayoutInstance?.isRefreshing = false
              view?.let {
                (it.parent as? ViewGroup)?.removeView(it)
                it.destroy()
              }
              return true
            }

            override fun shouldOverrideUrlLoading(
              view: WebView?,
              request: WebResourceRequest?
            ): Boolean {
              val uri = request?.url ?: return false
              val host = uri.host ?: ""
              val scheme = uri.scheme ?: ""

              if (scheme != "http" && scheme != "https") {
                try {
                  val intent = Intent(Intent.ACTION_VIEW, uri)
                  ctx.startActivity(intent)
                } catch (e: Exception) {
                  Toast.makeText(ctx, "No application found to open this link", Toast.LENGTH_SHORT).show()
                }
                return true
              }

              if (host.endsWith("vypio.xyz") || host == "vypio.xyz") {
                return false
              }

              return try {
                val intent = Intent(Intent.ACTION_VIEW, uri)
                ctx.startActivity(intent)
                true
              } catch (e: ActivityNotFoundException) {
                false
              }
            }
          }

          webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
              super.onProgressChanged(view, newProgress)
              pageProgress = newProgress
              if (newProgress >= 75) {
                isInitialLoading = false
              }
              if (newProgress >= 100) {
                isLoading = false
                isInitialLoading = false
                swipeRefreshLayoutInstance?.isRefreshing = false
              }
              canGoBack = view?.canGoBack() ?: false
            }

            override fun onShowFileChooser(
              webView: WebView?,
              filePathCallback: ValueCallback<Array<Uri>>?,
              fileChooserParams: FileChooserParams?
            ): Boolean {
              fileUploadCallback?.onReceiveValue(null)
              fileUploadCallback = filePathCallback

              val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
              }

              try {
                filePickerLauncher.launch(intent)
                return true
              } catch (e: Exception) {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = null
                return false
              }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
              customFullscreenView = view
              customViewCallback = callback
            }

            override fun onHideCustomView() {
              customViewCallback?.onCustomViewHidden()
              customFullscreenView = null
              customViewCallback = null
            }
          }

          val initialLoadUrl = deepLinkUrl ?: VYPIO_HOME_URL
          loadUrl(initialLoadUrl)
        }

        webViewInstance = webView

        val swipeRefresh = SwipeRefreshLayout(ctx).apply {
          layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
          )
          setColorSchemeColors(
            android.graphics.Color.parseColor("#F124D9"),
            android.graphics.Color.parseColor("#A855F7"),
            android.graphics.Color.parseColor("#EC4899")
          )
          setProgressBackgroundColorSchemeColor(android.graphics.Color.parseColor("#141417"))

          setOnRefreshListener {
            hasLoadError = false
            if (!networkMonitor.isCurrentlyOnline()) {
              webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            } else {
              webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
            }
            webView.reload()
          }

          setOnChildScrollUpCallback { _, _ ->
            webView.canScrollVertically(-1)
          }

          addView(
            webView,
            ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT
            )
          )
        }

        swipeRefreshLayoutInstance = swipeRefresh
        swipeRefresh
      }
    )

    // Fullscreen view overlay (e.g. video)
    if (customFullscreenView != null) {
      AndroidView(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black),
        factory = { ctx ->
          FrameLayout(ctx).apply {
            val v = customFullscreenView
            if (v != null) {
              (v.parent as? ViewGroup)?.removeView(v)
              addView(
                v,
                FrameLayout.LayoutParams(
                  FrameLayout.LayoutParams.MATCH_PARENT,
                  FrameLayout.LayoutParams.MATCH_PARENT
                )
              )
            }
          }
        }
      )
    }

    // LinearProgressIndicator at the top of the WebView providing visual feedback while web pages load
    AnimatedVisibility(
      visible = isLoading && pageProgress in 1..99,
      enter = fadeIn(animationSpec = tween(120)),
      exit = fadeOut(animationSpec = tween(220)),
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.TopCenter)
        .statusBarsPadding()
    ) {
      LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier
          .fillMaxWidth()
          .height(4.dp)
          .testTag("webview_linear_progress_indicator"),
        color = VypioPrimary,
        trackColor = VypioPrimary.copy(alpha = 0.2f),
      )
    }

    // Offline / Online Status Banner (Discreet pill at top)
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.TopCenter)
        .statusBarsPadding()
        .padding(top = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      AnimatedVisibility(
        visible = !isOnline && !isInitialLoading && !hasLoadError,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
      ) {
        Surface(
          color = VypioSurfaceDark.copy(alpha = 0.95f),
          shape = RoundedCornerShape(20.dp),
          tonalElevation = 4.dp,
          shadowElevation = 6.dp,
          modifier = Modifier
            .padding(horizontal = 16.dp)
            .testTag("offline_status_pill")
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
          ) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(VypioAccent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
              imageVector = Icons.Default.CloudOff,
              contentDescription = null,
              tint = VypioTextSecondaryDark,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Offline Mode · Viewing cached content",
              color = VypioTextPrimaryDark,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium
            )
          }
        }
      }

      AnimatedVisibility(
        visible = showOnlineRestoredBanner && isOnline && !isInitialLoading,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
      ) {
        Surface(
          color = VypioSurfaceDark.copy(alpha = 0.95f),
          shape = RoundedCornerShape(20.dp),
          tonalElevation = 4.dp,
          shadowElevation = 6.dp,
          modifier = Modifier
            .padding(horizontal = 16.dp)
            .clickable {
              showOnlineRestoredBanner = false
              webViewInstance?.reload()
            }
            .testTag("online_restored_pill")
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
          ) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF22C55E))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
              imageVector = Icons.Default.Wifi,
              contentDescription = null,
              tint = Color(0xFF22C55E),
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Back online · Tap to update feed",
              color = VypioTextPrimaryDark,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }
    }

    // Native Animated Splash Screen displaying Vypio logo while WebView initializes web content
    AnimatedVisibility(
      visible = isInitialLoading,
      enter = fadeIn(),
      exit = fadeOut(animationSpec = tween(durationMillis = 400)),
      modifier = Modifier.fillMaxSize()
    ) {
      VypioSplashScreen(
        progress = pageProgress,
        modifier = Modifier.fillMaxSize()
      )
    }

    // Comprehensive Offline Mode Hub when content cannot be loaded
    if (hasLoadError || (!isOnline && isLoading && !isInitialLoading)) {
      VypioOfflineHub(
        isOnline = isOnline,
        onRetryLive = {
          hasLoadError = false
          webViewInstance?.settings?.cacheMode = WebSettings.LOAD_DEFAULT
          webViewInstance?.reload()
        },
        onLoadCached = {
          hasLoadError = false
          webViewInstance?.settings?.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
          webViewInstance?.loadUrl(VYPIO_HOME_URL)
        }
      )
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      webViewInstance?.destroy()
      webViewInstance = null
      swipeRefreshLayoutInstance = null
    }
  }
}

/**
 * Native Animated Splash Screen displaying Vypio logo and updated tagline
 */
@Composable
fun VypioSplashScreen(
  progress: Int,
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "splash_pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.96f,
    targetValue = 1.04f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse_scale"
  )
  val pulseGlowAlpha by infiniteTransition.animateFloat(
    initialValue = 0.15f,
    targetValue = 0.35f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse_glow"
  )

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(VypioBackgroundDark)
      .testTag("vypio_splash_screen"),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.padding(32.dp)
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(140.dp)
      ) {
        Box(
          modifier = Modifier
            .size(130.dp)
            .scale(pulseScale)
            .clip(CircleShape)
            .background(VypioPrimary.copy(alpha = pulseGlowAlpha))
        )

        Image(
          painter = painterResource(id = R.drawable.ic_vypio_logo),
          contentDescription = "Vypio Logo",
          modifier = Modifier
            .size(92.dp)
            .scale(pulseScale)
            .clip(CircleShape)
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = "Vypio",
        style = MaterialTheme.typography.headlineMedium.copy(
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp
        ),
        color = VypioTextPrimaryDark
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = "A New Way to Connect",
        style = MaterialTheme.typography.bodyMedium.copy(
          fontWeight = FontWeight.Medium
        ),
        color = VypioTextSecondaryDark,
        letterSpacing = 0.5.sp
      )

      Spacer(modifier = Modifier.height(36.dp))

      CircularProgressIndicator(
        modifier = Modifier.size(24.dp),
        strokeWidth = 2.5.dp,
        color = VypioPrimary,
        trackColor = VypioSurfaceVariantDark
      )
    }
  }
}

/**
 * Dedicated Vypio Offline Mode Hub providing cached page access, offline drafts, and FCM notification controls
 */
@Composable
fun VypioOfflineHub(
  isOnline: Boolean,
  onRetryLive: () -> Unit,
  onLoadCached: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
  val savedDrafts = remember {
    val stored = prefs.getStringSet(KEY_DRAFTS, emptySet()) ?: emptySet()
    mutableStateListOf<String>().apply { addAll(stored) }
  }

  var currentDraftText by remember { mutableStateOf("") }
  val fcmToken = remember { VypioFirebaseMessagingService.getStoredToken(context) }

  fun saveDraft(text: String) {
    if (text.isNotBlank()) {
      savedDrafts.add(0, text.trim())
      prefs.edit().putStringSet(KEY_DRAFTS, savedDrafts.toSet()).apply()
      currentDraftText = ""
      Toast.makeText(context, "Draft saved offline", Toast.LENGTH_SHORT).show()
    }
  }

  fun deleteDraft(draft: String) {
    savedDrafts.remove(draft)
    prefs.edit().putStringSet(KEY_DRAFTS, savedDrafts.toSet()).apply()
  }

  fun copyToClipboard(text: String, label: String = "Vypio Draft") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
  }

  fun sendTestNotification() {
    VypioFirebaseMessagingService.createNotificationChannel(context)

    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(VypioFirebaseMessagingService.EXTRA_TARGET_URL, VYPIO_HOME_URL)
    }

    val pendingIntent = PendingIntent.getActivity(
      context,
      1001,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val largeIcon = try {
      BitmapFactory.decodeResource(context.resources, R.drawable.ic_vypio_logo)
    } catch (e: Exception) {
      null
    }

    val builder = NotificationCompat.Builder(context, VypioFirebaseMessagingService.CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_vypio_logo)
      .apply {
        if (largeIcon != null) setLargeIcon(largeIcon)
      }
      .setContentTitle("Vypio Activity Update")
      .setContentText("@alex replied: 'Welcome to Vypio! Let's connect.'")
      .setStyle(
        NotificationCompat.BigTextStyle()
          .bigText("@alex replied: 'Welcome to Vypio! Let's connect.'\nTap to open the update in Vypio.")
      )
      .setColor(context.getColor(R.color.vypio_primary))
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setContentIntent(pendingIntent)

    val manager = NotificationManagerCompat.from(context)
    try {
      manager.notify(1001, builder.build())
      Toast.makeText(context, "Test notification dispatched", Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
      Toast.makeText(context, "Please enable notification permission", Toast.LENGTH_SHORT).show()
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(VypioBackgroundDark)
      .testTag("offline_hub"),
    contentAlignment = Alignment.TopCenter
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .padding(horizontal = 20.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
      // Header Section
      item {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
          Box(
            modifier = Modifier
              .size(76.dp)
              .clip(CircleShape)
              .background(VypioSurfaceVariantDark),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (isOnline) Icons.Default.Refresh else Icons.Default.WifiOff,
              contentDescription = null,
              tint = VypioPrimary,
              modifier = Modifier.size(38.dp)
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          Text(
            text = if (isOnline) "Connection Interrupted" else "Offline Mode",
            style = MaterialTheme.typography.headlineSmall,
            color = VypioTextPrimaryDark,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
          )

          Spacer(modifier = Modifier.height(6.dp))

          Text(
            text = if (isOnline) {
              "The server couldn't be reached. You can retry or browse cached content."
            } else {
              "You are disconnected. You can browse previously cached pages or write post drafts to share when reconnected."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = VypioTextSecondaryDark,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
          )
        }
      }

      // Quick Actions Row
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Button(
            onClick = onRetryLive,
            colors = ButtonDefaults.buttonColors(
              containerColor = VypioPrimary,
              contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("retry_connection_button")
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Retry", fontWeight = FontWeight.SemiBold)
          }

          OutlinedButton(
            onClick = onLoadCached,
            colors = ButtonDefaults.outlinedButtonColors(
              contentColor = VypioTextPrimaryDark
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("load_cached_feed_button")
          ) {
            Icon(
              imageVector = Icons.Default.History,
              contentDescription = null,
              tint = VypioSecondary,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Cached Feed", fontWeight = FontWeight.SemiBold)
          }
        }
      }

      // FCM Activity Notifications Status Card
      item {
        Card(
          colors = CardDefaults.cardColors(
            containerColor = VypioSurfaceDark
          ),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("fcm_notifications_card")
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(bottom = 8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = VypioPrimary,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "FCM Push Notifications",
                color = VypioTextPrimaryDark,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
              )
            }

            Text(
              text = "Configured for Vypio activity updates (likes, mentions, replies).",
              color = VypioTextSecondaryDark,
              fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (!fcmToken.isNullOrBlank()) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .background(VypioSurfaceVariantDark)
                  .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "FCM Token: ${fcmToken.take(16)}…",
                  color = VypioTextSecondaryDark,
                  fontSize = 11.sp,
                  maxLines = 1
                )
                IconButton(
                  onClick = { copyToClipboard(fcmToken, "FCM Device Token") },
                  modifier = Modifier.size(28.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy FCM Token",
                    tint = VypioPrimary,
                    modifier = Modifier.size(14.dp)
                  )
                }
              }

              Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
              onClick = { sendTestNotification() },
              colors = ButtonDefaults.buttonColors(
                containerColor = VypioSurfaceVariantDark,
                contentColor = VypioTextPrimaryDark
              ),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .testTag("test_notification_button")
            ) {
              Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = VypioPrimary,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Send Test Activity Notification", fontSize = 12.sp)
            }
          }
        }
      }

      // Offline Post Drafter Card
      item {
        Card(
          colors = CardDefaults.cardColors(
            containerColor = VypioSurfaceDark
          ),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("offline_draft_card")
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(bottom = 10.dp)
            ) {
              Icon(
                imageVector = Icons.Default.EditNote,
                contentDescription = null,
                tint = VypioPrimary,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Draft Post Offline",
                color = VypioTextPrimaryDark,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
              )
            }

            OutlinedTextField(
              value = currentDraftText,
              onValueChange = { currentDraftText = it },
              placeholder = {
                Text(
                  text = "Write your thoughts now, post to Vypio when online…",
                  color = VypioTextSecondaryDark,
                  fontSize = 13.sp
                )
              },
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VypioPrimary,
                unfocusedBorderColor = VypioBorderDark,
                focusedTextColor = VypioTextPrimaryDark,
                unfocusedTextColor = VypioTextPrimaryDark,
                cursorColor = VypioPrimary
              ),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .testTag("offline_draft_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "${currentDraftText.length} chars",
                color = VypioTextSecondaryDark,
                fontSize = 12.sp
              )

              Button(
                onClick = { saveDraft(currentDraftText) },
                enabled = currentDraftText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                  containerColor = VypioSecondary,
                  contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                  .height(38.dp)
                  .testTag("save_draft_button")
              ) {
                Text("Save Draft", fontSize = 13.sp)
              }
            }
          }
        }
      }

      // Saved Drafts List Section
      if (savedDrafts.isNotEmpty()) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Saved Offline Drafts (${savedDrafts.size})",
              color = VypioTextPrimaryDark,
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp
            )
          }
        }

        items(savedDrafts.size) { index ->
          val draft = savedDrafts[index]
          Card(
            colors = CardDefaults.cardColors(
              containerColor = VypioSurfaceVariantDark
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("saved_draft_item_$index")
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text = draft,
                color = VypioTextPrimaryDark,
                fontSize = 13.sp,
                lineHeight = 18.sp
              )
              Spacer(modifier = Modifier.height(8.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
              ) {
                IconButton(
                  onClick = { copyToClipboard(draft) },
                  modifier = Modifier.size(32.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy draft",
                    tint = VypioPrimary,
                    modifier = Modifier.size(16.dp)
                  )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                  onClick = { deleteDraft(draft) },
                  modifier = Modifier.size(32.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete draft",
                    tint = VypioTextSecondaryDark,
                    modifier = Modifier.size(16.dp)
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
