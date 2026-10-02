package xyz.aries.omapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONObject;

/** JS bridge for the alert list; AlertService does the checking. */
@CapacitorPlugin(
        name = "Alerts",
        permissions = { @Permission(alias = "notifications", strings = { Manifest.permission.POST_NOTIFICATIONS }) })
public class AlertsPlugin extends Plugin {

    @PluginMethod
    public void list(PluginCall call) {
        JSArray arr = new JSArray();
        for (AlertLogic.Alert a : AlertStore.load(getContext())) arr.put(AlertStore.toJson(a));
        JSObject r = new JSObject();
        r.put("alerts", arr);
        r.put("running", AlertService.isRunning());
        r.put("intervalSec", AlertStore.intervalSec(getContext()));
        Long last = AlertStore.lastCheck(getContext());
        r.put("lastCheck", last == null ? JSONObject.NULL : last);
        String err = AlertStore.lastError(getContext());
        r.put("lastError", err == null ? JSONObject.NULL : err);
        call.resolve(r);
    }

    @PluginMethod
    public void add(PluginCall call) {
        JSObject o = call.getObject("alert");
        if (o == null) {
            call.reject("missing alert");
            return;
        }
        AlertLogic.Alert a = new AlertLogic.Alert();
        a.venue = o.getString("venue");
        a.symbol = o.getString("symbol");
        a.kind = o.getString("kind");
        a.note = o.getString("note", "");
        try {
            a.level = o.getDouble("level");
            a.basePrice = o.isNull("basePrice") ? null : o.getDouble("basePrice");
        } catch (Exception e) {
            call.reject("bad numbers: " + e.getMessage());
            return;
        }
        if (a.venue == null || a.symbol == null || a.kind == null || !(a.level > 0)) {
            call.reject("alert needs venue, symbol, kind and a positive level");
            return;
        }
        String id = AlertStore.add(getContext(), a);
        AlertService.ensure(getContext());
        JSObject r = new JSObject();
        r.put("id", id);
        call.resolve(r);
    }

    @PluginMethod
    public void remove(PluginCall call) {
        AlertStore.remove(getContext(), call.getString("id", ""));
        call.resolve();
    }

    @PluginMethod
    public void rearm(PluginCall call) {
        AlertStore.rearm(getContext(), call.getString("id", ""));
        AlertService.ensure(getContext());
        call.resolve();
    }

    @PluginMethod
    public void setInterval(PluginCall call) {
        AlertStore.setIntervalSec(getContext(), call.getInt("seconds", 60));
        call.resolve();
    }

    @PluginMethod
    public void requestNotifications(PluginCall call) {
        if (Build.VERSION.SDK_INT < 33 || getPermissionState("notifications") == PermissionState.GRANTED) {
            resolveGranted(call, true);
            return;
        }
        requestPermissionForAlias("notifications", call, "notificationsResult");
    }

    @PermissionCallback
    private void notificationsResult(PluginCall call) {
        resolveGranted(call, getPermissionState("notifications") == PermissionState.GRANTED);
    }

    private void resolveGranted(PluginCall call, boolean granted) {
        JSObject r = new JSObject();
        r.put("granted", granted);
        call.resolve(r);
    }

    @SuppressLint("BatteryLife") // sideloaded app, never on the Play Store
    @PluginMethod
    public void requestBatteryExemption(PluginCall call) {
        PowerManager pm = (PowerManager) getContext().getSystemService(android.content.Context.POWER_SERVICE);
        String pkg = getContext().getPackageName();
        if (pm.isIgnoringBatteryOptimizations(pkg)) {
            call.resolve();
            return;
        }
        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + pkg));
        getActivity().startActivity(i);
        call.resolve();
    }
}
