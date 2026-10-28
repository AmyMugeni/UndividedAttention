package com.dreammap.attentionshieldlogger

import android.content.Context
import java.util.UUID

object UserPreferences {

    private const val PREFS_NAME = "attentionshield_prefs"
    private const val KEY_USER_ID = "user_id"

    fun getUserId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_USER_ID, null)
        if (existing != null) return existing

        // First launch — generate and store a random anonymous ID
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_USER_ID, newId).apply()
        return newId
    }
}