package com.packatrack.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.packatrack.data.PrefsStore
import com.packatrack.data.TrackingRepository
import com.packatrack.feature.common.R
import com.packatrack.feature.detail.DetailScreen
import com.packatrack.notify.Notifier
import com.packatrack.feature.home.HomeScreen
import com.packatrack.feature.settings.SettingsScreen
import com.packatrack.feature.common.theme.PackaTrackTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var prefs: PrefsStore

    @Inject
    lateinit var repository: TrackingRepository

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best effort */ }

    private var isAuthenticated by mutableStateOf(false)

    // Parcel to open on launch, set when the activity is started from a notification tap.
    private var pendingShipmentId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate()
        val splashScreen = installSplashScreen()

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        pendingShipmentId = readOpenShipmentId(intent)

        // Mark database as ready after first frame renders
        setContent {
            val isLockEnabled = prefs.biometricLock
            val themeMode = prefs.themeMode

            PackaTrackTheme(themeMode = themeMode) {
                if (isLockEnabled && !isAuthenticated) {
                    LockScreen(onAuthenticate = { authenticate() })
                } else {
                    PackaTrackNavHost(
                        intent = intent,
                        openShipmentId = pendingShipmentId,
                        onOpenShipmentHandled = { pendingShipmentId = null },
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Re-lock whenever the app leaves the screen
        if (prefs.biometricLock) {
            isAuthenticated = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A notification tap while the activity is already alive arrives here, not onCreate.
        setIntent(intent)
        readOpenShipmentId(intent)?.let { pendingShipmentId = it }
    }

    private fun readOpenShipmentId(intent: Intent?): Long? =
        intent?.getLongExtra(Notifier.EXTRA_OPEN_SHIPMENT_ID, -1L)?.takeIf { it > 0 }

    private fun authenticate() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        Toast.makeText(applicationContext, getString(R.string.auth_error, errString), Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isAuthenticated = true
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_title))
            .setSubtitle(getString(R.string.lock_subtitle))
            .setNegativeButtonText(getString(R.string.cancel))
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

@Composable
private fun LockScreen(onAuthenticate: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LaunchedEffect(Unit) {
            onAuthenticate()
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            // Shown so a cancelled or failed prompt can be retried; normally hidden behind
            // the biometric dialog.
            Button(onClick = onAuthenticate) {
                Text(stringResource(R.string.unlock))
            }
        }
    }
}

@Composable
private fun PackaTrackNavHost(
    intent: Intent?,
    openShipmentId: Long?,
    onOpenShipmentHandled: () -> Unit,
) {
    val nav = rememberNavController()

    val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
        intent.getStringExtra(Intent.EXTRA_TEXT)
    } else null

    // Deep-link from a notification tap: open the parcel's detail on top of the list so
    // Back returns to the list. Cleared once handled to avoid re-navigating on recompose.
    LaunchedEffect(openShipmentId) {
        if (openShipmentId != null) {
            nav.navigate("detail/$openShipmentId")
            onOpenShipmentHandled()
        }
    }

    NavHost(
        navController = nav,
        startDestination = "home",
    ) {
        composable(
            route = "home",
            deepLinks = listOf(
                navDeepLink { uriPattern = "packatrack://add?number={number}" }
            )
        ) { backStackEntry ->
            val deepLinkNumber = backStackEntry.arguments?.getString("number")
            HomeScreen(
                onOpenDetail = { id -> nav.navigate("detail/$id") },
                onOpenSettings = { nav.navigate("settings") },
                initialNumber = deepLinkNumber ?: sharedText
            )
        }
        composable(
            route = "detail/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            DetailScreen(id = id, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
