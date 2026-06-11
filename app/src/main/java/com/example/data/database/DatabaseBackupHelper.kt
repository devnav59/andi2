package com.example.data.database

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.models.Category
import com.example.data.models.Note
import com.example.data.models.Tip
import com.example.data.repository.NoteRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class BackupData(
    val categories: List<Category>,
    val notes: List<Note>,
    val tips: List<Tip> = emptyList()
)

object DatabaseBackupHelper {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private val jsonAdapter = moshi.adapter(BackupData::class.java)

    /**
     * Serializes all current database contents into a JSON string.
     */
    suspend fun createBackupJson(repository: NoteRepository): String = withContext(Dispatchers.IO) {
        val categories = repository.getAllCategoriesOnce()
        val notes = repository.getAllNotesOnce()
        val tips = repository.getAllTipsOnce()
        val data = BackupData(categories = categories, notes = notes, tips = tips)
        jsonAdapter.toJson(data)
    }

    /**
     * Deserializes the backup JSON, mapping and merging with our current local DB.
     * Guaranteed to NOT delete any existing directories or notes added locally.
     */
    suspend fun restoreAndMergeBackup(
        repository: NoteRepository, 
        backupJson: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val backupData = jsonAdapter.fromJson(backupJson) ?: return@withContext false
            val importedCategories = backupData.categories
            val importedNotes = backupData.notes
            val importedTips = backupData.tips

            // 1. Fetch current database categories and build a lookup map
            val existingCategories = repository.getAllCategoriesOnce()
            
            // Map: backupCategoryOldId -> realCategoryLocalId
            val categoryIdMap = mutableMapOf<Int, Int>()

            for (importedCat in importedCategories) {
                // Find category with same name (case-insensitive and trimmed)
                val existingCat = existingCategories.find { 
                    it.name.trim().equals(importedCat.name.trim(), ignoreCase = true) 
                }

                val finalId = if (existingCat != null) {
                    // Match found! Use existing database ID and sync properties if modified
                    categoryIdMap[importedCat.id] = existingCat.id
                    existingCat.id
                } else {
                    // Category does not exist, insert it as a new category
                    // Set Id to 0 so Room autogenerates
                    val newCat = Category(
                        id = 0,
                        name = importedCat.name,
                        description = importedCat.description,
                        colorHex = importedCat.colorHex
                    )
                    val newId = repository.insertCategory(newCat).toInt()
                    categoryIdMap[importedCat.id] = newId
                    newId
                }
            }

            // 2. Fetch current notes to avoid duplicating exact note matchups
            val existingNotes = repository.getAllNotesOnce()
            val noteIdMap = mutableMapOf<Int, Int>()

            for (importedNote in importedNotes) {
                // Translate old categoryId to the verified local categoryId
                val localCategoryId = categoryIdMap[importedNote.categoryId] ?: continue

                // Check if a note with identical title, chapter, and categoryId already exists
                val matchedExistingNote = existingNotes.find {
                    it.categoryId == localCategoryId &&
                    it.title.trim().equals(importedNote.title.trim(), ignoreCase = true) &&
                    it.chapter.trim().equals(importedNote.chapter.trim(), ignoreCase = true)
                }

                val finalNoteId = if (matchedExistingNote != null) {
                    // Exact match exists -> Overwrite/Update with the imported note's content & assets, preserving its existing local ID
                    val updatedNote = importedNote.copy(
                        id = matchedExistingNote.id,
                        categoryId = localCategoryId
                    )
                    repository.updateNote(updatedNote)
                    matchedExistingNote.id
                } else {
                    // Key/Title doesn't exist -> Insert it as a brand new note
                    val brandNewNote = importedNote.copy(
                        id = 0, // Let Room autogenerate uniquely
                        categoryId = localCategoryId
                    )
                    repository.insertNote(brandNewNote).toInt()
                }
                noteIdMap[importedNote.id] = finalNoteId
            }

            // 3. Restore and Merge Tips
            val existingTips = repository.getAllTipsOnce()
            for (importedTip in importedTips) {
                val localCategoryId = categoryIdMap[importedTip.categoryId] ?: continue
                val localNoteId = noteIdMap[importedTip.noteId] ?: continue

                // Check if a tip with identical title, category, and note exists
                val matchedExistingTip = existingTips.find {
                    it.categoryId == localCategoryId &&
                    it.noteId == localNoteId &&
                    it.title.trim().equals(importedTip.title.trim(), ignoreCase = true)
                }

                if (matchedExistingTip != null) {
                    val updatedTip = importedTip.copy(
                        id = matchedExistingTip.id,
                        categoryId = localCategoryId,
                        noteId = localNoteId
                    )
                    repository.updateTip(updatedTip)
                } else {
                    val brandNewTip = importedTip.copy(
                        id = 0, // autogenerate ID
                        categoryId = localCategoryId,
                        noteId = localNoteId
                    )
                    repository.insertTip(brandNewTip)
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Seamless fully automated backup utilizing modern MediaStore.
     */
    suspend fun saveBackupToLocalFile(context: Context, backupJson: String): Boolean = withContext(Dispatchers.IO) {
        val fileName = "StudyNotes_Backup.json"
        val resolver = context.contentResolver
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(fileName)
            
            var existingUri: Uri? = null
            resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val id = cursor.getLong(idColumn)
                    existingUri = ContentUris.withAppendedId(collection, id)
                }
            }
            
            val uri = if (existingUri != null) {
                existingUri!!
            } else {
                val details = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                resolver.insert(collection, details) ?: return@withContext false
            }
            
            try {
                resolver.openOutputStream(uri, "rwt")?.use { outputStream ->
                    outputStream.write(backupJson.toByteArray(Charsets.UTF_8))
                    return@withContext true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Retry in write truncate fallback mode
                try {
                    resolver.openOutputStream(uri, "w")?.use { outputStream ->
                        outputStream.write(backupJson.toByteArray(Charsets.UTF_8))
                        return@withContext true
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            try {
                file.writeText(backupJson)
                return@withContext true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        false
    }

    /**
     * Seamless automated reload reading directly from local Downloads
     */
    suspend fun loadBackupFromLocalFile(context: Context): String? = withContext(Dispatchers.IO) {
        val fileName = "StudyNotes_Backup.json"
        val resolver = context.contentResolver
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(fileName)
            
            var uri: Uri? = null
            resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val id = cursor.getLong(idColumn)
                    uri = ContentUris.withAppendedId(collection, id)
                }
            }
            
            if (uri != null) {
                try {
                    resolver.openInputStream(uri!!)?.use { inputStream ->
                        return@withContext inputStream.bufferedReader().use { it.readText() }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (file.exists()) {
                try {
                    return@withContext file.readText()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        null
    }
}
