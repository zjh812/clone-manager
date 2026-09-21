package com.example.clonemanager.util

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

object ThemePrefs {
    const val THEME_DEFAULT = "default"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val THEME_CUSTOM = "custom"

    private const val PREF = "theme_prefs"
    private const val KEY_THEME = "theme"
    private const val KEY_CUSTOM_BG = "custom_bg_path"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun getTheme(ctx: Context): String = prefs(ctx).getString(KEY_THEME, THEME_DEFAULT)!!
    fun setTheme(ctx: Context, theme: String) = prefs(ctx).edit().putString(KEY_THEME, theme).apply()
    fun getCustomBgPath(ctx: Context): String? = prefs(ctx).getString(KEY_CUSTOM_BG, null)
    fun setCustomBgPath(ctx: Context, path: String?) {
        prefs(ctx).edit().apply {
            if (path == null) remove(KEY_CUSTOM_BG) else putString(KEY_CUSTOM_BG, path)
        }.apply()
    }
    fun getCustomBgDrawable(ctx: Context): Drawable? {
        val path = getCustomBgPath(ctx) ?: return null
        return try {
            val bmp = BitmapFactory.decodeFile(path) ?: return null
            BitmapDrawable(ctx.resources, bmp).apply { gravity = android.view.Gravity.FILL }
        } catch (_: Exception) { null }
    }
}
