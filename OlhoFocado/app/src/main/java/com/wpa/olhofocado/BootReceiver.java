package com.wpa.olhofocado;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            OlhoFocadoAdminReceiver.syncAllProtections(context);
            SharedPreferences prefs = context.getSharedPreferences("OlhoFocadoPrefs", Context.MODE_PRIVATE);
            String json = prefs.getString("state_json", null);
            if (json != null) {
                NotificationScheduler.scheduleAllFromJson(context, json);
            }
            HeatmapWidgetProvider.updateAllWidgets(context);
        }
    }
}
