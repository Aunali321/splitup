package app.splitup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.splitup.shared.domain.model.Group
import app.splitup.shared.domain.model.GroupType
import app.splitup.ui.theme.groupTint
import coil3.compose.AsyncImage

/** Group tile: the imported avatar image when present, else a tinted type glyph. */
@Composable
fun GroupAvatar(group: Group, size: Dp = 56.dp, corner: Dp = 16.dp) {
    val shape = RoundedCornerShape(corner)
    if (group.avatarUrl != null) {
        AsyncImage(
            model = group.avatarUrl,
            contentDescription = group.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(shape),
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(shape).background(groupTint(group.name)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = groupTypeIcon(group.type),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size / 2),
            )
        }
    }
}

fun groupTypeIcon(type: GroupType): ImageVector = when (type) {
    GroupType.HOME, GroupType.APARTMENT, GroupType.HOUSE -> Icons.Rounded.Home
    GroupType.TRIP -> Icons.Rounded.Flight
    GroupType.COUPLE -> Icons.Rounded.Favorite
    else -> Icons.Outlined.Groups
}
