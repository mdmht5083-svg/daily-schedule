package com.example.dailyschedule

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.runBlocking
import java.util.Calendar

object ReminderScheduler {
    private const val CHANNEL_ID = "schedule_reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Schedule reminders", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Reminders for your daily schedule" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun schedule(context: Context, task: ScheduleTask) {
        if (!task.enabled || !task.reminderEnabled) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("title", task.title)
            .putExtra("taskId", task.id)
        val pending = PendingIntent.getBroadcast(
            context, task.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, task.startMinute / 60)
            set(Calendar.MINUTE, task.startMinute % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pending)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pending)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pending)
        }
    }

    fun cancel(context: Context, task: ScheduleTask) {
        val pending = PendingIntent.getBroadcast(
            context, task.id.toInt(), Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pending?.let { (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(it) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.ensureChannel(context)
        val title = intent.getStringExtra("title") ?: "পরবর্তী কাজের সময় হয়েছে"
        val notification = NotificationCompat.Builder(context, "schedule_reminders")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Daily Schedule")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(intent.getIntExtra("taskId", 1), notification)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        Thread {
            try {
                val dao = ScheduleDatabase.get(context).dao()
                runBlocking { dao.allTasks().forEach { ReminderScheduler.schedule(context, it) } }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
