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
