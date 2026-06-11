package com.example.data.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(
    tableName = "tips",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["categoryId"]), Index(value = ["noteId"])]
)
@JsonClass(generateAdapter = true)
data class Tip(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val categoryId: Int,
    val noteId: Int,
    val title: String,
    val content: String,
    val pdfName: String? = null,
    val pdfUri: String? = null,
    val pdfPage: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)
