package com.ivnsrg.aicontrolcentre.data.storage.security

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecureApiKeyStorageTest {

    @Test
    fun `saved keys survive new storage instance`() {
        val prefs = FakeSharedPreferences()
        SecureApiKeyStorage(prefs, testOnly = true).addApiKey("sk-or-v1-first")

        val reloaded = SecureApiKeyStorage(prefs, testOnly = true)

        assertEquals(listOf("sk-or-v1-first"), reloaded.getApiKeys())
        assertEquals("sk-or-v1-first", reloaded.getPrimaryApiKey())
    }

    @Test
    fun `legacy key migrates into list on first read`() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString("openrouter_api_key", "legacy-key").apply()

        val storage = SecureApiKeyStorage(prefs, testOnly = true)

        assertEquals(listOf("legacy-key"), storage.getApiKeys())
        assertNull(prefs.getString("openrouter_api_key", null))
    }
}

private class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, String?>()

    override fun getString(key: String?, defValue: String?): String? = values[key] ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor(values)

    override fun getAll(): MutableMap<String, *> = values
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String?, defValue: Int): Int = defValue
    override fun getLong(key: String?, defValue: Long): Long = defValue
    override fun getFloat(key: String?, defValue: Float): Float = defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private class Editor(
        private val values: MutableMap<String, String?>,
    ) : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) values[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) values.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            values.clear()
            return this
        }

        override fun apply() = Unit
        override fun commit(): Boolean = true
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
    }
}
