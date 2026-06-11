package com.example.data.database

import androidx.room.*
import com.example.data.models.Category
import com.example.data.models.Note
import com.example.data.models.Tip
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    // Categories
    @Query("SELECT * FROM categories ORDER BY id DESC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories")
    suspend fun getAllCategoriesOnce(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Int): Category?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Delete
    suspend fun deleteCategory(category: Category)

    // Notes
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesOnce(): List<Note>

    @Query("SELECT * FROM notes WHERE categoryId = :categoryId ORDER BY chapter, title")
    fun getNotesByCategory(categoryId: Int): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE categoryId = :categoryId ORDER BY chapter, title")
    suspend fun getNotesByCategoryOnce(categoryId: Int): List<Note>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Int): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    // Tips
    @Query("SELECT * FROM tips ORDER BY createdAt DESC")
    fun getAllTips(): Flow<List<Tip>>

    @Query("SELECT * FROM tips")
    suspend fun getAllTipsOnce(): List<Tip>

    @Query("SELECT * FROM tips WHERE categoryId = :categoryId ORDER BY createdAt DESC")
    fun getTipsByCategory(categoryId: Int): Flow<List<Tip>>

    @Query("SELECT * FROM tips WHERE noteId = :noteId ORDER BY createdAt DESC")
    fun getTipsByNote(noteId: Int): Flow<List<Tip>>

    @Query("SELECT * FROM tips WHERE id = :id")
    suspend fun getTipById(id: Int): Tip?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTip(tip: Tip): Long

    @Update
    suspend fun updateTip(tip: Tip)

    @Delete
    suspend fun deleteTip(tip: Tip)
}
