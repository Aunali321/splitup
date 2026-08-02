package app.splitup.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Category(
    val id: CategoryId,
    val parentId: CategoryId?,
    val name: String,
    val icon: String,
    val sortOrder: Int,
)

/**
 * The Splitwise category taxonomy — seven parent groups, same names and
 * grouping, so imported data and muscle memory carry over 1:1. [icon] is a
 * stable slug the UI maps to a vector; ids are ours (Splitwise's numeric ids
 * are mapped in the importer).
 */
object DefaultCategories {
    val UNCATEGORIZED = CategoryId("uncategorized")

    private fun cat(id: String, parent: String?, name: String, icon: String = id, order: Int = 0) =
        Category(CategoryId(id), parent?.let { CategoryId(it) }, name, icon, order)

    val all: List<Category> = listOf(
        cat("uncategorized", null, "Uncategorized", order = 0),
        cat("general", "uncategorized", "General", order = 1),

        cat("entertainment", null, "Entertainment", order = 100),
        cat("games", "entertainment", "Games", order = 101),
        cat("movies", "entertainment", "Movies", order = 102),
        cat("music", "entertainment", "Music", order = 103),
        cat("sports", "entertainment", "Sports", order = 104),
        cat("entertainment_other", "entertainment", "Other", "entertainment", 105),

        cat("food_drink", null, "Food and drink", "food", 200),
        cat("dining", "food_drink", "Dining out", order = 201),
        cat("groceries", "food_drink", "Groceries", order = 202),
        cat("liquor", "food_drink", "Liquor", order = 203),
        cat("food_other", "food_drink", "Other", "food", 204),

        cat("home", null, "Home", order = 300),
        cat("electronics", "home", "Electronics", order = 301),
        cat("furniture", "home", "Furniture", order = 302),
        cat("household", "home", "Household supplies", "supplies", 303),
        cat("maintenance", "home", "Maintenance", order = 304),
        cat("mortgage", "home", "Mortgage", order = 305),
        cat("pets", "home", "Pets", order = 306),
        cat("rent", "home", "Rent", order = 307),
        cat("services", "home", "Services", order = 308),
        cat("home_other", "home", "Other", "home", 309),

        cat("life", null, "Life", order = 400),
        cat("clothing", "life", "Clothing", order = 401),
        cat("gifts", "life", "Gifts", order = 402),
        cat("insurance", "life", "Insurance", order = 403),
        cat("medical", "life", "Medical expenses", order = 404),
        cat("taxes", "life", "Taxes", order = 405),
        cat("life_other", "life", "Other", "life", 406),

        cat("transportation", null, "Transportation", "transport", 500),
        cat("bicycle", "transportation", "Bicycle", order = 501),
        cat("bus_train", "transportation", "Bus/train", "transit", 502),
        cat("car", "transportation", "Car", order = 503),
        cat("fuel", "transportation", "Gas/fuel", order = 504),
        cat("hotel", "transportation", "Hotel", order = 505),
        cat("parking", "transportation", "Parking", order = 506),
        cat("plane", "transportation", "Plane", order = 507),
        cat("taxi", "transportation", "Taxi", order = 508),
        cat("transport_other", "transportation", "Other", "transport", 509),

        cat("utilities", null, "Utilities", order = 600),
        cat("cleaning", "utilities", "Cleaning", order = 601),
        cat("electricity", "utilities", "Electricity", order = 602),
        cat("heat_gas", "utilities", "Heat/gas", "heat", 603),
        cat("trash", "utilities", "Trash", order = 604),
        cat("tv_phone_internet", "utilities", "TV/Phone/Internet", "tv", 605),
        cat("water", "utilities", "Water", order = 606),
        cat("utilities_other", "utilities", "Other", "utilities", 607),
    )

    private val byId: Map<CategoryId, Category> = all.associateBy { it.id }

    val parents: List<Category> = all.filter { it.parentId == null }.sortedBy { it.sortOrder }

    fun get(id: CategoryId): Category = byId[id] ?: byId.getValue(UNCATEGORIZED)

    fun childrenOf(parent: CategoryId): List<Category> =
        all.filter { it.parentId == parent }.sortedBy { it.sortOrder }
}
