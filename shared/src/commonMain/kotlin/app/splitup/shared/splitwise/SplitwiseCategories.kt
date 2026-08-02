package app.splitup.shared.splitwise

import app.splitup.shared.domain.model.CategoryId
import app.splitup.shared.domain.model.DefaultCategories

/**
 * Splitwise's numeric category ids (stable across their API since v3) mapped
 * onto the local taxonomy, which mirrors theirs one to one.
 */
object SplitwiseCategories {

    private val byRemoteId: Map<Long, CategoryId> = buildMap {
        fun put(remoteId: Long, local: String) = put(remoteId, CategoryId(local))

        put(1, "utilities")
        put(2, "uncategorized")
        put(3, "rent")
        put(4, "mortgage")
        put(5, "electricity")
        put(6, "heat_gas")
        put(7, "water")
        put(8, "tv_phone_internet")
        put(9, "parking")
        put(10, "insurance")
        put(11, "utilities_other")
        put(12, "groceries")
        put(13, "dining")
        put(14, "household")
        put(15, "car")
        put(16, "furniture")
        put(17, "maintenance")
        put(18, "general")
        put(19, "entertainment")
        put(20, "games")
        put(21, "movies")
        put(22, "music")
        put(23, "entertainment_other")
        put(24, "sports")
        put(25, "food_drink")
        put(26, "food_other")
        put(27, "home")
        put(28, "home_other")
        put(29, "pets")
        put(30, "services")
        put(31, "transportation")
        put(32, "bus_train")
        put(33, "fuel")
        put(34, "transport_other")
        put(35, "plane")
        put(36, "taxi")
        put(37, "trash")
        put(38, "liquor")
        put(39, "electronics")
        put(40, "life")
        put(41, "clothing")
        put(42, "gifts")
        put(43, "medical")
        put(44, "life_other")
        put(45, "taxes")
        put(46, "bicycle")
        put(47, "hotel")
        put(48, "cleaning")
    }

    fun toLocal(remoteId: Long): CategoryId = byRemoteId[remoteId] ?: DefaultCategories.UNCATEGORIZED
}
