package com.niloy

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.AcraApplication.Companion.context

object DhakaFlixSettingsManager {
    private const val SETTINGS_PREF = "dhakaflix_settings"
    private const val API_KEY = "tmdb_api_key"
    private const val TMDB_ENABLED = "tmdb_enabled"
    private const val TAG = "DhakaFlixSettings"

    private var cachedApiKey: String? = null
    private var cachedTmdbEnabled: Boolean? = null

    private fun getPrefs(): SharedPreferences? {
        return try {
            context?.getSharedPreferences(SETTINGS_PREF, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get SharedPreferences: ${e.message}")
            null
        }
    }

    fun getApiKey(): String? {
        // First try to get from cache
        if (!cachedApiKey.isNullOrEmpty()) {
            return cachedApiKey
        }

        return try {
            // If not in cache, get from SharedPreferences
            val key = getPrefs()?.getString(API_KEY, null)
            if (!key.isNullOrEmpty()) {
                cachedApiKey = key // Cache the key
            }
            key
        } catch (e: Exception) {
            Log.e(TAG, "Error getting API key: ${e.message}")
            null
        }
    }

    fun setApiKey(apiKey: String): Boolean {
        return try {
            if (apiKey.isEmpty()) {
                return false
            }

            val prefs = getPrefs() ?: return false
            
            // Use commit() for synchronous write
            val success = prefs.edit()
                .putString(API_KEY, apiKey)
                .commit()

            if (success) {
                cachedApiKey = apiKey // Update cache only on successful save
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save API key: ${e.message}")
            false
        }
    }

    fun clearApiKey() {
        try {
            getPrefs()?.edit()?.remove(API_KEY)?.apply()
            cachedApiKey = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear API key: ${e.message}")
        }
    }

    fun isTmdbEnabled(): Boolean {
        // First try to get from cache
        if (cachedTmdbEnabled != null) {
            return cachedTmdbEnabled!!
        }

        return try {
            // If not in cache, get from SharedPreferences (default to true)
            val enabled = getPrefs()?.getBoolean(TMDB_ENABLED, true) ?: true
            cachedTmdbEnabled = enabled // Cache the value
            enabled
        } catch (e: Exception) {
            Log.e(TAG, "Error getting TMDB enabled state: ${e.message}")
            true // Default to enabled if there's an error
        }
    }

    fun setTmdbEnabled(enabled: Boolean): Boolean {
        return try {
            val prefs = getPrefs() ?: return false
            
            // Use commit() for synchronous write
            val success = prefs.edit()
                .putBoolean(TMDB_ENABLED, enabled)
                .commit()

            if (success) {
                cachedTmdbEnabled = enabled // Update cache only on successful save
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save TMDB enabled state: ${e.message}")
            false
        }
    }
} 