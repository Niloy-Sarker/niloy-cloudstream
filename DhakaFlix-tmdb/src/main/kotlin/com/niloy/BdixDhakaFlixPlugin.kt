package com.niloy

import android.content.Context
import android.app.Activity
import android.util.Log
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class BdixDhakaFlixPlugin : Plugin() {
    private val TAG = "DhakaFlixPlugin"
    var activity: Activity? = null

    override fun load(context: Context) {
        activity = context as Activity
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(BdixDhakaFlix14Provider())
        registerMainAPI(BdixDhakaFlix12Provider())
        registerMainAPI(BdixDhakaFlix9Provider())
        registerMainAPI(BdixDhakaFlix7Provider())

        // Add settings
        openSettings = {
            try {
                val settingsDialog = DhakaFlixSettings.newInstance()
                activity?.let { act ->
                    settingsDialog.show(act.fragmentManager, "DhakaFlixSettings")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing settings dialog: ${e.message}")
            }
        }
    }
}