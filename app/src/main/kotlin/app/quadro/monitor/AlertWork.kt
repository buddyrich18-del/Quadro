package app.quadro.monitor

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.quadro.core.AlertRule
import app.quadro.core.AlertState
import app.quadro.core.AppConfig
import app.quadro.core.ClimateCard
import app.quadro.core.SeriesRef
import app.quadro.core.evaluateAlert
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

const val ALERT_CHANNEL = "alerts"

class QuadroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        I18n.english = I18n.resolve(ConfigStore.load(this).language)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, tr("Avvisi temperatura e umidità"), NotificationManager.IMPORTANCE_DEFAULT),
        )
        AlertScheduler.sync(this, ConfigStore.load(this))
    }
}

object AlertScheduler {
    private const val NAME = "quadro_alerts"

    /** Controllo ogni 15 minuti (il minimo di WorkManager), solo se qualche avviso è attivo. */
    fun sync(ctx: Context, config: AppConfig) {
        val wm = WorkManager.getInstance(ctx)
        val any = config.cards.any { it is ClimateCard && (it.tempAlert.enabled || it.humAlert.enabled) }
        if (any) {
            val request = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            wm.cancelUniqueWork(NAME)
        }
    }
}

class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val cfg = ConfigStore.load(ctx)
        I18n.english = I18n.resolve(cfg.language)
        val repo = Repo(ctx)
        val prefs = ctx.getSharedPreferences("alert_state", Context.MODE_PRIVATE)
        for (card in cfg.cards.filterIsInstance<ClimateCard>()) {
            checkOne(ctx, repo, prefs, cfg, card, card.temperature, card.tempAlert, "T", "Temperatura", "°C", 1.0)
            checkOne(ctx, repo, prefs, cfg, card, card.humidity, card.humAlert, "H", "Umidità", "%", 2.0)
        }
        return Result.success()
    }

    private suspend fun checkOne(
        ctx: Context,
        repo: Repo,
        prefs: SharedPreferences,
        cfg: AppConfig,
        card: ClimateCard,
        ref: SeriesRef,
        rule: AlertRule,
        quantity: String,
        name: String,
        defaultUnit: String,
        hysteresis: Double,
    ) {
        val key = "${card.id}:$quantity"
        if (!rule.enabled) {
            prefs.edit().remove(key).apply()
            return
        }
        try {
            val value = repo.latest(cfg, ref)
            val previous = try {
                AlertState.valueOf(prefs.getString(key, "OK") ?: "OK")
            } catch (e: IllegalArgumentException) {
                AlertState.OK
            }
            val decision = evaluateAlert(value, rule, previous, hysteresis)
            prefs.edit().putString(key, decision.state.name).apply()
            if (decision.notify) {
                val above = decision.state == AlertState.HIGH
                val limit = (if (above) rule.max else rule.min) ?: return
                val unit = ref.unit.ifBlank { defaultUnit }
                val text = "${tr(name)} ${fmtNum(value, 1)} $unit: ${if (above) tr("sopra") else tr("sotto")} ${pick("il limite", "the limit")} (${fmtNum(limit, 1)})"
                notify(ctx, key.hashCode(), card.title, text)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Server non raggiungibile: si riprova al prossimo controllo.
        }
    }

    private fun notify(ctx: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(ctx, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, notification)
        } catch (e: SecurityException) {
            // Permesso revocato nel frattempo.
        }
    }
}
