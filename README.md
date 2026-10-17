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
