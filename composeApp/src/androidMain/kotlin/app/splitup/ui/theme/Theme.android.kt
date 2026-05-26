package app.splitup.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun resolveColorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme {
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return when {
        dynamicColor && supportsDynamic -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }
}
