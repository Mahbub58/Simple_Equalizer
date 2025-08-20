package com.envobyte.simpleequalizer.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.envobyte.simpleequalizer.MainActivity
import com.envobyte.simpleequalizer.R


class ForegroundService() : Service() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 알림 생성 및 시작
        Log.i("test", "Intent: ${intent.toString()}")
        startForegroundService()
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startForegroundService() {
        Log.i("test", "startForegroundService")

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK and Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notificationChannelId = "Notification Channel ID"
        // 안드로이드 Oreo 이상을 위한 알림 채널 생성
        val channelName = "Simple Equalizer Service"
        val channel = NotificationChannel(
            notificationChannelId,
            channelName,
            NotificationManager.IMPORTANCE_HIGH,
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notificationBuilder = NotificationCompat.Builder(this, notificationChannelId)
        val notification = notificationBuilder.setOngoing(true)
            .setContentTitle("Simple Equalizer")
            .setContentText("Now Running!")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
//        isServiceRunning.postValue(true)
        startForeground(123, notification)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        Log.i("test", "destroy SMSFOREGROUND")
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}
