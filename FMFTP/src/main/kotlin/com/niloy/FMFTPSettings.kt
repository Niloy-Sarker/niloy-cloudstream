package com.niloy

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.AcraApplication

class FMFTPSettings : BottomSheetDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        
        // Create main layout
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        // Create TMDB enable toggle
        val tmdbToggle = CheckBox(context).apply {
            text = "Enable TMDB integration"
            isChecked = FMFTPSettingsManager.isTmdbEnabled()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }

        // Create description text
        val descriptionText = TextView(context).apply {
            text = "TMDB integration provides enhanced episode info, synopsis, ratings, and posters from The Movie Database."
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }

        // Create API key section
        val apiKeyLabel = TextView(context).apply {
            text = "TMDB API Key:"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 8)
            }
        }

        // Create EditText for API key input
        val input = EditText(context).apply {
            hint = "Enter TMDB API Key"
            setText(FMFTPSettingsManager.getApiKey() ?: "")
            
            // Set layout parameters
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 8)
            }
        }

        // Create help text
        val helpText = TextView(context).apply {
            text = "Get your free API key from themoviedb.org"
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }

        // Update input enabled state based on toggle
        input.isEnabled = tmdbToggle.isChecked
        apiKeyLabel.isEnabled = tmdbToggle.isChecked

        // Set up toggle listener
        tmdbToggle.setOnCheckedChangeListener { _, isChecked ->
            input.isEnabled = isChecked
            apiKeyLabel.isEnabled = isChecked
        }

        // Add views to layout
        layout.addView(tmdbToggle)
        layout.addView(descriptionText)
        layout.addView(apiKeyLabel)
        layout.addView(input)
        layout.addView(helpText)

        return AlertDialog.Builder(context)
            .setTitle("FMFTP Settings")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val tmdbEnabled = tmdbToggle.isChecked
                val apiKey = input.text.toString().trim()
                
                // Save TMDB enabled state
                if (!FMFTPSettingsManager.setTmdbEnabled(tmdbEnabled)) {
                    showToast("Failed to save TMDB settings. Please try again.")
                    return@setPositiveButton
                }
                
                // If TMDB is enabled, validate and save API key
                if (tmdbEnabled) {
                    when {
                        apiKey.isEmpty() -> {
                            showToast("Please enter a valid API key")
                            return@setPositiveButton
                        }
                        apiKey.length != 32 -> {
                            showToast("API key should be 32 characters long")
                            return@setPositiveButton
                        }
                        !apiKey.matches(Regex("^[a-zA-Z0-9]+$")) -> {
                            showToast("API key should only contain letters and numbers")
                            return@setPositiveButton
                        }
                        else -> {
                            if (FMFTPSettingsManager.setApiKey(apiKey)) {
                                showToast("Settings saved successfully")
                            } else {
                                showToast("Failed to save API key. Please try again.")
                            }
                        }
                    }
                } else {
                    // If TMDB is disabled, we can still save the API key for future use
                    if (apiKey.isNotEmpty()) {
                        FMFTPSettingsManager.setApiKey(apiKey)
                    }
                    showToast("TMDB integration disabled. Settings saved.")
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
    }

    private fun showToast(message: String) {
        try {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            try {
                Toast.makeText(AcraApplication.context, message, Toast.LENGTH_SHORT).show()
            } catch (e2: Exception) {
                // Ignore if both toast attempts fail
            }
        }
    }

    companion object {
        fun newInstance(): FMFTPSettings {
            return FMFTPSettings()
        }
    }
}
