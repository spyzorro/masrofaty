package com.mohamed.expenseguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;

public class BankSmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;
        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) return;
        StringBuilder body = new StringBuilder();
        for (SmsMessage msg : messages) if (msg != null) body.append(msg.getMessageBody()).append("\n");
        MessageParser.ParsedTransaction tx = MessageParser.parseBankMessage(body.toString());
        if (tx != null) {
            tx.source = "sms";
            ExpenseDbHelper db = new ExpenseDbHelper(context);
            long id = db.insertParsed(tx);
            if (id > 0 && tx.affectsBudget == 1 && db.isAbnormalExpense(tx.amount)) {
                DebtReminderReceiver.showGenericNotification(context, "مصروف كبير غير معتاد", "اتسجلت عملية " + tx.title + " بمبلغ " + db.money(tx.amount));
            }
        }
    }
}
