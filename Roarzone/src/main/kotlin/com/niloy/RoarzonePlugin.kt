package com.niloy

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.util.Log

@CloudstreamPlugin
class RoarzonePlugin: Plugin() {
    private val TAG = "RoarzonePlugin"
    var activity: AppCompatActivity? = null
    
    override fun load(context: Context) {
        activity = context as AppCompatActivity
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(RoarzoneProvider())
        
        // Add settings
        openSettings = { ctx ->
            try {
                val act = ctx as? AppCompatActivity
                if (act != null && !act.isFinishing && !act.isDestroyed) {
                    val settingsDialog = RoarzoneSettingsDialog.newInstance()
                    settingsDialog.show(act.supportFragmentManager, "RoarzoneSettings")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing settings dialog: ${e.message}")
            }
        }
    }
}
