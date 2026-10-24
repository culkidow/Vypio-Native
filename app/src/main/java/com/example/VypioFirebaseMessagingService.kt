package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class VypioFirebaseMessagingService : FirebaseMessagingService() {

  companion object {
    private const val TAG = "VypioFCM"
    const val CHANNEL_ID = "vypio_activity_channel"
    const val CHANNEL_NAME = "Vypio Activity Updates"
    const val CHANNEL_DESC = "Notifications for likes, mentions, comments, messages, and friend activity on Vypio"
    const val PREFS_FCM = "vypio_fcm_prefs"
    const val KEY_FCM_TOKEN = "latest_fcm_token"
    const val EXTRA_TARGET_URL = "target_url"

    fun createNotificationChannel(context: Context) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
          CHANNEL_ID,
          CHANNEL_NAME,
          NotificationManager.IMPORTANCE_HIGH
        ).apply {
          description = CHANNEL_DESC
          enableLights(true)
          lightColor = android.graphics.Color.parseColor("#F124D9")
          enableVibration(true)
          setShowBadge(true)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
      }
    }

    fun getStoredToken(context: Context): String? {
      return context.getSharedPreferences(PREFS_FCM, Context.MODE_PRIVATE)
        .getString(KEY_FCM_TOKEN, null)
    }

    fun storeToken(context: Context, token: String) {
      context.getSharedPreferences(PREFS_FCM, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_FCM_TOKEN, token)
        .apply()
    }
  }

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    Log.d(TAG, "New FCM Token received: $token")
    storeToken(applicationContext, token)
  }

  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    super.onMessageReceived(remoteMessage)
    Log.d(TAG, "From: ${remoteMessage.from}")

    createNotificationChannel(this)

    // Extract title, body, and target URL from payload
    val title = remoteMessage.notification?.title
      ?: remoteMessage.data["title"]
      ?: "Vypio Activity"

    val body = remoteMessage.notification?.body
      ?: remoteMessage.data["body"]
      ?: "New activity on your Vypio feed"

    val targetUrl = remoteMessage.data["url"]
      ?: remoteMessage.data["target_url"]
      ?: remoteMessage.data["link"]
      ?: VYPIO_HOME_URL

    showNotification(title, body, targetUrl)
  }

  private fun showNotification(title: String, body: String, targetUrl: String) {
    val intent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(EXTRA_TARGET_URL, targetUrl)
    }

    val pendingIntent = PendingIntent.getActivity(
      this,
      System.currentTimeMillis().toInt(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val largeIcon = try {
      BitmapFactory.decodeResource(resources, R.drawable.ic_vypio_logo)
    } catch (e: Exception) {
      null
    }

    val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_vypio_logo)
      .apply {
        if (largeIcon != null) {
          setLargeIcon(largeIcon)
        }
      }
      .setContentTitle(title)
      .setContentText(body)
      .setStyle(NotificationCompat.BigTextStyle().bigText(body))
      .setColor(getColor(R.color.vypio_primary))
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setDefaults(NotificationCompat.DEFAULT_ALL)
      .setContentIntent(pendingIntent)

    val notificationManager = NotificationManagerCompat.from(this)
    try {
      notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    } catch (e: SecurityException) {
      Log.e(TAG, "Missing POST_NOTIFICATIONS permission to display notification", e)
    }
  }
}
