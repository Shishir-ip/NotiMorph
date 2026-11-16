package com.notimorph.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.notimorph.app.core.NotiListener
import com.notimorph.app.ui.about.AboutScreen
import com.notimorph.app.ui.history.HistoryScreen
import com.notimorph.app.ui.home.HomeScreen
import com.notimorph.app.ui.settings.SettingsScreen
import com.notimorph.app.ui.theme.NotiMorphTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as NotiMorphApp
        setContent {
            val settings by app.prefs.state.collectAsState()
            NotiMorphTheme(settings.themeMode, settings.dynamicColor) {
                androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as NotiMorphApp

    // Runtime permission for posting our restyled notifications (Android 13+).
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Reflect current system state in case the service was connected before the UI.
        app.listenerConnected.value = NotiListener.isConnected(context)
    }

    NavHost(
        navController = nav,
        startDestination = "home",
        enterTransition = { fadeIn(tween(240)) + slideInHorizontally(tween(240)) { it / 10 } },
        exitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -it / 14 } },
        popEnterTransition = { fadeIn(tween(240)) + slideInHorizontally(tween(240)) { -it / 10 } },
        popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 14 } },
    ) {
        composable("home") { HomeScreen(nav) }
        composable("settings") { SettingsScreen(nav) }
        composable("about") { AboutScreen(nav) }
        composable("history") { HistoryScreen(nav) }
    }
}
