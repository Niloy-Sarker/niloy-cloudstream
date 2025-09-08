package com.niloy

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import android.app.Activity
import android.util.Log

@CloudstreamPlugin
class FTPBDPlugin: Plugin() {
    private val TAG = "FTPBDPlugin"
    var activity: Activity? = null
    
    override fun load(context: Context) {
        activity = context as Activity
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(FTPBDProvider())
        
        // Add settings
        openSettings = {
            try {
                val settingsDialog = FTPBDSettingsDialog.newInstance()
                activity?.let { act ->
                    settingsDialog.show(act.fragmentManager, "FTPBDSettings")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing settings dialog: ${e.message}")
            }
        }
    }
}
