# تشغيل وبناء تطبيق مصروفاتي من VS Code فقط

أنت لا تحتاج Android Studio لفتح الكود، لكن بناء APK يحتاج واحد من حلين:

## الحل الأسهل بدون تثبيت Android Studio: GitHub Actions

1. افتح GitHub واعمل Repository جديد.
2. ارفع محتويات فولدر `ExpenseGuardApp` بالكامل.
3. ادخل على تبويب `Actions`.
4. افتح Workflow باسم `Build Android APK`.
5. اضغط `Run workflow`.
6. بعد ما يخلص، افتح آخر Run.
7. من أسفل الصفحة ستجد Artifact باسم:
   `ExpenseGuard-debug-apk`
8. حمله، فك الضغط، وستجد ملف APK.
9. انقله للموبايل وثبته.

هذا الحل لا يحتاج Android Studio ولا Android SDK على جهازك.

## الحل المحلي من VS Code

لازم تثبت:

1. Java JDK 17
2. Android SDK Command Line Tools
3. Gradle

بعدها افتح فولدر `ExpenseGuardApp` في VS Code وافتح Terminal وشغل:

```bash
gradle assembleDebug
```

بعد البناء ستجد APK في:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## ملاحظات مهمة

- لو هتبني محليًا من VS Code فقط، لازم يكون Android SDK متسطب ومتضاف في متغير `ANDROID_HOME`.
- لو مش عايز تثبت أدوات كتير، استخدم حل GitHub Actions لأنه هيبني APK على السحابة.
- التطبيق يستخدم صلاحيات SMS والصوت والإشعارات، لذلك عند التثبيت على الموبايل وافق على الصلاحيات.
