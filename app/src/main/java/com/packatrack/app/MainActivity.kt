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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.packatrack.app.ui.detail.DetailScreen
import com.packatrack.app.notify.Notifier
import com.packatrack.app.ui.home.HomeScreen
import com.packatrack.app.ui.settings.SettingsScreen
import com.packatrack.app.ui.theme.PackaTrackTheme

class MainActivity : FragmentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best effort */ }

    private var isAuthenticated by mutableStateOf(false)

    // Parcel to open on launch, set when the activity is started from a notification tap.
    private var pendingShipmentId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate() - it will use the splash theme
        // and hold the splash screen while SQLCipher database decrypts on cold start.
        val splashScreen = installSplashScreen()

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        pendingShipmentId = readOpenShipmentId(intent)

        // Keep splash screen visible until the database is ready (max 3 seconds)
        val app = (application as PackaTrackApp)
        splashScreen.setKeepOnScreenCondition { app.containerState.value == null }

        // Mark database as ready after first frame renders
        setContent {
            val container by app.containerState.collectAsStateWithLifecycle()

            container?.let { readyContainer ->
                val isLockEnabled = readyContainer.prefs.biometricLock

                PackaTrackTheme {
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
            } ?: run {
                LoadingScreen()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Re-lock whenever the app leaves the screen; the lock screen re-prompts on return.
        // Prompting here (onResume) would race the lock screen's own prompt and crash with
        // "Only one biometric prompt can be active at once".
        val app = application as PackaTrackApp
        if (app.containerState.value?.prefs?.biometricLock == true) {
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
                        Toast.makeText(applicationContext, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
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
            .setTitle("PackaTrack Lock")
            .setSubtitle("Authenticate to open the app")
            .setNegativeButtonText("Cancel")
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
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
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
