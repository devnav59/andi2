package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.models.Category
import com.example.data.models.Note
import com.example.data.models.Tip
import com.example.data.repository.NoteRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NoteRepository
    val categories: StateFlow<List<Category>>
    val allNotes: StateFlow<List<Note>>
    val allTips: StateFlow<List<Tip>>
    
    private val _isLoaded = MutableStateFlow(false)
    val isLoaded = _isLoaded.asStateFlow()
    
    // Theme preference: true = Dark, false = Light, null = System
    private val _darkThemePreference = MutableStateFlow<Boolean?>(false)
    val darkThemePreference = _darkThemePreference.asStateFlow()

    fun setDarkTheme(isDark: Boolean?) {
        _darkThemePreference.value = isDark
    }
    
    // Active category and currently focused note
    private val _selectedCategoryId = MutableStateFlow<Int?>(null)
    val selectedCategoryId = _selectedCategoryId.asStateFlow()

    private val _currentNote = MutableStateFlow<Note?>(null)
    val currentNote = _currentNote.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application, viewModelScope)
        repository = NoteRepository(database.noteDao())
        
        categories = repository.allCategories
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        allNotes = repository.allNotes
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        allTips = repository.allTips
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        
        viewModelScope.launch {
            combine(repository.allCategories, repository.allNotes, repository.allTips) { _, _, _ -> true }
                .collect {
                    _isLoaded.value = true
                }
        }
        
        // Listen to changes in the active category and all_notes to automatically select its first note completely in memory
        viewModelScope.launch {
            combine(_selectedCategoryId, allNotes) { catId, notesList ->
                Pair(catId, notesList)
            }.collect { (catId, notesList) ->
                if (catId != null) {
                    val filtered = notesList.filter { it.categoryId == catId }
                        .sortedWith(compareBy({ it.chapter }, { it.title }))
                    if (!filtered.isNullOrEmpty()) {
                        val current = _currentNote.value
                        if (current == null || current.categoryId != catId) {
                            _currentNote.value = filtered.first()
                        }
                    } else {
                        _currentNote.value = null
                    }
                } else {
                    _currentNote.value = null
                }
            }
        }
    }

    fun selectCategory(categoryId: Int) {
        _selectedCategoryId.value = categoryId
        
        // Sync-select the first note of this category from in-memory state to avoid layout and transition delays
        val notes = allNotes.value.filter { it.categoryId == categoryId }
            .sortedWith(compareBy({ it.chapter }, { it.title }))
        if (!notes.isNullOrEmpty()) {
            val current = _currentNote.value
            if (current == null || current.categoryId != categoryId) {
                _currentNote.value = notes.first()
            }
        } else {
            _currentNote.value = null
        }
    }

    fun getNotesFlowForCategory(categoryId: Int): Flow<List<Note>> {
        return repository.getNotesByCategory(categoryId)
    }

    fun selectNote(note: Note) {
        _currentNote.value = note
    }

    fun selectNoteById(noteId: Int) {
        viewModelScope.launch {
            repository.getNoteById(noteId)?.let { note ->
                _selectedCategoryId.value = note.categoryId
                _currentNote.value = note
            }
        }
    }

    fun selectNoteByTitle(title: String) {
        viewModelScope.launch {
            val all = repository.allNotes.firstOrNull() ?: emptyList()
            val match = all.find { it.title.trim().equals(title.trim(), ignoreCase = true) }
            if (match != null) {
                _selectedCategoryId.value = match.categoryId
                _currentNote.value = match
            }
        }
    }

    // Category actions
    fun addCategory(name: String, description: String, colorHex: String) {
        viewModelScope.launch {
            repository.insertCategory(Category(name = name, description = description, colorHex = colorHex))
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
            if (_selectedCategoryId.value == category.id) {
                _selectedCategoryId.value = null
                _currentNote.value = null
            }
        }
    }

    // Note actions
    fun saveNote(
        id: Int = 0,
        categoryId: Int,
        title: String,
        chapter: String,
        content: String,
        pdfName: String? = null,
        pdfUri: String? = null,
        pdfPage: Int? = null
    ) {
        viewModelScope.launch {
            val note = Note(
                id = id,
                categoryId = categoryId,
                title = title,
                chapter = chapter,
                content = content,
                pdfName = pdfName,
                pdfUri = pdfUri,
                pdfPage = pdfPage
            )
            if (id == 0) {
                val newId = repository.insertNote(note)
                _currentNote.value = note.copy(id = newId.toInt())
            } else {
                repository.updateNote(note)
                _currentNote.value = note
            }
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
            val catNotes = repository.getNotesByCategory(note.categoryId).firstOrNull()
            val remaining = catNotes?.filter { it.id != note.id }
            if (!remaining.isNullOrEmpty()) {
                _currentNote.value = remaining.first()
            } else {
                _currentNote.value = null
            }
        }
    }

    // Tip actions
    fun saveTip(
        id: Int = 0,
        categoryId: Int,
        noteId: Int,
        title: String,
        content: String,
        pdfName: String? = null,
        pdfUri: String? = null,
        pdfPage: Int? = null
    ) {
        viewModelScope.launch {
            val tip = Tip(
                id = id,
                categoryId = categoryId,
                noteId = noteId,
                title = title,
                content = content,
                pdfName = pdfName,
                pdfUri = pdfUri,
                pdfPage = pdfPage
            )
            if (id == 0) {
                repository.insertTip(tip)
            } else {
                repository.updateTip(tip)
            }
        }
    }

    fun deleteTip(tip: Tip) {
        viewModelScope.launch {
            repository.deleteTip(tip)
        }
    }

    // Backup and Restore Actions
    fun exportBackupToFile(context: android.content.Context, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val json = com.example.data.database.DatabaseBackupHelper.createBackupJson(repository)
                val success = com.example.data.database.DatabaseBackupHelper.saveBackupToLocalFile(context, json)
                onResult(success)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    fun importBackupFromFile(context: android.content.Context, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val json = com.example.data.database.DatabaseBackupHelper.loadBackupFromLocalFile(context)
                if (json != null) {
                    val success = com.example.data.database.DatabaseBackupHelper.restoreAndMergeBackup(repository, json)
                    onResult(success)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    fun importBackupFromJson(json: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val success = com.example.data.database.DatabaseBackupHelper.restoreAndMergeBackup(repository, json)
                onResult(success)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    suspend fun exportToString(): String {
        return com.example.data.database.DatabaseBackupHelper.createBackupJson(repository)
    }
}
