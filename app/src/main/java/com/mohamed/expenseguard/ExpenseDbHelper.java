package com.mohamed.expenseguard;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

public class ExpenseDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "expense_guard.db";
    private static final int DB_VERSION = 4;

    public ExpenseDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        db.execSQL("CREATE TABLE transactions(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "type TEXT, status TEXT, amount REAL, currency TEXT," +
                "title TEXT, merchant TEXT, category TEXT, source TEXT, raw TEXT," +
                "dateMillis INTEGER, fingerprint TEXT UNIQUE, card TEXT, extra TEXT," +
                "affectsBudget INTEGER, createdAt INTEGER)");
        db.execSQL("CREATE TABLE debts(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT, amount REAL, paid REAL, whatsapp TEXT, facebook TEXT," +
                "notes TEXT, status TEXT, direction TEXT, dueDateMillis INTEGER," +
                "createdAt INTEGER, updatedAt INTEGER)");
        db.execSQL("CREATE TABLE debt_payments(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "debtId INTEGER, amount REAL, note TEXT, dateMillis INTEGER, txId INTEGER)");
        db.execSQL("CREATE TABLE subscriptions(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT, amount REAL, currency TEXT, merchant TEXT, category TEXT," +
                "nextDateMillis INTEGER, active INTEGER, notes TEXT, lastChargedMillis INTEGER," +
                "createdAt INTEGER, updatedAt INTEGER)");
        db.execSQL("CREATE TABLE budget_categories(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT UNIQUE, monthlyLimit REAL, currency TEXT, active INTEGER," +
                "createdAt INTEGER, updatedAt INTEGER)");
        db.execSQL("CREATE TABLE month_archives(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "monthKey TEXT UNIQUE, budget REAL, spent REAL, extraIncome REAL, cashBalance REAL," +
                "closedAt INTEGER, summary TEXT)");
        setSetting(db, "monthly_budget", "0");
        setSetting(db, "currency", "SAR");
        setSetting(db, "debt_remind_day", "1");
        setSetting(db, "debt_remind_2h", "1");
        setSetting(db, "debt_repeat_overdue", "1");
        setSetting(db, "app_lock_enabled", "0");
        setSetting(db, "app_lock_pin", "");
        setSetting(db, "language", "ar");
        setSetting(db, "cash_balance", "0");
        setSetting(db, "abnormal_expense_threshold", "0");
        setSetting(db, "subscription_remind_3d", "1");
        setSetting(db, "subscription_remind_1d", "1");
        setSetting(db, "last_open_month", currentMonthKey());
        seedDefaultCategories(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try { db.execSQL("ALTER TABLE debts ADD COLUMN direction TEXT DEFAULT 'OWED_TO_ME'"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE debts ADD COLUMN dueDateMillis INTEGER DEFAULT 0"); } catch (Exception ignored) {}
            try { setSetting(db, "currency", "SAR"); } catch (Exception ignored) {}
        }
        if (oldVersion < 3) {
            try { db.execSQL("CREATE TABLE IF NOT EXISTS subscriptions(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT, amount REAL, currency TEXT, merchant TEXT, category TEXT," +
                    "nextDateMillis INTEGER, active INTEGER, notes TEXT, lastChargedMillis INTEGER," +
                    "createdAt INTEGER, updatedAt INTEGER)"); } catch (Exception ignored) {}
            try { setSetting(db, "debt_remind_day", "1"); } catch (Exception ignored) {}
            try { setSetting(db, "debt_remind_2h", "1"); } catch (Exception ignored) {}
            try { setSetting(db, "debt_repeat_overdue", "1"); } catch (Exception ignored) {}
            try { setSetting(db, "app_lock_enabled", "0"); } catch (Exception ignored) {}
            try { setSetting(db, "app_lock_pin", ""); } catch (Exception ignored) {}
        }
        if (oldVersion < 4) {
            try { db.execSQL("CREATE TABLE IF NOT EXISTS budget_categories(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT UNIQUE, monthlyLimit REAL, currency TEXT, active INTEGER," +
                    "createdAt INTEGER, updatedAt INTEGER)"); } catch (Exception ignored) {}
            try { db.execSQL("CREATE TABLE IF NOT EXISTS month_archives(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "monthKey TEXT UNIQUE, budget REAL, spent REAL, extraIncome REAL, cashBalance REAL," +
                    "closedAt INTEGER, summary TEXT)"); } catch (Exception ignored) {}
            try { setSetting(db, "language", "ar"); } catch (Exception ignored) {}
            try { setSetting(db, "cash_balance", "0"); } catch (Exception ignored) {}
            try { setSetting(db, "abnormal_expense_threshold", "0"); } catch (Exception ignored) {}
            try { setSetting(db, "subscription_remind_3d", "1"); } catch (Exception ignored) {}
            try { setSetting(db, "subscription_remind_1d", "1"); } catch (Exception ignored) {}
            try { setSetting(db, "last_open_month", currentMonthKey()); } catch (Exception ignored) {}
            try { seedDefaultCategories(db); } catch (Exception ignored) {}
        }
    }

    public double getBudget() { return getDoubleSetting("monthly_budget", 0); }
    public void setBudget(double value) { setSetting("monthly_budget", String.valueOf(Math.max(0, value))); }
    public void addToBudget(double delta) { setBudget(getBudget() + delta); }

    public String getCurrency() { return getSetting("currency", "SAR"); }
    public void setCurrency(String value) {
        String v = "EGP".equalsIgnoreCase(value) ? "EGP" : "SAR";
        setSetting("currency", v);
    }
    public String currencyName() { return "EGP".equals(getCurrency()) ? "جنيه مصري" : "ريال سعودي"; }
    public String currencySymbol() { return "EGP".equals(getCurrency()) ? "ج.م" : "ر.س"; }
    public String money(double v) { return String.format(Locale.US, "%.2f %s", v, currencySymbol()); }

    public String getSetting(String key, String fallback) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT value FROM settings WHERE key=?", new String[]{key})) {
            if (c.moveToFirst()) return c.getString(0);
        }
        return fallback;
    }

    public double getDoubleSetting(String key, double fallback) {
        try { return Double.parseDouble(getSetting(key, String.valueOf(fallback))); } catch (Exception e) { return fallback; }
    }

    public void setSetting(String key, String value) { setSetting(getWritableDatabase(), key, value); }
    private void setSetting(SQLiteDatabase db, String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put("key", key); cv.put("value", value);
        db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public long insertParsed(MessageParser.ParsedTransaction tx) {
        if (tx == null) return -1;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("type", tx.type);
        cv.put("status", tx.status);
        cv.put("amount", tx.amount);
        cv.put("currency", tx.currency);
        cv.put("title", tx.title);
        cv.put("merchant", tx.merchant);
        cv.put("category", guessCategory(tx.title + " " + tx.merchant + " " + tx.raw));
        cv.put("source", tx.source);
        cv.put("raw", tx.raw);
        cv.put("dateMillis", tx.dateMillis);
        cv.put("fingerprint", tx.fingerprint);
        cv.put("card", tx.card);
        cv.put("extra", tx.extra);
        cv.put("affectsBudget", tx.affectsBudget);
        cv.put("createdAt", System.currentTimeMillis());
        return db.insertWithOnConflict("transactions", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public long insertManual(String type, String status, double amount, String title, int affectsBudget, String raw) {
        return insertManualCurrency(type, status, amount, title, affectsBudget, raw, getCurrency());
    }

    public long insertManualCurrency(String type, String status, double amount, String title, int affectsBudget, String raw, String currency) {
        return insertManualCurrencyCategory(type, status, amount, title, affectsBudget, raw, currency, null);
    }

    public long insertManualCurrencyCategory(String type, String status, double amount, String title, int affectsBudget, String raw, String currency, String category) {
        MessageParser.ParsedTransaction tx = new MessageParser.ParsedTransaction();
        tx.type = type; tx.status = status; tx.amount = amount; tx.currency = "EGP".equalsIgnoreCase(currency) ? "EGP" : "SAR";
        tx.title = title; tx.merchant = title; tx.source = "manual"; tx.raw = raw;
        tx.dateMillis = System.currentTimeMillis();
        tx.fingerprint = MessageParser.sha256("manual|" + raw + "|" + tx.dateMillis);
        tx.affectsBudget = affectsBudget; tx.card = ""; tx.extra = category == null ? "" : ("category=" + category.trim());
        long id = insertParsed(tx);
        if (id > 0 && category != null && category.trim().length() > 0) {
            ContentValues cv = new ContentValues();
            cv.put("category", category.trim());
            getWritableDatabase().update("transactions", cv, "id=?", new String[]{String.valueOf(id)});
        }
        return id;
    }

    public double getMonthlySpent() {
        long start = monthStart();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE affectsBudget=1 AND status='CONFIRMED' AND dateMillis>=?", new String[]{String.valueOf(start)})) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    public double getExtraIncome() {
        long start = monthStart();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type='EXTRA_INCOME' AND status='CONFIRMED' AND dateMillis>=?", new String[]{String.valueOf(start)})) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    public int getPendingCount() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM transactions WHERE status LIKE 'PENDING%'", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public List<Tx> getPending() {
        return queryTx("SELECT * FROM transactions WHERE status LIKE 'PENDING%' ORDER BY dateMillis DESC", null);
    }

    public List<Tx> getRecent(int limit) {
        return queryTx("SELECT * FROM transactions ORDER BY dateMillis DESC LIMIT " + limit, null);
    }

    public List<Tx> getRecentFiltered(String currency, String category, int limit) {
        String sql = "SELECT * FROM transactions WHERE 1=1";
        List<String> args = new ArrayList<>();
        if (currency != null && currency.trim().length() > 0 && !"ALL".equals(currency)) {
            sql += " AND currency=?";
            args.add(currency.trim());
        }
        if (category != null && category.trim().length() > 0 && !"ALL".equals(category)) {
            sql += " AND category=?";
            args.add(category.trim());
        }
        sql += " ORDER BY dateMillis DESC LIMIT " + limit;
        return queryTx(sql, args.toArray(new String[0]));
    }

    public List<String> getCategories() {
        List<String> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT DISTINCT COALESCE(category,'عام') FROM transactions ORDER BY 1", null)) {
            while (c.moveToNext()) {
                String cat = c.getString(0);
                if (cat != null && cat.trim().length() > 0) list.add(cat);
            }
        }
        return list;
    }

    private List<Tx> queryTx(String sql, String[] args) {
        List<Tx> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(sql, args)) {
            while (c.moveToNext()) list.add(Tx.from(c));
        }
        return list;
    }

    public void approveOnline(long id) {
        ContentValues cv = new ContentValues();
        cv.put("status", "CONFIRMED"); cv.put("affectsBudget", 1); cv.put("type", "ONLINE_PURCHASE_APPROVED");
        getWritableDatabase().update("transactions", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void saveOnly(long id) {
        ContentValues cv = new ContentValues();
        cv.put("status", "SAVED_ONLY"); cv.put("affectsBudget", 0);
        getWritableDatabase().update("transactions", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void markExtraIncome(long id) {
        ContentValues cv = new ContentValues();
        cv.put("status", "CONFIRMED"); cv.put("affectsBudget", 0); cv.put("type", "EXTRA_INCOME");
        getWritableDatabase().update("transactions", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void markDebtPayment(long id) {
        ContentValues cv = new ContentValues();
        cv.put("status", "CONFIRMED"); cv.put("affectsBudget", 0); cv.put("type", "DEBT_PAYMENT");
        getWritableDatabase().update("transactions", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void updateTransactionAmount(long id, double amount) {
        ContentValues cv = new ContentValues(); cv.put("amount", amount);
        getWritableDatabase().update("transactions", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void updateTransaction(long id, double amount, String title, String category, int affectsBudget) {
        ContentValues cv = new ContentValues();
        cv.put("amount", amount);
        cv.put("title", title == null ? "" : title.trim());
        cv.put("merchant", title == null ? "" : title.trim());
        cv.put("category", category == null || category.trim().isEmpty() ? "عام" : category.trim());
        cv.put("affectsBudget", affectsBudget);
        getWritableDatabase().update("transactions", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteTransaction(long id) {
        getWritableDatabase().delete("transactions", "id=?", new String[]{String.valueOf(id)});
    }

    public void adjustMonthlySpentTo(double targetSpent) {
        targetSpent = Math.max(0, targetSpent);
        double current = getMonthlySpent();
        double delta = targetSpent - current;
        if (Math.abs(delta) < 0.01) return;
        String title = delta >= 0 ? "تصحيح زيادة المصروف" : "تصحيح تقليل المصروف";
        insertManual("SPENT_ADJUSTMENT", "CONFIRMED", delta, title, 1, "تعديل إجمالي المصروف إلى " + targetSpent);
    }

    public long addDebt(String name, double amount, String whatsapp, String facebook, String notes, String direction, long dueDateMillis) {
        ContentValues cv = new ContentValues();
        cv.put("name", name); cv.put("amount", amount); cv.put("paid", 0);
        cv.put("whatsapp", whatsapp); cv.put("facebook", facebook); cv.put("notes", notes);
        cv.put("status", "OPEN");
        cv.put("direction", "OWE_TO_OTHERS".equals(direction) ? "OWE_TO_OTHERS" : "OWED_TO_ME");
        cv.put("dueDateMillis", Math.max(0, dueDateMillis));
        cv.put("createdAt", System.currentTimeMillis()); cv.put("updatedAt", System.currentTimeMillis());
        return getWritableDatabase().insert("debts", null, cv);
    }

    public void updateDebtDueDate(long debtId, long dueDateMillis) {
        ContentValues cv = new ContentValues();
        cv.put("dueDateMillis", Math.max(0, dueDateMillis));
        cv.put("updatedAt", System.currentTimeMillis());
        getWritableDatabase().update("debts", cv, "id=?", new String[]{String.valueOf(debtId)});
    }

    public List<Debt> getDebts() {
        List<Debt> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM debts ORDER BY status ASC, dueDateMillis ASC, updatedAt DESC", null)) {
            while (c.moveToNext()) list.add(Debt.from(c));
        }
        return list;
    }

    public Debt getDebtById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM debts WHERE id=?", new String[]{String.valueOf(id)})) {
            if (c.moveToFirst()) return Debt.from(c);
        }
        return null;
    }

    public List<Debt> getDebtsByDirection(String direction) {
        List<Debt> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String dir = "OWE_TO_OTHERS".equals(direction) ? "OWE_TO_OTHERS" : "OWED_TO_ME";
        try (Cursor c = db.rawQuery("SELECT * FROM debts WHERE direction=? ORDER BY status ASC, dueDateMillis ASC, updatedAt DESC", new String[]{dir})) {
            while (c.moveToNext()) list.add(Debt.from(c));
        }
        return list;
    }

    public void addDebtPayment(long debtId, double amount, String note, long txId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues p = new ContentValues();
        p.put("debtId", debtId); p.put("amount", amount); p.put("note", note); p.put("dateMillis", System.currentTimeMillis()); p.put("txId", txId);
        db.insert("debt_payments", null, p);
        try (Cursor c = db.rawQuery("SELECT amount, paid FROM debts WHERE id=?", new String[]{String.valueOf(debtId)})) {
            if (c.moveToFirst()) {
                double total = c.getDouble(0); double paid = c.getDouble(1) + amount;
                ContentValues cv = new ContentValues(); cv.put("paid", paid); cv.put("updatedAt", System.currentTimeMillis());
                cv.put("status", paid >= total ? "PAID" : "PARTIAL");
                db.update("debts", cv, "id=?", new String[]{String.valueOf(debtId)});
            }
        }
    }

    public long addSubscription(String name, double amount, String currency, String merchant, String category, long nextDateMillis, String notes) {
        ContentValues cv = new ContentValues();
        cv.put("name", name == null ? "" : name.trim());
        cv.put("amount", amount);
        cv.put("currency", "EGP".equalsIgnoreCase(currency) ? "EGP" : "SAR");
        cv.put("merchant", merchant == null ? "" : merchant.trim());
        cv.put("category", category == null || category.trim().isEmpty() ? "اشتراكات/أونلاين" : category.trim());
        cv.put("nextDateMillis", Math.max(0, nextDateMillis));
        cv.put("active", 1);
        cv.put("notes", notes == null ? "" : notes.trim());
        cv.put("lastChargedMillis", 0);
        cv.put("createdAt", System.currentTimeMillis());
        cv.put("updatedAt", System.currentTimeMillis());
        return getWritableDatabase().insert("subscriptions", null, cv);
    }

    public List<Subscription> getSubscriptions() {
        List<Subscription> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM subscriptions ORDER BY active DESC, nextDateMillis ASC, updatedAt DESC", null)) {
            while (c.moveToNext()) list.add(Subscription.from(c));
        }
        return list;
    }

    public Subscription getSubscriptionById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM subscriptions WHERE id=?", new String[]{String.valueOf(id)})) {
            if (c.moveToFirst()) return Subscription.from(c);
        }
        return null;
    }

    public void toggleSubscription(long id, boolean active) {
        ContentValues cv = new ContentValues();
        cv.put("active", active ? 1 : 0); cv.put("updatedAt", System.currentTimeMillis());
        getWritableDatabase().update("subscriptions", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public long chargeSubscription(long id) {
        Subscription s = getSubscriptionById(id);
        if (s == null) return -1;
        long tx = insertManualCurrency("SUBSCRIPTION", "CONFIRMED", s.amount, s.name, 1, "اشتراك شهري: " + s.name, s.currency);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(s.nextDateMillis > 0 ? s.nextDateMillis : System.currentTimeMillis());
        cal.add(Calendar.MONTH, 1);
        ContentValues cv = new ContentValues();
        cv.put("lastChargedMillis", System.currentTimeMillis());
        cv.put("nextDateMillis", cal.getTimeInMillis());
        cv.put("updatedAt", System.currentTimeMillis());
        getWritableDatabase().update("subscriptions", cv, "id=?", new String[]{String.valueOf(id)});
        return tx;
    }

    public double getActiveSubscriptionsTotal() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM subscriptions WHERE active=1", null)) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    public double getOpenDebtsTotal() { return getDebtTotal(null); }

    public double getDebtTotal(String direction) {
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT COALESCE(SUM(amount-paid),0) FROM debts WHERE status!='PAID'";
        String[] args = null;
        if (direction != null) { sql += " AND direction=?"; args = new String[]{direction}; }
        try (Cursor c = db.rawQuery(sql, args)) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    public int getUpcomingDebtCount(long untilMillis) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM debts WHERE status!='PAID' AND dueDateMillis>0 AND dueDateMillis<=?", new String[]{String.valueOf(untilMillis)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public double getTodaySpent() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE affectsBudget=1 AND status='CONFIRMED' AND dateMillis>=?", new String[]{String.valueOf(start)})) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    public int getMonthlyExpenseCount() {
        long start = monthStart();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM transactions WHERE affectsBudget=1 AND status='CONFIRMED' AND dateMillis>=?", new String[]{String.valueOf(start)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public double getPendingTotal() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE status LIKE 'PENDING%'", null)) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    public List<CatTotal> getSpendingByCategory(int limit) {
        long start = monthStart();
        List<CatTotal> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COALESCE(category,'عام') AS cat, COALESCE(SUM(amount),0) AS total FROM transactions WHERE affectsBudget=1 AND status='CONFIRMED' AND dateMillis>=? GROUP BY category ORDER BY total DESC LIMIT " + limit, new String[]{String.valueOf(start)})) {
            while (c.moveToNext()) {
                CatTotal ct = new CatTotal();
                ct.category = c.getString(0) == null ? "عام" : c.getString(0);
                ct.total = c.getDouble(1);
                list.add(ct);
            }
        }
        return list;
    }

    private long monthStart() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private String guessCategory(String text) {
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (t.contains("google") || t.contains("netflix") || t.contains("apple") || t.contains("spotify") || t.contains("شاهد")) return "اشتراكات/أونلاين";
        if (t.contains("مطعم") || t.contains("raghif") || t.contains("قهوة") || t.contains("كوفي") || t.contains("اكل") || t.contains("أكل") || t.contains("food") || t.contains("restaurant")) return "أكل ومشروبات";
        if (t.contains("بنزين") || t.contains("وقود") || t.contains("uber") || t.contains("careem") || t.contains("taxi")) return "مواصلات";
        if (t.contains("صيدلية") || t.contains("pharmacy") || t.contains("دواء")) return "صحة";
        if (t.contains("stc") || t.contains("زين") || t.contains("كهرب") || t.contains("مياه") || t.contains("فاتورة")) return "فواتير";
        if (t.contains("حوالة") || t.contains("transfer")) return "تحويلات";
        return "عام";
    }

    private void seedDefaultCategories(SQLiteDatabase db) {
        String[] names = {"أكل ومشروبات", "مواصلات", "فواتير", "اشتراكات/أونلاين", "تحويلات", "صحة", "كاش", "ترفيه", "عام"};
        for (String n : names) {
            ContentValues cv = new ContentValues();
            cv.put("name", n); cv.put("monthlyLimit", 0); cv.put("currency", "SAR"); cv.put("active", 1);
            cv.put("createdAt", System.currentTimeMillis()); cv.put("updatedAt", System.currentTimeMillis());
            db.insertWithOnConflict("budget_categories", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    public long addOrUpdateBudgetCategory(String name, double monthlyLimit) {
        if (name == null || name.trim().isEmpty()) return -1;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name.trim()); cv.put("monthlyLimit", Math.max(0, monthlyLimit)); cv.put("currency", getCurrency()); cv.put("active", 1);
        cv.put("createdAt", System.currentTimeMillis()); cv.put("updatedAt", System.currentTimeMillis());
        return db.insertWithOnConflict("budget_categories", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void deleteBudgetCategory(long id) { getWritableDatabase().delete("budget_categories", "id=?", new String[]{String.valueOf(id)}); }

    public List<BudgetCategory> getBudgetCategories() {
        List<BudgetCategory> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM budget_categories WHERE active=1 ORDER BY name", null)) {
            while (c.moveToNext()) list.add(BudgetCategory.from(c));
        }
        return list;
    }

    public double getCategorySpent(String category) {
        long start = monthStart();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE affectsBudget=1 AND status='CONFIRMED' AND dateMillis>=? AND category=?", new String[]{String.valueOf(start), category})) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    public double getCashBalance() { return getDoubleSetting("cash_balance", 0); }
    public void setCashBalance(double value) { setSetting("cash_balance", String.valueOf(Math.max(0, value))); }
    public void addCash(double amount, String note) {
        if (amount <= 0) return;
        setCashBalance(getCashBalance() + amount);
        insertManualCurrencyCategory("CASH_IN", "CONFIRMED", amount, note == null || note.isEmpty() ? "إضافة كاش" : note, 0, "cash in", getCurrency(), "كاش");
    }
    public void spendCash(double amount, String title, String category) {
        if (amount <= 0) return;
        setCashBalance(Math.max(0, getCashBalance() - amount));
        insertManualCurrencyCategory("CASH_EXPENSE", "CONFIRMED", amount, title == null || title.isEmpty() ? "مصروف كاش" : title, 1, "cash expense", getCurrency(), category == null || category.trim().isEmpty() ? "كاش" : category);
    }

    public double abnormalThreshold() {
        double custom = getDoubleSetting("abnormal_expense_threshold", 0);
        if (custom > 0) return custom;
        double b = getBudget();
        return Math.max(500, b > 0 ? b * 0.35 : 500);
    }
    public boolean isAbnormalExpense(double amount) { return amount >= abnormalThreshold(); }

    public String currentMonthKey() {
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
    }
    public void autoArchiveIfNewMonth() {
        String current = currentMonthKey();
        String last = getSetting("last_open_month", "");
        if (last.length() == 0) { setSetting("last_open_month", current); return; }
        if (!current.equals(last)) {
            closeMonth(last);
            setSetting("last_open_month", current);
        }
    }
    public void closeCurrentMonth() { closeMonth(currentMonthKey()); }
    private void closeMonth(String monthKey) {
        long[] range = monthRange(monthKey);
        double spent = sumTransactions(range[0], range[1], "affectsBudget=1 AND status='CONFIRMED'");
        double income = sumTransactions(range[0], range[1], "type='EXTRA_INCOME' AND status='CONFIRMED'");
        JSONObject summary = new JSONObject();
        try {
            summary.put("monthKey", monthKey); summary.put("currency", getCurrency()); summary.put("closedAt", System.currentTimeMillis());
            JSONArray cats = new JSONArray();
            SQLiteDatabase rdb = getReadableDatabase();
            try (Cursor c = rdb.rawQuery("SELECT COALESCE(category,'عام') AS cat, COALESCE(SUM(amount),0) AS total FROM transactions WHERE affectsBudget=1 AND status='CONFIRMED' AND dateMillis>=? AND dateMillis<? GROUP BY category ORDER BY total DESC", new String[]{String.valueOf(range[0]), String.valueOf(range[1])})) {
                while (c.moveToNext()) { JSONObject o = new JSONObject(); o.put("category", c.getString(0)); o.put("total", c.getDouble(1)); cats.put(o); }
            }
            summary.put("categories", cats);
        } catch (Exception ignored) {}
        ContentValues cv = new ContentValues();
        cv.put("monthKey", monthKey); cv.put("budget", getBudget()); cv.put("spent", spent); cv.put("extraIncome", income); cv.put("cashBalance", getCashBalance());
        cv.put("closedAt", System.currentTimeMillis()); cv.put("summary", summary.toString());
        getWritableDatabase().insertWithOnConflict("month_archives", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }
    private long[] monthRange(String monthKey) {
        Calendar cal = Calendar.getInstance();
        try { Date d = new SimpleDateFormat("yyyy-MM", Locale.US).parse(monthKey); if (d != null) cal.setTime(d); } catch (Exception ignored) {}
        cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis(); cal.add(Calendar.MONTH, 1); return new long[]{start, cal.getTimeInMillis()};
    }
    private double sumTransactions(long start, long end, String where) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE " + where + " AND dateMillis>=? AND dateMillis<?", new String[]{String.valueOf(start), String.valueOf(end)})) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }
    public List<MonthArchive> getMonthArchives() {
        List<MonthArchive> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM month_archives ORDER BY monthKey DESC", null)) { while (c.moveToNext()) list.add(MonthArchive.from(c)); }
        return list;
    }


    public String exportBackupJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", "مصروفاتي");
        root.put("backupVersion", 1);
        root.put("generatedAt", System.currentTimeMillis());

        JSONObject settingsJson = new JSONObject();
        SQLiteDatabase rdb = getReadableDatabase();
        try (Cursor c = rdb.rawQuery("SELECT key,value FROM settings", null)) {
            while (c.moveToNext()) {
                String key = c.getString(0);
                if (key == null) continue;
                if (key.startsWith("firebase_") || key.startsWith("google_")) continue;
                settingsJson.put(key, c.getString(1));
            }
        }
        root.put("settings", settingsJson);
        root.put("transactions", tableToJson("transactions"));
        root.put("debts", tableToJson("debts"));
        root.put("debt_payments", tableToJson("debt_payments"));
        root.put("subscriptions", tableToJson("subscriptions"));
        root.put("budget_categories", tableToJson("budget_categories"));
        root.put("month_archives", tableToJson("month_archives"));
        return root.toString();
    }

    private JSONArray tableToJson(String table) throws Exception {
        JSONArray arr = new JSONArray();
        SQLiteDatabase rdb = getReadableDatabase();
        try (Cursor c = rdb.rawQuery("SELECT * FROM " + table, null)) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                for (int i = 0; i < c.getColumnCount(); i++) {
                    String name = c.getColumnName(i);
                    int type = c.getType(i);
                    if (type == Cursor.FIELD_TYPE_NULL) o.put(name, JSONObject.NULL);
                    else if (type == Cursor.FIELD_TYPE_INTEGER) o.put(name, c.getLong(i));
                    else if (type == Cursor.FIELD_TYPE_FLOAT) o.put(name, c.getDouble(i));
                    else o.put(name, c.getString(i));
                }
                arr.put(o);
            }
        }
        return arr;
    }

    public void importBackupJson(String jsonText) throws Exception {
        JSONObject root = new JSONObject(jsonText);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("transactions", null, null);
            db.delete("debts", null, null);
            db.delete("debt_payments", null, null);
            db.delete("subscriptions", null, null);
            db.delete("budget_categories", null, null);
            db.delete("month_archives", null, null);

            JSONObject settingsJson = root.optJSONObject("settings");
            if (settingsJson != null) {
                JSONArray keys = settingsJson.names();
                if (keys != null) {
                    for (int i = 0; i < keys.length(); i++) {
                        String key = keys.getString(i);
                        if (key.startsWith("firebase_") || key.startsWith("google_")) continue;
                        setSetting(db, key, settingsJson.optString(key, ""));
                    }
                }
            }
            jsonToTable(db, "transactions", root.optJSONArray("transactions"));
            jsonToTable(db, "debts", root.optJSONArray("debts"));
            jsonToTable(db, "debt_payments", root.optJSONArray("debt_payments"));
            jsonToTable(db, "subscriptions", root.optJSONArray("subscriptions"));
            jsonToTable(db, "budget_categories", root.optJSONArray("budget_categories"));
            jsonToTable(db, "month_archives", root.optJSONArray("month_archives"));
            setSetting(db, "last_cloud_restore", String.valueOf(System.currentTimeMillis()));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void jsonToTable(SQLiteDatabase db, String table, JSONArray arr) throws Exception {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            ContentValues cv = new ContentValues();
            JSONArray names = o.names();
            if (names == null) continue;
            for (int n = 0; n < names.length(); n++) {
                String k = names.getString(n);
                Object v = o.opt(k);
                if (v == null || v == JSONObject.NULL) cv.putNull(k);
                else if (v instanceof Integer || v instanceof Long) cv.put(k, ((Number) v).longValue());
                else if (v instanceof Float || v instanceof Double) cv.put(k, ((Number) v).doubleValue());
                else cv.put(k, String.valueOf(v));
            }
            db.insertWithOnConflict(table, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    public static String money(double v) { return String.format(Locale.US, "%.2f ر.س", v); }
    public static String date(long ms) { return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(ms); }

    public static class CatTotal {
        public String category;
        public double total;
    }

    public static class BudgetCategory {
        public long id; public String name; public double monthlyLimit; public String currency; public int active;
        static BudgetCategory from(Cursor c) {
            BudgetCategory b = new BudgetCategory();
            b.id = c.getLong(c.getColumnIndexOrThrow("id"));
            b.name = c.getString(c.getColumnIndexOrThrow("name"));
            b.monthlyLimit = c.getDouble(c.getColumnIndexOrThrow("monthlyLimit"));
            b.currency = c.getString(c.getColumnIndexOrThrow("currency"));
            b.active = c.getInt(c.getColumnIndexOrThrow("active"));
            return b;
        }
    }

    public static class MonthArchive {
        public long id; public String monthKey; public double budget; public double spent; public double extraIncome; public double cashBalance; public long closedAt; public String summary;
        static MonthArchive from(Cursor c) {
            MonthArchive m = new MonthArchive();
            m.id = c.getLong(c.getColumnIndexOrThrow("id"));
            m.monthKey = c.getString(c.getColumnIndexOrThrow("monthKey"));
            m.budget = c.getDouble(c.getColumnIndexOrThrow("budget"));
            m.spent = c.getDouble(c.getColumnIndexOrThrow("spent"));
            m.extraIncome = c.getDouble(c.getColumnIndexOrThrow("extraIncome"));
            m.cashBalance = c.getDouble(c.getColumnIndexOrThrow("cashBalance"));
            m.closedAt = c.getLong(c.getColumnIndexOrThrow("closedAt"));
            m.summary = c.getString(c.getColumnIndexOrThrow("summary"));
            return m;
        }
    }

    public static class Tx {
        public long id; public String type; public String status; public double amount; public String currency; public String title; public String merchant;
        public String category; public String raw; public long dateMillis; public String card; public String extra; public int affectsBudget;
        static Tx from(Cursor c) {
            Tx t = new Tx();
            t.id = c.getLong(c.getColumnIndexOrThrow("id"));
            t.type = c.getString(c.getColumnIndexOrThrow("type"));
            t.status = c.getString(c.getColumnIndexOrThrow("status"));
            t.amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
            int curIndex = c.getColumnIndex("currency");
            t.currency = curIndex >= 0 ? c.getString(curIndex) : "SAR";
            t.title = c.getString(c.getColumnIndexOrThrow("title"));
            t.merchant = c.getString(c.getColumnIndexOrThrow("merchant"));
            t.category = c.getString(c.getColumnIndexOrThrow("category"));
            t.raw = c.getString(c.getColumnIndexOrThrow("raw"));
            t.dateMillis = c.getLong(c.getColumnIndexOrThrow("dateMillis"));
            t.card = c.getString(c.getColumnIndexOrThrow("card"));
            t.extra = c.getString(c.getColumnIndexOrThrow("extra"));
            t.affectsBudget = c.getInt(c.getColumnIndexOrThrow("affectsBudget"));
            return t;
        }
    }

    public static class Subscription {
        public long id; public String name; public double amount; public String currency; public String merchant; public String category; public long nextDateMillis; public int active; public String notes; public long lastChargedMillis;
        static Subscription from(Cursor c) {
            Subscription s = new Subscription();
            s.id = c.getLong(c.getColumnIndexOrThrow("id"));
            s.name = c.getString(c.getColumnIndexOrThrow("name"));
            s.amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
            s.currency = c.getString(c.getColumnIndexOrThrow("currency"));
            s.merchant = c.getString(c.getColumnIndexOrThrow("merchant"));
            s.category = c.getString(c.getColumnIndexOrThrow("category"));
            s.nextDateMillis = c.getLong(c.getColumnIndexOrThrow("nextDateMillis"));
            s.active = c.getInt(c.getColumnIndexOrThrow("active"));
            s.notes = c.getString(c.getColumnIndexOrThrow("notes"));
            s.lastChargedMillis = c.getLong(c.getColumnIndexOrThrow("lastChargedMillis"));
            return s;
        }
    }

    public static class Debt {
        public long id; public String name; public double amount; public double paid; public String whatsapp; public String facebook; public String notes; public String status; public String direction; public long dueDateMillis;
        static Debt from(Cursor c) {
            Debt d = new Debt();
            d.id = c.getLong(c.getColumnIndexOrThrow("id"));
            d.name = c.getString(c.getColumnIndexOrThrow("name"));
            d.amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
            d.paid = c.getDouble(c.getColumnIndexOrThrow("paid"));
            d.whatsapp = c.getString(c.getColumnIndexOrThrow("whatsapp"));
            d.facebook = c.getString(c.getColumnIndexOrThrow("facebook"));
            d.notes = c.getString(c.getColumnIndexOrThrow("notes"));
            d.status = c.getString(c.getColumnIndexOrThrow("status"));
            int dirIndex = c.getColumnIndex("direction");
            d.direction = dirIndex >= 0 ? c.getString(dirIndex) : "OWED_TO_ME";
            int dueIndex = c.getColumnIndex("dueDateMillis");
            d.dueDateMillis = dueIndex >= 0 ? c.getLong(dueIndex) : 0;
            return d;
        }
    }
}
