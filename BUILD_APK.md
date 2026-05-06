# Building Release APK

## Method 1: Using Android Studio (Recommended)

This is the easiest and most reliable method:

### Steps:

1. **Open Project in Android Studio**
   - Launch Android Studio
   - Open `/Users/valentina.peikova/PersonalBudgetApp`
   - Wait for Gradle sync to complete

2. **Configure Firebase**
   - Replace `app/google-services.json` with your Firebase config
   - See `QUICK_START.md` for Firebase setup

3. **Build Release APK**
   - In Android Studio, go to: **Build → Generate Signed Bundle / APK**
   - Select: **APK**
   - Click **Next**

4. **Sign the APK**
   - Key store path: `app/release-key.jks`
   - Key store password: `android123`
   - Key alias: `budget-app-key`
   - Key password: `android123`
   - Click **Next**

5. **Select Build Variant**
   - Choose: **release**
   - Click **Finish**

6. **Get Your APK**
   - APK will be created at: `app/release/app-release.apk`
   - Transfer to your phone and install!

---

## Method 2: Command Line (Advanced)

If you have Android SDK properly configured:

### Prerequisites:
- Android SDK installed
- ANDROID_HOME environment variable set
- Java 17 installed

### Steps:

```bash
cd /Users/valentina.peikova/PersonalBudgetApp

# Clean previous builds
./gradlew clean

# Build release APK
./gradlew assembleRelease

# APK location
# app/build/outputs/apk/release/app-release.apk
```

---

## Method 3: Quick Build Script

Create and run this script:

```bash
#!/bin/bash
cd /Users/valentina.peikova/PersonalBudgetApp

# Set Android SDK path (update this to your SDK location)
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools

# Build
./gradlew clean assembleRelease

# Copy APK to desktop
cp app/build/outputs/apk/release/app-release.apk ~/Desktop/PersonalBudgetApp.apk

echo "✅ APK created at: ~/Desktop/PersonalBudgetApp.apk"
```

---

## Installing on Your Phone

### Option 1: USB Transfer
1. Connect phone to computer via USB
2. Copy APK to phone's Download folder
3. On phone, open Files app
4. Navigate to Downloads
5. Tap the APK file
6. Allow "Install from Unknown Sources" if prompted
7. Install the app

### Option 2: Cloud Transfer
1. Upload APK to Google Drive / Dropbox
2. Open link on your phone
3. Download the APK
4. Install as above

### Option 3: Direct Download
1. Upload APK to GitHub Release
2. Open GitHub on your phone
3. Download directly from release
4. Install

---

## Troubleshooting

### "ANDROID_HOME not set"
```bash
# For macOS (add to ~/.zshrc):
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools
```

### "SDK location not found"
- Create `local.properties` in project root:
```
sdk.dir=/Users/valentina.peikova/Library/Android/sdk
```

### "Gradle version incompatible"
- Use Android Studio's Gradle (recommended)
- Or downgrade system Gradle to 8.4:
```bash
brew unlink gradle
brew install gradle@8.4
brew link gradle@8.4
```

### "Build failed"
1. In Android Studio: File → Invalidate Caches → Invalidate and Restart
2. Try building again

---

## APK Information

**App Details:**
- Package: `com.budgetapp`
- Version: 1.0.0 (1)
- Min SDK: 26 (Android 8.0)
- Target SDK: 34 (Android 14)
- Signing: Debug keystore (for testing)

**APK Size:** ~20-30 MB (estimated)

**Permissions Required:**
- Internet (for Firebase sync)
- Network State (for offline detection)
- Read Media Images (for screenshot upload - Phase 3)

---

## Next Steps After Installing

1. **Launch the app**
2. **Sign up** with your email
3. **Verify email** (check inbox)
4. **Login** and start using!

The app works completely offline, so you can use it immediately even without internet.

---

## Creating GitHub Release

Once you have the APK:

```bash
# Create release tag
git tag -a v1.0.0 -m "Release v1.0.0 - Phase 1 & 2 Complete"
git push origin v1.0.0

# Create GitHub release with APK
gh release create v1.0.0 \
  app/build/outputs/apk/release/app-release.apk \
  --title "Personal Budget App v1.0.0" \
  --notes "First release with offline-first architecture and cloud sync"
```

Or use GitHub web interface:
1. Go to: https://github.com/vpeikova233455-Val/PersonalBudgetApp/releases/new
2. Tag: `v1.0.0`
3. Title: `Personal Budget App v1.0.0`
4. Upload APK file
5. Publish release

---

**Recommended:** Use Android Studio (Method 1) for the smoothest experience!
