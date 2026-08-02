package app.splitup.ui.util

/** Digits plus at most one dot, fraction capped to [decimals]; everything else stripped. */
fun cleanDecimal(value: String, decimals: Int): String {
    val dot = value.indexOf('.')
    val filtered = value.filterIndexed { i, c -> c.isDigit() || (c == '.' && i == dot) }
    if (decimals == 0) return filtered.substringBefore('.')
    val sep = filtered.indexOf('.')
    return if (sep < 0) filtered else filtered.substring(0, minOf(filtered.length, sep + 1 + decimals))
}
