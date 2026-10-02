package xyz.aries.omapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts the alert monitor after a reboot or an app update, if any alert is armed. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            AlertService.ensure(context);
        }
    }
}
