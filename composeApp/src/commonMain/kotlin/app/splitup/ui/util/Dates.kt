package app.splitup.ui.util

import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

private val MONTHS_SHORT = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

private val MONTHS_FULL = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

fun monthAbbr(month: Int): String = MONTHS_SHORT[month - 1]

/** "5 Aug 2026" */
fun LocalDate.formatShort(): String = "$dayOfMonth ${monthAbbr(monthNumber)} $year"

/** "August 2026" — list section header. */
fun LocalDate.monthHeader(): String = "${MONTHS_FULL[monthNumber - 1]} $year"

/** "May 24, 2026" — the expense-detail metadata style. */
fun LocalDate.formatMedium(): String = "${monthAbbr(monthNumber)} $dayOfMonth, $year"

/** Rows settled up with no activity since this date collapse behind the expander. */
fun staleSettledCutoff(clock: Clock, timeZone: TimeZone): LocalDate =
    clock.now().toLocalDateTime(timeZone).date.minus(DatePeriod(months = 1))

/** "25 May, 19:17" — activity feed timestamp. */
fun Instant.formatEventTime(timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val dt = toLocalDateTime(timeZone)
    val hh = dt.hour.toString().padStart(2, '0')
    val mm = dt.minute.toString().padStart(2, '0')
    return "${dt.dayOfMonth} ${monthAbbr(dt.monthNumber)}, $hh:$mm"
}
