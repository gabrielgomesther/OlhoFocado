package com.wpa.olhofocado;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;

public class NotificationScheduler {

    public static void scheduleAllFromJson(Context context, String jsonPayload) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            JSONObject root = new JSONObject(jsonPayload);
            JSONArray notifs = root.optJSONArray("notifications");
            JSONObject activeVerse = root.optJSONObject("activeVerse");
            String verseSnippet = activeVerse != null
                    ? activeVerse.optString("ref", "Prov. 27:11") + ": " + activeVerse.optString("text", "")
                    : "Seja sábio, meu filho, e alegre meu coração (Provérbios 27:11).";

            if (notifs == null) return;
            int reqCode = 2000;

            for (int i = 0; i < notifs.length(); i++) {
                JSONObject cat = notifs.getJSONObject(i);
                String catId = cat.optString("id", "");
                String title = "Olho Focado · " + cat.optString("title", "Lembrete");
                String body = cat.optString("desc", "");
                if ("texto_biblico".equals(catId)) {
                    body = verseSnippet;
                }
                JSONArray times = cat.optJSONArray("times");
                if (times == null) continue;

                for (int t = 0; t < times.length(); t++) {
                    String hhmm = times.optString(t, "08:00");
                    String[] parts = hhmm.split(":");
                    if (parts.length != 2) continue;
                    int hour = Integer.parseInt(parts[0]);
                    int minute = Integer.parseInt(parts[1]);

                    Calendar cal = Calendar.getInstance();
                    cal.set(Calendar.HOUR_OF_DAY, hour);
                    cal.set(Calendar.MINUTE, minute);
                    cal.set(Calendar.SECOND, 0);
                    if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                        cal.add(Calendar.DAY_OF_YEAR, 1);
                    }

                    Intent intent = new Intent(context, AlarmReceiver.class);
                    intent.putExtra("title", title);
                    intent.putExtra("message", body);
                    intent.putExtra("notif_id", reqCode);

                    PendingIntent pi = PendingIntent.getBroadcast(
                            context,
                            reqCode,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    );

                    am.setRepeating(
                            AlarmManager.RTC_WAKEUP,
                            cal.getTimeInMillis(),
                            AlarmManager.INTERVAL_DAY,
                            pi
                    );
                    reqCode++;
                }
            }
        } catch (Exception ignored) {}
    }
}
