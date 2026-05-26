package app.splitup.shared.domain.split

import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId
import kotlinx.serialization.Serializable

/**
 * How a total cost is divided among participants. Deterministic: re-applying
 * the same input always produces the same per-person allocation, with rounding
 * remainders distributed in stable PersonId order (matters for sync).
 */
@Serializable
sealed interface SplitStrategy {

    @Serializable
    data class Equal(val participants: List<PersonId>) : SplitStrategy {
        init { require(participants.isNotEmpty()) { "Equal split needs at least one participant" } }
    }

    @Serializable
    data class Exact(val amounts: Map<PersonId, Money>) : SplitStrategy {
        init { require(amounts.isNotEmpty()) { "Exact split needs at least one participant" } }
    }

    @Serializable
    data class Percent(val basisPoints: Map<PersonId, Int>) : SplitStrategy {
        init {
            require(basisPoints.isNotEmpty())
            require(basisPoints.values.all { it >= 0 }) { "Negative percentage" }
            require(basisPoints.values.sum() == TOTAL_BP) {
                "Percentages must sum to 100.00% (10000 bp), got ${basisPoints.values.sum()}"
            }
        }
        companion object { const val TOTAL_BP = 10_000 }
    }

    @Serializable
    data class Shares(val shares: Map<PersonId, Int>) : SplitStrategy {
        init {
            require(shares.isNotEmpty())
            require(shares.values.all { it > 0 }) { "All share counts must be positive" }
        }
    }

    /** Equal split with per-person plus/minus adjustments — Splitwise's "extra drink" case. */
    @Serializable
    data class Adjustment(
        val participants: List<PersonId>,
        val adjustments: Map<PersonId, Money>,
    ) : SplitStrategy {
        init {
            require(participants.isNotEmpty())
            require(adjustments.keys.all { it in participants }) {
                "Adjustment for non-participant"
            }
        }
    }
}
