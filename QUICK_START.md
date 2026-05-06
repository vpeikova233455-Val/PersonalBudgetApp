# Quick Start Guide

Get your Personal Budget App running in 5 steps!

## Step 1: Install Android Studio
Download and install the latest version from https://developer.android.com/studio

## Step 2: Setup Firebase (REQUIRED)

### A. Create Firebase Project
1. Go to https://console.firebase.google.com/
2. Click "Add project" or use existing project
3. Follow the wizard to create your project

### B. Add Android App
1. In Firebase Console, click "Add app" → Android
2. Enter package name: `com.budgetapp`
3. Download `google-services.json`
4. Move it to: `PersonalBudgetApp/app/google-services.json` (replace the placeholder)

### C. Enable Authentication
1. In Firebase Console, go to "Authentication"
2. Click "Get started"
3. Enable "Email/Password" sign-in method

### D. Enable Firestore (Optional for Phase 1, Required for Phase 2)
1. In Firebase Console, go to "Firestore Database"
2. Click "Create database"
3. Start in test mode (we'll add security rules in Phase 2)

### E. Enable Storage (Optional for Phase 1, Required for Phase 3)
1. In Firebase Console, go to "Storage"
2. Click "Get started"
3. Use default security rules

## Step 3: Open Project in Android Studio
1. Launch Android Studio
2. Click "Open"
3. Navigate to `/Users/valentina.peikova/PersonalBudgetApp`
4. Click "OK"
5. Wait for Gradle sync to complete (may take a few minutes first time)

## Step 4: Run the App
1. Connect an Android device OR start an emulator
   - **Recommended:** Pixel 6 API 34 (Android 14)
   - **Minimum:** API 26 (Android 8.0)
2. Click the green "Run" button (or press Shift+F10)
3. Wait for build to complete
4. App will launch on your device/emulator

## Step 5: Test the App
1. **Sign Up:**
   - Click "Sign Up"
   - Enter email and password (min 6 characters)
   - Click "Sign Up"
   - Check email for verification link

2. **Login:**
   - Click "OK" on verification dialog
   - Enter your credentials
   - Click "Login"

3. **Add Transaction:**
   - Click the floating "+" button
   - Select Income or Expense
   - Enter amount (e.g., 1000)
   - Enter description (e.g., "Salary")
   - Click "Select Category" → Choose one
   - Click "Save Transaction"

4. **View Dashboard:**
   - See your transaction in the list
   - Check income/expense totals
   - Verify balance is correct

## Troubleshooting

### "Failed to resolve: com.google.firebase..."
**Solution:** Make sure you added `google-services.json` to `app/` folder

### "An error occurred while signing in"
**Solution:** Check Firebase Authentication is enabled for Email/Password

### "Cleartext traffic not permitted"
**Solution:** This is expected - app enforces HTTPS. Firebase uses HTTPS by default.

### Build fails with "Duplicate class"
**Solution:** File → Invalidate Caches → Invalidate and Restart

### App crashes on launch
**Solution:**
1. Check `google-services.json` is in the right location
2. Verify package name is `com.budgetapp` in Firebase
3. Check Logcat for error messages

## Quick Feature Overview

### ✅ What Works Now (Phase 1)
- Sign up / Login
- Add income/expense transactions
- Edit/delete transactions
- View dashboard with totals
- Category management
- Everything works offline

### ⏳ Coming Soon (Phase 2+)
- Cloud sync across devices
- AI-powered import
- Budget tracking
- Recurring transactions
- Analytics and charts

## Need Help?

- See `README.md` for detailed documentation
- See `PHASE_1_COMPLETE.md` for feature list
- See `IMPLEMENTATION_STATUS.md` for technical details

## Next Steps After Testing

1. **Phase 2:** Implement cloud sync with Firestore
2. **Phase 3:** Add AI-powered transaction import
3. **Phase 4:** Add budgets, recurring transactions, analytics
4. **Phase 5:** Polish and publish to Play Store

---

**Project Directory:** `/Users/valentina.peikova/PersonalBudgetApp`

Happy coding! 🚀
