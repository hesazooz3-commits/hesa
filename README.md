# A7A Cards

تطبيق Android لإدارة كروت الشحن محليًا على الهاتف، بدون سيرفر أو قاعدة بيانات خارجية.

## الشبكات
- Orange: `#102*رقم_الكارت#`
- Vodafone: `*858*رقم_الكارت#`
- WE: `*555*رقم_الكارت#`
- e& Egypt: `*556*رقم_الكارت#`

## المتطلبات
- JDK 17
- Gradle 8.9
- Android Gradle Plugin 8.7.3
- Android SDK 35

## البناء
الأمر المقصود في GitHub Actions هو:

```bash
gradle :app:assembleDebug --no-daemon
```

وليس `gradle assembleDebug`.

المشروع يحتوي على Android Application Module حقيقي في `app/`، وملف `settings.gradle.kts` في الجذر يحتوي على:

```kotlin
include(":app")
```

## GitHub Actions
شغّل workflow باسم **Build A7A Cards APK** من تبويب Actions باستخدام **Run workflow**. سيُرفع ملف `app-debug.apk` كـ artifact باسم `A7A-Cards-APK`.

## ملاحظة عن USSD
زر الشحن يستخدم `ACTION_CALL` مع كود USSD ويطلب `CALL_PHONE` وقت التشغيل عند الحاجة. بعد بدء الشحن، يتم تعليم الكارت كمستخدم، ولا يتم الانتقال تلقائيًا إلى أي كارت آخر ولا يتم الحذف تلقائيًا.
