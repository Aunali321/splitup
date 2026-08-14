package app.splitup.ui.util

/** Past this the value stops being a plausible amount and starts risking Long overflow. */
private const val MAX_WHOLE_DIGITS = 15

/**
 * Digits plus at most one dot, fraction capped to [decimals]; everything else stripped.
 *
 * Both '.' and ',' count as the decimal separator, because a decimal keyboard offers
 * whichever one the device locale uses — dropping the comma turned "12,50" into 1250.
 * The last separator is the decimal one; any earlier separator is grouping.
 */
fun cleanDecimal(value: String, decimals: Int): String {
    val kept = value.filter { it.isDigit() || it == '.' || it == ',' }
    val lastSep = kept.indexOfLast { it == '.' || it == ',' }
    val whole = (if (lastSep < 0) kept else kept.take(lastSep))
        .filter { it.isDigit() }
        .take(MAX_WHOLE_DIGITS)
    if (decimals == 0 || lastSep < 0) return whole
    val fraction = kept.substring(lastSep + 1).take(decimals)
    return "$whole.$fraction"
}
