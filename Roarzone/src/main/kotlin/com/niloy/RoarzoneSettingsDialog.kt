package com.niloy

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CloudStreamApp

class RoarzoneSettingsDialog : BottomSheetDialogFragment() {
    
    companion object {
        const val PREF_NAME = "roarzone_settings"
        const val KEY_SITE_URL = "site_url"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_IS_LOGGED_IN = "is_logged_in"
        const val DEFAULT_SITE_URL = "https://play.roarzone.info"
        const val DEFAULT_USERNAME = "RoarZone_Guest"
        const val DEFAULT_PASSWORD = ""
        
        fun newInstance(): RoarzoneSettingsDialog {
            return RoarzoneSettingsDialog()
        }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Create main layout
        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }
        scrollView.addView(mainLayout)
        
        // Title
        val titleText = TextView(context).apply {
            text = "RoarZone Settings"
            textSize = 20f
            setPadding(0, 0, 0, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(titleText)
        
        // Status display
        val isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
        val currentSiteUrl = sharedPreferences.getString(KEY_SITE_URL, DEFAULT_SITE_URL) ?: DEFAULT_SITE_URL
        val currentUsername = sharedPreferences.getString(KEY_USERNAME, DEFAULT_USERNAME) ?: DEFAULT_USERNAME
        val currentPassword = sharedPreferences.getString(KEY_PASSWORD, DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD
        
        val statusText = TextView(context).apply {
            text = if (isLoggedIn) {
                "Status: Custom configuration active ($currentUsername)"
            } else {
                "Status: Default settings (Guest mode)"
            }
            setPadding(0, 0, 0, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(statusText)
        
        // --- Field 1: Site URL ---
        val siteUrlLabel = TextView(context).apply {
            text = "Site URL:"
            setPadding(0, 0, 0, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(siteUrlLabel)
        
        val siteUrlInput = EditText(context).apply {
            hint = "e.g. https://play.roarzone.info"
            setText(currentSiteUrl)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }
        mainLayout.addView(siteUrlInput)

        // --- Field 2: Username ---
        val usernameLabel = TextView(context).apply {
            text = "Username:"
            setPadding(0, 0, 0, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(usernameLabel)
        
        val usernameInput = EditText(context).apply {
            hint = "Enter username"
            setText(currentUsername)
            inputType = InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }
        mainLayout.addView(usernameInput)
        
        // --- Field 3: Password ---
        val passwordLabel = TextView(context).apply {
            text = "Password (optional):"
            setPadding(0, 0, 0, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(passwordLabel)
        
        val passwordInput = EditText(context).apply {
            hint = "Enter password (leave empty if none)"
            setText(currentPassword)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }
        mainLayout.addView(passwordInput)
        
        return AlertDialog.Builder(context)
            .setView(scrollView)
            .setPositiveButton("Save Settings") { _, _ ->
                val rawSiteUrl = siteUrlInput.text.toString().trim()
                val username = usernameInput.text.toString().trim()
                val password = passwordInput.text.toString()
                
                // Validation: Site URL empty check
                if (rawSiteUrl.isEmpty()) {
                    showToast("Site URL cannot be empty")
                    return@setPositiveButton
                }
                
                // Ensure proper scheme and remove trailing slashes
                val normalizedUrl = if (!rawSiteUrl.startsWith("http://", ignoreCase = true) && 
                                       !rawSiteUrl.startsWith("https://", ignoreCase = true)) {
                    "https://$rawSiteUrl"
                } else {
                    rawSiteUrl
                }.trimEnd('/')
                
                // Validation: Valid URL format
                if (!URLUtil.isValidUrl(normalizedUrl) && !Patterns.WEB_URL.matcher(normalizedUrl).matches()) {
                    showToast("Please enter a valid Site URL")
                    return@setPositiveButton
                }
                
                // Validation: Username empty check
                if (username.isEmpty()) {
                    showToast("Username cannot be empty")
                    return@setPositiveButton
                }
                
                // Save settings to SharedPreferences
                sharedPreferences.edit()
                    .putString(KEY_SITE_URL, normalizedUrl)
                    .putString(KEY_USERNAME, username)
                    .putString(KEY_PASSWORD, password)
                    .putBoolean(KEY_IS_LOGGED_IN, true)
                    .apply()
                
                // Clear the authentication cache so new URL & credentials take effect
                RoarzoneProvider.clearAuthCache()
                showToast("Settings saved successfully")
            }
            .setNeutralButton("Reset Defaults") { _, _ ->
                sharedPreferences.edit()
                    .putBoolean(KEY_IS_LOGGED_IN, false)
                    .remove(KEY_SITE_URL)
                    .remove(KEY_USERNAME)
                    .remove(KEY_PASSWORD)
                    .apply()
                
                // Clear the authentication cache
                RoarzoneProvider.clearAuthCache()
                showToast("Settings reset to defaults")
            }
            .setNegativeButton("Cancel", null)
            .create()
    }
    
    private fun showToast(message: String) {
        try {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            try {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            } catch (e2: Exception) {
                try {
                    Toast.makeText(CloudStreamApp.context, message, Toast.LENGTH_SHORT).show()
                } catch (e3: Exception) {
                    // Ignore if toast cannot be shown
                }
            }
        }
    }
}
