package xyz.aries.omapp;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(OmWebViewPlugin.class);
        registerPlugin(AlertsPlugin.class);
        super.onCreate(savedInstanceState);
        // Alerts armed before the app was killed: bring the monitor back.
        AlertService.ensure(this);
    }
}
