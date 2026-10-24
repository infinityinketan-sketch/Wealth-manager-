# Google Play listing: Wealth Manager

Copy-paste text for Play Console. Character limits are Play's.

## Main store listing

**App name** (30 max)
Wealth Manager: SMS Expenses

**Short description** (80 max)
Auto-track spending from bank & UPI SMS. Your messages never leave your phone.

**Full description** (4000 max)

Wealth Manager tracks your money automatically from the SMS your bank already sends you. Your messages and money data stay on your phone.

★ AUTOMATIC TRACKING
• Reads bank, UPI, credit card and loan SMS from all major Indian banks
• Finds the amount, shop, account and balance by itself
• Sorts spending into categories: Food, Groceries, Health, Shopping, Travel, Bills and more
• Learns: correct a shop once and it is remembered for every future payment
• Spots transfers between your own accounts, so they are not counted as spending
• FD, mutual fund and investment moves are counted as saving, not spending
• Refunds reduce spending instead of looking like income

★ CLEAR PICTURE
• What you spent, earned and saved, for any period
• Balance of each bank account separately
• Colourful charts: by category, over time, trend and top merchants
• Budgets per category with progress
• Sort screen: fix many uncategorised payments in one go

★ NEVER MISS A BILL
• Card bills, EMIs and subscriptions found in your messages
• A reminder before the due date
• Repeating payments detected for you

★ PRIVATE BY DESIGN
• Your SMS, amounts, balances and bank details are never uploaded
• Sign in with Google: only your name, email and basic app details are kept for your account
• No ads, no advertising ID, and your details are never sold
• Optional app lock with fingerprint or screen lock
• Encrypted backup file (AES-256, your password) to move to a new phone

★ GOOD TO KNOW
Some payments send no SMS (small UPI payments on some banks, cash, wallets). You can add those yourself in two taps.

★ TRY FREE FOR 30 DAYS
Every feature is free for 30 days. Then choose what suits you, all through Google Play:
• Monthly or yearly subscription (cancel any time)
• 1-month or 1-year pass, paid once, never auto-renews
• Lifetime: pay once, keep it forever

**App category:** Finance
**Tags:** Budget, Expense tracker, Personal finance
**Contact email:** your support email (required, shown publicly)
**Privacy policy URL:** https://infinityinketan-sketch.github.io/Wealth-manager-/privacy-policy.html

**Graphics** (in the `store/` folder)
• App icon: `store/play-icon-512.png` (512×512)
• Feature graphic: `store/feature-graphic-1024x500.png`
• Phone screenshots: 2–8 needed, take them on your phone (Power + Volume down). Good ones: home screen with the chart, sort screen, dues, the onboarding "your data stays on your phone" page, the More tab. Use a phone with test data, or blur real amounts and names you don't want public.

## App content forms (Policy → App content)

**Privacy policy:** URL above.

**Ads:** No, the app contains no ads.

**App access:** Login required. Choose "All or some functionality is restricted" and explain: "Sign in with any Google account on the first screen (tick the consent box, then Continue with Google). New accounts get a 30-day free trial with every feature unlocked, so no test credentials are needed." For SMS features, reviewers use the video below.

**Content rating:** Category "Utility, productivity, communication or other". Answer No to violence, sexuality, language, controlled substances, gambling, user interaction/sharing, and location sharing. Expected rating: Everyone / 3+.

**Target audience:** 18 and over only.

**News app:** No. **Government app:** No. **Health app:** No.

**Financial features:** choose "Budgeting / money management / personal finance tracking" (no loans, no investments sold, no payments made through the app). Not a lender, not a payment app.

**Data safety**
• Does your app collect or share any of the required user data types? **Yes, collects. Shares: No.**
• Is all of the user data collected by your app encrypted in transit? **Yes.**
• Do you provide a way for users to request that their data is deleted? **Yes**, delete-account URL: https://infinityinketan-sketch.github.io/Wealth-manager-/delete-account.html

Data types to tick (for each: Collected = Yes, Shared = No, Processed ephemerally = No):
| Data type | Required or optional | Purposes |
|---|---|---|
| Personal info → **Name** | Required | Account management, App functionality |
| Personal info → **Email address** | Required | Account management, App functionality, Developer communications |
| App activity → **App interactions** | Optional (user can turn off) | Analytics |
| App info and performance → **Diagnostics** (app/Android version, phone model) | Required | App functionality, Analytics |
| Personal info → **User IDs** | Required | Account management (the Firebase account ID) |

Do **not** tick: Financial info, Messages (SMS is processed only on the device and never transmitted), Location, Contacts, Photos, Audio, Files, Calendar, Health, Web browsing.
Purchase history: leave unticked; the purchase is handled by Google Play itself and the app only stores a yes/no "Pro" flag. (If you prefer to be extra careful, tick Financial info → Purchase history, purpose App functionality.)

**SMS and Call Log permissions declaration** (Policy → App content → Sensitive permissions)
• Core functionality: **SMS-based money management**
  (one of the exceptions Play lists in its SMS permission policy, for apps that track budgets from transaction SMS).
• Permissions: READ_SMS, RECEIVE_SMS.
• Description to paste:
  "Wealth Manager is an offline personal finance tracker. It reads transaction SMS from banks, card issuers, UPI apps and lenders to record spending, income and account balances automatically, and RECEIVE_SMS records new transactions as they arrive. Messages are processed only on the device. No SMS content or data derived from it (amounts, balances, merchants) is uploaded or shared; the app's internet use is limited to Google sign-in, an account record (name, email, app version) and Google Play billing. Messages that are not from financial senders are ignored."
• Video demo link: an unlisted YouTube video, about 1 minute (script below).

### Video script (record on your phone with the built-in screen recorder)
1. (0:00) Fresh install. Open the app: the safety pages say your messages stay on your phone. Tap Next through them, then sign in with Google (show the consent screen).
2. (0:15) The SMS screen explains why messages are needed. Tap "Allow and scan my messages" and show the Android permission dialog. Allow.
3. (0:25) Home screen fills with transactions found in bank SMS. Tap one: show the bank message it came from.
4. (0:40) When a real bank SMS arrives (make a small UPI payment), show the "what was this for?" notification and the entry appearing in the app.
5. (0:50) Open Android Settings → Apps → Wealth Manager → Permissions: show it asks only for SMS and notifications.
Upload to YouTube as **Unlisted** and paste the link.

## Release notes for 1.0.0
First release.
• Automatic tracking from bank, UPI and card SMS
• Categories that learn, self-transfer detection, investments counted as saving
• Charts for any period, per-account balances, budgets
• Bill dues and reminders
• Encrypted backup and restore
• Sign in with Google; every feature free for 30 days, then monthly, yearly, pass or lifetime plans
