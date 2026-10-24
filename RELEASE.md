# Releasing Wealth Manager on Google Play

Do these in order. Ticks you can do today are marked **(now)**.

## 1. Signing key (now)
You were given three files: `wealth-manager-upload.jks`, `wealth-manager-upload.jks.base64.txt`, `SIGNING-KEY-KEEP-SAFE.txt`.
1. Save all three somewhere private (Google Drive **and** a pen drive). Never commit them to GitHub; the repo is public.
2. GitHub → this repo → Settings → Secrets and variables → Actions → **New repository secret**, four times:
   | Name | Value |
   |---|---|
   | `WM_KEYSTORE_BASE64` | everything inside `wealth-manager-upload.jks.base64.txt` |
   | `WM_KEYSTORE_PASSWORD` | the password in `SIGNING-KEY-KEEP-SAFE.txt` |
   | `WM_KEY_ALIAS` | `upload` |
   | `WM_KEY_PASSWORD` | the same password |
3. Actions tab → **build-release** → Run workflow. When it finishes, download **wealth-manager-release**: it has the `.aab` for Play, a signed `.apk` and `mapping.txt`.

Google Play keeps the real app-signing key ("Play App Signing", on by default). Yours is only the upload key, so if it is ever lost, Play support can reset it.

## 2. Firebase: Google sign-in and accounts (now, about 20 minutes)
1. https://console.firebase.google.com → **Add project** → name "Wealth Manager" → you can switch Google Analytics **off** (the app doesn't use it).
2. **Build → Authentication → Get started → Sign-in method → Google → Enable**, choose your support email, Save.
3. **Build → Firestore Database → Create database** → location **asia-south1 (Mumbai)** → start in production mode.
   Then the **Rules** tab: paste everything from `firestore.rules` in this repo → **Publish**.
4. **Project settings (gear) → Your apps → Add app → Android**, twice:
   * Package `in.wealthmanager.app` (the Play app). Add these SHA-1 fingerprints:
     * Upload key: `00:7A:97:6B:26:11:C8:E5:F9:04:95:22:D4:13:7E:75:09:40:AF:B7`
     * Play app-signing key: after your first upload, copy it from Play Console → Test and release → App integrity → App signing → SHA-1, and add it here too. **Without it, sign-in fails in the copy installed from Play.**
   * Package `in.wealthmanager.app.debug` (test builds). SHA-1: `FF:AC:82:55:E6:76:49:E2:6E:DE:9C:19:35:D9:2A:14:31:27:1F:60`
5. Download **google-services.json** (after adding both apps and the SHA-1s, download it again so it has everything) and upload it to the repo as `app/google-services.json` (GitHub → app folder → Add file → Upload files → Commit).
   It is safe in a public repo (Google treats these keys as public; the Firestore rules protect the data). If you prefer, store its contents as a secret named `WM_GOOGLE_SERVICES_JSON` instead.
6. Optional but good: Google Cloud console → APIs & Services → Credentials → the "Android key" → restrict it to the two package names + SHA-1s.
7. Install the next test APK from GitHub Actions and try "Continue with Google". The release workflow refuses to build without this file.

## 3. Privacy policy and delete-account pages (now)
1. In `docs/privacy-policy.html` replace `DEVELOPER_NAME` with your name and every `SUPPORT_EMAIL` with your support email; same for `SUPPORT_EMAIL` in `docs/delete-account.html`.
2. GitHub → Settings → Pages → Source: **Deploy from a branch**, branch `main`, folder `/docs` → Save.
3. Live in a minute at
   * https://infinityinketan-sketch.github.io/Wealth-manager-/privacy-policy.html
   * https://infinityinketan-sketch.github.io/Wealth-manager-/delete-account.html (Play asks for this in Data safety)

## 4. Play Console account
1. https://play.google.com/console → pay the one-time US$25.
2. **Personal** account: needs ID verification and the closed test in step 7.
   **Organization** account: needs a D-U-N-S number (a sole proprietorship works) but skips the 12-tester rule.
3. Set up a **payments profile** (Settings → Payments profile) so you can sell Pro. Add bank account for payouts; Google keeps 15%.

## 5. Create the app
Create app → name "Wealth Manager: SMS Expenses", default language English (India), App, Free, accept declarations.
Free with in-app purchase is correct: you cannot make a free app paid later, but in-app products are fine.

## 6. First upload (internal testing)
1. Testing → Internal testing → Create release → upload the `.aab` from step 1. Release notes are in `STORE_LISTING.md`.
2. Add your own Gmail as an internal tester and install from the opt-in link. **Billing only works in a copy installed from Play.**
3. Settings → **License testing** → add your Gmail, so test purchases are free.

## 7. Create the Pro product
Monetize → Products → In-app products → Create:
* Product ID: `wealth_manager_pro` (must be exactly this)
* Name: Wealth Manager Pro · Description: One-time unlock of all Pro features
* Price: ₹149 (let Play convert for other countries, or limit to India)
* Activate.
Then in the Play-installed app: More → Wealth Manager Pro → See Pro → Get Pro (or wait for the trial to end: the unlock screen appears). With license testing it says "test card, always approves". Also try "Restore purchase" after reinstalling.

## 8. Store listing and App content
Fill everything from `STORE_LISTING.md`: listing text, icon and feature graphic from `store/`, phone screenshots, and every App content form (Privacy policy, Ads, App access, Content rating, Target audience, Data safety, Financial features, **SMS permissions declaration with the video**).

## 9. Closed test (personal accounts only)
Testing → Closed testing → create a track, upload the same `.aab`, add **at least 12 real testers** (their Gmail addresses or a Google Group) who stay opted in for **14 days in a row**. Friends, family, colleagues; they just need to install and open it.
After 14 days: Dashboard → **Apply for production** and answer the questions about the test.

## 10. Production
Production → Create release → the `.aab` → roll out (start at 20% if you like). Review usually takes a few days; SMS-permission apps can take longer.

## Every update after that
1. In `app/build.gradle.kts` raise `versionCode` by 1 (and `versionName`, e.g. 1.0.1).
2. Push, run **build-release**, upload the new `.aab`.

## Moving your own data to the Play version
The Play app (`in.wealthmanager.app`) is a different app from the test builds you installed before, so it starts empty.
In the old app: More → Back up now. In the Play app: More → Restore. Then uninstall the old one.

## How the trial and lock work (for reference)
* First sign-in with a Google account starts its 30-day trial on the server (Mumbai). Every feature is open during the trial.
* The trial belongs to the Google account: reinstalling, a new phone, or deleting and re-creating the account does not restart it.
* After 30 days without the ₹149 unlock, the whole app is locked. The unlock screen offers only: Unlock, Restore purchase, Sign out, Delete account, Privacy policy. New bank SMS are still noted in the background, so nothing is missing after unlocking.
* Testing the lock yourself: Firebase console → Firestore → `trials` → your document → change `start` to a date over 30 days ago, then reopen the app while online.

## Looking at your users
Firebase console → Firestore Database → `users`: one document per person with name, email, createdAt, lastOpenAt, appVersion, androidVersion, deviceModel, pro, and (only if they agreed) `usage` counts such as app_open, sort_screen, category_open, backup.
Use this data only for the purposes in the privacy policy (running accounts, support, improving the app). Emailing people marketing needs separate consent.
