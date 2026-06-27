package com.mohamed.expenseguard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class FirebaseSyncManager {
    public interface Callback {
        void ok(String message);
        void fail(String message);
    }

    private final Context context;
    private final ExpenseDbHelper db;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    public FirebaseSyncManager(Context context, ExpenseDbHelper db) {
        this.context = context.getApplicationContext();
        this.db = db;
    }

    public boolean hasConfig() {
        return !db.getSetting("firebase_api_key", "").trim().isEmpty()
                && !db.getSetting("firebase_app_id", "").trim().isEmpty()
                && !db.getSetting("firebase_project_id", "").trim().isEmpty()
                && !db.getSetting("google_web_client_id", "").trim().isEmpty();
    }

    public void saveConfig(String apiKey, String appId, String projectId, String webClientId) {
        db.setSetting("firebase_api_key", apiKey.trim());
        db.setSetting("firebase_app_id", appId.trim());
        db.setSetting("firebase_project_id", projectId.trim());
        db.setSetting("google_web_client_id", webClientId.trim());
    }

    public void init() throws Exception {
        if (!hasConfig()) throw new Exception("كمّل بيانات Firebase و Web Client ID الأول");
        FirebaseApp app;
        try {
            app = FirebaseApp.getInstance("masrofaty");
        } catch (Exception ignored) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApiKey(db.getSetting("firebase_api_key", ""))
                    .setApplicationId(db.getSetting("firebase_app_id", ""))
                    .setProjectId(db.getSetting("firebase_project_id", ""))
                    .build();
            app = FirebaseApp.initializeApp(context, options, "masrofaty");
        }
        auth = FirebaseAuth.getInstance(app);
        firestore = FirebaseFirestore.getInstance(app);
    }

    public FirebaseUser currentUser() {
        try {
            init();
            return auth.getCurrentUser();
        } catch (Exception e) {
            return null;
        }
    }

    public Intent signInIntent() throws Exception {
        init();
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(db.getSetting("google_web_client_id", ""))
                .requestEmail()
                .build();
        GoogleSignInClient client = GoogleSignIn.getClient(context, gso);
        return client.getSignInIntent();
    }

    public void handleSignInResult(Intent data, Callback cb) {
        try {
            init();
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account == null || account.getIdToken() == null) {
                cb.fail("تسجيل الدخول فشل: مفيش Token من جوجل");
                return;
            }
            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
            auth.signInWithCredential(credential)
                    .addOnSuccessListener(r -> cb.ok("تم تسجيل الدخول: " + (r.getUser() == null ? "" : r.getUser().getEmail())))
                    .addOnFailureListener(e -> cb.fail("فشل تسجيل الدخول: " + e.getMessage()));
        } catch (Exception e) {
            cb.fail("فشل تسجيل الدخول: " + e.getMessage());
        }
    }

    public void signOut(Activity activity, Callback cb) {
        try {
            init();
            FirebaseAuth.getInstance(FirebaseApp.getInstance("masrofaty")).signOut();
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(db.getSetting("google_web_client_id", ""))
                    .requestEmail()
                    .build();
            GoogleSignIn.getClient(activity, gso).signOut()
                    .addOnCompleteListener(t -> cb.ok("تم تسجيل الخروج"));
        } catch (Exception e) {
            cb.fail(e.getMessage());
        }
    }

    public void uploadBackup(Callback cb) {
        try {
            init();
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) { cb.fail("سجل دخول بجوجل الأول"); return; }
            String backup = db.exportBackupJson();
            Map<String, Object> map = new HashMap<>();
            map.put("backupJson", backup);
            map.put("updatedAt", Timestamp.now());
            map.put("appName", "مصروفاتي");
            map.put("version", "1.4");
            firestore.collection("users").document(user.getUid()).collection("backups").document("current")
                    .set(map)
                    .addOnSuccessListener(v -> { db.setSetting("last_cloud_sync", String.valueOf(System.currentTimeMillis())); cb.ok("تم رفع نسخة احتياطية على حسابك"); })
                    .addOnFailureListener(e -> cb.fail("فشل الرفع: " + e.getMessage()));
        } catch (Exception e) {
            cb.fail(e.getMessage());
        }
    }

    public void restoreBackup(Callback cb) {
        try {
            init();
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) { cb.fail("سجل دخول بجوجل الأول"); return; }
            firestore.collection("users").document(user.getUid()).collection("backups").document("current")
                    .get()
                    .addOnSuccessListener(doc -> {
                        try {
                            if (!doc.exists()) { cb.fail("مفيش نسخة محفوظة على السحابة"); return; }
                            String backup = doc.getString("backupJson");
                            if (backup == null || backup.trim().isEmpty()) { cb.fail("النسخة السحابية فاضية"); return; }
                            db.importBackupJson(backup);
                            cb.ok("تم استرجاع الداتا من جوجل");
                        } catch (Exception e) {
                            cb.fail("فشل الاسترجاع: " + e.getMessage());
                        }
                    })
                    .addOnFailureListener(e -> cb.fail("فشل التحميل: " + e.getMessage()));
        } catch (Exception e) {
            cb.fail(e.getMessage());
        }
    }
}
