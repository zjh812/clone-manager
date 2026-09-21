package com.example.clonemanager.util

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout

object ThemeManager {
    fun applyTheme(activity: Activity) {
        when (ThemePrefs.getTheme(activity)) {
            ThemePrefs.THEME_LIGHT -> activity.setTheme(com.example.clonemanager.R.style.Theme_CloneManager_Light)
            ThemePrefs.THEME_DARK -> activity.setTheme(com.example.clonemanager.R.style.Theme_CloneManager_Dark)
            else -> activity.setTheme(com.example.clonemanager.R.style.Theme_CloneManager)
        }
    }

    fun applyCustomBackground(activity: Activity) {
        val drawable = ThemePrefs.getCustomBgDrawable(activity) ?: return
        val content = (activity.findViewById<View>(android.R.id.content) as? FrameLayout)?.getChildAt(0) ?: return
        content.background = drawable
    }
}
