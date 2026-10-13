package vn.worddeck

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.google.mlkit.nl.translate.*
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray

class Dictionary {
    suspend fun enrich(card: Card): Card =
        withContext(Dispatchers.IO) {
            var result = card
            try {
                val conn =
                    URL(
                            "https://api.dictionaryapi.dev/api/v2/entries/en/" +
                                java.net.URLEncoder.encode(card.word, "UTF-8")
                        )
                        .openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                try {
                    if (conn.responseCode == 200) {
                        val entry =
                            JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                                .getJSONObject(0)
                        val m = entry.optJSONArray("meanings")?.optJSONObject(0)
                        result =
                            card.copy(
                                phonetic = entry.optString("phonetic"),
                                kind = m?.optString("partOfSpeech") ?: "",
                                example =
                                    m?.optJSONArray("definitions")
                                        ?.optJSONObject(0)
                                        ?.optString("example") ?: "",
                            )
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                /* Editable preview remains available. */
            }
            if (result.meaning.isBlank()) {
                val translator =
                    Translation.getClient(
                        TranslatorOptions.Builder()
                            .setSourceLanguage(TranslateLanguage.ENGLISH)
                            .setTargetLanguage(TranslateLanguage.VIETNAMESE)
                            .build()
                    )
                try {
                    translator.downloadModelIfNeeded().await()
                    result = result.copy(meaning = translator.translate(card.word).await())
                } catch (_: Exception) {} finally {
                    translator.close()
                }
            }
            result
        }
}

class ReminderWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        val prefs = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("reminder", false)) return Result.success()
        val manager = c.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel("study", "Nhắc học", NotificationManager.IMPORTANCE_DEFAULT)
        )
        if (
            Build.VERSION.SDK_INT < 33 ||
                c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        ) {
            val intent =
                PendingIntent.getActivity(
                    c,
                    0,
                    Intent(c, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            manager.notify(
                1,
                NotificationCompat.Builder(c, "study")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Đến giờ học WordDeck")
                    .setContentText("Dành vài phút ôn từ và giữ chuỗi ngày học nhé!")
                    .setContentIntent(intent)
                    .setAutoCancel(true)
                    .build(),
            )
        }
        scheduleReminder(c, true)
        return Result.success()
    }
}

fun scheduleReminder(c: Context, fromWorker: Boolean = false) {
    val p = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val wm = WorkManager.getInstance(c)
    if (!p.getBoolean("reminder", false)) {
        wm.cancelUniqueWork("study-reminder")
        return
    }
    val now = ZonedDateTime.now()
    var next = now.withHour(p.getInt("hour", 20)).withMinute(p.getInt("minute", 0)).withSecond(0)
    if (!next.isAfter(now)) next = next.plusDays(1)
    wm.enqueueUniqueWork(
        "study-reminder",
        if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(Duration.between(now, next).seconds, TimeUnit.SECONDS)
            .build(),
    )
}
