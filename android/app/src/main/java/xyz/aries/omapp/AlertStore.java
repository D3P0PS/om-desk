package xyz.aries.omapp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Alerts and monitor status in SharedPreferences, shared by the plugin (UI thread)
 * and AlertService (its worker thread). Every read-modify-write holds the class lock.
 */
public final class AlertStore {
    private static final String PREFS = "omapp.alerts";
    private static final String K_ALERTS = "alerts";
    private static final String K_INTERVAL = "intervalSec";
    private static final String K_LAST_CHECK = "lastCheck";
    private static final String K_LAST_ERROR = "lastError";

    private AlertStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized List<AlertLogic.Alert> load(Context c) {
        List<AlertLogic.Alert> out = new ArrayList<>();
        String raw = prefs(c).getString(K_ALERTS, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) out.add(fromJson(arr.getJSONObject(i)));
        } catch (JSONException e) {
            // A corrupt store must be visible, not silently emptied.
            throw new IllegalStateException("alert store unreadable: " + e.getMessage(), e);
        }
        return out;
    }

    public static synchronized void save(Context c, List<AlertLogic.Alert> alerts) {
        JSONArray arr = new JSONArray();
        for (AlertLogic.Alert a : alerts) arr.put(toJson(a));
        prefs(c).edit().putString(K_ALERTS, arr.toString()).apply();
    }

    public static synchronized String add(Context c, AlertLogic.Alert a) {
        List<AlertLogic.Alert> all = load(c);
        a.id = UUID.randomUUID().toString();
        all.add(a);
        save(c, all);
        return a.id;
    }

    public static synchronized void remove(Context c, String id) {
        List<AlertLogic.Alert> all = load(c);
        all.removeIf(a -> a.id.equals(id));
        save(c, all);
    }

    public static synchronized void rearm(Context c, String id) {
        List<AlertLogic.Alert> all = load(c);
        for (AlertLogic.Alert a : all) {
            if (a.id.equals(id)) {
                a.triggered = false;
                a.triggeredAt = null;
                a.lastPrice = null; // a cross must see a fresh side first
                if ("pct".equals(a.kind)) a.basePrice = null;
            }
        }
        save(c, all);
    }

    /**
     * Applies prices ("VENUE:SYMBOL" → price) to a fresh read of the store and saves,
     * all under the lock: alerts added or removed while the service was fetching
     * prices are kept. Returns the alerts that fired, with their price.
     */
    public static synchronized List<Object[]> applyPrices(Context c, java.util.Map<String, Double> prices, long now) {
        List<AlertLogic.Alert> all = load(c);
        List<Object[]> fired = new ArrayList<>();
        for (AlertLogic.Alert a : all) {
            if (a.triggered) continue;
            Double p = prices.get(a.venue + ":" + a.symbol);
            if (p == null) continue;
            if (AlertLogic.observe(a, p, now)) fired.add(new Object[] { a, p });
        }
        save(c, all);
        return fired;
    }

    public static synchronized boolean hasActive(Context c) {
        for (AlertLogic.Alert a : load(c)) if (!a.triggered) return true;
        return false;
    }

    public static int intervalSec(Context c) {
        return prefs(c).getInt(K_INTERVAL, 60);
    }

    public static void setIntervalSec(Context c, int s) {
        prefs(c).edit().putInt(K_INTERVAL, Math.max(15, s)).apply();
    }

    public static void markCheck(Context c, long when, String error) {
        prefs(c).edit().putLong(K_LAST_CHECK, when).putString(K_LAST_ERROR, error).apply();
    }

    public static Long lastCheck(Context c) {
        long v = prefs(c).getLong(K_LAST_CHECK, -1);
        return v < 0 ? null : v;
    }

    public static String lastError(Context c) {
        return prefs(c).getString(K_LAST_ERROR, null);
    }

    static JSONObject toJson(AlertLogic.Alert a) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", a.id);
            o.put("venue", a.venue);
            o.put("symbol", a.symbol);
            o.put("kind", a.kind);
            o.put("level", a.level);
            o.put("basePrice", a.basePrice == null ? JSONObject.NULL : a.basePrice);
            o.put("lastPrice", a.lastPrice == null ? JSONObject.NULL : a.lastPrice);
            o.put("triggered", a.triggered);
            o.put("triggeredAt", a.triggeredAt == null ? JSONObject.NULL : a.triggeredAt);
            o.put("note", a.note == null ? "" : a.note);
            return o;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    static AlertLogic.Alert fromJson(JSONObject o) throws JSONException {
        AlertLogic.Alert a = new AlertLogic.Alert();
        a.id = o.getString("id");
        a.venue = o.getString("venue");
        a.symbol = o.getString("symbol");
        a.kind = o.getString("kind");
        a.level = o.getDouble("level");
        a.basePrice = o.isNull("basePrice") ? null : o.getDouble("basePrice");
        a.lastPrice = o.isNull("lastPrice") ? null : o.getDouble("lastPrice");
        a.triggered = o.optBoolean("triggered", false);
        a.triggeredAt = o.isNull("triggeredAt") ? null : o.getLong("triggeredAt");
        a.note = o.optString("note", "");
        return a;
    }
}
