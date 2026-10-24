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

## 2. Privacy policy page (now)
1. In `docs/privacy-policy.html`, replace both `SUPPORT_EMAIL` with the email you want users to write to.
2. GitHub → Settings → Pages → Source: **Deploy from a branch**, branch `main`, folder `/docs` → Save.
3. After a minute it is live at https://infinityinketan-sketch.github.io/Wealth-manager-/privacy-policy.html
   (GitHub Pages on a free account needs the repo to stay public.)

## 3. Play Console account
1. https://play.google.com/console → pay the one-time US$25.
2. **Personal** account: needs ID verification and the closed test in step 7.
   **Organization** account: needs a D-U-N-S number (a sole proprietorship works) but skips the 12-tester rule.
3. Set up a **payments profile** (Settings → Payments profile) so you can sell Pro. Add bank account for payouts; Google keeps 15%.

## 4. Create the app
Create app → name "Wealth Manager: SMS Expenses", default language English (India), App, Free, accept declarations.
Free with in-app purchase is correct: you cannot make a free app paid later, but in-app products are fine.

## 5. First upload (internal testing)
1. Testing → Internal testing → Create release → upload the `.aab` from step 1. Release notes are in `STORE_LISTING.md`.
2. Add your own Gmail as an internal tester and install from the opt-in link. **Billing only works in a copy installed from Play.**
3. Settings → **License testing** → add your Gmail, so test purchases are free.

## 6. Create the Pro product
Monetize → Products → In-app products → Create:
* Product ID: `wealth_manager_pro` (must be exactly this)
* Name: Wealth Manager Pro · Description: One-time unlock of all Pro features
* Price: ₹149 (let Play convert for other countries, or limit to India)
* Activate.
Then in the Play-installed app: More → Wealth Manager Pro → See Pro → Get Pro. With license testing it says "test card, always approves". Also try "Restore purchase" after reinstalling.

## 7. Store listing and App content
Fill everything from `STORE_LISTING.md`: listing text, icon and feature graphic from `store/`, phone screenshots, and every App content form (Privacy policy, Ads, App access, Content rating, Target audience, Data safety, Financial features, **SMS permissions declaration with the video**).

## 8. Closed test (personal accounts only)
Testing → Closed testing → create a track, upload the same `.aab`, add **at least 12 real testers** (their Gmail addresses or a Google Group) who stay opted in for **14 days in a row**. Friends, family, colleagues; they just need to install and open it.
After 14 days: Dashboard → **Apply for production** and answer the questions about the test.

## 9. Production
Production → Create release → the `.aab` → roll out (start at 20% if you like). Review usually takes a few days; SMS-permission apps can take longer.

## Every update after that
1. In `app/build.gradle.kts` raise `versionCode` by 1 (and `versionName`, e.g. 1.0.1).
2. Push, run **build-release**, upload the new `.aab`.

## Moving your own data to the Play version
The Play app (`in.wealthmanager.app`) is a different app from the test builds you installed before, so it starts empty.
In the old app: More → Back up now. In the Play app: More → Restore. Then uninstall the old one.

## What Pro unlocks (for reference)
Free forever: SMS tracking, categories and learning, balances, budgets, This/Last month, sort screen, restore from backup.
Pro (30 days free from first open, then ₹149 once): other periods and custom dates, Over time / Trend / Top merchants charts, dues and reminders with alerts, "what was this for?" notifications, making backup files.
