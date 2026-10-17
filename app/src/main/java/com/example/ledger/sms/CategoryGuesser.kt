package com.example.ledger.sms

/** The starter categories every install gets. [excluded] ones are money moved, not spent or earned. */
object StarterCategories {
    data class Starter(val name: String, val isIncome: Boolean, val color: Long, val excluded: Boolean = false)

    val all = listOf(
        Starter("Food & dining", false, 0xFFE57373),
        Starter("Groceries", false, 0xFF81C784),
        Starter("Health", false, 0xFF7986CB),
        Starter("Transport", false, 0xFF64B5F6),
        Starter("Fuel", false, 0xFF4FC3F7),
        Starter("Rent", false, 0xFFBA68C8),
        Starter("EMI", false, 0xFFFFB74D),
        Starter("Electricity & bills", false, 0xFFFFD54F),
        Starter("Insurance", false, 0xFF9575CD),
        Starter("Clothes", false, 0xFFF06292),
        Starter("Online shopping", false, 0xFF4DB6AC),
        Starter("Home & household", false, 0xFFAED581),
        Starter("Personal care", false, 0xFFFF8A65),
        Starter("Entertainment", false, 0xFFA1887F),
        Starter("Travel", false, 0xFF26A69A),
        Starter("Education", false, 0xFF8D6E63),
        Starter("Donations", false, 0xFFFFCC80),
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

    /** Categories that mean "the app was not sure"; these get re-guessed when the app learns more. */
    val fallbackNames: Set<String> = setOf("Other", "Other income", "Sent to people", "Received from people")
}

/**
 * Guesses a category from the merchant name and the message words, the way a person would:
 * "SAI MEDICAL", "Shree Chemist", "saimedicals@ybl" are all Health; "Om Sweets" is Food.
 *
 * Matching rules for each keyword:
 *  - it must start a word ("emi " does not match "premium", "atm" does not match "treatment");
 *  - a keyword written with a trailing space must be a whole word ("pg ", "mess ");
 *  - in the merchant name only, keywords of 6+ letters also match inside joined-up names
 *    ("SAIMEDICALS", "CITYHOSPITAL"), unless listed in [noJoinedMatch].
 * A category the person chose (a learned rule) always wins over these guesses.
 */
object CategoryGuesser {

    const val INVESTMENTS = "Investments & FD"
    const val CARD_BILL = "Credit card bill"
    const val SELF_TRANSFER = "Self transfer"
    const val TRANSFER_IN = "Transfer in (not income)"

    // Order matters: the first matching rule wins. Money movements first, then the most specific.
    private val expenseRules: List<Pair<String, List<String>>> = listOf(
        INVESTMENTS to listOf(
            "fixed deposit", "fixed dep", "fd ", "fd no", "fd a/c", "fd booking", "term deposit", "tdr ", "e-tdr", "stdr",
            "recurring deposit", "rd a/c", "rd instal", "sweep", "mutual fund", "mf ", "sip ", "zerodha", "groww",
            "upstox", "kuvera", "paytm money", "indian clearing", "iccl", "nse clearing", "bse ltd", "cams ", "kfin",
            "nps ", "nps trust", "ppf", "sukanya", "angel one", "angelone", "dhan ", "smallcase", "coin by", "investment",
            "demat", "broking", "stock broker", "securities", "digital gold", "safegold", "augmont", "mmtc pamp",
            "sovereign gold", "sgb ", "scss", "post office", "nsc ", "kisan vikas"
        ),
        CARD_BILL to listOf("credit card bill", "credit card payment", "cc payment", "cc bill", "cred club", "cred ", "billpay cc", "card bill"),
        SELF_TRANSFER to listOf("self transfer", "own account", "to self", "self a/c", "own a/c"),
        "EMI" to listOf(
            "emi ", "nach", "ach d", "ach dr", "achdr", "ecs ", "loan", "instalment", "installment", "bajaj fin", "bajaj finance",
            "bajaj finserv", "home credit", "hdb fin", "hdb financial", "tata capital", "lic housing", "mahindra finance",
            "muthoot", "manappuram", "iifl", "fullerton", "l&t finance", "shriram fin", "chola", "kreditbee", "moneyview",
            "money view", "navi ", "cashe", "zestmoney", "lazypay", "simpl ", "finance ltd", "fincorp", "finserv"
        ),
        "Cash withdrawal" to listOf(
            "atm wdl", "atm withdrawal", "atm cash", "withdrawn at atm", "at atm", "atm txn", "cash withdrawal",
            "cash withdrawn", "cash wdl", "nfs/", "cwdr", "cash wd"
        ),
        "Insurance" to listOf(
            "insurance", "assurance", "insure", "lic ", "lic of india", "licindia", "life insurance", "premium ", "policy ",
            "hdfc life", "hdfc ergo", "icici pru", "icici prudential", "icici lombard", "sbi life", "sbi general",
            "star health", "niva bupa", "care health", "acko", "go digit", "digit insurance", "max life", "tata aig",
            "bajaj allianz", "policybazaar", "kotak life", "reliance general", "new india", "united india", "oriental insurance",
            "pmjjby", "pmsby", "term plan"
        ),
        "Rent" to listOf("rent", "house rent", "room rent", "pg ", "paying guest", "nobroker", "landlord", "nestaway", "rentomojo", "furlenco"),
        "Fuel" to listOf(
            "petrol", "petroleum", "fuel", "fuels", "filling station", "service station", "diesel", "cng ", "hpcl",
            "hindustan petroleum", "bpcl", "bharat petroleum", "iocl", "indian oil", "indianoil", "nayara", "shell ",
            "jio bp", "jiobp", "hp pay", "hpay", "auto fuel", "petro "
        ),
        "Electricity & bills" to listOf(
            "electricity", "electric supply", "msedcl", "mahadiscom", "mahavitaran", "msebcl", "bescom", "tata power",
            "adani elec", "adani electricity", "torrent power", "bses", "tneb", "kseb", "cesc", "billdesk", "bill pay", "billpay",
            "bbps", "bharat bill", "recharge", "prepaid", "postpaid", "airtel", "jio ", "reliance jio", "vi ", "vodafone",
            "idea ", "bsnl", "mtnl", "broadband", "fibernet", "fiber ", "act fiber", "hathway", "excitel", "gtpl", "den ",
            "tata play", "tatasky", "tata sky", "dish tv", "dishtv", "d2h", "dth ", "sun direct", "gas ", "gas bill", "mngl",
            "maharashtra natural gas", "mahanagar gas", "igl ", "indane", "hp gas", "bharat gas", "bharatgas", "lpg",
            "cylinder", "water bill", "water tax", "property tax", "pmc ", "pcmc", "municipal", "society", "maintenance",
            "housing soc", "chs ", "apartment", "electricity bill", "light bill"
        ),
        "Health" to listOf(
            "medical", "medicals", "medicine", "medicines", "medico", "medicos", "med plus", "medplus", "pharma", "pharmacy",
            "pharmacies", "chemist", "chemists", "druggist", "drug", "drugs", "aushadh", "aushadhi", "aushadhalaya", "dawa",
            "dawakhana", "davakhana", "hospital", "hospitals", "clinic", "clinics", "nursing home", "multispeciality",
            "multi speciality", "superspeciality", "healthcare", "health care", "diagnostic", "diagnostics", "pathology",
            "path lab", "pathlab", "lab ", "labs ", "laboratory", "laboratories", "imaging", "x ray", "xray",
            "mri ", "ct scan", "sonography", "dental", "dentist", "dentistry", "eye care", "eyecare", "eye hospital", "optical",
            "opticals", "optician", "netmeds", "1mg", "tata 1mg", "pharmeasy", "apollo", "practo", "doctor",
            "physio", "physiotherapy", "ayurved", "ayurvedic", "homeo", "homoeo", "homeopathy", "wellness forever",
            "healthkart", "cult fit", "cultfit", "gym ", "fitness", "yoga", "vaccin", "maternity", "children hospital",
            "ruby hall", "jehangir", "sahyadri", "deenanath", "aditya birla hospital", "dr lal", "lal path", "metropolis",
            "thyrocare", "redcliffe", "orange health", "healthians"
        ),
        "Donations" to listOf(
            "temple", "mandir", "devasthan", "devasthanam", "devalaya", "trust ", "charitable", "charity", "donation",
            "donate", "gurudwara", "church", "masjid", "mosque", "dargah", "ngo ", "giveindia", "give india",
            "ketto", "milaap", "pm cares", "relief fund", "dagdusheth", "iskcon",
            "math "
        ),
        "Groceries" to listOf(
            "kirana", "kiraana", "grocery", "groceries", "grocer", "provision", "provisions", "general store", "general stores",
            "supermarket", "super market", "mart ", "dmart", "d mart", "d-mart", "avenue supermarts", "bigbasket",
            "big basket", "bbdaily", "bb daily", "blinkit", "grofers", "zepto", "instamart", "jiomart", "reliance fresh",
            "reliance smart", "smart bazaar", "smart point", "more retail", "more supermarket", "spencer", "nature basket",
            "star bazaar", "big bazaar", "ratnadeep", "dairy", "milk", "doodh", "dudh", "gokul", "amul", "vegetable",
            "vegetables", "veggies", "sabji", "sabzi", "fruit", "fruits", "flour mill", "chakki", "atta ", "rice ", "masale",
            "masala", "spices", "oil depot", "country delight", "milkbasket", "dunzo", "meat", "mutton shop", "chicken centre",
            "chicken shop", "fish market", "licious", "freshtohome", "eggs", "egg ", "kirana store", "bhandar", "stores "
        ),
        "Food & dining" to listOf(
            "restaurant", "restaurent", "resturant", "restro", "resto ", "hospitality", "hotel", "hotels", "dhaba",
            "bhojanalaya", "bhojnalay", "khanaval", "mess ", "canteen", "cafe", "caffe", "coffee", "tea ", "chai", "chaha",
            "amruttulya", "tapri", "juice", "juices", "lassi", "ice cream", "icecream", "kulfi", "falooda", "sweets",
            "sweet mart", "sweet home", "mithai", "halwai", "bakery", "bakers", "bake ", "cake", "cakes", "pastry",
            "snacks", "namkeen", "farsan", "chaat", "pani puri", "vada pav", "vadapav", "misal", "dosa", "idli", "udupi",
            "biryani", "kebab", "tandoor", "tiffin", "thali", "pizza", "burger", "sandwich", "momos", "shawarma", "rolls ",
            "chinese", "swiggy", "zomato", "eatsure", "box8", "faasos", "behrouz", "dominos", "domino", "pizza hut", "mcdonald",
            "kfc", "subway", "starbucks", "ccd ", "cafe coffee day", "chaayos", "haldiram", "chitale", "barbeque", "bbq",
            "wow momo", "burger king", "theobroma", "foods", "food", "kitchen", "caterer", "caterers", "catering", "bar ",
            "beer", "wine", "liquor", "permit room", "pub ", "brewery", "brew ", "lounge", "dining", "eatery", "eat ",
            "family restaurant", "kitchens", "paan", "pan shop", "dabba", "bites"
        ),
        "Travel" to listOf(
            "makemytrip", "make my trip", "mmtrip", "goibibo", "yatra", "cleartrip", "ixigo", "easemytrip", "indigo",
            "interglobe", "air india", "akasa", "spicejet", "vistara", "airasia", "flight", "airline", "airlines", "airport",
            "oyo", "booking com", "agoda", "airbnb", "treebo", "fabhotel", "resort", "resorts", "holiday", "holidays",
            "tours", "travels", "tour ", "visa ", "passport", "thomas cook", "sotc"
        ),
        "Transport" to listOf(
            "uber", "ola ", "olacabs", "ola cabs", "rapido", "meru", "cab ", "cabs", "taxi", "metro", "maha metro",
            "pmpml", "msrtc", "best ", "redbus", "abhibus", "irctc", "indian railway", "railway", "railways", "rail ",
            "fastag", "toll plaza", "parking", "park plus", "yulu", "bounce", "vogo", "rickshaw", "tyre", "tyres",
            "puncture", "car wash", "car care", "car spa", "garage", "motors", "automobile", "automobiles", "auto parts",
            "spares", "maruti", "nexa", "arena ", "hyundai", "tata motors", "mahindra", "honda", "hero moto", "bajaj auto",
            "tvs ", "royal enfield", "two wheeler", "service centre"
        ),
        "Entertainment" to listOf(
            "netflix", "spotify", "hotstar", "jiohotstar", "disney", "jiocinema", "prime video", "primevideo",
            "amazon prime", "youtube", "google play", "play store", "playstore", "apple com", "apple services", "itunes",
            "bookmyshow", "book my show", "pvr", "inox", "cinepolis", "carnival", "cinema", "cinemas", "multiplex",
            "theatre", "theater", "movies", "sonyliv", "zee5", "voot", "altbalaji", "mxplayer", "gaana", "wynk",
            "jiosaavn", "audible", "kindle", "steam", "playstation", "xbox", "gaming", "games", "dream11", "my11circle",
            "mpl ", "amusement", "water park", "imagica", "wonderla", "snow world", "events", "concert", "zoo "
        ),
        "Education" to listOf(
            "school", "schools", "college", "university", "vidyalaya", "vidyalay", "vidya", "shala", "academy",
            "institute", "institution", "classes", "coaching", "tuition", "tution", "tutorial", "tutorials", "byju",
            "unacademy", "vedantu", "physics wallah", "udemy", "coursera", "upgrad", "simplilearn", "book depot",
            "book store", "bookstore", "books ", "stationery", "stationary", "stationers", "xerox", "fees", "exam ",
            "admission", "kindergarten", "play school", "preschool", "daycare", "day care", "abacus"
        ),
        "Personal care" to listOf(
            "salon", "saloon", "hair ", "haircut", "hair cut", "barber", "parlour", "parlor", "beauty", "spa ", "unisex",
            "lakme", "naturals", "jawed habib", "cosmetic", "cosmetics", "makeup", "urban company", "urbanclap",
            "grooming", "nail ", "mehendi", "tattoo"
        ),
        "Clothes" to listOf(
            "myntra", "ajio", "zara", "h&m", "hm ", "trends", "reliance trends", "lifestyle", "max fashion", "max retail",
            "pantaloons", "westside", "shoppers stop", "brand factory", "v mart", "vmart", "zudio", "fashion", "fashions",
            "apparel", "apparels", "clothing", "clothes", "cloth ", "cloth stores", "garment", "garments", "textile",
            "textiles", "saree", "sarees", "sari ", "dress", "dresses", "kurti", "boutique", "menswear", "mens wear",
            "ladies wear", "kids wear", "jeans", "shirts", "footwear", "shoes", "shoe ", "chappal", "bata", "metro shoes",
            "mochi", "puma", "nike", "adidas", "reebok", "skechers", "levis", "uniqlo", "decathlon", "manyavar", "fabindia",
            "raymond", "allen solly", "peter england", "van heusen", "louis philippe", "biba", "global desi", "tailor",
            "tailors", "jewellers", "jewellery", "jewelers", "tanishq", "kalyan jewel", "pngs", "p n gadgil"
        ),
        "Home & household" to listOf(
            "hardware", "electricals", "electrical", "electronics", "furniture", "furnishing", "furnishings", "home decor",
            "decor", "interiors", "paints", "asian paints", "plumbing", "plumber", "sanitary", "tiles", "utensils",
            "crockery", "kitchenware", "appliances", "home appliances", "croma", "vijay sales", "reliance digital",
            "ikea", "pepperfry", "urban ladder", "home centre", "homecentre", "laundry", "dry clean", "dryclean",
            "drycleaners", "ironing", "pest control", "carpenter", "electrician", "repair", "repairs", "mobile shop",
            "mobiles", "mobile gallery", "sangeetha", "poorvika", "steel", "glass", "plastic", "hardwares", "locksmith",
            "gas stove", "water purifier", "aquaguard", "kent ro", "nursery", "plants", "pet shop", "pets", "vet "
        ),
        "Online shopping" to listOf(
            "amazon", "amzn", "flipkart", "meesho", "nykaa", "snapdeal", "tatacliq", "tata cliq", "ebay", "firstcry",
            "lenskart", "shopsy", "purplle", "mamaearth", "boat ", "shopclues", "paytm mall", "indiamart", "razorpay",
            "payu", "cashfree", "ccavenue", "pinelabs", "pine labs", "shopify"
        )
    )

    /** Phrases that contain another category's keyword but mean something else ("hospitality" is not a hospital). */
    private val expenseExceptions: List<Pair<String, List<String>>> = listOf(
        "Food & dining" to listOf("hospitality", "hotel and restaurant"),
        "Education" to listOf("medical college", "nursing college", "pharmacy college", "dental college"),
        "Health" to listOf("medical store", "medical stores", "medical and general")
    )

    private val incomeRules: List<Pair<String, List<String>>> = listOf(
        // Money coming back from the person's own FD / RD / transfers: not income.
        TRANSFER_IN to listOf(
            "fd closure", "fd matur", "maturity", "premature", "fixed deposit", "term deposit", "sweep", "redemption",
            "redeemed", "self transfer", "own account", "own a/c", "disburs", "rd closure", "loan amount", "withdrawal from",
            "payout", "pay out", "liquidat", "sell proceeds", "sale proceeds"
        ),
        "Interest" to listOf("interest", "int pd", "int.pd", "int. pd", "int cr", "int.cr", "int credit", "dividend", "sb int"),
        // Money back from brokers, mutual funds and other investments: the person's own money returning.
        TRANSFER_IN to listOf(
            "zerodha", "groww", "nextbillion", "upstox", "rksv", "kuvera", "paytm money", "indian clearing", "iccl",
            "nse clearing", "nsccl", "bse ltd", "bse limited", "indian clearing corp", "clearing corp", "cams ", "camsonline",
            "kfin", "karvy", "mutual fund", "mf ", "amc", "asset management", "angel one", "angelone", "angel broking",
            "dhan ", "raise securities", "smallcase", "coin by", "5paisa", "sharekhan", "motilal", "icici securities",
            "hdfc securities", "kotak securities", "axis securities", "sbicap", "sbi securities", "broking", "securities",
            "demat", "nps ", "nps trust", "ppf", "digital gold", "safegold", "augmont", "sovereign gold", "sgb ",
            "investment", "invest"
        ),
        "Salary" to listOf("salary", "sal ", "sal cr", "salcr", "payroll", "wages", "stipend", "salary credit", "sal for"),
        "Refund" to listOf("refund", "reversal", "reversed", "cashback", "chargeback", "cash back", "rev ")
    )

    /** Long keywords that are too vague to match inside joined-up names. */
    private val noJoinedMatch = setOf(
        "premium", "policy", "society", "maintenance", "online", "service", "investment", "insure", "assurance",
        "events", "games", "plastic", "trends", "lifestyle", "steel", "repair", "repairs", "glass", "plants", "holiday"
    )

    /** Shop-name words too common to learn from ("SHREE", "SAI", "NEW", "TRADERS"). */
    private val learnStopwords = setOf(
        "shree", "shri", "sri", "new", "the", "and", "sai", "om", "jai", "maa", "shivam", "ganesh", "balaji",
        "india", "pvt", "ltd", "limited", "private", "llp", "enterprises", "enterprise", "traders", "trading",
        "services", "service", "centre", "center", "store", "stores", "shop", "company", "corporation", "agency",
        "agencies", "group", "sons", "brothers", "bros", "pune", "mumbai", "upi", "payment", "payments", "mr", "mrs",
        "ms", "kumar", "bank", "paytm", "phonepe", "gpay", "bharatpe", "merchant", "online", "retail"
    )

    /** Lowercase, punctuation to spaces ("A/c" -> "a c"), single spaces. Keeps '&' (h&m). */
    private fun normalise(s: String): String = s.lowercase().replace(Regex("[^a-z0-9&]+"), " ")

    private fun matches(text: String, keyword: String, joined: String?): Boolean {
        val wholeWord = keyword.endsWith(" ") || keyword.endsWith("/")
        val k = normalise(keyword).trim()
        if (k.isEmpty()) return false
        if (text.contains(" " + k + if (wholeWord) " " else "")) return true
        if (joined != null && !wholeWord && k.length >= 6 && k !in noJoinedMatch) {
            return joined.contains(k.replace(" ", ""))
        }
        return false
    }

    private fun firstRule(rules: List<Pair<String, List<String>>>, raw: String, allowJoined: Boolean): String? {
        val text = " " + normalise(raw).trim() + " "
        val joined = if (allowJoined) normalise(raw).replace(" ", "") else null
        if (rules === expenseRules) {
            for ((name, words) in expenseExceptions) if (words.any { matches(text, it, joined) }) return name
        }
        for ((name, words) in rules) if (words.any { matches(text, it, joined) }) return name
        return null
    }

    /** The words of a merchant name worth learning from: 4+ letters, not a generic shop word. */
    fun learnableWords(merchant: String): List<String> =
        normalise(merchant).split(" ").filter { w -> w.length >= 4 && w.any { it.isLetter() } && w !in learnStopwords }

    /**
     * Builds "word -> category" from merchants the person has categorised themselves. A word used for
     * two different categories is ambiguous and dropped.
     */
    fun learnWords(examples: List<Pair<String, String>>): Map<String, String> {
        val seen = HashMap<String, MutableSet<String>>()
        for ((merchant, category) in examples) {
            for (w in learnableWords(merchant)) seen.getOrPut(w) { HashSet() }.add(category)
        }
        return seen.filterValues { it.size == 1 }.mapValues { it.value.first() }
    }

    /**
     * Returns the best category name, or null when nothing matches. Order: the merchant name against
     * the dictionary, then words learned from the person's own choices, then the rest of the message,
     * then "looks like a person" for UPI transfers to and from people.
     */
    fun guess(isIncome: Boolean, merchant: String?, body: String, learned: Map<String, String> = emptyMap()): String? {
        val rules = if (isIncome) incomeRules else expenseRules
        if (merchant != null) {
            firstRule(rules, merchant, allowJoined = true)?.let { return it }
            learnableWords(merchant).firstNotNullOfOrNull { learned[it] }?.let { return it }
        }
        firstRule(rules, body, allowJoined = false)?.let { return it }
        if (merchant != null && looksLikePerson(merchant)) return if (isIncome) "Received from people" else "Sent to people"
        return null
    }

    /** "RAMESH KUMAR", "PRIYA S" look like people; anything with business words does not. */
    fun looksLikePerson(merchant: String): Boolean {
        val words = merchant.trim().split(" ").filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > 4) return false
        if (merchant.any { it.isDigit() || it == '&' || it == '.' || it == '@' || it == '*' }) return false
        // Indian shop names usually start like this: "SHREE VIGNESHWARA", "SAI KRUPA", "OM SAI".
        val shopStarts = setOf("shree", "shri", "sri", "sree", "shiv", "sai", "om", "jai", "jay", "maa", "new", "hotel", "the", "royal", "krishna", "ganesh", "balaji", "laxmi", "lakshmi", "mahalaxmi", "ambika", "durga", "hari", "jagdamba", "gurukrupa", "swami", "siddhi", "vighnaharta", "vigneshwara", "mauli", "datta", "shubham", "mangal", "annapurna")
        if (words.first().lowercase() in shopStarts) return false
        val business = listOf(
            "pvt", "ltd", "limited", "llp", "store", "stores", "mart", "shop", "enterprise", "enterprises", "traders",
            "services", "solutions", "technologies", "tech", "india", "company", "corp", "agency", "centre", "center",
            "hotel", "cafe", "restaurant", "medical", "hospital", "bank", "finance", "payments", "retail", "brothers",
            "and", "sons", "industries", "foods", "collection", "point", "bazar", "bazaar", "online", "digital",
            "corporation", "clearing", "securities", "broking", "capital", "fund", "funds", "amc", "asset", "management",
            "investments", "wealth", "insurance", "life", "trust", "exchange", "financial", "fintech", "ventures", "global"
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
