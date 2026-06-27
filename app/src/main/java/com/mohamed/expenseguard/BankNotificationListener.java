package com.mohamed.expenseguard;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class BankNotificationListener extends NotificationListenerService {
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        Bundle extras = sbn.getNotification().extras;
        if (extras == null) return;
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE, "");
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT, "");
        CharSequence big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT, "");
        String body = String.valueOf(title) + "\n" + String.valueOf(text) + "\n" + String.valueOf(big);
        MessageParser.ParsedTransaction tx = MessageParser.parseBankMessage(body);
        if (tx != null) {
            tx.source = "notification";
            new ExpenseDbHelper(this).insertParsed(tx);
        }
    }
}
