package com.ctrldevice.features.agent_engine.config

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages application configuration and secrets.
 */
class AppConfig(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_config", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_BRAIN_TYPE = "brain_type"
        private const val KEY_LLM_API_KEY = "llm_api_key"
        private const val KEY_LLM_ENDPOINT = "llm_endpoint"
        private const val KEY_LLM_MODEL = "llm_model"

        const val BRAIN_RULE_BASED = "rule_based"
        const val BRAIN_LLM_REMOTE = "llm_remote"
        const val BRAIN_LLM_LOCAL = "llm_local"

        const val DEFAULT_MAX_RETRIES = 20
        private const val KEY_MAX_RETRIES = "max_retries"
    }

    var maxRetries: Int
        get() = prefs.getInt(KEY_MAX_RETRIES, DEFAULT_MAX_RETRIES)
        set(value) = prefs.edit().putInt(KEY_MAX_RETRIES, value).apply()

    // Cache values in memory to avoid I/O on every read
    private var _brainType: String? = null
    var brainType: String
        get() {
            if (_brainType == null) {
                _brainType = prefs.getString(KEY_BRAIN_TYPE, BRAIN_RULE_BASED) ?: BRAIN_RULE_BASED
            }
            return _brainType!!
        }
        set(value) {
            _brainType = value
            prefs.edit().putString(KEY_BRAIN_TYPE, value).apply()
        }

    private var _llmApiKey: String? = null
    var llmApiKey: String
        get() {
            if (_llmApiKey == null) {
                _llmApiKey = prefs.getString(KEY_LLM_API_KEY, "") ?: ""
            }
            return _llmApiKey!!
        }
        set(value) {
            _llmApiKey = value
            prefs.edit().putString(KEY_LLM_API_KEY, value).apply()
        }

    private var _llmEndpoint: String? = null
    var llmEndpoint: String
        get() {
            if (_llmEndpoint == null) {
                _llmEndpoint = prefs.getString(KEY_LLM_ENDPOINT, "https://api.openai.com/v1/chat/completions") ?: "https://api.openai.com/v1/chat/completions"
            }
            return _llmEndpoint!!
        }
        set(value) {
            _llmEndpoint = value
            prefs.edit().putString(KEY_LLM_ENDPOINT, value).apply()
        }

    private var _llmModel: String? = null
    var llmModel: String
        get() {
            if (_llmModel == null) {
                _llmModel = prefs.getString(KEY_LLM_MODEL, "gpt-4-turbo") ?: "gpt-4-turbo"
            }
            return _llmModel!!
        }
        set(value) {
            _llmModel = value
            prefs.edit().putString(KEY_LLM_MODEL, value).apply()
        }
}
