package com.example.ledger.core

/** The free Pro trial, counted on the phone from the first day the app was used. */
object Trial {
    const val DAYS = 30
    private const val DAY = 24L * 60 * 60 * 1000

    /** Whole days of Pro trial left: DAYS on the first day, 0 once it has ended. */
    fun daysLeft(firstUseMillis: Long, nowMillis: Long): Int {
        if (firstUseMillis <= 0L) return DAYS
        val used = ((nowMillis - firstUseMillis).coerceAtLeast(0L) / DAY).toInt()
        return (DAYS - used).coerceIn(0, DAYS)
    }

    fun isPro(purchased: Boolean, firstUseMillis: Long, nowMillis: Long): Boolean =
        purchased || daysLeft(firstUseMillis, nowMillis) > 0

    /** Days a subscription or pass is trusted offline after Google Play last confirmed it. */
    const val OFFLINE_GRACE_DAYS = 7

    /** A subscription/pass Play confirmed at [confirmedMillis] still counts at [nowMillis] (phone offline). */
    fun subscriptionStillValid(active: Boolean, confirmedMillis: Long, nowMillis: Long): Boolean =
        active && confirmedMillis > 0L && nowMillis - confirmedMillis in 0 until OFFLINE_GRACE_DAYS * DAY

    /** Show the "X days of Pro left" note in the last few days of the trial. */
    fun showCountdown(purchased: Boolean, daysLeft: Int): Boolean = !purchased && daysLeft in 1..5
}
