package com.magic.localprojectserver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

class ServerForegroundService : Service() {

    private var server: LocalHttpServer? = null

    companion object {
        const val CHANNEL_ID = "LocalServerChannel"
        const val PORT = 8080
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_PATH = "EXTRA_PATH"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val path = intent.getStringExtra(EXTRA_PATH)
                if (path != null) {
                    startForegroundServer(File(path))
                }
            }
            ACTION_STOP -> {
                stopForegroundServer()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundServer(rootDir: File) {
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Local Server Online")
            .setContentText("Listening on http://localhost:$PORT")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setOngoing(true)
            .build()

        startForeground(101, notification)

        try {
            server?.stop()
            server = LocalHttpServer(PORT, rootDir)
            server?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopForegroundServer() {
        server?.stop()
        server = null
    }

    override fun onDestroy() {
        stopForegroundServer()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Local Server Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
