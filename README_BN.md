# Daily Schedule — Android project (প্রাথমিক সংস্করণ)

এই প্রজেক্টটি Kotlin + Jetpack Compose + Room দিয়ে তৈরি একটি অফলাইন-প্রথম Android অ্যাপের starter source।

## কী আছে
- Room database-এ রুটিন ও দৈনিক completion সংরক্ষণ
- কাজ যোগ করা, শুরুর সময় নির্ধারণ, সময়কাল ও রিমাইন্ডার
- কাজ সম্পন্ন করার টিক চিহ্ন
- আজকের progress ও প্রাথমিক রিপোর্ট
- DataStore-এ ডার্ক মোড সেটিং
- Android document picker দিয়ে JSON backup/restore
- reboot-এর পরে রিমাইন্ডার পুনরায় schedule করার receiver
- Android 9 (API 28) minimum

## কী এখনো উন্নত করা দরকার
এটি প্রথম কার্যকর ভিত্তি, production release নয়। এই সংস্করণে:
- weekly/monthly report নেই; Reports পেজে আজকের হিসাব দেখায়
- দৈনিক পুনরাবৃত্তির advanced rules, overlapping schedule warning, edit task, snooze ও notification action নেই
- reboot receiver কাজগুলো পুনরায় schedule করে, কিন্তু Android/ফোনের battery policy exact delivery বিলম্বিত করতে পারে
- JSON restore বর্তমান ডেটা replace করে; restore করার আগে backup করে নাও
- restore-এর সময় task ID-গুলো নতুন করে তৈরি হয় এবং completion-এর ID mapping করা হয়; তবুও বাস্তব ডেটা দিয়ে backup/restore পরীক্ষা করে নেবে।
- API 31+ exact alarm permission ব্যবহারকারীর সেটিংসের ওপর নির্ভরশীল হতে পারে

## Android ফোনে build করার পথ
শুধু ফোনে কাজ করতে চাইলে AndroidIDE-এর মতো Android development IDE ব্যবহার করে ZIP extract করে project folder open/import করার চেষ্টা করতে পারো। Gradle sync-এর জন্য প্রথমবার ইন্টারনেট লাগবে; build হয়ে গেলে অ্যাপের দৈনন্দিন ব্যবহার সম্পূর্ণ offline হতে পারে। AndroidIDE-এর বর্তমান compatibility ও Gradle/JDK support আগে যাচাই করো।

## Build
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21
- Gradle 8.9 বা Android Gradle Plugin-এর সঙ্গে compatible wrapper
- JDK 17
- SDK Platform 35

Android Studio/IDE-তে project open করে Gradle sync করো, তারপর `app` > `assembleDebug` চালাও। তৈরি হওয়া debug APK সাধারণত `app/build/outputs/apk/debug/app-debug.apk`-এ থাকবে।

## প্রথম চালুর পর
1. notification permission চাইলে allow করো।
2. একটি test task যোগ করো, সময় এখন থেকে 2–3 মিনিট পর রাখো।
3. ফোনে notification দেখায় কি না পরীক্ষা করো।
4. Settings থেকে JSON backup তৈরি করে আবার restore পরীক্ষা করো।
5. APK install করার আগে Android-এর “Install unknown apps” অনুমতি প্রয়োজন হতে পারে।

এই source কোনো APK তৈরি হয়েছে বলে দাবি করছে না; এটি APK build করার জন্য project source।
