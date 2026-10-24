# Google Play listing: Wealth Manager

Copy-paste text for Play Console. Character limits are Play's.

## Main store listing

**App name** (30 max)
Wealth Manager: SMS Expenses

**Short description** (80 max)
Auto-track spending from bank & UPI SMS. No internet, no login, stays on phone.

**Full description** (4000 max)

Wealth Manager tracks your money automatically from the SMS your bank already sends you, and keeps everything on your phone.

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
• No internet permission: the app cannot upload your data, even by mistake
• No signup, no login, no ads, no tracking
• Optional app lock with fingerprint or screen lock
• Encrypted backup file (AES-256, your password) to move to a new phone

★ GOOD TO KNOW
Some payments send no SMS (small UPI payments on some banks, cash, wallets). You can add those yourself in two taps.

★ FREE AND PRO
Tracking, categories, balances, budgets and this/last month are free forever.
Pro (free for the first 30 days, then a one-time ₹149, no subscription) adds longer periods and custom dates, all charts, bill reminders, "what was this for?" prompts after each payment, and backup files.

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

**App access:** All functionality is available without special access (no login). For SMS features, reviewers use the video below.

**Content rating:** Category "Utility, productivity, communication or other". Answer No to violence, sexuality, language, controlled substances, gambling, user interaction/sharing, and location sharing. Expected rating: Everyone / 3+.

**Target audience:** 18 and over only.

**News app:** No. **Government app:** No. **Health app:** No.

**Financial features:** choose "Budgeting / money management / personal finance tracking" (no loans, no investments sold, no payments made through the app). Not a lender, not a payment app.

**Data safety**
• Does your app collect or share any of the required user data types? **No.**
  The app has no internet permission, no SDKs that send data, no analytics. SMS and money data are processed only on the device and never leave it ("processed only on device" is not "collected" under Play's definition).
• The Pro purchase is handled by Google Play itself. The app sends nothing to the developer.
• Is all user data encrypted in transit? Not applicable (no data is sent).
• Can users request deletion? Uninstall or clear storage removes everything.

**SMS and Call Log permissions declaration** (Policy → App content → Sensitive permissions)
• Core functionality: **SMS-based money management**
  (one of the exceptions Play lists in its SMS permission policy, for apps that track budgets from transaction SMS).
• Permissions: READ_SMS, RECEIVE_SMS.
• Description to paste:
  "Wealth Manager is an offline personal finance tracker. It reads transaction SMS from banks, card issuers, UPI apps and lenders to record spending, income and account balances automatically, and RECEIVE_SMS records new transactions as they arrive. Messages are processed only on the device; the app has no INTERNET permission and no server, so no SMS content is uploaded or shared. Messages that are not from financial senders are ignored."
• Video demo link: an unlisted YouTube video, about 1 minute (script below).

### Video script (record on your phone with the built-in screen recorder)
1. (0:00) Fresh install. Open the app: the safety pages say "no internet, stays on your phone". Tap Next through them.
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
• 30 days of Pro free
