package dev.sriramcodessw.versesync.nfc

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat

/**
 * The authenticators the app lock accepts: a biometric or the device screen lock.
 *
 * androidx.biometric rejects `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` on Android 9 and 10
 * (API 28 and 29). Checking it with `canAuthenticate` returns an "unsupported" error, and building
 * a prompt with it throws. `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` is accepted on every version, so
 * it is used on those two versions. Everywhere else the stronger pair is used.
 *
 * Both the Settings switch and the unlock prompt must use this one function, or they can disagree.
 */
fun appLockAuthenticators(sdkInt: Int = Build.VERSION.SDK_INT): Int =
    if (sdkInt == Build.VERSION_CODES.P || sdkInt == Build.VERSION_CODES.Q) {
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }

/** Whether this device has a screen lock or biometric that the app lock can use. */
fun canUseAppLock(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(appLockAuthenticators()) == BiometricManager.BIOMETRIC_SUCCESS

class SecurityManager(private val activity: FragmentActivity) {

    fun canAuthenticate(): Boolean = canUseAppLock(activity)

    fun showBiometricPrompt(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(appLockAuthenticators())
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
