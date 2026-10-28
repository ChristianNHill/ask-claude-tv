package com.askclaude.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Base64;

import java.nio.charset.StandardCharsets;

/** Saves the Anthropic API key sent over ADB. The manifest limits senders to the ADB shell. */
public class KeyReceiver extends BroadcastReceiver {
    static final String PREFS = "config";
    static final String KEY = "api_key";
    static final String SITES = "sites";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Saved sites arrive base64-encoded so newlines survive the ADB shell.
        String sites = intent.getStringExtra("sites_b64");
        if (sites != null) {
            String decoded = new String(Base64.decode(sites, Base64.DEFAULT), StandardCharsets.UTF_8);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(SITES, decoded.trim()).apply();
            setResultData("sites saved");
            return;
        }
        String key = intent.getStringExtra("key");
        if (key == null || key.trim().isEmpty()) {
            setResultData("missing key extra");
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, key.trim()).apply();
        setResultData("key saved");
    }
}
