# Wealth Manager: stage 1, core ledger

Android expense and income tracker. Everything stays on the phone: no internet permission, no SMS permission, no account.

## What is in this stage
- Income and expense entries with categories, accounts (cash, bank, UPI, card) and notes
- Rupee amounts with lakh/crore grouping (stored as paise, so no rounding errors)
- Monthly view with a custom start day (for example salary day), summary, per-category spending
- Monthly budgets, overall and per category, with a bar that turns red when exceeded
- Repeating entries (weekly, monthly, yearly), created at app start and once a day in the background
- Editable categories; deleting one keeps its entries as "Uncategorised"
- App lock with fingerprint, face or screen lock (re-locks when the app goes to the background)

Not in this stage: SMS capture, purpose tagging, statement upload, CSV export and backup, assets. See the build plan in the planning doc.

## Get the app without Android Studio
1. On GitHub, open the **Actions** tab, choose **build-apk**, click **Run workflow** (a build also starts on every push).
2. When the run finishes (about 5 to 10 minutes), open it and download **wealth-manager-debug-apk** under Artifacts.
3. Unzip it, send `app-debug.apk` to your phone and tap it to install.
If the run shows a red cross, the error lines are listed on the run page; copy them to Claude.

## Open and run in Android Studio (optional)
1. Install Android Studio (a recent release) and open this folder.
2. Let Gradle sync (needs internet the first time).
3. Run on a phone or emulator (Android 8.0 / API 26 or newer).

Command line: `./gradlew assembleDebug` and `./gradlew test`.

## Before the first Play Store upload
- Change `applicationId` in `app/build.gradle.kts` (it can never be changed after upload) and the `namespace` / package name if you want them to match.
- Replace the placeholder launcher icon. The app name shown on the phone is "Wealth Manager" (see `app/src/main/res/values/strings.xml`). Internal code names still say "ledger"; they can be renamed with the package name.

## Status of this code
The plain-Kotlin logic in `core/` (money formatting and parsing, budget months, recurrence dates) was compiled and its 23 unit tests were run. The Android parts (database, screens, lock) were written without being compiled, because the build environment could not reach Google's Maven repository. Expect a few compile errors or warnings on the first build; they should be small and quick to fix.
