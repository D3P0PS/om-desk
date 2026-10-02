package xyz.aries.omapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Foreground service that checks price alerts every N seconds, also with the app closed.
 * Runs only while at least one alert is armed, and stops itself otherwise.
 * Type specialUse: dataSync is capped at 6 h/day on Android 15, too short for alerts.
 */
public class AlertService extends Service {
    private static final String TAG = "OmAlerts";
    static final String CH_MONITOR = "monitor";
    static final String CH_ALERTS = "alerts";
    private static final int ID_MONITOR = 1;

    private ScheduledExecutorService exec;
    private PowerManager.WakeLock wake;
    private static volatile boolean running = false;
    private volatile int lastStartId = 0;

    public static boolean isRunning() {
        return running;
    }

    /** Starts the monitor if any alert is armed; no-op otherwise. */
    public static void ensure(Context c) {
        if (!AlertStore.hasActive(c)) return;
        ContextCompat.startForegroundService(c, new Intent(c, AlertService.class));
    }

    static void ensureChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT < 26 || nm == null) return;
        NotificationChannel mon = new NotificationChannel(CH_MONITOR, c.getString(R.string.ch_monitor_name), NotificationManager.IMPORTANCE_LOW);
        mon.setDescription(c.getString(R.string.ch_monitor_desc));
        NotificationChannel al = new NotificationChannel(CH_ALERTS, c.getString(R.string.ch_alerts_name), NotificationManager.IMPORTANCE_HIGH);
        al.setDescription(c.getString(R.string.ch_alerts_desc));
        al.enableVibration(true);
        nm.createNotificationChannel(mon);
        nm.createNotificationChannel(al);
    }

    private PendingIntent openApp() {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification monitorNotification(String text) {
        return new NotificationCompat.Builder(this, CH_MONITOR)
                .setSmallIcon(R.drawable.ic_stat_alert)
                .setContentTitle(getString(R.string.monitor_title))
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp())
                .build();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannels(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        lastStartId = startId;
        Notification n = monitorNotification(getString(R.string.monitor_starting));
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_MONITOR, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(ID_MONITOR, n);
        }
        if (exec == null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "omapp:alerts");
            wake.acquire();
            exec = Executors.newSingleThreadScheduledExecutor();
            exec.execute(this::loop);
            running = true;
        }
        return START_STICKY;
    }

    private void loop() {
        try {
            // stopSelfResult is false when a newer start arrived (an alert was just
            // added): then keep looping instead of leaving an idle service behind.
            if (!AlertStore.hasActive(this) && stopSelfResult(lastStartId)) return;
            if (AlertStore.hasActive(this)) check();
        } catch (Throwable t) {
            Log.e(TAG, "check failed", t);
            AlertStore.markCheck(this, System.currentTimeMillis(), String.valueOf(t.getMessage()));
        }
        if (exec != null && !exec.isShutdown()) {
            exec.schedule(this::loop, AlertStore.intervalSec(this), TimeUnit.SECONDS);
        }
    }

    private void check() {
        Map<String, Set<String>> byVenue = new HashMap<>();
        int armed = 0;
        for (AlertLogic.Alert a : AlertStore.load(this)) {
            if (a.triggered) continue;
            armed++;
            byVenue.computeIfAbsent(a.venue, k -> new HashSet<>()).add(a.symbol);
        }
        Map<String, Double> prices = new HashMap<>();
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : byVenue.entrySet()) {
            try {
                Map<String, Double> got = PriceFetcher.fetch(e.getKey(), e.getValue());
                for (String s : e.getValue()) {
                    Double p = got.get(s);
                    if (p == null) errors.add(getString(R.string.no_price, e.getKey() + ":" + s));
                    else prices.put(e.getKey() + ":" + s, p);
                }
            } catch (Exception ex) {
                errors.add(e.getKey() + ": " + ex.getMessage());
            }
        }
        long now = System.currentTimeMillis();
        for (Object[] f : AlertStore.applyPrices(this, prices, now)) {
            notifyFired((AlertLogic.Alert) f[0], (Double) f[1]);
        }
        String err = errors.isEmpty() ? null : String.join("; ", errors);
        AlertStore.markCheck(this, now, err);
        String when = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(now));
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(ID_MONITOR, monitorNotification(getString(err == null ? R.string.monitor_status : R.string.monitor_status_error, armed, when)));
    }

    private void notifyFired(AlertLogic.Alert a, double price) {
        String title = "🔔 " + a.symbol;
        String body = describe(a, price) + (a.note == null || a.note.isEmpty() ? "" : " · " + a.note);
        Notification n = new NotificationCompat.Builder(this, CH_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .build();
        getSystemService(NotificationManager.class).notify(a.id.hashCode(), n);
    }

    private String describe(AlertLogic.Alert a, double price) {
        String now = AlertLogic.fmt(price);
        switch (a.kind) {
            case "above": return getString(R.string.alert_above, a.symbol, AlertLogic.fmt(a.level), now);
            case "below": return getString(R.string.alert_below, a.symbol, AlertLogic.fmt(a.level), now);
            case "cross": return getString(R.string.alert_cross, a.symbol, AlertLogic.fmt(a.level), now);
            default: return getString(R.string.alert_pct, a.symbol, AlertLogic.pctMove(a, price),
                    AlertLogic.fmt(a.basePrice == null ? price : a.basePrice), now);
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        if (exec != null) exec.shutdownNow();
        exec = null;
        if (wake != null && wake.isHeld()) wake.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
