package com.ctrldevice.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun loadPrompt(filename: String, variables: Map<String, String> = emptyMap()): String {
        val assetPath = "prompts/$filename"
        val rawText = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        
        var formattedText = rawText
        variables.forEach { (key, value) ->
            formattedText = formattedText.replace("{{$key}}", value)
        }
        return formattedText
    }
}
