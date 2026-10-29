package com.packatrack.app

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        // MainActivity requests POST_NOTIFICATIONS on startup, and the system dialog
        // would cover the app's compose hierarchy and fail every UI test. Grant it
        // up-front so the request is a no-op.
        val targetPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        // executeShellCommand is asynchronous: closing the descriptor returns before `pm grant`
        // has run, so the system dialog could still appear over MainActivity and swallow the
        // taps the UI tests perform. Draining the stream blocks until the command completes.
        ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("pm grant $targetPackage android.permission.POST_NOTIFICATIONS")
        ).use { it.readBytes() }
    }
}
