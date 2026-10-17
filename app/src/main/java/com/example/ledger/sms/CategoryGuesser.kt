package com.example.ledger.sms

/**
 * Guesses a starter category name from the merchant and message text using plain keywords.
 * A category the person chose themselves (a learned rule) always wins over this guess.
 */
object CategoryGuesser {

    private val expenseRules: List<Pair<String, List<String>>> = listOf(
        "EMI" to listOf("emi", "nach", "loan", "instalment", "installment", "bajaj fin", "ecs ", "home credit", "hdb fin", "tata capital"),
        "Rent" to listOf(" rent", "nobroker", "landlord"),
        "Electricity & bills" to listOf(
            "electric", "msedcl", "mahadiscom", "bescom", "tata power", "adani elec", "torrent power", "bses",
            "billdesk", "bill pay", "bbps", "recharge", "airtel", "jio", " vi ", "vodafone", "bsnl", "broadband",
            "act fiber", "gas ", "mahanagar", "water bill", "dth", "tata play", "tatasky", "d2h", "postpaid"
        ),
        "Groceries" to listOf(
            "bigbasket", "blinkit", "zepto", "dmart", "d mart", "instamart", "grocer", "supermarket", "jiomart",
            "reliance fresh", "reliance smart", "more retail", "kirana", "vegetable", "milk", "nature basket", "spencer"
        ),
        "Food & dining" to listOf(
            "swiggy", "zomato", "restaurant", "cafe", "dominos", "pizza", "mcdonald", "kfc", "burger", "starbucks",
            "eatery", "food", "bakery", "biryani", "chaayos", "haldiram", "dhaba", "hotel", "barbeque", "subway", "eatsure", "box8"
        ),
        "Transport" to listOf(
            "uber", " ola", "rapido", "irctc", "redbus", "metro", "fuel", "petrol", "diesel", "hpcl", "bpcl", "iocl",
            "indian oil", "fastag", "parking", "toll", "makemytrip", "goibibo", "indigo", "air india", "flight", "railway", "cab"
        ),
        "Entertainment" to listOf(
            "netflix", "spotify", "hotstar", "prime video", "bookmyshow", "pvr", "inox", "youtube", "gaming",
            "sonyliv", "zee5", "jiocinema", "apple.com", "google play", "playstore", "steam"
        ),
        "Health" to listOf(
            "pharmacy", "apollo", "medplus", "1mg", "pharmeasy", "hospital", "clinic", "diagnostic", "netmeds",
            "medical", "doctor", "lab ", "labs", "dental", "practo", "healthkart", "cult.fit", "cultfit"
        ),
        "Clothes" to listOf(
            "myntra", "ajio", "zara", "h&m", "trends", "lifestyle", "max fashion", "pantaloons", "westside",
            "fashion", "apparel", "clothing", "levis", "uniqlo", "decathlon", "nykaa fashion"
        ),
        "Online shopping" to listOf(
            "amazon", "flipkart", "meesho", "nykaa", "snapdeal", "tatacliq", "tata cliq", "ebay", "croma",
            "reliance digital", "ikea", "pepperfry", "firstcry", "lenskart", "paytm mall", "shopsy"
        )
    )

    private val incomeRules: List<Pair<String, List<String>>> = listOf(
        "Salary" to listOf("salary", "payroll", "sal cr", "wages"),
        "Refund" to listOf("refund", "reversal", "reversed", "cashback"),
        "Interest" to listOf("interest", "int.pd", "int pd", "int. pd", "int cr")
    )

    /** Returns the matching starter category name, or null when nothing matches. */
    fun guess(isIncome: Boolean, merchant: String?, body: String): String? {
        val text = " " + ((merchant ?: "") + " " + body).lowercase() + " "
        val rules = if (isIncome) incomeRules else expenseRules
        for ((name, words) in rules) if (words.any { text.contains(it) }) return name
        return null
    }

    /** Lowercase letters and digits only, so "Swiggy Ltd." and "SWIGGY LTD" share one learned rule. */
    fun merchantKey(merchant: String?): String? {
        val key = merchant?.lowercase()?.filter { it.isLetterOrDigit() } ?: return null
        return if (key.length >= 2) key else null
    }
}
