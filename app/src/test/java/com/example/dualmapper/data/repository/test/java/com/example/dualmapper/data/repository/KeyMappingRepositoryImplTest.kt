package com.example.dualmapper.data.repository

import com.example.dualmapper.data.AppDatabase
import com.example.dualmapper.data.KeyMappingDao
import com.example.dualmapper.data.KeyMappingEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class KeyMappingRepositoryImplTest {

    private lateinit var repository: KeyMappingRepositoryImpl
    private lateinit var mockDb: AppDatabase
    private lateinit var mockDao: KeyMappingDao

    @Before
    fun setUp() {
        mockDb = mock(AppDatabase::class.java)
        mockDao = mock(KeyMappingDao::class.java)
        `when`(mockDb.keyMappingDao()).thenReturn(mockDao)
        repository = KeyMappingRepositoryImpl(mockDb)
    }

    @Test
    fun `getAll returns list from dao`() = runTest {
        val expected = listOf(createTestEntity("1"))
        `when`(mockDao.getAll()).thenReturn(expected)

        val result = repository.getAll()

        assertEquals(expected, result)
        verify(mockDao).getAll()
    }

    @Test
    fun `observeAll returns flow from dao`() = runTest {
        val flow = flowOf(listOf(createTestEntity("1")))
        `when`(mockDao.observeAll()).thenReturn(flow)

        val result = repository.observeAll().first()

        assertEquals(1, result.size)
        assertEquals("1", result[0].id)
    }

    @Test
    fun `insertOrUpdate uses upsert behavior`() = runTest {
        val entity = createTestEntity("1")

        repository.insertOrUpdate(entity)

        // insertOrUpdate 内部直接调用 insert (DAO 使用 REPLACE 策略实现 upsert)
        verify(mockDao).insert(entity)
        verify(mockDao, never()).update(any())
        verify(mockDao, never()).getById(any())
    }

    @Test
    fun `deleteById calls dao delete`() = runTest {
        repository.deleteById("1")

        verify(mockDao).deleteById("1")
    }

    @Test
    fun `replaceAll deletes all and upserts new entities`() = runTest {
        val entities = listOf(createTestEntity("1"), createTestEntity("2"))

        repository.replaceAll(entities)

        verify(mockDao).deleteAll()
        verify(mockDao).upsertAll(entities)
    }

    private fun createTestEntity(id: String) = KeyMappingEntity(
        id = id,
        label = "Test",
        layoutX = 0,
        layoutY = 0,
        alpha = 1f,
        actionType = "tap",
        targetX = 100f,
        targetY = 200f,
        endX = 0f,
        endY = 0f,
        durationMs = 50L,
        keyCode = 96,
        playerIndex = 1
    )
}
