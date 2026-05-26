package app.splitup.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Currency(
    val code: String,
    val symbol: String,
    val decimals: Int,
    val displayName: String = code,
) {
    init {
        require(code.length == 3) { "Currency code must be ISO 4217 (3 letters)" }
        require(decimals in 0..4) { "decimals out of range" }
    }

    val scale: Long = TENS[decimals]

    companion object {
        private val TENS = longArrayOf(1L, 10L, 100L, 1_000L, 10_000L)

        val USD = Currency("USD", "$", 2, "US Dollar")
        val INR = Currency("INR", "₹", 2, "Indian Rupee")
        val EUR = Currency("EUR", "€", 2, "Euro")
        val GBP = Currency("GBP", "£", 2, "British Pound")
        val JPY = Currency("JPY", "¥", 0, "Japanese Yen")
        val CNY = Currency("CNY", "¥", 2, "Chinese Yuan")
        val AUD = Currency("AUD", "A$", 2, "Australian Dollar")
        val CAD = Currency("CAD", "C$", 2, "Canadian Dollar")
        val CHF = Currency("CHF", "CHF", 2, "Swiss Franc")
        val SGD = Currency("SGD", "S$", 2, "Singapore Dollar")
        val AED = Currency("AED", "د.إ", 2, "UAE Dirham")
        val SAR = Currency("SAR", "﷼", 2, "Saudi Riyal")
        val KRW = Currency("KRW", "₩", 0, "Korean Won")
        val THB = Currency("THB", "฿", 2, "Thai Baht")
        val IDR = Currency("IDR", "Rp", 2, "Indonesian Rupiah")
        val MYR = Currency("MYR", "RM", 2, "Malaysian Ringgit")
        val PHP = Currency("PHP", "₱", 2, "Philippine Peso")
        val VND = Currency("VND", "₫", 0, "Vietnamese Dong")
        val NPR = Currency("NPR", "₨", 2, "Nepalese Rupee")
        val LKR = Currency("LKR", "Rs", 2, "Sri Lankan Rupee")
        val PKR = Currency("PKR", "₨", 2, "Pakistani Rupee")
        val BDT = Currency("BDT", "৳", 2, "Bangladeshi Taka")
        val NZD = Currency("NZD", "NZ$", 2, "New Zealand Dollar")
        val ZAR = Currency("ZAR", "R", 2, "South African Rand")
        val BRL = Currency("BRL", "R$", 2, "Brazilian Real")
        val MXN = Currency("MXN", "Mex$", 2, "Mexican Peso")
        val TRY = Currency("TRY", "₺", 2, "Turkish Lira")
        val RUB = Currency("RUB", "₽", 2, "Russian Ruble")
        val SEK = Currency("SEK", "kr", 2, "Swedish Krona")
        val NOK = Currency("NOK", "kr", 2, "Norwegian Krone")
        val DKK = Currency("DKK", "kr", 2, "Danish Krone")
        val PLN = Currency("PLN", "zł", 2, "Polish Złoty")
        val HKD = Currency("HKD", "HK$", 2, "Hong Kong Dollar")
        val TWD = Currency("TWD", "NT$", 2, "Taiwan Dollar")
        val EGP = Currency("EGP", "E£", 2, "Egyptian Pound")
        val NGN = Currency("NGN", "₦", 2, "Nigerian Naira")
        val KES = Currency("KES", "KSh", 2, "Kenyan Shilling")
        val ILS = Currency("ILS", "₪", 2, "Israeli Shekel")
        val BHD = Currency("BHD", ".د.ب", 3, "Bahraini Dinar")
        val KWD = Currency("KWD", "د.ك", 3, "Kuwaiti Dinar")

        /** Fallback currency before the user completes onboarding. */
        val DEFAULT: Currency = USD

        val ALL: List<Currency> = listOf(
            USD, INR, EUR, GBP, JPY, CNY, AUD, CAD, CHF, SGD,
            AED, SAR, KRW, THB, IDR, MYR, PHP, VND,
            NPR, LKR, PKR, BDT,
            NZD, ZAR, BRL, MXN, TRY, RUB,
            SEK, NOK, DKK, PLN, HKD, TWD,
            EGP, NGN, KES, ILS, BHD, KWD,
        )

        private val byCode: Map<String, Currency> = ALL.associateBy { it.code }

        fun ofCode(code: String): Currency? = byCode[code.uppercase()]

        fun ofCodeOrDefault(code: String): Currency =
            byCode[code.uppercase()] ?: Currency(code.uppercase(), code.uppercase(), 2)
    }
}

/**
 * Currency-safe amount stored in the currency's minor units (cents, paise, yen).
 * Cross-currency arithmetic throws; conversion is explicit via [CurrencyConverter].
 */
@Serializable
data class Money(
    val minorUnits: Long,
    val currency: Currency,
) : Comparable<Money> {

    private fun sameCurrencyCheck(other: Money) {
        require(currency == other.currency) {
            "Currency mismatch: ${currency.code} vs ${other.currency.code}. Use CurrencyConverter."
        }
    }

    operator fun plus(other: Money): Money {
        sameCurrencyCheck(other)
        return copy(minorUnits = minorUnits + other.minorUnits)
    }

    operator fun minus(other: Money): Money {
        sameCurrencyCheck(other)
        return copy(minorUnits = minorUnits - other.minorUnits)
    }

    operator fun unaryMinus(): Money = copy(minorUnits = -minorUnits)
    operator fun times(factor: Int): Money = copy(minorUnits = minorUnits * factor)
    operator fun times(factor: Long): Money = copy(minorUnits = minorUnits * factor)

    override operator fun compareTo(other: Money): Int {
        sameCurrencyCheck(other)
        return minorUnits.compareTo(other.minorUnits)
    }

    val isZero: Boolean get() = minorUnits == 0L
    val isPositive: Boolean get() = minorUnits > 0L
    val isNegative: Boolean get() = minorUnits < 0L
    fun abs(): Money = if (minorUnits < 0) copy(minorUnits = -minorUnits) else this

    fun format(): String {
        val sign = if (minorUnits < 0) "-" else ""
        val mag = if (minorUnits < 0) -minorUnits else minorUnits
        if (currency.decimals == 0) return "$sign${currency.symbol}$mag"
        val major = mag / currency.scale
        val minor = mag % currency.scale
        val padded = minor.toString().padStart(currency.decimals, '0')
        return "$sign${currency.symbol}$major.$padded"
    }

    companion object {
        fun zero(currency: Currency): Money = Money(0L, currency)
        fun ofMinor(minorUnits: Long, currency: Currency): Money = Money(minorUnits, currency)

        fun parse(value: String, currency: Currency): Money {
            val trimmed = value.trim()
            require(trimmed.isNotEmpty()) { "Empty amount" }
            val negative = trimmed.startsWith('-')
            val body = trimmed.removePrefix("-").removePrefix("+")
            val parts = body.split('.')
            require(parts.size in 1..2) { "Invalid amount: $value" }
            val major = parts[0].toLong()
            val minor = if (parts.size == 2) {
                val frac = parts[1].padEnd(currency.decimals, '0')
                require(frac.length == currency.decimals) {
                    "Too many decimal places for ${currency.code}: $value"
                }
                frac.toLong()
            } else 0L
            val total = major * currency.scale + minor
            return Money(if (negative) -total else total, currency)
        }
    }
}

fun Iterable<Money>.sumOrZero(currency: Currency): Money =
    fold(Money.zero(currency)) { acc, m -> acc + m }
