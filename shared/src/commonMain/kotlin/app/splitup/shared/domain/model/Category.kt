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

object DefaultCategories {
    private fun cat(id: String, parent: String?, name: String, icon: String, order: Int) =
        Category(CategoryId(id), parent?.let { CategoryId(it) }, name, icon, order)

    val all: List<Category> = listOf(
        cat("uncategorized", null, "Uncategorized", "uncategorized", 0),

        cat("food_drink", null, "Food & Drink", "food", 100),
        cat("groceries", "food_drink", "Groceries", "groceries", 101),
        cat("dining", "food_drink", "Dining out", "dining", 102),
        cat("liquor", "food_drink", "Liquor", "liquor", 103),

        cat("home", null, "Home", "home", 200),
        cat("rent", "home", "Rent", "rent", 201),
        cat("mortgage", "home", "Mortgage", "mortgage", 202),
        cat("electronics", "home", "Electronics", "electronics", 203),
        cat("furniture", "home", "Furniture", "furniture", 204),
        cat("household", "home", "Household supplies", "supplies", 205),
        cat("maintenance", "home", "Maintenance", "maintenance", 206),
        cat("mortgage_pets", "home", "Pets", "pets", 207),

        cat("utilities", null, "Utilities", "utilities", 300),
        cat("electricity", "utilities", "Electricity", "electricity", 301),
        cat("water", "utilities", "Water", "water", 302),
        cat("gas", "utilities", "Gas", "gas", 303),
        cat("internet", "utilities", "Internet", "internet", 304),
        cat("trash", "utilities", "Trash", "trash", 305),
        cat("phone", "utilities", "Phone", "phone", 306),
        cat("tv", "utilities", "TV", "tv", 307),

        cat("transportation", null, "Transportation", "transport", 400),
        cat("car", "transportation", "Car", "car", 401),
        cat("fuel", "transportation", "Fuel", "fuel", 402),
        cat("parking", "transportation", "Parking", "parking", 403),
        cat("taxi", "transportation", "Taxi / Rideshare", "taxi", 404),
        cat("public_transit", "transportation", "Public transit", "transit", 405),
        cat("flight", "transportation", "Flight", "flight", 406),

        cat("entertainment", null, "Entertainment", "entertainment", 500),
        cat("movies", "entertainment", "Movies", "movies", 501),
        cat("music", "entertainment", "Music", "music", 502),
        cat("sports", "entertainment", "Sports", "sports", 503),
        cat("games", "entertainment", "Games", "games", 504),

        cat("life", null, "Life", "life", 600),
        cat("childcare", "life", "Childcare", "childcare", 601),
        cat("clothing", "life", "Clothing", "clothing", 602),
        cat("education", "life", "Education", "education", 603),
        cat("gifts", "life", "Gifts", "gifts", 604),
        cat("insurance", "life", "Insurance", "insurance", 605),
        cat("medical", "life", "Medical", "medical", 606),
        cat("taxes", "life", "Taxes", "taxes", 607),

        cat("travel", null, "Travel", "travel", 700),
        cat("lodging", "travel", "Lodging", "lodging", 701),
        cat("travel_misc", "travel", "Travel — other", "travel_other", 799),

        cat("financial", null, "Financial", "financial", 800),
        cat("fees", "financial", "Bank fees", "fees", 801),
        cat("settlement", "financial", "Settlement", "settlement", 802),
    )
}
