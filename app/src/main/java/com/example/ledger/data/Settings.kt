package com.example.ledger.data

import android.content.Context
import com.example.ledger.core.BudgetPeriod

/** Small on-device preferences. Nothing here ever leaves the phone. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ledger_settings", Context.MODE_PRIVATE)

    var budgetStartDay: Int
        get() = prefs.getInt(KEY_START_DAY, 1).coerceIn(BudgetPeriod.MIN_START_DAY, BudgetPeriod.MAX_START_DAY)
        set(value) = prefs.edit().putInt(KEY_START_DAY, value.coerceIn(BudgetPeriod.MIN_START_DAY, BudgetPeriod.MAX_START_DAY)).apply()

    var appLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_APP_LOCK, value).apply()

    /** True once the first-run "read my messages" screen has been shown (whatever the person chose). */
    var smsPromptDone: Boolean
        get() = prefs.getBoolean(KEY_SMS_PROMPT, false)
        set(value) = prefs.edit().putBoolean(KEY_SMS_PROMPT, value).apply()

    var lastSmsScanMillis: Long
        get() = prefs.getLong(KEY_LAST_SCAN, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SCAN, value).apply()

    /** Which version of the SMS reading rules the stored SMS transactions were made with. */
    var smsParserVersion: Int
        get() = prefs.getInt(KEY_PARSER_VERSION, 0)
        set(value) = prefs.edit().putInt(KEY_PARSER_VERSION, value).apply()

    /** Merchants the person said "No" to when asked to set a reminder. */
    var declinedReminders: Set<String>
        get() = prefs.getStringSet(KEY_DECLINED, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_DECLINED, value).apply()

    /** The person's choice per category: true = count in income/spending totals, false = leave out. */
    var countChoices: Map<Long, Boolean>
        get() = (prefs.getStringSet(KEY_COUNT, emptySet()) ?: emptySet()).mapNotNull { e ->
            val parts = e.split(":")
            val id = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            id to (parts.getOrNull(1) == "1")
        }.toMap()
        set(value) = prefs.edit().putStringSet(KEY_COUNT, value.map { (id, on) -> id.toString() + ":" + (if (on) "1" else "0") }.toSet()).apply()

    /** Messages read, bank transactions found, bank messages not understood (last full read). */
    var lastFullScan: Triple<Int, Int, Int>?
        get() = prefs.getString(KEY_FULL_SCAN, null)?.split(",")?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == 3 }?.let { Triple(it[0], it[1], it[2]) }
        set(value) = prefs.edit().putString(KEY_FULL_SCAN, value?.let { "${it.first},${it.second},${it.third}" }).apply()

    /** True once the person dismissed the "some payments send no SMS" card. */
    var trackingNoteSeen: Boolean
        get() = prefs.getBoolean(KEY_TRACKING_NOTE, false)
        set(value) = prefs.edit().putBoolean(KEY_TRACKING_NOTE, value).apply()

    /** Ask "what was this for?" after each new payment or credit read from SMS. */
    var askCategory: Boolean
        get() = prefs.getBoolean(KEY_ASK_CATEGORY, true)
        set(value) = prefs.edit().putBoolean(KEY_ASK_CATEGORY, value).apply()

    /** When the last backup file was saved (0 = never). */
    var lastBackupMillis: Long
        get() = prefs.getLong(KEY_LAST_BACKUP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKUP, value).apply()

    /** True once the safety walkthrough was finished or skipped. */
    var onboardingSeen: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    /** Cached answer from Google Play: true once Pro was bought on this Google account. */
    var proPurchased: Boolean
        get() = prefs.getBoolean(KEY_PRO, false)
        set(value) = prefs.edit().putBoolean(KEY_PRO, value).apply()

    /** When the app was first opened; the 30-day Pro trial counts from here. */
    var firstUseMillis: Long
        get() = prefs.getLong(KEY_FIRST_USE, 0L)
        set(value) = prefs.edit().putLong(KEY_FIRST_USE, value).apply()

    /** True once the "trial has ended" note was shown. */
    var trialEndSeen: Boolean
        get() = prefs.getBoolean(KEY_TRIAL_END_SEEN, false)
        set(value) = prefs.edit().putBoolean(KEY_TRIAL_END_SEEN, value).apply()

    val isPro: Boolean
        get() = com.example.ledger.core.Trial.isPro(proPurchased, firstUseMillis, System.currentTimeMillis())

    private companion object {
        const val KEY_PRO = "pro_purchased"
        const val KEY_FIRST_USE = "first_use"
        const val KEY_TRIAL_END_SEEN = "trial_end_seen"
        const val KEY_ONBOARDING = "onboarding_seen"
        const val KEY_LAST_BACKUP = "last_backup"
        const val KEY_ASK_CATEGORY = "ask_category"
        const val KEY_TRACKING_NOTE = "tracking_note_seen"
        const val KEY_FULL_SCAN = "last_full_scan"
        const val KEY_COUNT = "count_choices"
        const val KEY_DECLINED = "declined_reminders"
        const val KEY_PARSER_VERSION = "sms_parser_version"
        const val KEY_SMS_PROMPT = "sms_prompt_done"
        const val KEY_LAST_SCAN = "last_sms_scan"
        const val KEY_START_DAY = "budget_start_day"
        const val KEY_APP_LOCK = "app_lock"
    }
}
