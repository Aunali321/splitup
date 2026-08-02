package app.splitup.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// SplitUp! identity: cool teal primary, warm amber accent. M3 expressive baseline.
private val PrimaryLight = Color(0xFF006A65)
private val OnPrimaryLight = Color(0xFFFFFFFF)
private val PrimaryContainerLight = Color(0xFF6FF7EE)
private val OnPrimaryContainerLight = Color(0xFF00201E)

private val SecondaryLight = Color(0xFF4A6361)
private val OnSecondaryLight = Color(0xFFFFFFFF)
private val SecondaryContainerLight = Color(0xFFCCE8E5)
private val OnSecondaryContainerLight = Color(0xFF051F1E)

private val TertiaryLight = Color(0xFF466179)
private val OnTertiaryLight = Color(0xFFFFFFFF)
private val TertiaryContainerLight = Color(0xFFCDE5FF)
private val OnTertiaryContainerLight = Color(0xFF001D32)

private val ErrorLight = Color(0xFFBA1A1A)
private val OnErrorLight = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFFFDAD6)

private val BackgroundLight = Color(0xFFF4FBF9)
private val SurfaceLight = Color(0xFFF4FBF9)
private val SurfaceVariantLight = Color(0xFFDAE5E2)
private val OnSurfaceLight = Color(0xFF161D1C)
private val OnSurfaceVariantLight = Color(0xFF3F4947)
private val OutlineLight = Color(0xFF6F7977)

// Dark equivalents
private val PrimaryDark = Color(0xFF4FDAD1)
private val OnPrimaryDark = Color(0xFF003734)
private val PrimaryContainerDark = Color(0xFF00504C)
private val OnPrimaryContainerDark = Color(0xFF6FF7EE)

private val SecondaryDark = Color(0xFFB0CCC9)
private val OnSecondaryDark = Color(0xFF1B3533)
private val SecondaryContainerDark = Color(0xFF324B49)
private val OnSecondaryContainerDark = Color(0xFFCCE8E5)

private val TertiaryDark = Color(0xFFAECAE6)
private val OnTertiaryDark = Color(0xFF153349)
private val TertiaryContainerDark = Color(0xFF2D4961)
private val OnTertiaryContainerDark = Color(0xFFCDE5FF)

private val ErrorDark = Color(0xFFFFB4AB)
private val OnErrorDark = Color(0xFF690005)
private val ErrorContainerDark = Color(0xFF93000A)

private val BackgroundDark = Color(0xFF0E1514)
private val SurfaceDark = Color(0xFF0E1514)
private val SurfaceVariantDark = Color(0xFF3F4947)
private val OnSurfaceDark = Color(0xFFDDE4E2)
private val OnSurfaceVariantDark = Color(0xFFBEC9C6)
private val OutlineDark = Color(0xFF889390)

// Semantic colors for balances (rendered consistently in any color scheme)
val PositiveBalance = Color(0xFF1B7F44) // you are owed
val PositiveBalanceDark = Color(0xFF6BD494)
val NegativeBalance = Color(0xFFC4302B) // you owe
val NegativeBalanceDark = Color(0xFFFF7A75)

private val groupTints = listOf(
    Color(0xFFE65100), Color(0xFF1B7F44), Color(0xFF7A4F9C),
    Color(0xFFC2185B), Color(0xFF1976D2), Color(0xFF00897B),
    Color(0xFFD84315), Color(0xFF455A64),
)

/** Stable per-group accent, hashed from the group name. */
fun groupTint(name: String): Color {
    var h = 0
    for (c in name) h = 31 * h + c.code
    return groupTints[(h.rem(groupTints.size) + groupTints.size).rem(groupTints.size)]
}

val LightScheme: ColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
)

val DarkScheme: ColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
)
