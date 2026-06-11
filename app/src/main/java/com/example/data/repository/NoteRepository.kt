package com.example.data.repository

import com.example.data.database.NoteDao
import com.example.data.models.Category
import com.example.data.models.Note
import com.example.data.models.Tip
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {
    val allCategories: Flow<List<Category>> = noteDao.getAllCategories()
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()
    val allTips: Flow<List<Tip>> = noteDao.getAllTips()

    suspend fun getAllCategoriesOnce(): List<Category> = noteDao.getAllCategoriesOnce()
    suspend fun getAllNotesOnce(): List<Note> = noteDao.getAllNotesOnce()
    suspend fun getAllTipsOnce(): List<Tip> = noteDao.getAllTipsOnce()

    fun getNotesByCategory(categoryId: Int): Flow<List<Note>> = noteDao.getNotesByCategory(categoryId)
    suspend fun getNotesByCategoryOnce(categoryId: Int): List<Note> = noteDao.getNotesByCategoryOnce(categoryId)

    fun getTipsByCategory(categoryId: Int): Flow<List<Tip>> = noteDao.getTipsByCategory(categoryId)
    fun getTipsByNote(noteId: Int): Flow<List<Tip>> = noteDao.getTipsByNote(noteId)

    suspend fun getCategoryById(id: Int): Category? = noteDao.getCategoryById(id)
    suspend fun getNoteById(id: Int): Note? = noteDao.getNoteById(id)
    suspend fun getTipById(id: Int): Tip? = noteDao.getTipById(id)

    suspend fun insertCategory(category: Category): Long = noteDao.insertCategory(category)
    suspend fun deleteCategory(category: Category) = noteDao.deleteCategory(category)

    suspend fun insertNote(note: Note): Long = noteDao.insertNote(note)
    suspend fun updateNote(note: Note) = noteDao.updateNote(note)
    suspend fun deleteNote(note: Note) = noteDao.deleteNote(note)

    suspend fun insertTip(tip: Tip): Long = noteDao.insertTip(tip)
    suspend fun updateTip(tip: Tip) = noteDao.updateTip(tip)
    suspend fun deleteTip(tip: Tip) = noteDao.deleteTip(tip)
}
