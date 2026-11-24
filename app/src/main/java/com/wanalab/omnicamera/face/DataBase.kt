package com.wanalab.godeye

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// WanaLab God Eye v2.2 - DataBase.kt
// Room DB - Known Faces + Embeddings - Offline
// Author: Nasir Khan - Dera Ismail Khan, Khyber Pakhtunkhwa - 2026-11-23
// PRO Rs. 2,500 / Year

object DataBaseInfo {
    const val VERSION = "2.2"
    const val APP_NAME = "WanaLab God Eye"
    const val PRICING = "PRO Rs. 2,500 / Year"
    // v2.2 - Universal
    var credentialsShop: String = ""
    var credentialsOffice: String = ""
    var credentialsDepartment: String = ""
    var gpsTimestamp: Boolean = true
    var gpsShare: Boolean = true
    var antiFakeGps: Boolean = true
    var wanalabWatermark: Boolean = true
    const val DB_NAME = "wanalab_faces.db"
    // NOTE: DB_VERSION = 1 - Room version hai, App version nahi. Change mat karo warna data delete hoga.
    const val DB_VERSION = 1
}

// Type converter for FloatArray embedding
class Converters {
    @TypeConverter
    fun fromFloatArray(value: FloatArray): String = value.joinToString(",")
    @TypeConverter
    fun toFloatArray(value: String): FloatArray {
        if (value.isEmpty()) return FloatArray(0)
        return value.split(",").map { it.toFloat() }.toFloatArray()
    }
}

@Entity(tableName = "known_faces")
data class KnownFaceEntity(
    @PrimaryKey val personId: String,
    val name: String,
    val embedding: FloatArray,
    val createdAt: Long,
    val photoPath: String? = null
)

@Dao
interface FaceDao {
    @Query("SELECT * FROM known_faces")
    suspend fun getAll(): List<KnownFaceEntity>

    @Query("SELECT * FROM known_faces WHERE personId = :id")
    suspend fun getById(id: String): KnownFaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(face: KnownFaceEntity)

    @Query("DELETE FROM known_faces WHERE personId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM known_faces")
    suspend fun clearAll()
}

@Database(entities = [KnownFaceEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class FaceDatabase : RoomDatabase() {
    abstract fun dao(): FaceDao
    companion object {
        @Volatile private var INSTANCE: FaceDatabase? = null
        fun get(context: Context): FaceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FaceDatabase::class.java,
                    "wanalab_faces.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
