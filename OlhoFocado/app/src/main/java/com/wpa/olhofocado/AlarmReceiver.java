package com.wpa.olhofocado;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "olho_focado_daily_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        String message = intent.getStringExtra("message");
        int notifId = intent.getIntExtra("notif_id", 100);
        if (title == null) title = "Olho Focado — Vigilância e Sabedoria";
        if (message == null) message = "Lembre-se do seu compromisso diário, do texto bíblico e da prática de Surfing.";
        showNotification(context, title, message, notifId);
    }

    public static void showNotification(Context context, String title, String message, int notifId) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Lembretes Diários Olho Focado",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notificações de Compromisso, Texto Bíblico, Surfing e Fechamento Diário");
            nm.createNotificationChannel(channel);
        }

        Intent openIntent = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, notifId, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_ivory)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);

        nm.notify(notifId, builder.build());
    }
}
