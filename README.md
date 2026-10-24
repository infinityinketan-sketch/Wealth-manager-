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

## Releasing on Google Play
See [RELEASE.md](RELEASE.md) for the step-by-step checklist and [STORE_LISTING.md](STORE_LISTING.md) for the listing text and policy forms.
App id `in.wealthmanager.app` (fixed forever once uploaded). Debug builds install alongside as `in.wealthmanager.app.debug`, named "Wealth Manager (test)".

## Status of this code
Every push is built in GitHub Actions: debug APK, unit tests, and a shrunk release build that is checked to have no INTERNET permission. The **build-release** workflow makes the signed `.aab` for Play.

## Stage 2: automatic SMS capture (v0.2.0)

On first open the app asks for SMS permission, then reads the last 12 months of bank / UPI / card
messages **on the phone** and builds accounts, balances, transactions and category totals from them.
New messages are picked up as they arrive. The app has no INTERNET permission.

- `sms/SmsParser.kt` reads amount, debit/credit, account last 4 digits, merchant and available balance.
- `sms/CategoryGuesser.kt` guesses the category from the merchant (Swiggy -> Food & dining, etc.).
- Re-categorising a merchant's entry asks "every time or just this one?" and remembers the choice.
- The database was reset by this version (stage 1 test data is not carried over).

## v0.3.0: better SMS reading

- Only real bank, card and lender messages are used; merchant receipts (MSEDCL, MakeMyTrip, Juspay ...) are ignored,
  so the same payment is not counted twice.
- Accounts are matched on the last 3 digits (ICICI shows 3, HDFC shows 4), so each account keeps its own balance.
  Balances come only from the bank's SMS; accounts whose SMS never shows a balance are not added to the total.
- FD / RD / investments, credit-card bill payments, own-account transfers, FD maturity and loan disbursals are
  kept out of "spent" and "income".
- A lender's "EMI received" message (credit to a loan account) is an EMI expense, and is dropped when the bank
  debit for the same EMI is also there.
- When the reading rules change (`SmsParser.VERSION`), SMS transactions are rebuilt from the inbox automatically;
  manual entries and learned "every time" merchant choices are kept.

## v0.4.0: smarter categories

- Much larger Indian merchant dictionary (medical/chemist/aushadhalaya -> Health, kirana/dairy -> Groceries,
  sweets/amruttulya -> Food, petrol pump, salon, hardware, classes, temple trusts ...), matched on word starts and,
  for merchant names, inside joined-up names like "SAIMEDICALS".
- New categories: Home & household, Personal care, Donations.
- Learns words from the person's own "every time" choices (e.g. "AGRO" -> Groceries) and re-tags uncertain entries.
- The edit screen shows the original bank message.
- Fixed: a credit whose footer advertised a loan was treated as an EMI payment (income showed ₹0).

## v0.5.0: periods, charts and a new look

- Period picker: This month, Last month, 3 / 6 / 12 months, This year, This FY (Apr–Mar) or custom dates;
  arrows step the chosen period back and forward (`core/Periods.kt`).
- Charts for the chosen period (`ui/Charts.kt`, drawn on Canvas): category donut, spent vs income over time
  (days / weeks / months picked automatically), running-total trend, top merchants.
- Colourful theme, gradient summary and account cards, an icon and colour per category.

## v0.6.0: sort what the app could not place

- Home shows "N payments need a category"; the Sort screen lists each unknown merchant with one-tap
  category chips (remembered for every payment there) and single entries for payments without a name.
- Shop names starting with Shree / Sai / Om ... are no longer treated as people; shop QR codes
  (paytmqr…, Q123…) are not shown as merchant names.

## v0.7.0: fix categories in bulk

- Tap a category (Spending / Income by category, or the donut legend) to see its entries for the period,
  search them, tick several (or Select all) and "Move to…" another category, optionally remembering the shops.

## v0.8.0: dues & reminders, self-transfers

- Bill SMS (card statements, electricity, phone, gas, loan EMIs, insurance premiums) become dues with a date
  and amount (`sms/DueParser.kt`); they close themselves when the payment shows up, or tap Paid.
- Regular payments are spotted (`core/RecurringDetector.kt`): "Repeats quarterly · ₹1,740 — Set reminder?".
- Manual reminders with repeat; daily notification for anything due within 2 days.
- Money moved between the person's own accounts is matched (`core/TransferMatcher.kt`) and kept out of
  spending and income.
- First scan shows "Reading your messages 13100/18870".
- Database version 3 adds the `dues` table with a migration (nothing is lost).

## v0.8.1: review fixes

- One-off category / note fixes to SMS entries are stored (`overrides`, DB v4) and re-applied whenever the
  inbox is read again; a later "every time" choice for that shop replaces them.
- Bank balances: last SMS balance plus every entry after it ("Bank SMS 16 Sep + 12 later").
- Duplicate alerts are only identical texts or a second sender id; two real same-amount payments are both kept.
- Quick self-transfer pairs start at ₹500 (was ₹100).

## v0.9.0: correct income and savings

- Money back from investments (MF redemptions via ICCL / BSE / CAMS / KFin, broker payouts from Zerodha,
  Groww, Upstox…, FD/RD maturity, own transfers, loan disbursal) is "Transfer in (not income)".
  Clearing corporations, AMCs and brokers are never taken for people.
- Refunds reduce spending instead of counting as income (`core/Totals.kt`).
- Saved = income − spent; the summary says how much of it went into investments / FD.
- "How is this worked out?" on the summary shows the full breakdown.
- Every category has a "Count in totals" switch; a "Not counted in totals" section lists what is left out.

## v0.9.1: no more dropped alerts

- Real debit/credit alerts are no longer thrown away because of footer lines ("Never share OTP",
  "Get a loan, apply now", "EMI due…"). OTP / offer / due / failed words only skip messages that do not
  report a completed debit or credit on an account (`SmsParser.parseWhy`).
- The first read covers the whole inbox, not only the last 12 months.
- Home shows "Read N messages · M bank transactions · K not understood"; the "not understood" screen lists
  those bank messages with the reason and can copy them with long numbers hidden.

## v0.9.2: say up front what SMS cannot see

- The first screen, a home card (until "Got it") and a "Why are some payments missing?" link explain that small UPI
  payments (some banks send no SMS), UPI Lite, cash and wallets are not in bank SMS, and how to add them.

## v0.9.3: stability

- Background errors no longer close the app: they show a short message and are saved (`data/CrashLog.kt`).
- A crash is recorded on the phone and offered on the next start (and under More › Problem report) with "Copy details".
- Bulk category moves are split into chunks (older SQLite allows 999 values per query).
- Starter categories are added under a lock; duplicates created by earlier versions are merged.
- SMS import and re-guessing write in batches, so screens refresh a few times instead of once per entry.

## v0.10.0: "What was this for?"

- After each new payment or credit read from SMS, a notification asks what it was for, with three one-tap
  category buttons (the app's guess, then the most used). Tapping the notification opens a quick picker.
- The choice is remembered for that shop; shops already decided, self transfers, FD and card bills are not asked.
- Switch under More › Settings: "Ask what each payment was for".

## v0.11.0: encrypted backup & restore

- More › Backup & restore: one file with all tables and settings, gzip + AES-256-GCM with a key from the
  person's password (PBKDF2-HMAC-SHA256, 150k rounds) — `core/BackupCrypto.kt`, `data/Backup.kt`.
- Saved wherever the person picks (Google Drive, Downloads…) through the system file picker; the app stays offline.
- Restore replaces the data on the phone after asking for the password; newer-version backups are refused.

## v0.12.0: safety walkthrough

- First launch opens 4 swipeable pages before anything else (`ui/OnboardingScreen.kt`): data stays on the phone
  (no internet, no account, no ads), only bank messages are read, the app can never move money (never asks for
  password/PIN/OTP), and the person stays in control (app lock, password backups, uninstall removes all).
- Skip / Next / Get started; reopen any time from More › "How your data is kept safe".

## v1.0.0: ready for Google Play
- New app id `in.wealthmanager.app`, targets Android 16 (API 36), edge-to-edge screens.
- Wealth Manager Pro: 30 days free from first open, then a one-time ₹149 through Google Play. Tracking stays free; Pro adds other periods, extra charts, dues & reminders, "what was this for?" prompts and making backup files. "Restore purchase" in More.
- New app icon (with Android 13+ themed icon), Play icon and feature graphic in `store/`.
- Privacy policy page in `docs/` (GitHub Pages).
- Signed release workflow; release builds are shrunk with R8 and checked for no INTERNET permission.
- Database upgrades never wipe data from v2 onwards.

