package app.splitup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.splitup.shared.domain.model.Person

@Composable
fun PersonAvatar(person: Person, size: Dp = 40.dp) {
    val bg = avatarColor(person.id.value)
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = person.initials,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private val palette = listOf(
    Color(0xFF1F8A70), Color(0xFFBE7C4D), Color(0xFF8E44AD), Color(0xFFE67E22),
    Color(0xFF2980B9), Color(0xFFC0392B), Color(0xFF16A085), Color(0xFF7F8C8D),
)

private fun avatarColor(seed: String): Color {
    var h = 0
    for (c in seed) h = 31 * h + c.code
    return palette[(h.rem(palette.size) + palette.size).rem(palette.size)]
}
