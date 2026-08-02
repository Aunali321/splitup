package app.splitup.shared.data.repository

import app.splitup.shared.data.local.dao.GroupDao
import app.splitup.shared.data.mapper.toDomain
import app.splitup.shared.data.mapper.toEntity
import app.splitup.shared.domain.model.Group
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class RoomGroupRepository(
    private val dao: GroupDao,
    private val clock: Clock = Clock.System,
) : GroupRepository {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeAll(includeArchived: Boolean): Flow<List<Group>> =
        dao.observeAll(includeArchived).flatMapLatest { groupEntities ->
            if (groupEntities.isEmpty()) flow { emit(emptyList<Group>()) }
            else combine(groupEntities.map { g -> dao.observeMembers(g.id).map { g to it } }) { array ->
                array.map { (g, members) -> g.toDomain(members) }
            }
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observe(id: GroupId): Flow<Group?> =
        dao.observe(id.value).flatMapLatest { g ->
            if (g == null) flow<Group?> { emit(null) }
            else dao.observeMembers(g.id).map<List<app.splitup.shared.data.local.entity.GroupMemberEntity>, Group?> { members -> g.toDomain(members) }
        }

    override suspend fun get(id: GroupId): Group? {
        val g = dao.get(id.value) ?: return null
        return g.toDomain(dao.getMembers(g.id))
    }

    override suspend fun save(group: Group) =
        dao.upsertWithMembers(group.toEntity(), group.members.map { it.toEntity(group.id) })

    override suspend fun archive(id: GroupId) =
        dao.archive(id.value, clock.now().toEpochMilliseconds())

    override suspend fun delete(id: GroupId) = dao.delete(id.value)

    override suspend fun findByExternal(source: String, externalId: String): Group? {
        val g = dao.findByExternal(source, externalId) ?: return null
        return g.toDomain(dao.getMembers(g.id))
    }
}
