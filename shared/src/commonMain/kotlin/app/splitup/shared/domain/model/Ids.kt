package app.splitup.shared.domain.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable @JvmInline value class PersonId(val value: String)
@Serializable @JvmInline value class GroupId(val value: String)
@Serializable @JvmInline value class ExpenseId(val value: String)
@Serializable @JvmInline value class CategoryId(val value: String)
@Serializable @JvmInline value class CommentId(val value: String)
