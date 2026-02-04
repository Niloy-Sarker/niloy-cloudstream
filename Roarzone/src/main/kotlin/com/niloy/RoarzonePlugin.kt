package com.niloy

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import android.app.Activity
import android.util.Log

@CloudstreamPlugin
class RoarzonePlugin: Plugin() {
    private val TAG = "RoarzonePlugin"
    var activity: Activity? = null
    
    override fun load(context: Context) {
        activity = context as Activity
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(RoarzoneProvider())
        
        // Add settings
        openSettings = {
            try {
                val settingsDialog = RoarzoneSettingsDialog.newInstance()
                activity?.let { act ->
                    settingsDialog.show(act.fragmentManager, "RoarzoneSettings")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing settings dialog: ${e.message}")
            }
        }
    }
}
