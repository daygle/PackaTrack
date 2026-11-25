package com.packatrack.data.security

import android.os.Build
import androidx.biometric.BiometricManager

/**
 * The authenticators App Lock accepts.
 *
 * Settings and the unlock prompt both read this, so the switch can never be offered on a device
 * the prompt would reject, or hidden on one it would accept. The settings screen used to check
 * `BIOMETRIC_STRONG` alone, which disabled App Lock on PIN-only devices and on devices whose only
 * biometric is Class 2 - even though unlocking would have worked on both.
 *
 * The device PIN/pattern/password is always part of the set: removing enrolled biometrics must
 * never lock a user out of their own tracking history.
 *
 * `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` is unsupported on API 28-29 only (see
 * androidx.biometric's AuthenticatorUtils.isSupportedCombination), where the Class 2 combination
 * is the only one that still offers a credential fallback.
 */
object AppLockAuthenticators {

    /**
     * True below API 28, where App Lock confirms the device credential instead of prompting for a
     * biometric.
     *
     * androidx.biometric 1.1.0 routes fingerprint authentication on API 24-27 through
     * `FingerprintManagerCompat` from androidx.core. `FingerprintManager` was removed from the
     * platform SDK in Android 37, and androidx.core has since replaced that class with a stub: it
     * reports no enrolled fingerprints and its `authenticate()` is a no-op. A prompt on those
     * versions would therefore show a fingerprint dialog that can never call back, and the lock
     * screen would have no way through at all. Device credential - always part of [allowed] - is
     * the one authenticator they can honour, so it is confirmed through KeyguardManager instead.
     *
     * API 28 and above are unaffected: they go through the framework BiometricPrompt.
     */
    val requiresDeviceCredentialPrompt: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.P

    /** A bitwise combination for [BiometricManager.canAuthenticate] and the unlock prompt. */
    val allowed: Int
        get() = if (Build.VERSION.SDK_INT in Build.VERSION_CODES.P..Build.VERSION_CODES.Q) {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
}
