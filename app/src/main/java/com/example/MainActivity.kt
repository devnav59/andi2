package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NoteEditorScreen
import com.example.ui.screens.NotesDisplayScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.TipEditorScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.NoteViewModel
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween

class MainActivity : ComponentActivity() {
    private val viewModel: NoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkThemePref by viewModel.darkThemePreference.collectAsStateWithLifecycle()
            val systemTheme = isSystemInDarkTheme()
            val isDarkTheme = when (darkThemePref) {
                true -> true
                false -> false
                null -> systemTheme
            }
            
            MyApplicationTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()
                
                NavHost(
                    navController = navController,
                    startDestination = "splash",
                    enterTransition = { fadeIn(animationSpec = tween(150)) },
                    exitTransition = { fadeOut(animationSpec = tween(150)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(150)) },
                    popExitTransition = { fadeOut(animationSpec = tween(150)) }
                ) {
                    // 0. Beautiful Animated Splash Loading Screen
                    composable("splash") {
                        SplashScreen(
                            onSplashComplete = {
                                navController.navigate("home") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            }
                        )
                    }

                    // 1. Home Category Grid Dashboard
                    composable("home") {
                        val categories by viewModel.categories.collectAsStateWithLifecycle()
                        val allNotes by viewModel.allNotes.collectAsStateWithLifecycle()
                        
                        HomeScreen(
                            categories = categories,
                            notesList = allNotes,
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = {
                                viewModel.setDarkTheme(!isDarkTheme)
                            },
                            onCategoryClick = { category, isTipsMode ->
                                viewModel.selectCategory(category.id)
                                navController.navigate("notes/${category.id}?initialTipsMode=$isTipsMode")
                            },
                            onNoteClick = { note ->
                                viewModel.selectCategory(note.categoryId)
                                viewModel.selectNote(note)
                                navController.navigate("notes/${note.categoryId}")
                            },
                            onAddCategory = { name, desc, colorHex ->
                                viewModel.addCategory(name, desc, colorHex)
                            },
                            onDeleteCategory = { category ->
                                viewModel.deleteCategory(category)
                            },
                            onFastBackup = { context, callback ->
                                viewModel.exportBackupToFile(context, callback)
                            },
                            onFastRestore = { context, callback ->
                                viewModel.importBackupFromFile(context, callback)
                            },
                            onGetBackupJson = {
                                viewModel.exportToString()
                            },
                            onImportBackupJson = { json, callback ->
                                viewModel.importBackupFromJson(json, callback)
                            }
                        )
                    }

                    // 2. Single-Note Reader screen per category
                    composable(
                        route = "notes/{categoryId}?initialTipsMode={initialTipsMode}",
                        arguments = listOf(
                            navArgument("categoryId") { type = NavType.IntType },
                            navArgument("initialTipsMode") { 
                                type = NavType.BoolType
                                defaultValue = false 
                            }
                        )
                    ) { backStackEntry ->
                        val categoryId = backStackEntry.arguments?.getInt("categoryId") ?: 0
                        val initialTipsMode = backStackEntry.arguments?.getBoolean("initialTipsMode") ?: false
                        
                        val categories by viewModel.categories.collectAsStateWithLifecycle()
                        val category = categories.find { it.id == categoryId }
                        
                        // Observe notes specifically in active category by filtering the in-memory state synchronously
                        val rawNotes by viewModel.allNotes.collectAsStateWithLifecycle()
                        val allNotes = remember(rawNotes, categoryId) {
                            rawNotes.filter { it.categoryId == categoryId }
                                .sortedWith(compareBy({ it.chapter }, { it.title }))
                        }

                        // Observe tips specifically in active category
                        val rawTips by viewModel.allTips.collectAsStateWithLifecycle()
                        val allTips = remember(rawTips, categoryId) {
                            rawTips.filter { it.categoryId == categoryId }
                        }

                        val isNotesLoaded by viewModel.isLoaded.collectAsStateWithLifecycle()
                        val currentNote by viewModel.currentNote.collectAsStateWithLifecycle()
                        
                        if (category != null) {
                            NotesDisplayScreen(
                                category = category,
                                allNotes = allNotes,
                                allTips = allTips,
                                globalNotes = rawNotes,
                                currentNote = currentNote,
                                isLoaded = isNotesLoaded,
                                onBack = { navController.popBackStack() },
                                onNoteSelected = { note ->
                                    viewModel.selectNote(note)
                                },
                                onAddNoteClick = {
                                    navController.navigate("editor/$categoryId?noteId=-1")
                                },
                                onEditNoteClick = { note ->
                                    navController.navigate("editor/$categoryId?noteId=${note.id}")
                                },
                                onDeleteNoteClick = { note ->
                                    viewModel.deleteNote(note)
                                },
                                onAddTipClick = { noteId ->
                                    val idParam = noteId ?: -1
                                    navController.navigate("tip_editor/$categoryId?tipId=-1&initialNoteId=$idParam")
                                },
                                onEditTipClick = { tip ->
                                    navController.navigate("tip_editor/$categoryId?tipId=${tip.id}&initialNoteId=${tip.noteId}")
                                },
                                onDeleteTipClick = { tip ->
                                    viewModel.deleteTip(tip)
                                },
                                onOpenPdfViewer = { pdfName, pdfUri, page ->
                                    val encodedUri = if (pdfUri != null) Uri.encode(pdfUri) else ""
                                    navController.navigate("pdf?pdfName=$pdfName&pdfPage=$page&pdfUri=$encodedUri")
                                },
                                initialTipsMode = initialTipsMode
                            )
                        }
                    }

                    // 3. Creative Markdown Note Composer editor
                    composable(
                        route = "editor/{categoryId}?noteId={noteId}",
                        arguments = listOf(
                            navArgument("categoryId") { type = NavType.IntType },
                            navArgument("noteId") { 
                                type = NavType.IntType
                                defaultValue = -1
                            }
                        )
                    ) { backStackEntry ->
                        val categoryId = backStackEntry.arguments?.getInt("categoryId") ?: 0
                        val noteId = backStackEntry.arguments?.getInt("noteId") ?: -1
                        
                        // Observe notes specifically in active category by filtering the in-memory state synchronously
                        val rawNotes by viewModel.allNotes.collectAsStateWithLifecycle()
                        val categoryNotes = remember(rawNotes, categoryId) {
                            rawNotes.filter { it.categoryId == categoryId }
                                .sortedWith(compareBy({ it.chapter }, { it.title }))
                        }
                        val noteToEdit = categoryNotes.find { it.id == noteId }
                        
                        val rawTips by viewModel.allTips.collectAsStateWithLifecycle()
                        val categoryTips = remember(rawTips, categoryId) {
                            rawTips.filter { it.categoryId == categoryId }
                        }
                        
                        NoteEditorScreen(
                            categoryId = categoryId,
                            noteToEdit = noteToEdit,
                            categoryNotes = categoryNotes,
                            categoryTips = categoryTips,
                            onBack = { navController.popBackStack() },
                            onSave = { id, title, chapter, content, pdfName, pdfUri, pdfPage ->
                                viewModel.saveNote(id, categoryId, title, chapter, content, pdfName, pdfUri, pdfPage)
                                navController.popBackStack()
                            }
                        )
                    }

                    // 4. Dual-mode PDF Reader/Viewer
                    composable(
                        route = "pdf?pdfName={pdfName}&pdfPage={pdfPage}&pdfUri={pdfUri}",
                        arguments = listOf(
                            navArgument("pdfName") { type = NavType.StringType; defaultValue = "کتاب مرجع" },
                            navArgument("pdfPage") { type = NavType.IntType; defaultValue = 1 },
                            navArgument("pdfUri") { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { backStackEntry ->
                        val pdfName = backStackEntry.arguments?.getString("pdfName") ?: "کتاب مرجع"
                        val pdfPage = backStackEntry.arguments?.getInt("pdfPage") ?: 1
                        val encodedUri = backStackEntry.arguments?.getString("pdfUri") ?: ""
                        val pdfUri = if (encodedUri.isNotEmpty()) Uri.decode(encodedUri) else null
                        
                        val currentNote by viewModel.currentNote.collectAsStateWithLifecycle()
                        
                        PdfViewerScreen(
                            pdfName = pdfName,
                            pdfUriString = pdfUri,
                            pageNumber = pdfPage,
                            onBack = { navController.popBackStack() },
                            onBindPdfUri = { newUriString ->
                                // Bind selected PDF URI back to active note in SQLite Room
                                val active = currentNote
                                if (active != null) {
                                    viewModel.saveNote(
                                        id = active.id,
                                        categoryId = active.categoryId,
                                        title = active.title,
                                        chapter = active.chapter,
                                        content = active.content,
                                        pdfName = active.pdfName ?: pdfName,
                                        pdfPage = active.pdfPage ?: pdfPage,
                                        pdfUri = newUriString
                                    )
                                }
                            }
                        )
                    }

                    // 5. Creative Markdown Tip Composer editor
                    composable(
                         route = "tip_editor/{categoryId}?tipId={tipId}&initialNoteId={initialNoteId}",
                         arguments = listOf(
                             navArgument("categoryId") { type = NavType.IntType },
                             navArgument("tipId") {
                                 type = NavType.IntType
                                 defaultValue = -1
                             },
                             navArgument("initialNoteId") {
                                 type = NavType.IntType
                                 defaultValue = -1
                             }
                         )
                    ) { backStackEntry ->
                         val categoryId = backStackEntry.arguments?.getInt("categoryId") ?: 0
                         val tipId = backStackEntry.arguments?.getInt("tipId") ?: -1
                         val initialNoteIdArg = backStackEntry.arguments?.getInt("initialNoteId") ?: -1
                         val initialNoteId = if (initialNoteIdArg == -1) null else initialNoteIdArg
                         
                         val rawNotes by viewModel.allNotes.collectAsStateWithLifecycle()
                         val categoryNotes = remember(rawNotes, categoryId) {
                             rawNotes.filter { it.categoryId == categoryId }
                                 .sortedWith(compareBy({ it.chapter }, { it.title }))
                         }
                         
                         val rawTips by viewModel.allTips.collectAsStateWithLifecycle()
                          val categoryTips = remember(rawTips, categoryId) {
                              rawTips.filter { it.categoryId == categoryId }
                          }
                          val tipToEdit = rawTips.find { it.id == tipId }
                         
                         TipEditorScreen(
                             categoryId = categoryId,
                             tipToEdit = tipToEdit,
                             categoryNotes = categoryNotes,
                             categoryTips = categoryTips,
                             initialNoteId = initialNoteId,
                             onBack = { navController.popBackStack() },
                             onSave = { id, noteId, title, content, pdfName, pdfUri, pdfPage ->
                                 viewModel.saveTip(id, categoryId, noteId, title, content, pdfName, pdfUri, pdfPage)
                                 navController.popBackStack()
                             }
                         )
                    }
                }
            }
        }
    }
}
