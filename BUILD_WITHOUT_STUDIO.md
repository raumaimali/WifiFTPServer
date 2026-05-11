# Android Studio کے بغیر APK بنانے کے 3 طریقے

---

## ✅ طریقہ 1 — Gradle Command Line (Windows/Linux/Mac پر)

### ضرورت:
- JDK 17 انسٹال ہو (مفت)
- انٹرنیٹ کنیکشن

### مرحلہ بہ مرحلہ:

#### قدم 1 — JDK 17 انسٹال کریں
```
https://adoptium.net پر جائیں → Temurin 17 ڈاؤنلوڈ کریں → انسٹال کریں
```

#### قدم 2 — Android Command Line Tools ڈاؤنلوڈ کریں
```
https://developer.android.com/studio#command-tools
"Command line tools only" سیکشن سے اپنے OS کا zip ڈاؤنلوڈ کریں
```

#### قدم 3 — SDK انسٹال کریں
**Windows (CMD as Administrator):**
```cmd
set ANDROID_HOME=C:\android-sdk
mkdir %ANDROID_HOME%\cmdline-tools\latest
:: zip کو extract کر کے latest فولڈر میں رکھیں
%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

**Linux/Mac:**
```bash
export ANDROID_HOME=$HOME/android-sdk
mkdir -p $ANDROID_HOME/cmdline-tools/latest
# zip extract کریں latest میں
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

#### قدم 4 — پروجیکٹ میں local.properties بنائیں
```
WifiFTPServer/local.properties فائل بنائیں اور لکھیں:

Windows:
sdk.dir=C\:\\android-sdk

Linux/Mac:
sdk.dir=/home/yourname/android-sdk
```

#### قدم 5 — APK بنائیں
**Windows:**
```cmd
cd WifiFTPServer
gradlew.bat assembleDebug
```

**Linux/Mac:**
```bash
cd WifiFTPServer
chmod +x gradlew
./gradlew assembleDebug
```

#### قدم 6 — APK کہاں ملے گی؟
```
WifiFTPServer/app/build/outputs/apk/debug/app-debug.apk
```
یہ فائل موبائل میں کاپی کریں اور انسٹال کریں!

---

## ✅ طریقہ 2 — GitHub Actions (سب سے آسان — مفت)

### کوئی سافٹ وئیر انسٹال نہیں، موبائل پر بھی کام کرتا ہے!

#### قدم 1 — GitHub اکاؤنٹ بنائیں
```
https://github.com پر مفت اکاؤنٹ بنائیں
```

#### قدم 2 — نئی Repository بنائیں
```
New Repository → نام: WifiFTPServer → Create
```

#### قدم 3 — پروجیکٹ فائلیں اپلوڈ کریں
```
Add file → Upload files → تمام فائلیں drag & drop کریں
```

#### قدم 4 — Workflow فائل بنائیں
Repository میں یہ فائل بنائیں:
```
.github/workflows/build.yml
```

فائل میں یہ لکھیں:
```yaml
name: Build APK

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - name: Checkout code
      uses: actions/checkout@v4
      
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        
    - name: Grant execute permission
      run: chmod +x gradlew
      
    - name: Build Debug APK
      run: ./gradlew assembleDebug
      
    - name: Upload APK
      uses: actions/upload-artifact@v4
      with:
        name: app-debug
        path: app/build/outputs/apk/debug/app-debug.apk
```

#### قدم 5 — APK ڈاؤنلوڈ کریں
```
Actions ٹیب → آخری workflow → Artifacts → app-debug ڈاؤنلوڈ کریں
```

**بس! APK تیار ہے — کچھ بھی انسٹال نہیں کرنا پڑا۔**

---

## ✅ طریقہ 3 — Replit پر Online Build

### کوئی انسٹال نہیں — براؤزر میں سب کچھ

#### قدم 1 — Replit کھولیں
```
https://replit.com پر مفت اکاؤنٹ بنائیں
```

#### قدم 2 — نیا Repl بنائیں
```
Create Repl → Bash template منتخب کریں
```

#### قدم 3 — فائلیں اپلوڈ کریں
```
Files panel → Upload folder → WifiFTPServer فولڈر
```

#### قدم 4 — JDK اور Android SDK انسٹال کریں
```bash
# JDK انسٹال
apt-get install -y openjdk-17-jdk

# Android Command Line Tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-11076708_latest.zip -d $HOME/android-sdk/cmdline-tools
mv $HOME/android-sdk/cmdline-tools/cmdline-tools $HOME/android-sdk/cmdline-tools/latest

export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin

# SDK components
yes | sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

#### قدم 5 — APK بنائیں
```bash
cd WifiFTPServer
echo "sdk.dir=$HOME/android-sdk" > local.properties
chmod +x gradlew
./gradlew assembleDebug
```

#### قدم 6 — APK ڈاؤنلوڈ کریں
```
Files panel → app/build/outputs/apk/debug/app-debug.apk → Download
```

---

## 📱 APK موبائل میں انسٹال کرنا

### قدم 1 — Unknown Sources آن کریں
```
Settings → Security → Unknown Sources → ON کریں
(یا: Settings → Apps → Special app access → Install unknown apps)
```

### قدم 2 — APK فائل ٹرانسفر کریں
```
USB کیبل سے یا WhatsApp سے موبائل میں بھیجیں
```

### قدم 3 — انسٹال کریں
```
File Manager میں APK تلاش کریں → ٹیپ کریں → Install
```

---

## 📊 موازنہ

| طریقہ | آسانی | وقت | کوئی انسٹال? |
|-------|--------|------|--------------|
| Gradle CLI | ★★★ | 30 منٹ | JDK ضروری |
| GitHub Actions | ★★★★★ | 10 منٹ | کچھ نہیں |
| Replit | ★★★★ | 20 منٹ | کچھ نہیں |

**سفارش: GitHub Actions سب سے آسان ہے۔**

---

## ❓ عام مسائل

**"gradlew: Permission denied"**
```bash
chmod +x gradlew
```

**"SDK location not found"**
```
local.properties میں sdk.dir صحیح لکھیں
```

**"Build failed - API level"**
```
build.gradle میں compileSdk کو 33 کر دیں
```
