package app.splitup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Attractions
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Commute
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalGroceryStore
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.splitup.shared.domain.model.CategoryId
import app.splitup.shared.domain.model.DefaultCategories

/** Square tinted tile with the category's vector — the app-wide category glyph. */
@Composable
fun CategoryIcon(
    categoryId: CategoryId,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = categoryVector(DefaultCategories.get(categoryId).icon),
            contentDescription = DefaultCategories.get(categoryId).name,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/** The vector for a category — exposed for surfaces that draw the glyph themselves. */
fun categoryVectorFor(categoryId: CategoryId): ImageVector =
    categoryVector(DefaultCategories.get(categoryId).icon)

private fun categoryVector(icon: String): ImageVector = when (icon) {
    "general", "uncategorized" -> Icons.Outlined.Receipt

    "entertainment" -> Icons.Outlined.Attractions
    "games" -> Icons.Outlined.SportsEsports
    "movies" -> Icons.Outlined.Movie
    "music" -> Icons.Outlined.MusicNote
    "sports" -> Icons.Outlined.SportsSoccer

    "food" -> Icons.Outlined.Restaurant
    "dining" -> Icons.Outlined.LocalDining
    "groceries" -> Icons.Outlined.LocalGroceryStore
    "liquor" -> Icons.Outlined.LocalBar

    "home" -> Icons.Outlined.Home
    "electronics" -> Icons.Outlined.Devices
    "furniture" -> Icons.Outlined.Chair
    "supplies" -> Icons.Outlined.Inventory2
    "maintenance" -> Icons.Outlined.Build
    "mortgage" -> Icons.Outlined.AccountBalance
    "pets" -> Icons.Outlined.Pets
    "rent" -> Icons.Outlined.Apartment
    "services" -> Icons.Outlined.Handyman

    "life" -> Icons.Outlined.Favorite
    "clothing" -> Icons.Outlined.Checkroom
    "gifts" -> Icons.Outlined.CardGiftcard
    "insurance" -> Icons.Outlined.HealthAndSafety
    "medical" -> Icons.Outlined.MedicalServices
    "taxes" -> Icons.Outlined.RequestQuote

    "transport" -> Icons.Outlined.Commute
    "bicycle" -> Icons.Outlined.DirectionsBike
    "transit" -> Icons.Outlined.Train
    "car" -> Icons.Outlined.DirectionsCar
    "fuel" -> Icons.Outlined.LocalGasStation
    "hotel" -> Icons.Outlined.Hotel
    "parking" -> Icons.Outlined.LocalParking
    "plane" -> Icons.Outlined.Flight
    "taxi" -> Icons.Outlined.LocalTaxi

    "utilities" -> Icons.Outlined.Lightbulb
    "cleaning" -> Icons.Outlined.CleaningServices
    "electricity" -> Icons.Outlined.Bolt
    "heat" -> Icons.Outlined.LocalFireDepartment
    "trash" -> Icons.Outlined.Delete
    "tv" -> Icons.Outlined.Tv
    "water" -> Icons.Outlined.WaterDrop

    else -> Icons.Outlined.Receipt
}
