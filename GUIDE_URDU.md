# 📡 Personal FTP Server — مکمل گائیڈ

## پروجیکٹ کا خاکہ

```
WifiFTPServer/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/personal/ftpserver/
│       │   ├── FTPServer.java      ← اصل سرور (سب سے اہم)
│       │   ├── FTPService.java     ← بیک گراؤنڈ سروس
│       │   └── MainActivity.java   ← UI اور کنٹرول
│       └── res/
│           ├── layout/activity_main.xml
│           ├── values/strings.xml
│           ├── values/themes.xml
│           └── drawable/card_bg.xml, input_bg.xml
├── build.gradle
└── settings.gradle
```

---

## ⚙️ انسٹال کرنے کا طریقہ

### مرحلہ 1 — Android Studio ڈاؤنلوڈ کریں
1. https://developer.android.com/studio پر جائیں
2. Android Studio ڈاؤنلوڈ اور انسٹال کریں

### مرحلہ 2 — پروجیکٹ کھولیں
1. Android Studio کھولیں
2. "Open an existing project" کلک کریں
3. WifiFTPServer فولڈر منتخب کریں
4. Gradle sync ہونے دیں (چند منٹ لگتے ہیں)

### مرحلہ 3 — APK بنائیں
```
Build → Generate Signed Bundle/APK → APK → Next
```
یا debug APK کے لیے:
```
Build → Build Bundle(s)/APK(s) → Build APK(s)
```

---

## 📱 موبائل میں استعمال کا طریقہ

### سرور شروع کرنا
1. ایپ کھولیں
2. **یوزر نیم اور پاسورڈ** سیٹ کریں (یا Anonymous آن کریں)
3. **پورٹ** رہنے دیں: 2121
4. **"▶ سرور شروع کریں"** دبائیں
5. اسکرین پر IP Address دکھے گا جیسے:
   ```
   ftp://192.168.1.100:2121
   ```

---

## 💻 کمپیوٹر سے کنیکٹ کرنا

### طریقہ 1 — Windows Explorer (آسان ترین)
1. Windows Explorer کھولیں (Win + E)
2. Address bar میں ٹائپ کریں:
   ```
   ftp://192.168.1.100:2121
   ```
3. یوزر نیم اور پاسورڈ ڈالیں
4. ✅ آپ کے موبائل کی فائلیں نظر آئیں گی

### طریقہ 2 — FileZilla (بہترین اسپیڈ کے لیے)
1. https://filezilla-project.org سے FileZilla ڈاؤنلوڈ کریں
2. اوپر ان باکسز میں بھریں:
   ```
   Host:     192.168.1.100
   Username: admin
   Password: 1234
   Port:     2121
   ```
3. "Quickconnect" کلک کریں
4. فائلیں Drag & Drop کریں

### طریقہ 3 — Total Commander (Android سے Android)
1. Total Commander انسٹال کریں
2. FTP connection بنائیں اور IP ڈالیں

---

## 📱 دوسرے موبائل سے کنیکٹ (SHAREit جیسا)

### ES File Explorer سے:
1. ES File Explorer کھولیں
2. Network → FTP
3. IP اور پاسورڈ ڈالیں
4. فائلیں ٹرانسفر کریں

### QR Code سے (سب سے تیز):
1. سرور موبائل پر QR کوڈ اسکین کریں
2. دوسرا موبائل فوری کنیکٹ ہو جائے گا

---

## ⚡ اسپیڈ بڑھانے کی ترکیبیں

| طریقہ | فائدہ |
|-------|-------|
| FileZilla میں Transfer → 5 Simultaneous transfers | ملٹی تھریڈ |
| 5GHz WiFi استعمال کریں | 2x اسپیڈ |
| Buffer size 64KB (پہلے سے سیٹ ہے) | تیز ڈیٹا |
| موبائل اور کمپیوٹر قریب رکھیں | بہتر سگنل |
| Resume: فائل رک جائے تو دوبارہ شروع | وقت بچائے |

---

## 🔒 سیکیورٹی ٹپس

- ✅ ہمیشہ پاسورڈ لگائیں
- ✅ گھر کے WiFi پر ہی استعمال کریں
- ❌ پبلک WiFi پر استعمال نہ کریں
- ✅ استعمال کے بعد سرور بند کریں

---

## ❓ عام مسائل اور حل

**مسئلہ:** کنیکٹ نہیں ہو رہا
**حل:** دونوں ڈیوائس ایک ہی WiFi پر ہوں

**مسئلہ:** Permission denied
**حل:** ایپ کو Storage permission دیں (Settings → Apps)

**مسئلہ:** اسپیڈ کم ہے
**حل:** 5GHz WiFi استعمال کریں، FileZilla سے simultaneous transfers بڑھائیں

**مسئلہ:** سرور بند ہو جاتا ہے
**حل:** موبائل کی بیٹری آپٹیمائزیشن ایپ کے لیے بند کریں

---

## 📊 فیچرز کا خلاصہ

| فیچر | تفصیل |
|------|--------|
| Multi-threading | بیک وقت 10 کنیکشن |
| Resume Transfer | رکی ہوئی ڈاؤنلوڈ جاری رکھیں |
| Buffer: 64KB | تیز ڈیٹا ٹرانسفر |
| QR Code | فوری کنیکٹ |
| Password | محفوظ رسائی |
| Speed Monitor | MB/s دکھائے |
| Wake Lock | بیک گراؤنڈ میں چلے |
| TCP NoDelay | تاخیر کم |

---

**نوٹ:** یہ مکمل ذاتی استعمال کے لیے ہے۔
