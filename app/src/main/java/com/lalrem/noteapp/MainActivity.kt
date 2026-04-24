package com.lalrem.noteapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lalrem.noteapp.data.repository.NoteRepository
import com.lalrem.noteapp.ui.edit.EditScreen
import com.lalrem.noteapp.ui.edit.EditViewModel
import com.lalrem.noteapp.ui.workspace.WorkspaceScreen
import com.lalrem.noteapp.ui.workspace.WorkspaceViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var repository: NoteRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val initialSession = repository.getSession()
        val startDest = if (initialSession.first.isNotEmpty()) "edit" else "workspace"

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    
                    NavHost(navController = navController, startDestination = startDest) {
                        composable("workspace") {
                            val workspaceViewModel: WorkspaceViewModel = hiltViewModel()
                            WorkspaceScreen(viewModel = workspaceViewModel, navController = navController)
                        }
                        composable("edit") {
                            val editViewModel: EditViewModel = hiltViewModel()
                            EditScreen(viewModel = editViewModel, navController = navController)
                        }
                    }
                }
            }
        }
    }
}
