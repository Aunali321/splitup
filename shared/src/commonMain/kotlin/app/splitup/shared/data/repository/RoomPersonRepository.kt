package app.splitup.shared.data.repository

import app.splitup.shared.data.local.dao.PersonDao
import app.splitup.shared.data.mapper.toDomain
import app.splitup.shared.data.mapper.toEntity
import app.splitup.shared.domain.model.Person
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.PersonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class RoomPersonRepository(
    private val dao: PersonDao,
    private val clock: Clock = Clock.System,
) : PersonRepository {

    override fun observeAll(): Flow<List<Person>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeFriends(): Flow<List<Person>> =
        dao.observeFriends().map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: PersonId): Person? = dao.get(id.value)?.toDomain()

    override fun observe(id: PersonId): Flow<Person?> =
        dao.observe(id.value).map { it?.toDomain() }

    override fun observeMe(): Flow<Person> =
        dao.observeMe().filterNotNull().map { it.toDomain() }

    override suspend fun getMe(): Person? = dao.getMe()?.toDomain()

    override suspend fun save(person: Person) = dao.upsert(person.toEntity())

    override suspend fun delete(id: PersonId) =
        dao.softDelete(id.value, clock.now().toEpochMilliseconds())

    override suspend fun findByExternal(source: String, externalId: String): Person? =
        dao.findByExternal(source, externalId)?.toDomain()
}
