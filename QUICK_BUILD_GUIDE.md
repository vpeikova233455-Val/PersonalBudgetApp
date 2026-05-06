# Quick Guide: Build APK for Your Android Phone

## Your project is ready to build!

The signing configuration has been set up and pushed to GitHub. Follow these simple steps to get the APK on your phone.

---

## Prerequisites

**Do you have Android Studio installed?**
- ✅ **Yes** → Skip to Step 2
- ❌ **No** → Download from https://developer.android.com/studio (takes ~10 minutes to install)

---

## Step 1: Open Project in Android Studio

1. Launch Android Studio
2. Click **"Open"** (not "New Project")
3. Navigate to: `/Users/valentina.peikova/PersonalBudgetApp`
4. Click **Open**
5. Wait for Android Studio to sync (progress bar at bottom) - first time takes 2-5 minutes

---

## Step 2: Build Signed APK

### Method A: Using the UI (Easiest - 2 clicks)

1. Click **Build** menu → **Generate Signed Bundle / APK**
2. Select **APK** → Click **Next**
3. Fill in the form:
   ```
   Key store path: /Users/valentina.peikova/PersonalBudgetApp/app/release-key.jks
   Key store password: android123
   Key alias: budget-app-key
   Key password: android123
   ```
4. Click **Next**
5. Select **release** → Click **Finish**
6. Wait ~2 minutes for build to complete
7. Click **"locate"** in the notification that appears
8. Your APK is ready! File: `app-release.apk`

### Method B: Using Terminal in Android Studio

1. Open **Terminal** tab at bottom of Android Studio
2. Run:
   ```bash
   ./gradlew assembleRelease
   ```
3. APK location: `app/build/outputs/apk/release/app-release.apk`

---

## Step 3: Install on Your Android Phone

### Option 1: USB Cable (Fastest)

1. Connect phone to Mac with USB cable
2. On phone: Enable Developer Options
   - Go to **Settings** → **About Phone**
   - Tap **Build Number** 7 times
   - Go back → **Developer Options** → Enable **USB Debugging**
3. On phone: When "Allow USB debugging?" appears → Tap **Allow**
4. In Android Studio: Click **Run** menu → **Run 'app'**
5. Select your phone from the list
6. App installs and launches automatically!

### Option 2: Transfer APK File

1. In Finder, go to: `/Users/valentina.peikova/PersonalBudgetApp/app/build/outputs/apk/release/`
2. Copy `app-release.apk` to your phone via:
   - **AirDrop** (if iPhone) - won't work, need Android phone
   - **Email** - Send to yourself and download on phone
   - **Google Drive** - Upload and download on phone
   - **USB** - Copy to phone's Download folder
3. On phone: Open the APK file
4. Tap **Install** (may need to allow "Install from unknown sources")

---

## Step 4: (Optional) Create GitHub Release

After building the APK, you can create a release on GitHub:

```bash
cd /Users/valentina.peikova/PersonalBudgetApp

# Tag the release
git tag -a v1.0.0 -m "Personal Budget App v1.0.0 - Phase 2 Complete"
git push origin v1.0.0

# Create GitHub release with APK
gh release create v1.0.0 \
  app/build/outputs/apk/release/app-release.apk \
  --title "Personal Budget App v1.0.0" \
  --notes "**Features:**
- User Authentication (Firebase)
- Transaction Management (Add/Edit/Delete)
- Category Management
- Dashboard with Analytics
- Cloud Sync (Offline-First)
- Automatic Background Sync

**What's Included:**
- Phase 1: Foundation ✅
- Phase 2: Cloud Sync ✅
- Phase 3-5: Coming soon

**Installation:**
1. Download app-release.apk
2. Install on Android device
3. Sign up with email/password
4. Start tracking your budget!"
```

---

## Troubleshooting

### "App not installed" on phone
- Enable **"Install unknown apps"** for the app you're using to install (e.g., Chrome, Files)
- Settings → Apps → [App Name] → Install unknown apps → Allow

### Build fails in Android Studio
- Check that you're using JDK 17
- File → Project Structure → SDK Location → JDK location should be JDK 17

### "Unsigned APK" or signing errors
- Double-check the keystore path and passwords
- Make sure file exists: `app/release-key.jks`

---

## What's Next?

After installing and testing the app, you can:
- **Phase 3**: Implement AI Integration (Gemini OCR, smart categorization)
- **Phase 4**: Add recurring transactions, budgets, analytics
- **Phase 5**: Polish and publish to Google Play Store

---

## Quick Summary

✅ **Project Status**: Build-ready, signing configured, pushed to GitHub
✅ **Keystore**: Created and configured
✅ **Next Step**: Open in Android Studio → Build → Generate Signed APK
⏱️ **Time to APK**: ~5 minutes if Android Studio installed
📱 **Compatible with**: Android 8.0 (API 26) and above
🔐 **Signing**: Release keystore configured (android123)

---

**Repository**: https://github.com/vpeikova233455-Val/PersonalBudgetApp

**Need help?** Check BUILD_APK.md for detailed build instructions or refer to Android Studio documentation.
