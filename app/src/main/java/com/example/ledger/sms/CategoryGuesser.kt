package com.example.ledger.sms

/** The starter categories every install gets. [excluded] ones are money moved, not spent or earned. */
object StarterCategories {
    data class Starter(val name: String, val isIncome: Boolean, val color: Long, val excluded: Boolean = false)

    val all = listOf(
        Starter("Food & dining", false, 0xFFE57373),
        Starter("Groceries", false, 0xFF81C784),
        Starter("Transport", false, 0xFF64B5F6),
        Starter("Fuel", false, 0xFF4FC3F7),
        Starter("Rent", false, 0xFFBA68C8),
        Starter("EMI", false, 0xFFFFB74D),
        Starter("Electricity & bills", false, 0xFFFFD54F),
        Starter("Insurance", false, 0xFF9575CD),
        Starter("Clothes", false, 0xFFF06292),
        Starter("Online shopping", false, 0xFF4DB6AC),
        Starter("Health", false, 0xFF7986CB),
        Starter("Entertainment", false, 0xFFA1887F),
        Starter("Travel", false, 0xFF26A69A),
        Starter("Education", false, 0xFF8D6E63),
        Starter("Cash withdrawal", false, 0xFFBDBDBD),
        Starter("Sent to people", false, 0xFFCE93D8),
        Starter("Other", false, 0xFF90A4AE),
        Starter(CategoryGuesser.INVESTMENTS, false, 0xFF26C6DA, excluded = true),
        Starter(CategoryGuesser.CARD_BILL, false, 0xFF78909C, excluded = true),
        Starter(CategoryGuesser.SELF_TRANSFER, false, 0xFF607D8B, excluded = true),
        Starter("Salary", true, 0xFF388E3C),
        Starter("Refund", true, 0xFF0097A7),
        Starter("Interest", true, 0xFF5C6BC0),
        Starter("Received from people", true, 0xFF9CCC65),
        Starter("Other income", true, 0xFF78909C),
        Starter(CategoryGuesser.TRANSFER_IN, true, 0xFF607D8B, excluded = true)
    )

    /** Names whose amounts are left out of "spent" and "income" totals. */
    val excludedNames: Set<String> = all.filter { it.excluded }.map { it.name }.toSet()
}

/**
 * Guesses a starter category name from the merchant and message text using plain keywords.
 * A category the person chose themselves (a learned rule) always wins over this guess.
 */
object CategoryGuesser {

    const val INVESTMENTS = "Investments & FD"
    const val CARD_BILL = "Credit card bill"
    const val SELF_TRANSFER = "Self transfer"
    const val TRANSFER_IN = "Transfer in (not income)"

    // Order matters: the first matching rule wins, so specific money-movement rules come first.
    private val expenseRules: List<Pair<String, List<String>>> = listOf(
        INVESTMENTS to listOf(
            "fixed deposit", "fixed dep", " fd ", "fd no", "fd a/c", "fd booking", "term deposit", " tdr", "e-tdr", "stdr",
            "recurring deposit", "rd a/c", "rd instal", "sweep", "mutual fund", " mf ", " sip", "zerodha", "groww",
            "upstox", "kuvera", "paytm money", "indian clearing", "iccl", "nse clearing", "bse ltd", "cams", "kfin",
            " nps", "ppf", "sukanya", "angel one", "angelone", "dhan ", "smallcase", "coin by", "invest"
        ),
        CARD_BILL to listOf("credit card bill", "credit card payment", "cc payment", "cc bill", "cred club", " cred ", "billpay cc"),
        SELF_TRANSFER to listOf("self transfer", "own account", "to self", "self a/c"),
        "EMI" to listOf(
            "emi ", "nach", "loan", "instalment", "installment", "bajaj fin", "ecs ", "home credit", "hdb fin",
            "tata capital", "ach d-", "achdr", "ach dr"
        ),
        "Cash withdrawal" to listOf("atm wdl", "atm withdrawal", "atm cash", "withdrawn at atm", "at atm", "cash withdrawal", "cash wdl", "nfs/", "cwdr"),
        "Insurance" to listOf(
            "insurance", " lic ", "lic of india", "licindia", "premium", "policy", "hdfc life", "icici pru", "sbi life",
            "star health", "acko", "go digit", "max life", "tata aig", "bajaj allianz", "niva bupa", "care health", "policybazaar"
        ),
        "Rent" to listOf(" rent", "nobroker", "landlord", "house rent"),
        "Fuel" to listOf(
            "fuel", "petrol", "diesel", "hpcl", "bpcl", "iocl", "indian oil", "indianoil", "hp pay", "filling station",
            "service station", "nayara", "shell ", "petroleum", "fuels", " cng"
        ),
        "Electricity & bills" to listOf(
            "electric", "msedcl", "mahadiscom", "mahavitaran", "bescom", "tata power", "adani elec", "torrent power", "bses",
            "billdesk", "bill pay", "bbps", "recharge", "airtel", "jio ", "reliance jio", " vi ", "vodafone", "bsnl", "broadband",
            "act fiber", "gas ", "gas bill", "mngl", "maharashtra natural gas", "mahanagar", "water bill", "dth ", "tata play",
            "tatasky", "d2h", "postpaid", "property tax", "pmc ", "pcmc", "municipal", "society maintenance", "maintenance"
        ),
        "Groceries" to listOf(
            "bigbasket", "blinkit", "zepto", "dmart", "d mart", "d-mart", "avenue supermarts", "instamart", "grocer",
            "supermarket", "jiomart", "reliance fresh", "reliance smart", "smart bazaar", "star bazaar", "more retail",
            "kirana", "vegetable", "fruits", "dairy", "milk", "nature basket", "spencer", "ratnadeep", "big bazaar",
            "provision", "general store", "kirana store", "country delight", "milkbasket", "bbdaily"
        ),
        "Food & dining" to listOf(
            "swiggy", "zomato", "restaurant", "restro", "cafe", "dominos", "domino", "pizza", "mcdonald", "kfc", "burger",
            "starbucks", "eatery", "foods", "food", "bakery", "bakers", "biryani", "chaayos", "haldiram", "dhaba",
            "barbeque", "subway", "eatsure", "box8", "kitchen", "caterer", "sweets", "mithai", "juice", " tea ",
            "chai", "snacks", "dosa", "vada pav", "misal", "bar & ", "wine", "lounge", "hotel"
        ),
        "Travel" to listOf(
            "makemytrip", "mmtrip", "goibibo", "yatra", "cleartrip", "ixigo", "indigo", "air india", "akasa", "spicejet",
            "vistara", "flight", "airline", "airport", "oyo", "booking.com", "agoda", "airbnb", "treebo", "fabhotel"
        ),
        "Transport" to listOf(
            "uber", " ola", "olacabs", "rapido", "irctc", "redbus", "metro", "fastag", "parking", "toll plaza", "railway",
            " cab ", "cabs", "taxi", "blablacar", "pmpml", "msrtc", "bus "
        ),
        "Entertainment" to listOf(
            "netflix", "spotify", "hotstar", "jiohotstar", "prime video", "primevideo", "bookmyshow", "pvr", "inox",
            "cinepolis", "youtube", "gaming", "sonyliv", "zee5", "jiocinema", "apple.com", "google play", "playstore",
            "steam", "audible", "gaana", "wynk"
        ),
        "Health" to listOf(
            "pharmacy", "pharma", "chemist", "medico", "medicals", "medical", "apollo", "medplus", "1mg", "pharmeasy",
            "hospital", "clinic", "diagnostic", "netmeds", "doctor", "lab ", "labs", "pathology", "dental",
            "practo", "healthkart", "cult.fit", "cultfit", "wellness forever", "nursing home", "physio", "gym", "fitness"
        ),
        "Education" to listOf("school", "college", "university", "tuition", "classes", "academy", "byju", "unacademy", "udemy", "coursera", "fees"),
        "Clothes" to listOf(
            "myntra", "ajio", "zara", "h&m", "trends", "lifestyle", "max fashion", "pantaloons", "westside",
            "fashion", "apparel", "clothing", "garment", "textile", "saree", "sarees", "levis", "uniqlo", "decathlon",
            "nykaa fashion", "bata", "footwear", "shoes", "manyavar", "fabindia", "raymond", "allen solly", "peter england"
        ),
        "Online shopping" to listOf(
            "amazon", "amzn", "flipkart", "meesho", "nykaa", "snapdeal", "tatacliq", "tata cliq", "ebay", "croma",
            "reliance digital", "vijay sales", "ikea", "pepperfry", "firstcry", "lenskart", "shopsy", "jiomart",
            "razorpay", "payu", "cashfree", "ccavenue"
        )
    )

    private val incomeRules: List<Pair<String, List<String>>> = listOf(
        TRANSFER_IN to listOf(
            "fd closure", "fd matur", "maturity", "premature", "fixed deposit", "term deposit", "sweep", "redemption",
            "redeemed", "self transfer", "own account", "disburs", "rd closure"
        ),
        "Salary" to listOf("salary", "payroll", "sal cr", "salcr", "wages", "stipend"),
        "Refund" to listOf("refund", "reversal", "reversed", "cashback", "chargeback"),
        "Interest" to listOf("interest", "int.pd", "int pd", "int. pd", "int cr", "int.cr", "dividend")
    )

    /** Lowercase, punctuation to spaces ("A/c" -> "a c"), single spaces. Keeps '&' (h&m). */
    private fun normalise(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9&]+"), " ")

    /**
     * A keyword matches only at the start of a word, so "emi " does not match "premium" and "atm"
     * does not match "treatment". A keyword ending in a space must be a whole word.
     */
    private fun matches(text: String, keyword: String): Boolean {
        val trailingSpace = keyword.endsWith(" ") || keyword.endsWith("/")
        val k = normalise(keyword).trim()
        if (k.isEmpty()) return false
        return text.contains(" " + k + if (trailingSpace) " " else "")
    }

    /** Returns the matching starter category name, or null when nothing matches. */
    fun guess(isIncome: Boolean, merchant: String?, body: String): String? {
        val rules = if (isIncome) incomeRules else expenseRules
        // The merchant name is more reliable than the rest of the message (which has footers, offers...).
        if (merchant != null) {
            val m = " " + normalise(merchant).trim() + " "
            for ((name, words) in rules) if (words.any { matches(m, it) }) return name
        }
        val text = " " + normalise(body).trim() + " "
        for ((name, words) in rules) if (words.any { matches(text, it) }) return name
        if (merchant != null && looksLikePerson(merchant)) return if (isIncome) "Received from people" else "Sent to people"
        return null
    }

    /** "RAMESH KUMAR", "PRIYA S" look like people; anything with business words does not. */
    fun looksLikePerson(merchant: String): Boolean {
        val words = merchant.trim().split(" ").filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > 4) return false
        if (merchant.any { it.isDigit() || it == '&' || it == '.' || it == '@' || it == '*' }) return false
        val business = listOf(
            "pvt", "ltd", "limited", "llp", "store", "stores", "mart", "shop", "enterprise", "enterprises", "traders",
            "services", "solutions", "technologies", "tech", "india", "company", "corp", "agency", "centre", "center",
            "hotel", "cafe", "restaurant", "medical", "hospital", "bank", "finance", "payments", "retail", "brothers",
            "and", "sons", "industries", "foods", "collection", "point", "bazar", "bazaar", "online", "digital"
        )
        val lower = words.map { it.lowercase() }
        if (lower.any { it in business }) return false
        return words.size >= 2 && words.all { w -> w.all { it.isLetter() } }
    }

    /** Lowercase letters and digits only, so "Swiggy Ltd." and "SWIGGY LTD" share one learned rule. */
    fun merchantKey(merchant: String?): String? {
        val key = merchant?.lowercase()?.filter { it.isLetterOrDigit() } ?: return null
        return if (key.length >= 2) key else null
    }
}
