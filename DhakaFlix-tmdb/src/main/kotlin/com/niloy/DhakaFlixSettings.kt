package com.niloy

import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.lagradost.cloudstream3.AcraApplication

class DhakaFlixSettings : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = activity ?: throw IllegalStateException("Activity cannot be null")
        
        // Create EditText for API key input
        val input = EditText(context).apply {
            hint = "Enter TMDB API Key"
            setText(DhakaFlixSettingsManager.getApiKey() ?: "")
            
            // Set layout parameters
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(20, 20, 20, 20)
            }
        }

        return AlertDialog.Builder(context)
            .setTitle("DhakaFlix Settings")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val apiKey = input.text.toString().trim()
                when {
                    apiKey.isEmpty() -> {
                        showToast("Please enter a valid API key")
                    }
                    apiKey.length != 32 -> {
                        showToast("API key should be 32 characters long")
                    }
                    !apiKey.matches(Regex("^[a-zA-Z0-9]+$")) -> {
                        showToast("API key should only contain letters and numbers")
                    }
                    else -> {
                        if (DhakaFlixSettingsManager.setApiKey(apiKey)) {
                            showToast("API Key saved successfully")
                        } else {
                            showToast("Failed to save API key. Please try again.")
                        }
                    }
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
        fun newInstance(): DhakaFlixSettings {
            return DhakaFlixSettings()
        }
    }
} 