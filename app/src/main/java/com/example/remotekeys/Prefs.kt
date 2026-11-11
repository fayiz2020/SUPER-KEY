package com.example.remotekeys

import android.content.Context

/** Custom buttons are stored as lines:  Label|COMBO */
object Prefs {
    const val DEFAULT = "Alt+Tab|ALT+TAB\n" +
        "Desktop|WIN+D\n" +
        "Copy|CTRL+C\n" +
        "Paste|CTRL+V\n" +
        "Fullscreen|F11\n" +
        "TaskMgr|CTRL+SHIFT+ESC\n" +
        "Lock|WIN+L\n" +
        "Close|ALT+F4"

    fun get(ctx: Context): String =
        ctx.getSharedPreferences("remotekeys", Context.MODE_PRIVATE).getString("custom", DEFAULT) ?: DEFAULT

    fun set(ctx: Context, value: String) {
        ctx.getSharedPreferences("remotekeys", Context.MODE_PRIVATE).edit().putString("custom", value).apply()
    }
}
