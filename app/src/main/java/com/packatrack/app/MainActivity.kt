package com.packatrack.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.withResumed
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.packatrack.data.PrefsStore
import com.packatrack.data.security.AppLockAuthenticators
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

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best effort */ }

    /**
     * Unlocked state and the lock grace timer. Held in a ViewModel so that a configuration change
     * does not re-lock the app, while a new process always starts locked.
     */
    private val lockState: AppLockState by viewModels()

    /**
     * Whether the lock screen can be satisfied right now. Enrolment is not permanent - the user
     * can remove their fingerprints (or the device PIN) from system settings while this app is
     * backgrounded - and the lock screen has no other way through, with Settings sitting behind
     * it. So the lock is only shown while some allowed authenticator actually exists.
     */
    private var lockAuthenticatorsAvailable by mutableStateOf(false)

    /**
     * Biometrics plus the device PIN/pattern/password. Shared with the settings switch, so the
     * two can never disagree about which devices can use App Lock.
     */
    private val allowedAuthenticators: Int
        get() = AppLockAuthenticators.allowed

    // Parcel to open on launch, set when the activity is started from a notification tap.
    private var pendingShipmentId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate()
        installSplashScreen()

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestNotificationPermissionOnce()

        pendingShipmentId = readOpenShipmentId(intent)
        lockAuthenticatorsAvailable = canAuthenticate()

        setContent {
            val themeMode by remember { prefs.observeThemeMode() }
                .collectAsStateWithLifecycle(initialValue = prefs.themeMode)
            // Observed rather than read once, so turning the switch on takes effect immediately
            // and the app-switcher thumbnail is protected from the moment it is enabled.
            val appLockEnabled by remember { prefs.observeBiometricLock() }
                .collectAsStateWithLifecycle(initialValue = prefs.biometricLock)
            // Keep parcel names and delivery addresses out of the recents thumbnail and
            // out of screenshots while the lock is on.
            LaunchedEffect(appLockEnabled) { applyScreenshotProtection(appLockEnabled) }
            // Re-read on every recomposition; [lockState] flips (e.g. in onStart) trigger one.
            val locked = appLockEnabled && !lockState.authenticated && lockAuthenticatorsAvailable

            PackaTrackTheme(themeMode = themeMode) {
                Box(Modifier.fillMaxSize()) {
                    // The app stays composed underneath the lock screen: re-locking in onStop
                    // happens whenever another activity is shown (the backup file picker, the
                    // Google account picker), and tearing the screens down then would lose the
                    // back stack and drop those pickers' results.
                    Box(Modifier.fillMaxSize().then(if (locked) Modifier.clearAndSetSemantics {} else Modifier)) {
                        PackaTrackNavHost(
                            intent = intent,
                            openShipmentId = pendingShipmentId,
                            onOpenShipmentHandled = { pendingShipmentId = null },
                        )
                    }
                    if (locked) {
                        LockScreen(
                            onAuthenticate = { authenticate() },
                            onBack = { moveTaskToBack(true) },
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Enrolment can change while the app is backgrounded, so re-check before deciding
        // whether to lock - including the case where the screen lock was removed entirely.
        lockAuthenticatorsAvailable = canAuthenticate()
        if (prefs.biometricLock && lockState.authenticated &&
            lockState.stoppedInBackground && lockGraceElapsed()
        ) {
            lockState.authenticated = false
        }
        lockState.stoppedInBackground = false
    }

    override fun onStop() {
        super.onStop()
        // Remember when we left; the grace period is measured on the way back in. A configuration
        // change is not the user leaving: the activity is recreated straight away, so starting the
        // timer there would demand biometrics for a rotation.
        if (!isChangingConfigurations && prefs.biometricLock) {
            lockState.stoppedAtElapsed = SystemClock.elapsedRealtime()
            lockState.stoppedInBackground = true
        }
    }

    /**
     * True when the app has been away long enough to lock again. With the default grace of 0
     * this is always true, which reproduces the original "lock on every return" behaviour.
     */
    private fun lockGraceElapsed(): Boolean {
        val graceMs = prefs.lockGraceSeconds * 1000L
        return SystemClock.elapsedRealtime() - lockState.stoppedAtElapsed >= graceMs
    }

    /**
     * Shows the notification-permission dialog once per install, and only while it is still
     * needed. It used to be requested on every launch, which nags anyone who declined.
     */
    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33 || prefs.notificationPermissionRequested) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        prefs.notificationPermissionRequested = true
        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A notification tap while the activity is already alive arrives here, not onCreate.
        setIntent(intent)
        readOpenShipmentId(intent)?.let { pendingShipmentId = it }
    }

    private fun readOpenShipmentId(intent: Intent?): Long? =
        intent?.getLongExtra(Notifier.EXTRA_OPEN_SHIPMENT_ID, -1L)?.takeIf { it > 0 }

    /** True when at least one allowed authenticator is present, enrolled and available. */
    private fun canAuthenticate(): Boolean =
        BiometricManager.from(this).canAuthenticate(allowedAuthenticators) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** Blocks screenshots and the app-switcher thumbnail while App Lock is enabled. */
    private fun applyScreenshotProtection(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

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
                    lockState.authenticated = true
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_title))
            .setSubtitle(getString(R.string.lock_subtitle))
            // No negative button: setNegativeButtonText is incompatible with device credential
            // authentication, and the system prompt provides its own cancel affordance.
            .setAllowedAuthenticators(allowedAuthenticators)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

@Composable
private fun LockScreen(onAuthenticate: () -> Unit, onBack: () -> Unit) {
    // Composed after the app content, so this takes Back before the navigation stack does.
    BackHandler(onBack = onBack)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Swallow every touch so nothing reaches the app content underneath.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
    ) {
        LaunchedEffect(Unit) {
            // The lock engages in onStop; prompt once the user is actually back.
            lifecycle.withResumed { onAuthenticate() }
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
            // The deep link supplies ?number=; declaring it here is what turns the query
            // parameter into a navigation argument the screen can read.
            arguments = listOf(
                navArgument("number") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
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
