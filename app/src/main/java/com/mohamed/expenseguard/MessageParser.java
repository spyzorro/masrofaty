package com.mohamed.expenseguard;

import java.security.MessageDigest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageParser {
    public static class ParsedTransaction {
        public String type;
        public String status;
        public double amount;
        public String currency = "SAR";
        public String title;
        public String merchant;
        public String card;
        public String source = "bank_message";
        public String raw;
        public long dateMillis;
        public String fingerprint;
        public int affectsBudget;
        public String extra;
    }

    public static ParsedTransaction parseBankMessage(String rawInput) {
        if (rawInput == null) return null;
        String raw = clean(rawInput);
        String lower = raw.toLowerCase(Locale.ROOT);

        if (isOtpOrIrrelevant(lower)) return null;

        ParsedTransaction tx = new ParsedTransaction();
        tx.raw = raw;
        tx.dateMillis = parseDateMillis(raw);
        tx.fingerprint = sha256(normalizeForFingerprint(raw));
        tx.card = findCard(raw);
        tx.extra = "";

        if (raw.contains("شراء انترنت") || raw.contains("شراء إنترنت") || raw.contains("اجمالي المبلغ المستحق") || raw.contains("إجمالي المبلغ المستحق")) {
            tx.type = "ONLINE_PURCHASE";
            tx.status = "PENDING_ONLINE";
            tx.amount = firstPositive(
                    findAmount(raw, "اجمالي\\s*المبلغ\\s*المستحق\\s*[:：]?\\s*([0-9]+(?:[\\.,][0-9]+)?)\\s*(?:SAR|SR)"),
                    findAmount(raw, "إجمالي\\s*المبلغ\\s*المستحق\\s*[:：]?\\s*([0-9]+(?:[\\.,][0-9]+)?)\\s*(?:SAR|SR)"),
                    findAmount(raw, "\\(([0-9]+(?:[\\.,][0-9]+)?)\\s*ريال\\)")
            );
            tx.merchant = findAfter(raw, "لدى[:：]", "\n");
            if (tx.merchant.length() == 0) tx.merchant = "شراء إنترنت";
            tx.title = "شراء أونلاين - " + tx.merchant;
            tx.affectsBudget = 0;
            tx.extra = findOriginalCurrency(raw);
            return tx.amount > 0 ? tx : null;
        }

        if (raw.contains("حوالة داخلية واردة") || raw.contains("واردة")) {
            tx.type = "INCOMING_TRANSFER";
            tx.status = "PENDING_INCOMING";
            tx.amount = firstPositive(
                    findAmount(raw, "واردة\\s*بـ?\\s*(?:SR|SAR|ريال)\\s*([0-9]+(?:[\\.,][0-9]+)?)"),
                    findAmount(raw, "بـ?\\s*(?:SR|SAR|ريال)\\s*([0-9]+(?:[\\.,][0-9]+)?)")
            );
            tx.merchant = findTransferName(raw, "من");
            if (tx.merchant.length() == 0) tx.merchant = "حوالة واردة";
            tx.title = "وارد للمراجعة - " + tx.merchant;
            tx.affectsBudget = 0;
            return tx.amount > 0 ? tx : null;
        }

        if (raw.contains("حوالة داخلية صادرة") || raw.contains("صادرة")) {
            tx.type = "OUTGOING_TRANSFER";
            tx.status = "CONFIRMED";
            tx.amount = firstPositive(
                    findAmount(raw, "صادرة\\s*بـ?\\s*(?:SR|SAR|ريال)\\s*([0-9]+(?:[\\.,][0-9]+)?)"),
                    findAmount(raw, "بـ?\\s*(?:SR|SAR|ريال)\\s*([0-9]+(?:[\\.,][0-9]+)?)")
            );
            tx.merchant = findTransferName(raw, "لـ");
            if (tx.merchant.length() == 0) tx.merchant = "حوالة صادرة";
            tx.title = "حوالة صادرة - " + tx.merchant;
            tx.affectsBudget = 1;
            return tx.amount > 0 ? tx : null;
        }

        if (raw.contains("شراء PoS") || raw.contains("شراء POS") || raw.contains("مدى باي") || raw.contains("مدى-مدى باي")) {
            tx.type = "POS_PURCHASE";
            tx.status = "CONFIRMED";
            tx.amount = firstPositive(
                    findAmount(raw, "بـ?\\s*(?:SAR|SR|ريال)\\s*([0-9]+(?:[\\.,][0-9]+)?)"),
                    findAmount(raw, "(?:SAR|SR|ريال)\\s*([0-9]+(?:[\\.,][0-9]+)?)")
            );
            tx.merchant = findAfter(raw, "لـ", "\n");
            if (tx.merchant.length() == 0) tx.merchant = "شراء كارت";
            tx.title = "شراء PoS - " + tx.merchant;
            tx.affectsBudget = 1;
            return tx.amount > 0 ? tx : null;
        }

        if (raw.contains("سحب نقدي") || raw.contains("ATM")) {
            tx.type = "CASH_WITHDRAWAL";
            tx.status = "CONFIRMED";
            tx.amount = findAmount(raw, "(?:SAR|SR|ريال)\\s*([0-9]+(?:[\\.,][0-9]+)?)");
            tx.merchant = "سحب نقدي";
            tx.title = "سحب نقدي";
            tx.affectsBudget = 1;
            return tx.amount > 0 ? tx : null;
        }

        return null;
    }

    public static ParsedTransaction parseManualText(String text, double currentBudget) {
        String raw = clean(text);
        if (raw.trim().isEmpty()) return null;
        double amount = findAnyAmount(raw);
        if (amount <= 0) return null;

        ParsedTransaction tx = new ParsedTransaction();
        tx.raw = raw;
        tx.amount = amount;
        tx.currency = "SAR";
        tx.dateMillis = System.currentTimeMillis();
        tx.fingerprint = sha256("manual|" + raw + "|" + tx.dateMillis);
        tx.source = "manual_or_voice";
        tx.card = "";
        tx.merchant = removeAmountWords(raw);
        tx.extra = "";

        if (containsAny(raw, "زود الميزانية", "زيادة الميزانية", "زود", "ضيف للميزانية")) {
            tx.type = "BUDGET_INCREASE";
            tx.status = "CONFIRMED";
            tx.title = "زيادة الميزانية";
            tx.affectsBudget = 0;
        } else if (containsAny(raw, "انقص الميزانية", "قلل الميزانية", "نقص الميزانية")) {
            tx.type = "BUDGET_DECREASE";
            tx.status = "CONFIRMED";
            tx.title = "إنقاص الميزانية";
            tx.affectsBudget = 0;
        } else if (containsAny(raw, "دخل", "وارد", "استرداد", "رجع", "راتب")) {
            tx.type = "EXTRA_INCOME";
            tx.status = "CONFIRMED";
            tx.title = "دخل إضافي - " + tx.merchant;
            tx.affectsBudget = 0;
        } else {
            tx.type = "MANUAL_EXPENSE";
            tx.status = "CONFIRMED";
            tx.title = tx.merchant.length() == 0 ? "مصروف يدوي" : tx.merchant;
            tx.affectsBudget = 1;
        }
        return tx;
    }

    private static String clean(String s) {
        return s.replace("\u061C", "").replace("\u200F", "").replace("\u200E", "").trim();
    }

    private static boolean isOtpOrIrrelevant(String s) {
        return containsAny(s, "otp", "رمز التحقق", "كود التحقق", "كلمة المرور المؤقتة")
                && !containsAny(s, "شراء", "حوالة", "سحب", "إيداع", "ايداع");
    }

    private static boolean containsAny(String s, String... keys) {
        String lower = s.toLowerCase(Locale.ROOT);
        for (String k : keys) if (lower.contains(k.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static double firstPositive(double... nums) {
        for (double d : nums) if (d > 0) return d;
        return 0;
    }

    private static double findAmount(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);
        if (m.find()) return parseDouble(m.group(1));
        return 0;
    }

    private static double findAnyAmount(String text) {
        Matcher m = Pattern.compile("([0-9]+(?:[\\.,][0-9]+)?)").matcher(text);
        if (m.find()) return parseDouble(m.group(1));
        return 0;
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s.replace(",", ".")); } catch (Exception e) { return 0; }
    }

    private static String findAfter(String text, String markerRegex, String end) {
        Matcher m = Pattern.compile(markerRegex + "\\s*([^\\n]+)").matcher(text);
        if (m.find()) return m.group(1).trim();
        return "";
    }

    private static String findTransferName(String text, String directionMarker) {
        Matcher m = Pattern.compile(Pattern.quote(directionMarker) + "\\s*([0-9]{2,})?\\s*;?\\s*([^\\n]+)").matcher(text);
        if (m.find()) return m.group(2).trim();
        return "";
    }

    private static String findCard(String text) {
        Matcher m = Pattern.compile("(?:عبر|بطاقة|من|لـ)[:：]?\\s*([0-9]{4})").matcher(text);
        if (m.find()) return m.group(1);
        return "";
    }

    private static String findOriginalCurrency(String text) {
        Matcher m = Pattern.compile("مبلغ[:：]?\\s*([0-9]+(?:[\\.,][0-9]+)?)\\s*([A-Z]{3})").matcher(text);
        if (m.find()) return "الأصل: " + m.group(1) + " " + m.group(2);
        return "";
    }

    private static long parseDateMillis(String text) {
        Matcher m = Pattern.compile("([0-9]{1,2}/[0-9]{1,2}/[0-9]{2,4})\\s+([0-9]{1,2}:[0-9]{2})").matcher(text);
        if (m.find()) {
            String date = m.group(1) + " " + m.group(2);
            String[] formats = {"d/M/yy HH:mm", "d/M/yyyy HH:mm"};
            for (String f : formats) {
                try {
                    Date d = new SimpleDateFormat(f, Locale.US).parse(date);
                    if (d != null) return d.getTime();
                } catch (ParseException ignored) {}
            }
        }
        return System.currentTimeMillis();
    }

    private static String removeAmountWords(String raw) {
        return raw.replaceAll("[0-9]+(?:[\\.,][0-9]+)?", "")
                .replace("ريال", "")
                .replace("SAR", "")
                .replace("SR", "")
                .replace("صرفت", "")
                .replace("خصم", "")
                .replace("زود", "")
                .replace("انقص", "")
                .trim();
    }

    private static String normalizeForFingerprint(String raw) {
        return raw.replaceAll("\\s+", " ").trim();
    }

    public static String sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) { return String.valueOf(base.hashCode()); }
    }
}
