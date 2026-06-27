package com.mohamed.expenseguard;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class DebtReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "debt_reminders";

    @Override public void onReceive(Context context, Intent intent) {
        String name = intent.getStringExtra("name");
        String amount = intent.getStringExtra("amount");
        String direction = intent.getStringExtra("direction");
        String kind = intent.getStringExtra("kind");
        long debtId = intent.getLongExtra("debtId", -1);

        if ("SUBSCRIPTION".equals(direction)) {
            String title;
            if ("SUBSCRIPTION_3D".equals(kind)) title = "اشتراك هيتخصم بعد 3 أيام";
            else if ("SUBSCRIPTION_1D".equals(kind)) title = "اشتراك هيتخصم بكرة";
            else title = "ميعاد خصم اشتراك شهري";
            showNotification(context, title, "اشتراك " + safe(name) + " بمبلغ " + amount);
            return;
        }

        // لو الدين اتسدد قبل ميعاد الإشعار، تجاهل الإشعار القديم.
        try {
            if (debtId > 0) {
                ExpenseDbHelper db = new ExpenseDbHelper(context);
                ExpenseDbHelper.Debt d = db.getDebtById(debtId);
                if (d == null || "PAID".equals(d.status)) return;
            }
        } catch (Exception ignored) {}

        boolean owe = "OWE_TO_OTHERS".equals(direction);
        String title;
        if ("BEFORE_DAY".equals(kind)) title = owe ? "تذكير قبل السداد بيوم" : "تذكير قبل التحصيل بيوم";
        else if ("BEFORE_2H".equals(kind)) title = owe ? "تذكير قبل السداد بساعتين" : "تذكير قبل التحصيل بساعتين";
        else if ("OVERDUE".equals(kind)) title = owe ? "متأخر عليك سداد فلوس" : "متأخر تحصيل فلوس";
        else title = owe ? "ميعاد سداد فلوس" : "ميعاد تحصيل فلوس";

        String body = owe ? "عليك تسدد " + amount + " لـ " + safe(name) : "ليك تستلم " + amount + " من " + safe(name);
        showNotification(context, title, body);
    }

    public static void showGenericNotification(Context context, String title, String body) {
        new DebtReminderReceiver().showNotification(context, title, body);
    }

    private void showNotification(Context context, String title, String body) {
        createChannel(context);
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 9001, open, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);

        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManagerCompat.from(context).notify((int)(System.currentTimeMillis() % 100000), b.build());
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "تذكيرات مصروفاتي", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("إشعارات مواعيد السداد والتحصيل والاشتراكات");
        nm.createNotificationChannel(ch);
    }

    private static String safe(String s) { return s == null || s.trim().isEmpty() ? "شخص" : s; }
}
