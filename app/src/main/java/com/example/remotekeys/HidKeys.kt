package com.example.remotekeys

/** Maps key names (used in buttons and custom combos) to USB HID codes. */
object HidKeys {
    data class Action(val mods: Int, val key: Int, val consumer: Int)

    val modifiers = mapOf(
        "CTRL" to 0x01, "SHIFT" to 0x02, "ALT" to 0x04,
        "WIN" to 0x08, "GUI" to 0x08, "CMD" to 0x08,
        "RCTRL" to 0x10, "RSHIFT" to 0x20, "ALTGR" to 0x40, "RWIN" to 0x80
    )

    // Media / volume keys (HID Consumer page)
    private val consumerKeys = mapOf(
        "PLAY" to 0xCD, "NEXT" to 0xB5, "PREV" to 0xB6, "STOP" to 0xB7,
        "FF" to 0xB3, "REW" to 0xB4,
        "VOLUP" to 0xE9, "VOLDOWN" to 0xEA, "MUTE" to 0xE2
    )

    private val keys = HashMap<String, Int>().apply {
        for (i in 0..25) put(('A' + i).toString(), 0x04 + i)
        for (i in 1..9) put(i.toString(), 0x1D + i)
        put("0", 0x27)
        put("ENTER", 0x28); put("RETURN", 0x28); put("ESC", 0x29)
        put("BKSP", 0x2A); put("BACKSPACE", 0x2A); put("TAB", 0x2B); put("SPACE", 0x2C)
        put("-", 0x2D); put("=", 0x2E); put("[", 0x2F); put("]", 0x30); put("\\", 0x31)
        put(";", 0x33); put("'", 0x34); put("`", 0x35)
        put(",", 0x36); put(".", 0x37); put("/", 0x38)
        put("CAPS", 0x39)
        for (i in 1..12) put("F$i", 0x39 + i)
        put("PRTSC", 0x46); put("SCRLK", 0x47); put("PAUSE", 0x48)
        put("INS", 0x49); put("HOME", 0x4A); put("PGUP", 0x4B)
        put("DEL", 0x4C); put("DELETE", 0x4C); put("END", 0x4D); put("PGDN", 0x4E)
        put("RIGHT", 0x4F); put("LEFT", 0x50); put("DOWN", 0x51); put("UP", 0x52)
        put("NUMLOCK", 0x53); put("MENU", 0x65)
    }

    /** Parses things like "CTRL+ALT+T", "F11", "VOLUP", "SHIFT+.". */
    fun parse(combo: String): Action? {
        var mods = 0; var key = 0; var cons = 0
        for (raw in combo.split("+")) {
            val p = raw.trim().uppercase()
            if (p.isEmpty()) continue
            val m = modifiers[p]; val c = consumerKeys[p]; val k = keys[p]
            when {
                m != null -> mods = mods or m
                c != null -> cons = c
                k != null -> key = k
                else -> return null
            }
        }
        return if (mods == 0 && key == 0 && cons == 0) null else Action(mods, key, cons)
    }
}
