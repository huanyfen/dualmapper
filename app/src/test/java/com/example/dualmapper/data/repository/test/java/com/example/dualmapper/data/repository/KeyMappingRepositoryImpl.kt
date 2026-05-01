package com.example.dualmapper.data.repository

import com.example.dualmapper.data.AppDatabase
import com.example.dualmapper.data.KeyMappingEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyMappingRepositoryImpl @Inject constructor(
    private val db: AppDatabase
) : KeyMappingRepository {

    override suspend fun getAll(): List<KeyMappingEntity> = db.keyMappingDao().getAll()

    override suspend fun getByKeyCode(keyCode: Int): KeyMappingEntity? = db.keyMappingDao().getByKeyCode(keyCode)

    override suspend fun getById(id: String): KeyMappingEntity? = db.keyMappingDao().getById(id)

    // 改用 @Insert(onConflict=REPLACE) 直接 upsert，无需先查后改
    override suspend fun insertOrUpdate(entity: KeyMappingEntity) {
        db.keyMappingDao().insert(entity)
    }

    override suspend fun deleteById(id: String) = db.keyMappingDao().deleteById(id)

    override suspend fun replaceAll(entities: List<KeyMappingEntity>) {
        db.keyMappingDao().replaceAllWithTransaction(entities)
    }

    override fun observeAll(): Flow<List<KeyMappingEntity>> = db.keyMappingDao().observeAll()
}