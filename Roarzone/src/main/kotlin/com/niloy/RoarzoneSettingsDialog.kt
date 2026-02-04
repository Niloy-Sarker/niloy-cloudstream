package com.niloy

import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class RoarzoneSettingsDialog : DialogFragment() {
    
    companion object {
        const val PREF_NAME = "roarzone_settings"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_IS_LOGGED_IN = "is_logged_in"
        const val DEFAULT_USERNAME = "RoarZone_Guest"
        const val DEFAULT_PASSWORD = ""
        
        fun newInstance(): RoarzoneSettingsDialog {
            return RoarzoneSettingsDialog()
        }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = activity ?: throw IllegalStateException("Activity cannot be null")
        val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        // Create main layout
        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }
        
        // Title
        val titleText = TextView(context).apply {
            text = "RoarZone Login Settings"
            textSize = 20f
            setPadding(0, 0, 0, 32)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(titleText)
        
        // Status display
        val isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
        val currentUsername = sharedPreferences.getString(KEY_USERNAME, DEFAULT_USERNAME) ?: DEFAULT_USERNAME
        
        val statusText = TextView(context).apply {
            text = if (isLoggedIn) {
                "Status: Logged in as $currentUsername"
            } else {
                "Status: Not logged in (using default credentials)"
            }
            setPadding(0, 0, 0, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(statusText)
        
        // Username label
        val usernameLabel = TextView(context).apply {
            text = "Username:"
            setPadding(0, 0, 0, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(usernameLabel)
        
        // Username input
        val usernameInput = EditText(context).apply {
            hint = "Enter username"
            setText(currentUsername)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }
        mainLayout.addView(usernameInput)
        
        // Password label
        val passwordLabel = TextView(context).apply {
            text = "Password (optional):"
            setPadding(0, 0, 0, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(passwordLabel)
        
        // Password input
        val currentPassword = sharedPreferences.getString(KEY_PASSWORD, DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD
        val passwordInput = EditText(context).apply {
            hint = "Enter password (leave empty if no password)"
            setText(currentPassword)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }
        mainLayout.addView(passwordInput)
        
        return AlertDialog.Builder(context)
            .setView(mainLayout)
            .setPositiveButton("Save") { _, _ ->
                val username = usernameInput.text.toString().trim()
                val password = passwordInput.text.toString()
                
                if (username.isEmpty()) {
                    showToast("Username cannot be empty")
                    return@setPositiveButton
                }
                
                // Save credentials
                sharedPreferences.edit()
                    .putString(KEY_USERNAME, username)
                    .putString(KEY_PASSWORD, password)
                    .putBoolean(KEY_IS_LOGGED_IN, true)
                    .apply()
                
                // Clear the authentication cache so it re-authenticates with new credentials
                RoarzoneProvider.clearAuthCache()
                showToast("Settings saved successfully")
            }
            .setNeutralButton("Logout") { _, _ ->
                sharedPreferences.edit()
                    .putBoolean(KEY_IS_LOGGED_IN, false)
                    .remove(KEY_USERNAME)
                    .remove(KEY_PASSWORD)
                    .apply()
                
                // Clear the authentication cache
                RoarzoneProvider.clearAuthCache()
                showToast("Logged out successfully")
            }
            .setNegativeButton("Cancel", null)
            .create()
    }
    
    private fun showToast(message: String) {
        try {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // Fallback if activity is null
            try {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            } catch (e2: Exception) {
                // Ignore if both attempts fail
            }
        }
    }
}
