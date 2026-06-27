package com.mohamed.expenseguard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Date;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONObject;

import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends Activity {
    private static final int REQ_VOICE = 44;
    private static final int REQ_GOOGLE_SIGN_IN = 77;
    private static final int REQ_DEVICE_LOCK = 88;

    private final int BG = Color.rgb(246, 250, 248);
    private final int DARK = Color.rgb(15, 31, 42);
    private final int MUTED = Color.rgb(108, 121, 130);
    private final int PRIMARY = Color.rgb(15, 166, 122);
    private final int PRIMARY_DARK = Color.rgb(8, 110, 100);
    private final int ORANGE = Color.rgb(239, 145, 38);
    private final int BLUE = Color.rgb(51, 122, 255);
    private final int RED = Color.rgb(229, 75, 75);
    private final int PURPLE = Color.rgb(121, 91, 219);

    private ExpenseDbHelper db;
    private FirebaseSyncManager syncManager;
    private LinearLayout root;
    private boolean appUnlocked = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        db = new ExpenseDbHelper(this);
        syncManager = new FirebaseSyncManager(this, db);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        styleSystemBars();
        requestNeededPermissions();
        showHome();
        maybeShowAppLock();
    }

    private void styleSystemBars() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            w.setStatusBarColor(PRIMARY_DARK);
            w.setNavigationBarColor(BG);
        }
        if (Build.VERSION.SDK_INT >= 23) {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    private void requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            List<String> perms = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.RECEIVE_SMS);
            if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.READ_SMS);
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.RECORD_AUDIO);
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.POST_NOTIFICATIONS);
            if (!perms.isEmpty()) requestPermissions(perms.toArray(new String[0]), 10);
        }
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void setup(String title) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(BG);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        root.setGravity(Gravity.RIGHT);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sv.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        setContentView(sv);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.RIGHT);
        top.setPadding(0, dp(4), 0, dp(10));
        root.addView(top, matchWrap());

        LinearLayout titleRow = row();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView menuIcon = text("☰", 28, true, DARK);
        menuIcon.setGravity(Gravity.CENTER);
        menuIcon.setClickable(true);
        menuIcon.setBackground(strokeBg(Color.WHITE, Color.rgb(224, 235, 231), 16, 1));
        if (Build.VERSION.SDK_INT >= 21) menuIcon.setElevation(dp(1));
        menuIcon.setOnClickListener(v -> openMainMenu());
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(50), dp(50));
        mlp.setMargins(dp(8), 0, 0, 0);
        titleRow.addView(menuIcon, mlp);

        TextView t = text(title, 26, true, DARK);
        t.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        titleRow.addView(t, new LinearLayout.LayoutParams(0, dp(54), 1));
        top.addView(titleRow, matchWrap());

        TextView sub = text("تحكم في ميزانيتك ومصاريفك من مكان واحد", 13, false, MUTED);
        sub.setGravity(Gravity.RIGHT);
        if ("مصروفاتي".equals(title)) top.addView(sub, matchWrap());
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(12));
        return lp;
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView tv = new TextView(this);
        tv.setText(s == null ? "" : s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.RIGHT);
        tv.setTextDirection(View.TEXT_DIRECTION_RTL);
        tv.setIncludeFontPadding(true);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return tv;
    }

    private TextView label(String text, int sp, boolean bold) {
        TextView tv = this.text(text, sp, bold, bold ? DARK : MUTED);
        tv.setPadding(dp(2), dp(4), dp(2), dp(4));
        return tv;
    }

    private GradientDrawable bg(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private GradientDrawable strokeBg(int color, int strokeColor, float radiusDp, int strokeDp) {
        GradientDrawable g = bg(color, radiusDp);
        g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private GradientDrawable gradient(int start, int end, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private LinearLayout card() { return card(Color.WHITE); }

    private LinearLayout card(int color) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(bg(color, 22));
        c.setLayoutParams(cardLp());
        if (Build.VERSION.SDK_INT >= 21) c.setElevation(dp(2));
        return c;
    }

    private Button btn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(48));
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setBackground(gradient(PRIMARY, PRIMARY_DARK, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private Button softBtn(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        b.setTextColor(color);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(46));
        int pale = pale(color);
        b.setBackground(strokeBg(pale, lighten(color), 15, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        lp.setMargins(0, dp(5), 0, dp(5));
        b.setLayoutParams(lp);
        return b;
    }

    private int pale(int color) {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        return Color.rgb((int)(r * 0.12 + 255 * 0.88), (int)(g * 0.12 + 255 * 0.88), (int)(b * 0.12 + 255 * 0.88));
    }

    private int lighten(int color) {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        return Color.rgb((int)(r * 0.28 + 255 * 0.72), (int)(g * 0.28 + 255 * 0.72), (int)(b * 0.28 + 255 * 0.72));
    }

    private TextView pill(String text, int color) {
        TextView p = this.text(text, 12, true, color);
        p.setGravity(Gravity.CENTER);
        p.setPadding(dp(10), dp(4), dp(10), dp(4));
        p.setBackground(bg(pale(color), 50));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(4), 0, dp(4));
        p.setLayoutParams(lp);
        return p;
    }

    private void addHomeButton() {
        Button home = softBtn("← رجوع للرئيسية", PRIMARY_DARK);
        home.setOnClickListener(v -> showHome());
        root.addView(home);
    }

    private void openMainMenu() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("القائمة").setView(box).create();

        Button home = softBtn("الرئيسية", PRIMARY_DARK);
        home.setOnClickListener(v -> { dialog.dismiss(); showHome(); });
        box.addView(home);

        Button budget = softBtn("تعديل الميزانية والمصروف", PRIMARY);
        budget.setOnClickListener(v -> { dialog.dismiss(); budgetAndSpentDialog(); });
        box.addView(budget);

        Button pendingBtn = softBtn("مراجعة الأونلاين والوارد", ORANGE);
        pendingBtn.setOnClickListener(v -> { dialog.dismiss(); showPending(); });
        box.addView(pendingBtn);

        Button debtsBtn = softBtn("الديون والمواعيد", PURPLE);
        debtsBtn.setOnClickListener(v -> { dialog.dismiss(); showDebts(); });
        box.addView(debtsBtn);

        Button subsBtn = softBtn("الاشتراكات الشهرية", BLUE);
        subsBtn.setOnClickListener(v -> { dialog.dismiss(); showSubscriptions(); });
        box.addView(subsBtn);

        Button reminderSettings = softBtn("إعدادات التذكير والقفل", ORANGE);
        reminderSettings.setOnClickListener(v -> { dialog.dismiss(); showSecurityAndReminderSettings(); });
        box.addView(reminderSettings);

        Button currencyBtn = softBtn("اختيار العملة", PRIMARY_DARK);
        currencyBtn.setOnClickListener(v -> { dialog.dismiss(); currencyDialog(); });
        box.addView(currencyBtn);

        Button logBtn = softBtn("سجل العمليات", BLUE);
        logBtn.setOnClickListener(v -> { dialog.dismiss(); showLog(); });
        box.addView(logBtn);

        Button syncBtn = softBtn("مزامنة جوجل وحفظ الداتا", PRIMARY);
        syncBtn.setOnClickListener(v -> { dialog.dismiss(); showGoogleSyncCenter(); });
        box.addView(syncBtn);

        Button updateBtn = softBtn("مركز التحديثات", PRIMARY);
        updateBtn.setOnClickListener(v -> { dialog.dismiss(); showUpdateCenter(); });
        box.addView(updateBtn);

        Button test = softBtn("اختبار / إدخال رسالة بنك يدويًا", MUTED);
        test.setOnClickListener(v -> { dialog.dismiss(); testMessageDialog(); });
        box.addView(test);

        Button notif = softBtn("تفعيل قراءة إشعارات البنك", PRIMARY_DARK);
        notif.setOnClickListener(v -> { dialog.dismiss(); startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); });
        box.addView(notif);

        dialog.show();
    }



    private void showGoogleSyncCenter() {
        setup("مزامنة جوجل");
        addHomeButton();

        LinearLayout info = card();
        info.addView(text("حفظ الداتا بحساب Google", 20, true, DARK), matchWrap());
        info.addView(text("الميزة دي بتربط كل مستخدم بإيميله، وترفع نسخة احتياطية من ميزانيته ومصاريفه ومديونياته على Firebase/Firestore. بيانات كل مستخدم تبقى في مساره الخاص ومش تختلط مع أي حد.", 13, false, MUTED), matchWrap());
        FirebaseUser user = syncManager.currentUser();
        String status = user == null ? "غير مسجل دخول" : "مسجل: " + user.getEmail();
        info.addView(pill("الحالة: " + status, user == null ? ORANGE : PRIMARY), matchWrap());
        root.addView(info);

        LinearLayout config = card(pale(PRIMARY));
        config.setBackground(strokeBg(pale(PRIMARY), lighten(PRIMARY), 22, 1));
        config.addView(text("إعداد Firebase مرة واحدة", 18, true, DARK), matchWrap());
        config.addView(text("من Firebase Console هتجيب القيم دي وتحطها هنا. بعد كده الناس تقدر تسجل بإيميل Google وتعمل حفظ/استرجاع للداتا.", 12, false, MUTED), matchWrap());

        EditText apiKey = field("Firebase API Key", db.getSetting("firebase_api_key", ""));
        EditText appId = field("Firebase App ID", db.getSetting("firebase_app_id", ""));
        EditText projectId = field("Firebase Project ID", db.getSetting("firebase_project_id", ""));
        EditText webClient = field("Google Web Client ID", db.getSetting("google_web_client_id", ""));
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT);
        appId.setInputType(InputType.TYPE_CLASS_TEXT);
        projectId.setInputType(InputType.TYPE_CLASS_TEXT);
        webClient.setInputType(InputType.TYPE_CLASS_TEXT);
        config.addView(apiKey); config.addView(appId); config.addView(projectId); config.addView(webClient);

        Button saveCfg = btn("حفظ إعدادات جوجل");
        saveCfg.setOnClickListener(v -> {
            syncManager.saveConfig(apiKey.getText().toString(), appId.getText().toString(), projectId.getText().toString(), webClient.getText().toString());
            toast("تم حفظ إعدادات Firebase");
        });
        config.addView(saveCfg);
        root.addView(config);

        LinearLayout actions = card();
        actions.addView(text("المزامنة", 18, true, DARK), matchWrap());
        actions.addView(text("ارفع نسخة احتياطية بعد أي تعديل مهم، واسترجعها لو غيرت الموبايل أو التطبيق اتمسح. Firestore بيدعم كاش ومزامنة محلية على أندرويد، بس هنا خليناها أزرار واضحة علشان مايحصلش تعارض في حسابات الناس.", 12, false, MUTED), matchWrap());

        Button login = btn(user == null ? "تسجيل الدخول بإيميل Google" : "تغيير حساب Google");
        login.setOnClickListener(v -> {
            try { startActivityForResult(syncManager.signInIntent(), REQ_GOOGLE_SIGN_IN); }
            catch (Exception e) { new AlertDialog.Builder(this).setTitle("ناقص إعداد").setMessage(e.getMessage()).setPositiveButton("تمام", null).show(); }
        });
        actions.addView(login);

        Button upload = softBtn("رفع نسخة احتياطية الآن", BLUE);
        upload.setOnClickListener(v -> syncManager.uploadBackup(new FirebaseSyncManager.Callback() {
            @Override public void ok(String message) { runOnUiThread(() -> toast(message)); }
            @Override public void fail(String message) { runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this).setTitle("فشل الرفع").setMessage(message).setPositiveButton("تمام", null).show()); }
        }));
        actions.addView(upload);

        Button restore = softBtn("استرجاع الداتا من جوجل", ORANGE);
        restore.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("استرجاع الداتا")
                .setMessage("ده هيستبدل الداتا الموجودة على الجهاز بالنسخة السحابية. تكمل؟")
                .setPositiveButton("استرجاع", (d, w) -> syncManager.restoreBackup(new FirebaseSyncManager.Callback() {
                    @Override public void ok(String message) { runOnUiThread(() -> { toast(message); showHome(); }); }
                    @Override public void fail(String message) { runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this).setTitle("فشل الاسترجاع").setMessage(message).setPositiveButton("تمام", null).show()); }
                }))
                .setNegativeButton("إلغاء", null).show());
        actions.addView(restore);

        Button logout = softBtn("تسجيل خروج", MUTED);
        logout.setOnClickListener(v -> syncManager.signOut(this, new FirebaseSyncManager.Callback() {
            @Override public void ok(String message) { runOnUiThread(() -> { toast(message); showGoogleSyncCenter(); }); }
            @Override public void fail(String message) { runOnUiThread(() -> toast(message)); }
        }));
        actions.addView(logout);
        root.addView(actions);

        LinearLayout help = card();
        help.addView(text("إعداد التحديثات للناس", 18, true, DARK), matchWrap());
        help.addView(text("تحديث التطبيق نفسه هيتم من مركز التحديثات: ترفع APK جديد وتحدث ملف update.json. المستخدم هيدوس فحص التحديث أو التطبيق يقدر يفحص عند الفتح في نسخة جاية.", 12, false, MUTED), matchWrap());
        root.addView(help);
    }

    private void showUpdateCenter() {
        setup("مركز التحديثات");
        addHomeButton();

        LinearLayout info = card();
        info.addView(text("تحديث التطبيق عند الناس", 20, true, DARK), matchWrap());
        info.addView(text("طالما التطبيق APK خارجي ومش على Google Play، التحديث مش هينزل تلقائي من المتجر. هنا بنخلي التطبيق يفحص ملف update.json من رابط ثابت، ولو لقى نسخة أحدث يفتح للمستخدم تحميل الـ APK الجديد.", 13, false, MUTED), matchWrap());
        info.addView(text("الإصدار الحالي: " + BuildConfig.VERSION_NAME + "  رقم " + BuildConfig.VERSION_CODE, 14, true, PRIMARY_DARK), matchWrap());
        root.addView(info);

        LinearLayout box = card(pale(PRIMARY));
        box.setBackground(strokeBg(pale(PRIMARY), lighten(PRIMARY), 22, 1));
        box.addView(text("رابط ملف التحديث update.json", 16, true, DARK), matchWrap());
        box.addView(text("حطه على GitHub Raw أو أي استضافة ثابتة. لما تغير الرابط ده عندك مرة، أي تحديث بعد كده يبقى من نفس الملف.", 12, false, MUTED), matchWrap());
        EditText url = field("مثال: https://raw.githubusercontent.com/user/repo/main/update.json", db.getSetting("update_json_url", ""));
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(url);

        Button save = btn("حفظ رابط التحديث");
        save.setOnClickListener(v -> {
            db.setSetting("update_json_url", url.getText().toString().trim());
            toast("تم حفظ رابط التحديث");
        });
        box.addView(save);

        Button check = softBtn("فحص التحديث الآن", BLUE);
        check.setOnClickListener(v -> {
            db.setSetting("update_json_url", url.getText().toString().trim());
            checkForUpdates(url.getText().toString().trim());
        });
        box.addView(check);
        root.addView(box);

        LinearLayout data = card();
        data.addView(text("تحديث الداتا والقواعد", 18, true, DARK), matchWrap());
        data.addView(text("آخر نسخة داتا محفوظة: " + db.getSetting("remote_data_version", "غير محدد"), 13, false, MUTED), matchWrap());
        String lastMsg = db.getSetting("remote_update_message", "");
        if (lastMsg.length() > 0) data.addView(text("آخر رسالة تحديث: " + lastMsg, 13, false, MUTED), matchWrap());
        data.addView(text("الداتا الشخصية لكل مستخدم مثل الميزانية والمصاريف والديون تفضل على جهازه ولا تتغير من عندك. اللي يتحدث من عندك هو نسخة التطبيق أو قواعد عامة زي كلمات البنوك وأنواع العمليات.", 12, false, MUTED), matchWrap());
        root.addView(data);

        LinearLayout sample = card();
        sample.addView(text("شكل ملف update.json", 18, true, DARK), matchWrap());
        sample.addView(text("ارفع ملف بالشكل ده وخلي apkUrl هو رابط تحميل آخر APK:", 12, false, MUTED), matchWrap());
        TextView code = text("{\n  \"latestVersionCode\": 4,\n  \"latestVersionName\": \"1.3\",\n  \"apkUrl\": \"https://github.com/USER/REPO/releases/download/v1.3/app-debug.apk\",\n  \"notes\": \"تحسين الواجهة وإصلاحات\",\n  \"dataVersion\": \"2026-06-27\",\n  \"message\": \"تم تحديث قواعد قراءة رسائل البنك\"\n}", 12, false, DARK);
        code.setTextDirection(View.TEXT_DIRECTION_LTR);
        code.setGravity(Gravity.LEFT);
        code.setPadding(dp(12), dp(12), dp(12), dp(12));
        code.setBackground(strokeBg(Color.rgb(248, 251, 250), Color.rgb(224, 235, 231), 14, 1));
        sample.addView(code, matchWrap());
        root.addView(sample);
    }

    private void checkForUpdates(String updateUrl) {
        if (updateUrl == null || updateUrl.trim().isEmpty()) {
            toast("حط رابط update.json الأول");
            return;
        }
        toast("جاري فحص التحديث...");
        new Thread(() -> {
            try {
                String jsonText = downloadText(updateUrl);
                JSONObject obj = new JSONObject(jsonText);
                int latestCode = obj.optInt("latestVersionCode", obj.optInt("versionCode", BuildConfig.VERSION_CODE));
                String latestName = obj.optString("latestVersionName", obj.optString("versionName", ""));
                String apkUrl = obj.optString("apkUrl", "");
                String notes = obj.optString("notes", "");
                String dataVersion = obj.optString("dataVersion", "");
                String msg = obj.optString("message", "");
                if (dataVersion.length() > 0) db.setSetting("remote_data_version", dataVersion);
                if (msg.length() > 0) db.setSetting("remote_update_message", msg);
                db.setSetting("last_update_json", jsonText);
                runOnUiThread(() -> showUpdateResult(latestCode, latestName, apkUrl, notes, dataVersion, msg));
            } catch (Exception e) {
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("فشل فحص التحديث")
                        .setMessage("اتأكد إن رابط update.json صحيح ومتاح للعامة.\n\n" + e.getMessage())
                        .setPositiveButton("تمام", null).show());
            }
        }).start();
    }

    private String downloadText(String urlText) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(urlText).openConnection();
        con.setConnectTimeout(12000);
        con.setReadTimeout(12000);
        con.setRequestMethod("GET");
        con.setRequestProperty("Accept", "application/json,text/plain,*/*");
        int code = con.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    private void showUpdateResult(int latestCode, String latestName, String apkUrl, String notes, String dataVersion, String msg) {
        boolean hasAppUpdate = latestCode > BuildConfig.VERSION_CODE && apkUrl != null && apkUrl.trim().length() > 0;
        StringBuilder m = new StringBuilder();
        m.append("الإصدار الحالي: ").append(BuildConfig.VERSION_NAME).append(" رقم ").append(BuildConfig.VERSION_CODE).append("\n");
        m.append("آخر إصدار: ").append(latestName.length() == 0 ? String.valueOf(latestCode) : latestName).append(" رقم ").append(latestCode).append("\n\n");
        if (notes != null && notes.length() > 0) m.append("ملاحظات التحديث:\n").append(notes).append("\n\n");
        if (dataVersion != null && dataVersion.length() > 0) m.append("نسخة الداتا: ").append(dataVersion).append("\n");
        if (msg != null && msg.length() > 0) m.append(msg).append("\n");
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(hasAppUpdate ? "في تحديث جديد" : "التطبيق محدث")
                .setMessage(m.toString())
                .setNegativeButton("إغلاق", null);
        if (hasAppUpdate) {
            b.setPositiveButton("تحميل التحديث", (d, w) -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl))); }
                catch (Exception e) { toast("مش قادر أفتح رابط التحميل"); }
            });
        }
        b.show();
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return r;
    }

    private void addWeighted(LinearLayout row, View child, float weight, int margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        lp.setMargins(dp(margin), dp(4), dp(margin), dp(4));
        row.addView(child, lp);
    }

    private LinearLayout statCard(String icon, String title, String value, int color) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(12), dp(12), dp(12));
        c.setGravity(Gravity.RIGHT);
        c.setBackground(bg(Color.WHITE, 20));
        if (Build.VERSION.SDK_INT >= 21) c.setElevation(dp(1));
        LinearLayout top = row();
        TextView ic = text(icon, 18, true, color);
        top.addView(ic, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        Space sp = new Space(this); top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1));
        TextView tt = text(title, 12, false, MUTED); top.addView(tt);
        c.addView(top, matchWrap());
        c.addView(text(value, 17, true, DARK), matchWrap());
        return c;
    }

    private LinearLayout actionCard(String icon, String title, String subtitle, int color, View.OnClickListener click) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(12), dp(12), dp(12));
        c.setGravity(Gravity.RIGHT);
        c.setClickable(true);
        c.setBackground(strokeBg(Color.WHITE, lighten(color), 20, 1));
        if (Build.VERSION.SDK_INT >= 21) c.setElevation(dp(1));
        c.setOnClickListener(click);
        TextView ic = text(icon, 23, true, color); ic.setGravity(Gravity.RIGHT); c.addView(ic, matchWrap());
        c.addView(text(title, 14, true, DARK), matchWrap());
        c.addView(text(subtitle, 11, false, MUTED), matchWrap());
        return c;
    }

    private String statusArabic(String status) {
        if ("CONFIRMED".equals(status)) return "تم الخصم";
        if ("PENDING_ONLINE".equals(status)) return "أونلاين للمراجعة";
        if ("PENDING_INCOMING".equals(status)) return "وارد للمراجعة";
        if ("SAVED_ONLY".equals(status)) return "حفظ فقط";
        return status;
    }

    private int statusColor(String status) {
        if ("CONFIRMED".equals(status)) return PRIMARY;
        if ("PENDING_ONLINE".equals(status)) return ORANGE;
        if ("PENDING_INCOMING".equals(status)) return BLUE;
        if ("SAVED_ONLY".equals(status)) return MUTED;
        return PURPLE;
    }

    private String debtStatusArabic(String status) {
        if ("PAID".equals(status)) return "تم السداد";
        if ("PARTIAL".equals(status)) return "سداد جزئي";
        return "لم يسدد";
    }

    private int debtStatusColor(String status) {
        if ("PAID".equals(status)) return PRIMARY;
        if ("PARTIAL".equals(status)) return ORANGE;
        return RED;
    }

    private void showHome() {
        setup("مصروفاتي");
        double budget = db.getBudget();
        double spent = db.getMonthlySpent();
        double remaining = budget - spent;
        double extraIncome = db.getExtraIncome();
        int pending = db.getPendingCount();
        double pendingTotal = db.getPendingTotal();
        double owedToMe = db.getDebtTotal("OWED_TO_ME");
        double oweToOthers = db.getDebtTotal("OWE_TO_OTHERS");
        int upcomingDues = db.getUpcomingDebtCount(System.currentTimeMillis() + 3L * 24L * 60L * 60L * 1000L);
        double subscriptionsTotal = db.getActiveSubscriptionsTotal();
        double today = db.getTodaySpent();
        int count = db.getMonthlyExpenseCount();
        double progress = budget <= 0 ? 0 : spent / budget;

        LinearLayout hero = card();
        hero.setBackground(gradient(PRIMARY, PRIMARY_DARK, 26));
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.setClickable(true);
        hero.setOnClickListener(v -> budgetAndSpentDialog());
        hero.addView(text("المتبقي من ميزانية الشهر", 14, false, Color.rgb(222, 255, 246)), matchWrap());
        TextView rem = text(db.money(remaining), 32, true, Color.WHITE);
        rem.setGravity(Gravity.RIGHT);
        hero.addView(rem, matchWrap());
        TextView spendLine = text("صرفت " + db.money(spent) + " من " + db.money(budget), 13, false, Color.rgb(220, 250, 242));
        hero.addView(spendLine, matchWrap());
        BudgetProgressView pv = new BudgetProgressView(this, progress, Color.argb(75, 255, 255, 255), Color.WHITE);
        LinearLayout.LayoutParams pvlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10));
        pvlp.setMargins(0, dp(12), 0, dp(4));
        hero.addView(pv, pvlp);
        TextView hint = text(budget <= 0 ? "حدد ميزانية الشهر عشان يبدأ الحساب" : "استهلاك الميزانية: " + Math.round(Math.min(progress, 1) * 100) + "%", 12, false, Color.rgb(220, 250, 242));
        hero.addView(hint, matchWrap());
        TextView editHint = text("اضغط هنا لتعديل الميزانية أو إجمالي المصروف لو الفويس سجل مبلغ غلط", 12, true, Color.WHITE);
        editHint.setPadding(0, dp(8), 0, 0);
        hero.addView(editHint, matchWrap());
        root.addView(hero);

        LinearLayout stats1 = row();
        addWeighted(stats1, statCard("📌", "عمليات للمراجعة", String.valueOf(pending), ORANGE), 1, 4);
        addWeighted(stats1, statCard("💳", "مصروف اليوم", db.money(today), BLUE), 1, 4);
        root.addView(stats1, matchWrap());

        LinearLayout stats2 = row();
        addWeighted(stats2, statCard("💰", "دخل إضافي منفصل", db.money(extraIncome), PRIMARY), 1, 4);
        addWeighted(stats2, statCard("🤝", "ليك عند الناس", db.money(owedToMe), PURPLE), 1, 4);
        root.addView(stats2, matchWrap());

        LinearLayout stats3 = row();
        addWeighted(stats3, statCard("📤", "عليك للناس", db.money(oweToOthers), RED), 1, 4);
        addWeighted(stats3, statCard("⏰", "مواعيد قريبة", String.valueOf(upcomingDues), ORANGE), 1, 4);
        root.addView(stats3, matchWrap());

        LinearLayout stats4 = row();
        addWeighted(stats4, statCard("🔁", "اشتراكات شهرية", db.money(subscriptionsTotal), BLUE), 1, 4);
        addWeighted(stats4, statCard("🔒", "قفل التطبيق", db.getSetting("app_lock_enabled", "0").equals("1") ? "مفعل" : "اختياري", PRIMARY_DARK), 1, 4);
        root.addView(stats4, matchWrap());

        LinearLayout actions = card();
        actions.addView(text("إجراءات سريعة", 18, true, DARK), matchWrap());
        actions.addView(text("ضيف مصروف، زود الميزانية، أو راجع عمليات البنك بسرعة", 12, false, MUTED), matchWrap());
        LinearLayout ar1 = row();
        addWeighted(ar1, actionCard("✍️", "إضافة كتابة", "مصروف أو دخل أو تعديل", BLUE, v -> manualDialog()), 1, 4);
        addWeighted(ar1, actionCard("🎙️", "إضافة بالفويس", "قول: صرفت 20 " + db.currencySymbol(), PRIMARY, v -> startVoice()), 1, 4);
        actions.addView(ar1, matchWrap());
        LinearLayout ar2 = row();
        addWeighted(ar2, actionCard("➕", "زود الميزانية", "إضافة مبلغ للشهر", PRIMARY_DARK, v -> budgetDialog("زود الميزانية بمبلغ", 1)), 1, 4);
        addWeighted(ar2, actionCard("➖", "انقص الميزانية", "تقليل ميزانية الشهر", RED, v -> budgetDialog("انقص الميزانية بمبلغ", -1)), 1, 4);
        actions.addView(ar2, matchWrap());
        LinearLayout ar3 = row();
        addWeighted(ar3, actionCard("🔁", "اشتراك شهري", "Google / Netflix وغيره", BLUE, v -> addSubscriptionDialog()), 1, 4);
        addWeighted(ar3, actionCard("🔐", "قفل التطبيق", "PIN أو قفل الجهاز", PRIMARY_DARK, v -> showSecurityAndReminderSettings()), 1, 4);
        actions.addView(ar3, matchWrap());
        root.addView(actions);

        if (pending > 0) {
            LinearLayout pendingCard = card(pale(ORANGE));
            pendingCard.setBackground(strokeBg(pale(ORANGE), lighten(ORANGE), 22, 1));
            pendingCard.addView(text("عندك " + pending + " عملية محتاجة مراجعة", 18, true, ORANGE), matchWrap());
            pendingCard.addView(text("إجمالي تقريبي: " + db.money(pendingTotal) + " — الأونلاين والوارد مش بيتخصموا غير بموافقتك", 12, false, DARK), matchWrap());
            Button review = btn("راجع العمليات الآن"); review.setOnClickListener(v -> showPending()); pendingCard.addView(review);
            root.addView(pendingCard);
        }

        LinearLayout chart = card();
        chart.addView(text("توزيع مصاريف الشهر", 18, true, DARK), matchWrap());
        chart.addView(text(count == 0 ? "لسه مفيش مصاريف مؤكدة الشهر ده" : count + " عملية مؤكدة خلال الشهر", 12, false, MUTED), matchWrap());
        CategoryChartView cv = new CategoryChartView(this, db.getSpendingByCategory(5));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150));
        clp.setMargins(0, dp(10), 0, dp(4));
        chart.addView(cv, clp);
        root.addView(chart);

        LinearLayout menuHint = card(Color.WHITE);
        menuHint.setBackground(strokeBg(Color.WHITE, Color.rgb(224, 235, 231), 20, 1));
        menuHint.addView(text("القائمة اتنقلت فوق", 16, true, DARK), matchWrap());
        menuHint.addView(text("اضغط على زر ☰ فوق عشان تفتح الأقسام بدل الأيقونات اللي كانت تحت.", 12, false, MUTED), matchWrap());
        root.addView(menuHint);

        root.addView(text("شراء PoS والحوالة الصادرة يتخصموا تلقائيًا. شراء الإنترنت والوارد يدخلوا مراجعة فقط.", 12, false, MUTED));
    }

    private void currencyDialog() {
        String[] items = new String[]{"ريال سعودي", "جنيه مصري"};
        int checked = "EGP".equals(db.getCurrency()) ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle("اختيار العملة")
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    db.setCurrency(which == 1 ? "EGP" : "SAR");
                    dialog.dismiss();
                    toast("تم تغيير العملة إلى " + db.currencyName());
                    showHome();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void budgetAndSpentDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(5), dp(20), dp(5));

        EditText budgetField = field("الميزانية الشهرية", String.valueOf(db.getBudget()));
        budgetField.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText spentField = field("إجمالي اللي اتصرف الشهر ده", String.valueOf(db.getMonthlySpent()));
        spentField.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextView note = text("استخدم الخانة دي لو الفويس أو رسالة بنك سجلت مبلغ غلط. التطبيق هيعمل عملية تصحيح عشان إجمالي المصروف يبقى الرقم اللي كتبته.", 12, false, MUTED);
        note.setPadding(0, dp(8), 0, dp(8));

        box.addView(label("الميزانية الشهرية", 13, true));
        box.addView(budgetField);
        box.addView(label("إجمالي المصروف الحالي", 13, true));
        box.addView(spentField);
        box.addView(note);

        new AlertDialog.Builder(this)
                .setTitle("تعديل الميزانية والمصروف")
                .setView(box)
                .setPositiveButton("حفظ", (d, w) -> {
                    double newBudget = parseAmount(budgetField.getText().toString());
                    double newSpent = parseAmount(spentField.getText().toString());
                    db.setBudget(newBudget);
                    db.adjustMonthlySpentTo(newSpent);
                    toast("تم التعديل");
                    showHome();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void budgetDialog(String title, int mode) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("اكتب المبلغ بـ " + db.currencyName());
        input.setGravity(Gravity.RIGHT);
        input.setTextDirection(View.TEXT_DIRECTION_RTL);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("حفظ", (d, w) -> {
                    double amount = parseAmount(input.getText().toString());
                    if (amount <= 0) return;
                    if (mode == 0) db.setBudget(amount);
                    else if (mode == 1) db.addToBudget(amount);
                    else db.addToBudget(-amount);
                    String raw = title + " " + amount;
                    db.insertManual(mode == -1 ? "BUDGET_DECREASE" : mode == 1 ? "BUDGET_INCREASE" : "BUDGET_SET", "CONFIRMED", amount, title, 0, raw);
                    showHome();
                }).setNegativeButton("إلغاء", null).show();
    }

    private void manualDialog() {
        final EditText input = new EditText(this);
        input.setMinLines(3);
        input.setGravity(Gravity.RIGHT);
        input.setTextDirection(View.TEXT_DIRECTION_RTL);
        input.setHint("مثال: صرفت 25 " + db.currencySymbol() + " قهوة\nأو: زود الميزانية 500\nأو: دخل 200 سداد شغل");
        new AlertDialog.Builder(this)
                .setTitle("إضافة كتابة")
                .setView(input)
                .setPositiveButton("إضافة", (d, w) -> handleManualInput(input.getText().toString()))
                .setNegativeButton("إلغاء", null).show();
    }

    private void handleManualInput(String text) {
        MessageParser.ParsedTransaction tx = MessageParser.parseManualText(text, db.getBudget());
        if (tx == null) { toast("مش قادر أفهم المبلغ"); return; }
        if ("BUDGET_INCREASE".equals(tx.type)) db.addToBudget(tx.amount);
        else if ("BUDGET_DECREASE".equals(tx.type)) db.addToBudget(-tx.amount);
        db.insertParsed(tx);
        toast("تم التسجيل");
        showHome();
    }

    private void startVoice() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "قول العملية: صرفت 20 " + db.currencySymbol() + " قهوة");
        try { startActivityForResult(i, REQ_VOICE); }
        catch (Exception e) { toast("محتاج تطبيق يدعم تحويل الصوت لنص على الجهاز"); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VOICE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> res = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (res != null && !res.isEmpty()) handleManualInput(res.get(0));
        } else if (requestCode == REQ_DEVICE_LOCK) {
            if (resultCode == RESULT_OK) { appUnlocked = true; toast("تم فتح القفل"); }
            else maybeShowAppLock();
        } else if (requestCode == REQ_GOOGLE_SIGN_IN && data != null) {
            syncManager.handleSignInResult(data, new FirebaseSyncManager.Callback() {
                @Override public void ok(String message) { runOnUiThread(() -> { toast(message); showGoogleSyncCenter(); }); }
                @Override public void fail(String message) { runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this).setTitle("مشكلة تسجيل الدخول").setMessage(message).setPositiveButton("تمام", null).show()); }
            });
        }
    }

    private void testMessageDialog() {
        final EditText input = new EditText(this);
        input.setMinLines(8);
        input.setGravity(Gravity.RIGHT);
        input.setTextDirection(View.TEXT_DIRECTION_RTL);
        input.setHint("الصق رسالة البنك هنا للاختبار");
        new AlertDialog.Builder(this)
                .setTitle("اختبار رسالة بنك")
                .setView(input)
                .setPositiveButton("تحليل وحفظ", (d, w) -> {
                    MessageParser.ParsedTransaction tx = MessageParser.parseBankMessage(input.getText().toString());
                    if (tx == null) { toast("الرسالة غير معروفة أو لا يوجد مبلغ"); return; }
                    long id = db.insertParsed(tx);
                    if (id == -1) toast("العملية مكررة وتم تجاهلها"); else toast("تم حفظ: " + tx.title);
                    showHome();
                })
                .setNegativeButton("إلغاء", null).show();
    }

    private void showPending() {
        setup("مراجعة العمليات"); addHomeButton();
        LinearLayout intro = card(pale(ORANGE));
        intro.setBackground(strokeBg(pale(ORANGE), lighten(ORANGE), 22, 1));
        intro.addView(text("الأونلاين والوارد مش بيتحسبوا غير بعد موافقتك", 16, true, ORANGE), matchWrap());
        intro.addView(text("اختار خصم، حفظ فقط، دخل إضافي، أو سداد دين حسب كل عملية.", 12, false, DARK), matchWrap());
        root.addView(intro);

        List<ExpenseDbHelper.Tx> list = db.getPending();
        if (list.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("✅ مفيش عمليات معلقة حاليًا", 18, true, PRIMARY), matchWrap());
            empty.addView(text("أي شراء أونلاين أو حوالة واردة هتظهر هنا للمراجعة.", 13, false, MUTED), matchWrap());
            root.addView(empty);
        }
        for (ExpenseDbHelper.Tx tx : list) {
            LinearLayout c = card();
            LinearLayout top = row();
            top.addView(pill(statusArabic(tx.status), statusColor(tx.status)));
            Space sp = new Space(this); top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1));
            top.addView(text(db.money(tx.amount), 18, true, DARK));
            c.addView(top, matchWrap());
            c.addView(text(tx.title, 20, true, DARK), matchWrap());
            c.addView(text("التاريخ: " + ExpenseDbHelper.date(tx.dateMillis), 13, false, MUTED), matchWrap());
            if (tx.card != null && tx.card.length() > 0) c.addView(text("البطاقة / الحساب: " + tx.card, 13, false, MUTED), matchWrap());
            if (tx.extra != null && tx.extra.length() > 0) c.addView(text(tx.extra, 13, false, MUTED), matchWrap());
            if ("PENDING_ONLINE".equals(tx.status)) {
                Button approve = btn("خصم من الميزانية"); approve.setOnClickListener(v -> { db.approveOnline(tx.id); toast("تم الخصم"); showPending(); }); c.addView(approve);
                Button edit = softBtn("تعديل المبلغ ثم خصم", BLUE); edit.setOnClickListener(v -> editAmountThenApprove(tx)); c.addView(edit);
                Button save = softBtn("تجاهل / حفظ فقط", MUTED); save.setOnClickListener(v -> { db.saveOnly(tx.id); showPending(); }); c.addView(save);
            } else if ("PENDING_INCOMING".equals(tx.status)) {
                Button income = btn("تسجيل كدخل إضافي منفصل"); income.setOnClickListener(v -> { db.markExtraIncome(tx.id); toast("اتسجل دخل إضافي"); showPending(); }); c.addView(income);
                Button debt = softBtn("تسجيل كسداد دين", PURPLE); debt.setOnClickListener(v -> chooseDebtForPayment(tx)); c.addView(debt);
                Button save = softBtn("تجاهل / حفظ فقط", MUTED); save.setOnClickListener(v -> { db.saveOnly(tx.id); showPending(); }); c.addView(save);
            }
            root.addView(c);
        }
    }

    private void editAmountThenApprove(ExpenseDbHelper.Tx tx) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.valueOf(tx.amount));
        input.setGravity(Gravity.RIGHT);
        new AlertDialog.Builder(this).setTitle("تعديل المبلغ")
                .setView(input)
                .setPositiveButton("خصم", (d, w) -> {
                    double a = parseAmount(input.getText().toString());
                    if (a > 0) { db.updateTransactionAmount(tx.id, a); db.approveOnline(tx.id); showPending(); }
                }).setNegativeButton("إلغاء", null).show();
    }

    private void chooseDebtForPayment(ExpenseDbHelper.Tx tx) {
        List<ExpenseDbHelper.Debt> debts = db.getDebtsByDirection("OWED_TO_ME");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(6), dp(10), dp(6));
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("اختار الشخص اللي سدد").setView(box).setNegativeButton("إلغاء", null).create();
        for (ExpenseDbHelper.Debt d : debts) {
            if ("PAID".equals(d.status)) continue;
            Button b = softBtn(d.name + " - المتبقي " + db.money(d.amount - d.paid), PURPLE);
            b.setOnClickListener(v -> {
                db.addDebtPayment(d.id, tx.amount, "سداد من حوالة واردة", tx.id);
                db.markDebtPayment(tx.id);
                toast("تم تسجيل السداد");
                dialog.dismiss();
                showPending();
            });
            box.addView(b);
        }
        Button addNew = btn("إضافة شخص جديد بهذا المبلغ");
        addNew.setOnClickListener(v -> { dialog.dismiss(); addDebtDialog(tx.merchant, tx.amount); });
        box.addView(addNew);
        dialog.show();
    }

    private void showDebts() {
        setup("الديون والمواعيد"); addHomeButton();
        double owedToMe = db.getDebtTotal("OWED_TO_ME");
        double oweToOthers = db.getDebtTotal("OWE_TO_OTHERS");

        LinearLayout hero = card();
        hero.setBackground(gradient(PURPLE, Color.rgb(92, 74, 168), 24));
        hero.addView(text("متابعة اللي ليك واللي عليك", 14, false, Color.rgb(235, 231, 255)), matchWrap());
        hero.addView(text("ليك: " + db.money(owedToMe), 25, true, Color.WHITE), matchWrap());
        hero.addView(text("عليك: " + db.money(oweToOthers), 21, true, Color.rgb(255, 236, 236)), matchWrap());
        hero.addView(text("حدد تاريخ ووقت للسداد أو التحصيل وهيجيلك إشعار بالاسم والمبلغ", 12, false, Color.rgb(235, 231, 255)), matchWrap());
        root.addView(hero);

        LinearLayout actions = row();
        addWeighted(actions, actionCard("📥", "فلوس عند الناس", "أضف شخص هتاخد منه", PURPLE, v -> addDebtDialog("", 0, "OWED_TO_ME")), 1, 4);
        addWeighted(actions, actionCard("📤", "ناس ليها عندي", "أضف شخص هتسدده", RED, v -> addDebtDialog("", 0, "OWE_TO_OTHERS")), 1, 4);
        root.addView(actions, matchWrap());

        Button pay = softBtn("تسجيل دفعة / سداد", PRIMARY); pay.setOnClickListener(v -> manualDebtPaymentDialog()); root.addView(pay);
        Button settings = softBtn("إعدادات تذكيرات الديون", ORANGE); settings.setOnClickListener(v -> showSecurityAndReminderSettings()); root.addView(settings);

        addDebtSection("📥 فلوس ليا عند الناس", "OWED_TO_ME", "هنا الأشخاص اللي أنت مستني تاخد منهم فلوس");
        addDebtSection("📤 ناس ليها فلوس عندي", "OWE_TO_OTHERS", "هنا الأشخاص اللي عليك تسدد لهم فلوس");
    }

    private void addDebtSection(String title, String direction, String emptyMsg) {
        List<ExpenseDbHelper.Debt> list = db.getDebtsByDirection(direction);
        LinearLayout section = card(Color.WHITE);
        section.setBackground(strokeBg(Color.WHITE, Color.rgb(224, 235, 231), 22, 1));
        section.addView(text(title, 18, true, DARK), matchWrap());
        section.addView(text(emptyMsg, 12, false, MUTED), matchWrap());
        if (list.isEmpty()) {
            section.addView(text("لا يوجد بيانات في هذا القسم", 13, false, MUTED), matchWrap());
            root.addView(section);
            return;
        }
        root.addView(section);
        for (ExpenseDbHelper.Debt d : list) addDebtCard(d);
    }

    private void addDebtCard(ExpenseDbHelper.Debt d) {
        LinearLayout c = card();
        double remaining = Math.max(0, d.amount - d.paid);
        boolean iOwe = "OWE_TO_OTHERS".equals(d.direction);
        int mainColor = iOwe ? RED : PURPLE;
        LinearLayout top = row();
        TextView avatar = text(initials(d.name), 16, true, Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(bg(mainColor, 50));
        LinearLayout.LayoutParams avlp = new LinearLayout.LayoutParams(dp(42), dp(42));
        avlp.setMargins(dp(6), dp(2), 0, dp(2));
        top.addView(avatar, avlp);
        LinearLayout names = new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL); names.setGravity(Gravity.RIGHT);
        names.addView(text(d.name, 19, true, DARK), matchWrap());
        names.addView(text((iOwe ? "متبقي عليك: " : "متبقي ليك: ") + db.money(remaining), 13, false, MUTED), matchWrap());
        top.addView(names, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        top.addView(pill(debtStatusArabic(d.status), debtStatusColor(d.status)));
        c.addView(top, matchWrap());
        DebtProgressView dpv = new DebtProgressView(this, d.amount <= 0 ? 0 : d.paid / d.amount, pale(debtStatusColor(d.status)), debtStatusColor(d.status));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(9));
        dlp.setMargins(0, dp(10), 0, dp(6)); c.addView(dpv, dlp);
        c.addView(text("الأصل: " + db.money(d.amount) + " | المدفوع: " + db.money(d.paid), 13, false, MUTED), matchWrap());
        c.addView(text(debtDueText(d), 13, true, d.dueDateMillis > 0 && d.dueDateMillis < System.currentTimeMillis() && !"PAID".equals(d.status) ? RED : MUTED), matchWrap());
        if (d.notes != null && d.notes.length() > 0) c.addView(text("ملاحظات: " + d.notes, 13, false, MUTED), matchWrap());
        if (d.whatsapp != null && d.whatsapp.trim().length() > 0) {
            Button w = softBtn("فتح واتساب", PRIMARY); w.setOnClickListener(v -> openUrl("https://wa.me/" + cleanPhone(d.whatsapp))); c.addView(w);
            Button msg = softBtn("رسالة واتساب جاهزة بالمبلغ", PRIMARY_DARK); msg.setOnClickListener(v -> openDebtWhatsappMessage(d)); c.addView(msg);
        }
        if (d.facebook != null && d.facebook.trim().length() > 0) {
            Button f = softBtn("فتح فيسبوك", BLUE); f.setOnClickListener(v -> openUrl(d.facebook)); c.addView(f);
        }
        Button payment = softBtn(iOwe ? "سجل إنك سددت جزء" : "سجل إنه دفع جزء", ORANGE);
        payment.setOnClickListener(v -> debtPaymentAmountDialog(d));
        c.addView(payment);
        Button reminder = softBtn("تعديل ميعاد التذكير", mainColor);
        reminder.setOnClickListener(v -> debtDueDateDialog(d));
        c.addView(reminder);
        root.addView(c);
    }

    private String debtDueText(ExpenseDbHelper.Debt d) {
        if (d.dueDateMillis <= 0) return "لا يوجد ميعاد تذكير";
        String prefix = "OWE_TO_OTHERS".equals(d.direction) ? "ميعاد السداد: " : "ميعاد التحصيل: ";
        long now = System.currentTimeMillis();
        String tail = d.dueDateMillis < now && !"PAID".equals(d.status) ? " — متأخر" : "";
        return prefix + ExpenseDbHelper.date(d.dueDateMillis) + tail;
    }

    private void addDebtDialog(String defaultName, double defaultAmount) { addDebtDialog(defaultName, defaultAmount, "OWED_TO_ME"); }

    private void addDebtDialog(String defaultName, double defaultAmount, String direction) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20), dp(5), dp(20), dp(5));
        EditText name = field("الاسم", defaultName);
        EditText amount = field("المبلغ بـ " + db.currencyName(), defaultAmount > 0 ? String.valueOf(defaultAmount) : ""); amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText whatsapp = field("رقم واتساب اختياري", "");
        EditText facebook = field("رابط فيسبوك اختياري", "");
        EditText due = field("ميعاد التذكير اختياري: 27/06/2026 20:30", "");
        EditText notes = field("ملاحظات", "");
        box.addView(name); box.addView(amount); box.addView(whatsapp); box.addView(facebook); box.addView(due); box.addView(notes);
        String title = "OWE_TO_OTHERS".equals(direction) ? "إضافة شخص ليه فلوس عندي" : "إضافة شخص ليا عنده فلوس";
        new AlertDialog.Builder(this).setTitle(title).setView(box)
                .setPositiveButton("حفظ", (d, w) -> {
                    double a = parseAmount(amount.getText().toString());
                    long dueMs = parseDateTime(due.getText().toString());
                    if (a > 0 && name.getText().toString().trim().length() > 0) {
                        long id = db.addDebt(name.getText().toString().trim(), a, whatsapp.getText().toString(), facebook.getText().toString(), notes.getText().toString(), direction, dueMs);
                        if (dueMs > 0) scheduleDebtReminder(id, name.getText().toString().trim(), a, direction, dueMs);
                        toast("تم الحفظ" + (dueMs > 0 ? " وتم ضبط التذكير" : "")); showDebts();
                    }
                }).setNegativeButton("إلغاء", null).show();
    }

    private void debtDueDateDialog(ExpenseDbHelper.Debt debt) {
        EditText due = new EditText(this);
        due.setHint("27/06/2026 20:30");
        due.setText(debt.dueDateMillis > 0 ? ExpenseDbHelper.date(debt.dueDateMillis) : "");
        due.setGravity(Gravity.RIGHT); due.setTextDirection(View.TEXT_DIRECTION_RTL);
        new AlertDialog.Builder(this).setTitle("تعديل ميعاد " + debt.name).setView(due)
                .setPositiveButton("حفظ", (d,w) -> {
                    long dueMs = parseDateTime(due.getText().toString());
                    db.updateDebtDueDate(debt.id, dueMs);
                    if (dueMs > 0) scheduleDebtReminder(debt.id, debt.name, debt.amount - debt.paid, debt.direction, dueMs);
                    toast("تم تحديث الميعاد"); showDebts();
                }).setNegativeButton("إلغاء", null).show();
    }

    private String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "؟";
        String[] parts = name.trim().split("\\s+");
        String s = parts[0].substring(0, 1);
        if (parts.length > 1) s += parts[1].substring(0, 1);
        return s;
    }

    private void manualDebtPaymentDialog() {
        List<ExpenseDbHelper.Debt> debts = db.getDebts();
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(10), dp(6), dp(10), dp(6));
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("اختار الشخص").setView(box).setNegativeButton("إلغاء", null).create();
        for (ExpenseDbHelper.Debt debt : debts) {
            if ("PAID".equals(debt.status)) continue;
            Button b = softBtn(debt.name + " - المتبقي " + db.money(debt.amount - debt.paid), PURPLE);
            b.setOnClickListener(v -> { dialog.dismiss(); debtPaymentAmountDialog(debt); });
            box.addView(b);
        }
        dialog.show();
    }

    private void debtPaymentAmountDialog(ExpenseDbHelper.Debt debt) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("المبلغ المدفوع");
        input.setGravity(Gravity.RIGHT);
        new AlertDialog.Builder(this).setTitle("دفعة من " + debt.name).setView(input)
                .setPositiveButton("حفظ", (d, w) -> {
                    double a = parseAmount(input.getText().toString());
                    if (a > 0) { db.addDebtPayment(debt.id, a, "دفعة يدوية", -1); showDebts(); }
                }).setNegativeButton("إلغاء", null).show();
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setText(value); e.setGravity(Gravity.RIGHT); e.setTextDirection(View.TEXT_DIRECTION_RTL);
        e.setSingleLine(false);
        return e;
    }

    private void showLog() { showLog("ALL", "ALL"); }

    private void showLog(String currencyFilter, String categoryFilter) {
        setup("سجل العمليات"); addHomeButton();
        LinearLayout filters = card(pale(BLUE));
        filters.setBackground(strokeBg(pale(BLUE), lighten(BLUE), 22, 1));
        filters.addView(text("فلترة السجل", 18, true, DARK), matchWrap());
        filters.addView(text("اختار العملة أو التصنيف عشان تراجع العمليات بسرعة", 12, false, MUTED), matchWrap());
        LinearLayout fr1 = row();
        addWeighted(fr1, actionCard("🌍", "كل العملات", "عرض الكل", MUTED, v -> showLog("ALL", categoryFilter)), 1, 4);
        addWeighted(fr1, actionCard("🇸🇦", "ريال سعودي", "SAR", PRIMARY, v -> showLog("SAR", categoryFilter)), 1, 4);
        filters.addView(fr1, matchWrap());
        LinearLayout fr2 = row();
        addWeighted(fr2, actionCard("🇪🇬", "جنيه مصري", "EGP", RED, v -> showLog("EGP", categoryFilter)), 1, 4);
        addWeighted(fr2, actionCard("🏷️", "التصنيف", categoryFilter == null || "ALL".equals(categoryFilter) ? "كل التصنيفات" : categoryFilter, BLUE, v -> categoryFilterDialog(currencyFilter)), 1, 4);
        filters.addView(fr2, matchWrap());
        filters.addView(pill("الحالي: " + ("ALL".equals(currencyFilter) ? "كل العملات" : currencyFilter) + " / " + ("ALL".equals(categoryFilter) ? "كل التصنيفات" : categoryFilter), BLUE));
        root.addView(filters);

        List<ExpenseDbHelper.Tx> txs = db.getRecentFiltered(currencyFilter, categoryFilter, 200);
        if (txs.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("مفيش عمليات بنفس الفلتر", 18, true, DARK), matchWrap());
            empty.addView(text("غيّر الفلتر أو أضف عملية جديدة.", 13, false, MUTED), matchWrap());
            root.addView(empty);
        }
        for (ExpenseDbHelper.Tx tx : txs) {
            LinearLayout c = card();
            LinearLayout top = row();
            top.addView(pill(statusArabic(tx.status), statusColor(tx.status)));
            Space sp = new Space(this); top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1));
            top.addView(text(txMoney(tx), 17, true, DARK));
            c.addView(top, matchWrap());
            c.addView(text(tx.title, 18, true, DARK), matchWrap());
            c.addView(text("التصنيف: " + tx.category + " | العملة: " + safeCurrency(tx.currency) + " | " + ExpenseDbHelper.date(tx.dateMillis), 13, false, MUTED), matchWrap());
            c.addView(text(tx.affectsBudget == 1 ? "يؤثر على الميزانية" : "منفصل عن الميزانية", 13, false, tx.affectsBudget == 1 ? PRIMARY : MUTED), matchWrap());
            if (tx.card != null && tx.card.length() > 0) c.addView(text("آخر 4 أرقام: " + tx.card, 12, false, MUTED), matchWrap());
            Button edit = softBtn("تعديل العملية", BLUE);
            edit.setOnClickListener(v -> editTransactionDialog(tx));
            c.addView(edit);
            root.addView(c);
        }
    }

    private void categoryFilterDialog(String currencyFilter) {
        List<String> cats = db.getCategories();
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(10), dp(6), dp(10), dp(6));
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("اختار التصنيف").setView(box).setNegativeButton("إلغاء", null).create();
        Button all = softBtn("كل التصنيفات", MUTED); all.setOnClickListener(v -> { dialog.dismiss(); showLog(currencyFilter, "ALL"); }); box.addView(all);
        for (String cat : cats) {
            Button b = softBtn(cat, BLUE);
            b.setOnClickListener(v -> { dialog.dismiss(); showLog(currencyFilter, cat); });
            box.addView(b);
        }
        dialog.show();
    }

    private String safeCurrency(String c) {
        if ("EGP".equalsIgnoreCase(c)) return "جنيه مصري";
        if ("SAR".equalsIgnoreCase(c)) return "ريال سعودي";
        return c == null || c.length() == 0 ? db.currencyName() : c;
    }

    private String txMoney(ExpenseDbHelper.Tx tx) {
        String cur = tx.currency == null ? db.getCurrency() : tx.currency;
        String sym = "EGP".equalsIgnoreCase(cur) ? "ج.م" : "ر.س";
        return String.format(Locale.US, "%.2f %s", tx.amount, sym);
    }

    private void editTransactionDialog(ExpenseDbHelper.Tx tx) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(5), dp(20), dp(5));

        EditText title = field("اسم العملية", tx.title == null ? "" : tx.title);
        EditText amount = field("المبلغ", String.valueOf(tx.amount));
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText category = field("التصنيف", tx.category == null ? "عام" : tx.category);
        CheckBox affects = new CheckBox(this);
        affects.setText("تؤثر على الميزانية الشهرية");
        affects.setTextSize(14);
        affects.setTextColor(DARK);
        affects.setGravity(Gravity.RIGHT);
        affects.setTextDirection(View.TEXT_DIRECTION_RTL);
        affects.setChecked(tx.affectsBudget == 1);

        box.addView(label("اسم العملية", 13, true));
        box.addView(title);
        box.addView(label("المبلغ", 13, true));
        box.addView(amount);
        box.addView(label("التصنيف", 13, true));
        box.addView(category);
        box.addView(affects);

        new AlertDialog.Builder(this)
                .setTitle("تعديل العملية")
                .setView(box)
                .setPositiveButton("حفظ", (d, w) -> {
                    double a = parseAmount(amount.getText().toString());
                    db.updateTransaction(tx.id, a, title.getText().toString(), category.getText().toString(), affects.isChecked() ? 1 : 0);
                    toast("تم تعديل العملية");
                    showLog();
                })
                .setNeutralButton("حذف", (d, w) -> {
                    db.deleteTransaction(tx.id);
                    toast("تم حذف العملية");
                    showLog();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }


    private void showSecurityAndReminderSettings() {
        setup("إعدادات التذكير والقفل"); addHomeButton();
        LinearLayout info = card();
        info.addView(text("تذكيرات الديون", 20, true, DARK), matchWrap());
        info.addView(text("اختار هل يوصلك تنبيه قبل الميعاد بيوم، قبل الميعاد بساعتين، وهل يتكرر لو السداد/التحصيل اتأخر.", 13, false, MUTED), matchWrap());
        CheckBox beforeDay = new CheckBox(this); beforeDay.setText("تذكير قبل الميعاد بيوم"); beforeDay.setChecked("1".equals(db.getSetting("debt_remind_day", "1"))); beforeDay.setGravity(Gravity.RIGHT); beforeDay.setTextDirection(View.TEXT_DIRECTION_RTL);
        CheckBox before2h = new CheckBox(this); before2h.setText("تذكير قبل الميعاد بساعتين"); before2h.setChecked("1".equals(db.getSetting("debt_remind_2h", "1"))); before2h.setGravity(Gravity.RIGHT); before2h.setTextDirection(View.TEXT_DIRECTION_RTL);
        CheckBox repeat = new CheckBox(this); repeat.setText("تكرار التذكير لو متأخر"); repeat.setChecked("1".equals(db.getSetting("debt_repeat_overdue", "1"))); repeat.setGravity(Gravity.RIGHT); repeat.setTextDirection(View.TEXT_DIRECTION_RTL);
        info.addView(beforeDay); info.addView(before2h); info.addView(repeat);
        Button saveRem = btn("حفظ إعدادات التذكير");
        saveRem.setOnClickListener(v -> {
            db.setSetting("debt_remind_day", beforeDay.isChecked() ? "1" : "0");
            db.setSetting("debt_remind_2h", before2h.isChecked() ? "1" : "0");
            db.setSetting("debt_repeat_overdue", repeat.isChecked() ? "1" : "0");
            for (ExpenseDbHelper.Debt d : db.getDebts()) {
                if (!"PAID".equals(d.status) && d.dueDateMillis > 0) scheduleDebtReminder(d.id, d.name, Math.max(0, d.amount - d.paid), d.direction, d.dueDateMillis);
            }
            toast("تم حفظ إعدادات التذكير وإعادة ضبط المواعيد");
        });
        info.addView(saveRem);
        root.addView(info);

        LinearLayout lock = card();
        lock.addView(text("قفل التطبيق", 20, true, DARK), matchWrap());
        lock.addView(text("لو فعلته، التطبيق يطلب PIN عند الفتح. وتقدر تفتحه كمان ببصمة/قفل الجهاز لو الجهاز بيدعم ده.", 13, false, MUTED), matchWrap());
        CheckBox enabled = new CheckBox(this); enabled.setText("تفعيل قفل التطبيق"); enabled.setChecked("1".equals(db.getSetting("app_lock_enabled", "0"))); enabled.setGravity(Gravity.RIGHT); enabled.setTextDirection(View.TEXT_DIRECTION_RTL);
        EditText pin = field("PIN جديد 4 أرقام أو أكثر", db.getSetting("app_lock_pin", ""));
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        lock.addView(enabled); lock.addView(pin);
        Button saveLock = btn("حفظ القفل");
        saveLock.setOnClickListener(v -> {
            String p = pin.getText().toString().trim();
            if (enabled.isChecked() && p.length() < 4) { toast("اكتب PIN من 4 أرقام على الأقل"); return; }
            db.setSetting("app_lock_enabled", enabled.isChecked() ? "1" : "0");
            db.setSetting("app_lock_pin", p);
            appUnlocked = !enabled.isChecked();
            toast(enabled.isChecked() ? "تم تفعيل القفل" : "تم إلغاء القفل");
        });
        lock.addView(saveLock);
        Button testDevice = softBtn("اختبار فتح ببصمة / قفل الجهاز", PRIMARY_DARK);
        testDevice.setOnClickListener(v -> openDeviceCredential());
        lock.addView(testDevice);
        root.addView(lock);
    }

    private void maybeShowAppLock() {
        if (appUnlocked) return;
        if (!"1".equals(db.getSetting("app_lock_enabled", "0"))) return;
        final EditText pin = new EditText(this);
        pin.setHint("اكتب PIN");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setGravity(Gravity.CENTER);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("مصروفاتي مقفول")
                .setMessage("افتح التطبيق بالـ PIN أو ببصمة/قفل الجهاز")
                .setView(pin)
                .setPositiveButton("فتح", null)
                .setNeutralButton("بصمة / قفل الجهاز", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (pin.getText().toString().trim().equals(db.getSetting("app_lock_pin", ""))) {
                    appUnlocked = true; dialog.dismiss(); toast("تم فتح القفل");
                } else toast("PIN غير صحيح");
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> openDeviceCredential());
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    private void openDeviceCredential() {
        if (Build.VERSION.SDK_INT < 21) { toast("جهازك لا يدعم فتح القفل من هنا"); return; }
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km == null || !km.isKeyguardSecure()) { toast("فعل بصمة أو PIN للجهاز الأول"); return; }
        Intent intent = km.createConfirmDeviceCredentialIntent("مصروفاتي", "افتح التطبيق بقفل الجهاز");
        if (intent != null) startActivityForResult(intent, REQ_DEVICE_LOCK);
    }

    private void openDebtWhatsappMessage(ExpenseDbHelper.Debt d) {
        if (d.whatsapp == null || d.whatsapp.trim().isEmpty()) { toast("مفيش رقم واتساب"); return; }
        double remaining = Math.max(0, d.amount - d.paid);
        boolean iOwe = "OWE_TO_OTHERS".equals(d.direction);
        String message = iOwe
                ? "السلام عليكم، للتذكير عليا ليك مبلغ " + db.money(remaining) + " وهسدده في أقرب وقت."
                : "السلام عليكم، للتذكير ليا عندك مبلغ " + db.money(remaining) + " يا ريت تراجعني في ميعاد السداد. شكرًا";
        openUrl("https://wa.me/" + cleanPhone(d.whatsapp) + "?text=" + Uri.encode(message));
    }

    private void showSubscriptions() {
        setup("الاشتراكات الشهرية"); addHomeButton();
        LinearLayout hero = card();
        hero.setBackground(gradient(BLUE, Color.rgb(36, 84, 180), 24));
        hero.addView(text("تابع الاشتراكات المتكررة", 14, false, Color.rgb(230, 238, 255)), matchWrap());
        hero.addView(text("الإجمالي النشط: " + db.money(db.getActiveSubscriptionsTotal()), 24, true, Color.WHITE), matchWrap());
        hero.addView(text("أمثلة: Google، Netflix، Apple، شاهد، برامج شهرية", 12, false, Color.rgb(230, 238, 255)), matchWrap());
        root.addView(hero);
        Button add = btn("إضافة اشتراك شهري"); add.setOnClickListener(v -> addSubscriptionDialog()); root.addView(add);

        List<ExpenseDbHelper.Subscription> list = db.getSubscriptions();
        if (list.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("لسه مفيش اشتراكات", 18, true, DARK), matchWrap());
            empty.addView(text("ضيف Google أو Netflix أو أي اشتراك شهري عشان يظهرلك ميعاده ويتسجل كمصروف.", 13, false, MUTED), matchWrap());
            root.addView(empty);
        }
        for (ExpenseDbHelper.Subscription sub : list) addSubscriptionCard(sub);
    }

    private void addSubscriptionCard(ExpenseDbHelper.Subscription sub) {
        LinearLayout c = card();
        LinearLayout top = row();
        top.addView(pill(sub.active == 1 ? "نشط" : "متوقف", sub.active == 1 ? PRIMARY : MUTED));
        Space sp = new Space(this); top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1));
        String sym = "EGP".equalsIgnoreCase(sub.currency) ? "ج.م" : "ر.س";
        top.addView(text(String.format(Locale.US, "%.2f %s", sub.amount, sym), 17, true, DARK));
        c.addView(top, matchWrap());
        c.addView(text(sub.name, 19, true, DARK), matchWrap());
        c.addView(text("التصنيف: " + sub.category + " | القادم: " + (sub.nextDateMillis > 0 ? ExpenseDbHelper.date(sub.nextDateMillis) : "غير محدد"), 13, false, MUTED), matchWrap());
        if (sub.notes != null && sub.notes.length() > 0) c.addView(text("ملاحظات: " + sub.notes, 12, false, MUTED), matchWrap());
        Button charge = softBtn("سجل خصم هذا الشهر", PRIMARY);
        charge.setOnClickListener(v -> { db.chargeSubscription(sub.id); ExpenseDbHelper.Subscription updated = db.getSubscriptionById(sub.id); if (updated != null) scheduleSubscriptionReminder(updated.id, updated.name, updated.amount, updated.currency, updated.nextDateMillis); toast("تم تسجيل الاشتراك كمصروف وتحديث الشهر القادم"); showSubscriptions(); });
        c.addView(charge);
        Button toggle = softBtn(sub.active == 1 ? "إيقاف الاشتراك" : "تفعيل الاشتراك", sub.active == 1 ? RED : PRIMARY);
        toggle.setOnClickListener(v -> { db.toggleSubscription(sub.id, sub.active != 1); showSubscriptions(); });
        c.addView(toggle);
        root.addView(c);
    }

    private void addSubscriptionDialog() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20), dp(5), dp(20), dp(5));
        EditText name = field("اسم الاشتراك: Google / Netflix", "");
        EditText amount = field("المبلغ", ""); amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText merchant = field("اسم التاجر اختياري", "");
        EditText category = field("التصنيف", "اشتراكات/أونلاين");
        EditText next = field("ميعاد الخصم القادم: 27/06/2026 20:30", "");
        EditText notes = field("ملاحظات", "");
        CheckBox egp = new CheckBox(this); egp.setText("جنيه مصري بدل ريال سعودي"); egp.setGravity(Gravity.RIGHT); egp.setTextDirection(View.TEXT_DIRECTION_RTL);
        box.addView(name); box.addView(amount); box.addView(merchant); box.addView(category); box.addView(next); box.addView(notes); box.addView(egp);
        new AlertDialog.Builder(this).setTitle("إضافة اشتراك شهري").setView(box)
                .setPositiveButton("حفظ", (d,w) -> {
                    double a = parseAmount(amount.getText().toString()); long nextMs = parseDateTime(next.getText().toString());
                    if (a <= 0 || name.getText().toString().trim().isEmpty()) { toast("اكتب الاسم والمبلغ"); return; }
                    String cur = egp.isChecked() ? "EGP" : "SAR";
                    long id = db.addSubscription(name.getText().toString(), a, cur, merchant.getText().toString(), category.getText().toString(), nextMs, notes.getText().toString());
                    if (nextMs > 0) scheduleSubscriptionReminder(id, name.getText().toString(), a, cur, nextMs);
                    toast("تم حفظ الاشتراك"); showSubscriptions();
                }).setNegativeButton("إلغاء", null).show();
    }

    private void openUrl(String url) {
        if (url == null || url.trim().isEmpty()) return;
        if (!url.startsWith("http") && !url.startsWith("whatsapp")) url = "https://" + url;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { toast("مش قادر أفتح الرابط"); }
    }

    private String cleanPhone(String p) {
        String s = p.replaceAll("[^0-9]", "");
        if (s.startsWith("0")) s = "966" + s.substring(1);
        return s;
    }

    private double parseAmount(String s) {
        try { return Double.parseDouble(s.trim().replace(",", ".")); } catch (Exception e) { return 0; }
    }

    private long parseDateTime(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        String v = value.trim().replace("-", "/");
        String[] patterns = {"dd/MM/yyyy HH:mm", "d/M/yyyy HH:mm", "dd/MM/yy HH:mm", "d/M/yy HH:mm", "dd/MM/yyyy", "d/M/yyyy"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
                f.setLenient(false);
                Date date = f.parse(v);
                return date == null ? 0 : date.getTime();
            } catch (Exception ignored) {}
        }
        toast("صيغة التاريخ غير واضحة. استخدم مثال: 27/06/2026 20:30");
        return 0;
    }

    private void scheduleDebtReminder(long debtId, String name, double amount, String direction, long dueDateMillis) {
        if (dueDateMillis <= 0) return;
        if ("1".equals(db.getSetting("debt_remind_day", "1"))) scheduleReminderAlarm(debtId, name, amount, direction, dueDateMillis - 24L * 60L * 60L * 1000L, "BEFORE_DAY", 11);
        if ("1".equals(db.getSetting("debt_remind_2h", "1"))) scheduleReminderAlarm(debtId, name, amount, direction, dueDateMillis - 2L * 60L * 60L * 1000L, "BEFORE_2H", 22);
        scheduleReminderAlarm(debtId, name, amount, direction, dueDateMillis, "DUE", 33);
        if ("1".equals(db.getSetting("debt_repeat_overdue", "1"))) {
            for (int i = 1; i <= 5; i++) scheduleReminderAlarm(debtId, name, amount, direction, dueDateMillis + i * 24L * 60L * 60L * 1000L, "OVERDUE", 100 + i);
        }
    }

    private void scheduleReminderAlarm(long debtId, String name, double amount, String direction, long atMillis, String kind, int salt) {
        if (atMillis <= System.currentTimeMillis()) return;
        try {
            Intent intent = new Intent(this, DebtReminderReceiver.class);
            intent.putExtra("debtId", debtId);
            intent.putExtra("name", name);
            intent.putExtra("amount", db.money(amount));
            intent.putExtra("direction", direction);
            intent.putExtra("kind", kind);
            int req = (int)((debtId % 100000) * 10 + salt);
            PendingIntent pi = PendingIntent.getBroadcast(this, req, intent, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am == null) return;
            if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi);
            else am.set(AlarmManager.RTC_WAKEUP, atMillis, pi);
        } catch (Exception ignored) {}
    }

    private void scheduleSubscriptionReminder(long subId, String name, double amount, String currency, long nextDateMillis) {
        if (nextDateMillis <= System.currentTimeMillis()) return;
        try {
            Intent intent = new Intent(this, DebtReminderReceiver.class);
            intent.putExtra("name", name);
            String sym = "EGP".equalsIgnoreCase(currency) ? "ج.م" : "ر.س";
            intent.putExtra("amount", String.format(Locale.US, "%.2f %s", amount, sym));
            intent.putExtra("direction", "SUBSCRIPTION");
            intent.putExtra("kind", "SUBSCRIPTION");
            PendingIntent pi = PendingIntent.getBroadcast(this, (int)(700000 + subId % 100000), intent, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am == null) return;
            if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextDateMillis, pi);
            else am.set(AlarmManager.RTC_WAKEUP, nextDateMillis, pi);
        } catch (Exception ignored) {}
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    @Override public void onBackPressed() { showHome(); }

    public static class BudgetProgressView extends View {
        private final double progress;
        private final int bgColor;
        private final int fgColor;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public BudgetProgressView(Activity ctx, double progress, int bgColor, int fgColor) {
            super(ctx); this.progress = Math.max(0, Math.min(1, progress)); this.bgColor = bgColor; this.fgColor = fgColor;
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float r = getHeight() / 2f;
            paint.setColor(bgColor);
            RectF full = new RectF(0, 0, getWidth(), getHeight());
            canvas.drawRoundRect(full, r, r, paint);
            paint.setColor(fgColor);
            RectF done = new RectF(0, 0, (float)(getWidth() * progress), getHeight());
            canvas.drawRoundRect(done, r, r, paint);
        }
    }

    public static class DebtProgressView extends BudgetProgressView {
        public DebtProgressView(Activity ctx, double progress, int bgColor, int fgColor) { super(ctx, progress, bgColor, fgColor); }
    }

    public class CategoryChartView extends View {
        private final List<ExpenseDbHelper.CatTotal> data;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] colors = new int[]{PRIMARY, BLUE, ORANGE, PURPLE, RED};
        public CategoryChartView(Activity ctx, List<ExpenseDbHelper.CatTotal> data) { super(ctx); this.data = data; }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (data == null || data.isEmpty()) {
                paint.setColor(Color.rgb(232, 239, 236));
                canvas.drawRoundRect(new RectF(0, dp(20), w, h - dp(20)), dp(18), dp(18), paint);
                paint.setColor(MUTED);
                paint.setTextSize(dp(13));
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("ابدأ تسجيل مصاريفك عشان يظهر الرسم", w / 2f, h / 2f, paint);
                return;
            }
            double max = 1;
            for (ExpenseDbHelper.CatTotal c : data) if (c.total > max) max = c.total;
            float itemH = h / Math.max(1, data.size());
            paint.setTextSize(dp(11));
            for (int i = 0; i < data.size(); i++) {
                ExpenseDbHelper.CatTotal ct = data.get(i);
                float y = i * itemH + dp(6);
                paint.setColor(MUTED);
                paint.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(ct.category, w - dp(4), y + dp(13), paint);
                paint.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(String.format(Locale.US, "%.0f", ct.total), dp(4), y + dp(13), paint);
                float barTop = y + dp(22);
                float barH = dp(10);
                paint.setColor(Color.rgb(232, 239, 236));
                canvas.drawRoundRect(new RectF(0, barTop, w, barTop + barH), dp(8), dp(8), paint);
                paint.setColor(colors[i % colors.length]);
                float bw = (float)(w * Math.min(1, ct.total / max));
                canvas.drawRoundRect(new RectF(w - bw, barTop, w, barTop + barH), dp(8), dp(8), paint);
            }
        }
    }
}
