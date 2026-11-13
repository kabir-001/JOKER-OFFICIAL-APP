package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedPaperDao {
  @Query("SELECT * FROM downloaded_papers ORDER BY downloadedAtMillis DESC")
  fun observeAll(): Flow<List<DownloadedPaperEntity>>

  @Query("SELECT paperId FROM downloaded_papers")
  fun observeDownloadedIds(): Flow<List<String>>

  @Query("SELECT * FROM downloaded_papers WHERE paperId = :paperId LIMIT 1")
  suspend fun getById(paperId: String): DownloadedPaperEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(paper: DownloadedPaperEntity)

  @Query("DELETE FROM downloaded_papers WHERE paperId = :paperId")
  suspend fun deleteById(paperId: String)
}
