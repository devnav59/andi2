package com.example.data.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["categoryId"])]
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val categoryId: Int,
    val title: String,
    val chapter: String, // Season or Chapter name
    val content: String, // Rich-Text / Markdown body
    val pdfName: String? = null, // The associated PDF book identifier or file name
    val pdfUri: String? = null,  // Persistent Uri path to load real storage PDF page
    val pdfPage: Int? = null,     // Page index in the PDF
    val createdAt: Long = System.currentTimeMillis()
)
