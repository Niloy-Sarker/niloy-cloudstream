package com.niloy

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import android.app.Activity
import android.util.Log

@CloudstreamPlugin
class FMFTPPlugin: Plugin() {
    private val TAG = "FMFTPPlugin"
    var activity: Activity? = null

    override fun load(context: Context) {
        activity = context as Activity
        // All providers should be added in this manner
        registerMainAPI(FMFTPProvider())

        // Add settings
        openSettings = {
            try {
                val settingsDialog = FMFTPSettings.newInstance()
                activity?.let { act ->
                    settingsDialog.show(act.fragmentManager, "FMFTPSettings")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing settings dialog: ${e.message}")
            }
        }
    }
}
