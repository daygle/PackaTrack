package com.packatrack.app

import android.app.Application
import android.content.Context
import android.os.Bundle
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
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant $targetPackage android.permission.POST_NOTIFICATIONS")
            .close()
    }
}
