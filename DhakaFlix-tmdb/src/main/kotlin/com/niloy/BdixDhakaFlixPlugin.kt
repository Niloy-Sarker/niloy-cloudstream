package com.niloy

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class BdixDhakaFlixPlugin : Plugin() {
    private val TAG = "DhakaFlixPlugin"
    var activity: AppCompatActivity? = null

    override fun load(context: Context) {
        activity = context as AppCompatActivity
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(BdixDhakaFlix14Provider())
        registerMainAPI(BdixDhakaFlix12Provider())
        registerMainAPI(BdixDhakaFlix9Provider())
        registerMainAPI(BdixDhakaFlix7Provider())

        // Add settings
        openSettings = { ctx ->
            try {
                val act = ctx as? AppCompatActivity
                if (act != null && !act.isFinishing && !act.isDestroyed) {
                    val settingsDialog = DhakaFlixSettings.newInstance()
                    settingsDialog.show(act.supportFragmentManager, "DhakaFlixSettings")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing settings dialog: ${e.message}")
            }
        }
    }
}