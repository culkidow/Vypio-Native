package com.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.VypioBackgroundDark
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {
  private lateinit var networkMonitor: NetworkMonitor
  private var targetNotificationUrl by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Create notification channel for FCM activity updates
    VypioFirebaseMessagingService.createNotificationChannel(applicationContext)

    // Retrieve initial intent URL if launched from push notification
    intent?.getStringExtra(VypioFirebaseMessagingService.EXTRA_TARGET_URL)?.let { url ->
      targetNotificationUrl = url
    }

    try {
      FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
        if (task.isSuccessful) {
          val token = task.result
          Log.d("MainActivity", "FCM Device Token: $token")
          VypioFirebaseMessagingService.storeToken(applicationContext, token)
        }
      }
    } catch (e: Exception) {
      Log.w("MainActivity", "Firebase not yet initialized: ${e.message}")
    }

    networkMonitor = NetworkMonitor(applicationContext)

    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = VypioBackgroundDark
        ) {
          VypioScreen(
            networkMonitor = networkMonitor,
            deepLinkUrl = targetNotificationUrl,
            onDeepLinkHandled = { targetNotificationUrl = null }
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    intent.getStringExtra(VypioFirebaseMessagingService.EXTRA_TARGET_URL)?.let { url ->
      targetNotificationUrl = url
    }
  }
}
