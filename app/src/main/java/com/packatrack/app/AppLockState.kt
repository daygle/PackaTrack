package com.packatrack.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * App Lock state that outlives an activity recreation.
 *
 * These values used to live in [MainActivity] fields, so a configuration change (rotating the
 * device) recreated the activity with `authenticated = false` and demanded biometrics again.
 * A ViewModel survives a configuration change but not process death, so unlike instance state it
 * can never be restored into a fresh launch and grant access without unlocking.
 */
class AppLockState : ViewModel() {
    /** True once this process has been unlocked. */
    var authenticated by mutableStateOf(false)

    /** Uptime when the app was last backgrounded, or 0 if it has not been. */
    var stoppedAtElapsed by mutableLongStateOf(0L)

    /**
     * True when the activity stopped because the user left it, as opposed to a configuration
     * change. Only a real stop starts the re-lock grace timer.
     */
    var stoppedInBackground = false
}
