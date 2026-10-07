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

    private companion object {
        const val KEY_START_DAY = "budget_start_day"
        const val KEY_APP_LOCK = "app_lock"
    }
}
