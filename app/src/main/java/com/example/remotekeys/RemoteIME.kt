package com.example.remotekeys

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

class RemoteIME : InputMethodService() {

    private data class Key(val label: String, val action: String = label, val weight: Float = 1f)

    private var pcMode = true
    private var page = "REMOTE"
    private val latched = mutableSetOf<String>()
    private val latchButtons = mutableMapOf<String, MutableList<Button>>()
    private lateinit var content: LinearLayout
    private lateinit var modeBtn: Button

    private val NORMAL = Color.parseColor("#455A64")
    private val ACTIVE = Color.parseColor("#FB8C00")

    override fun onCreate() {
        super.onCreate()
        HidManager.start(this)
    }

    override fun onEvaluateFullscreenMode() = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun bg(color: Int) = GradientDrawable().apply {
        cornerRadius = dp(8).toFloat(); setColor(color)
    }

    private fun btn(label: String, weight: Float = 1f, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(Color.WHITE)
        background = bg(NORMAL)
        setPadding(0, 0, 0, 0)
        minimumWidth = 0
        minimumHeight = 0
        layoutParams = LinearLayout.LayoutParams(0, dp(46), weight).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
        setOnClickListener { onClick() }
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#263238"))
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("REMOTE", "KEYS", "PC", "CUSTOM").forEach { name ->
            top.addView(btn(name) { showPage(name) })
        }
        modeBtn = btn("PC") {
            pcMode = !pcMode
            modeBtn.text = if (pcMode) "PC" else "PHONE"
        }
        top.addView(modeBtn)
        top.addView(btn("\uD83C\uDF10") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        root.addView(top)

        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(240))
            addView(content)
        }
        root.addView(scroll)
        showPage(page)
        return root
    }

    private fun showPage(name: String) {
        page = name
        content.removeAllViews()
        latchButtons.clear()
        val rows: List<List<Key>> = when (name) {
            "REMOTE" -> listOf(
                listOf(Key("⏮", "PREV"), Key("⏪", "LEFT"), Key("⏯", "PLAY"), Key("⏩", "RIGHT"), Key("⏭", "NEXT")),
                listOf(Key("Vol-", "VOLDOWN"), Key("Mute", "MUTE"), Key("Vol+", "VOLUP"),
                    Key("Slow", "SHIFT+,"), Key("Fast", "SHIFT+.")),
                listOf(Key("◀", "LEFT"), Key("▲", "UP"), Key("▼", "DOWN"), Key("▶", "RIGHT"), Key("OK", "ENTER")),
                listOf(Key("Esc", "ESC"), Key("F", "F"), Key("Space", "SPACE", 2f), Key("Win", "WIN"), Key("Alt⇥", "ALT+TAB"))
            )
            "KEYS" -> listOf(
                "1234567890".map { Key(it.toString()) },
                "qwertyuiop".map { Key(it.toString(), it.toString()) },
                "asdfghjkl".map { Key(it.toString(), it.toString()) },
                listOf(Key("⇧", "^SHIFT", 1.5f)) + "zxcvbnm".map { Key(it.toString(), it.toString()) } + Key("⌫", "BKSP", 1.5f),
                listOf(Key("Ctrl", "^CTRL"), Key("Alt", "^ALT"), Key("Space", "SPACE", 3f),
                    Key(","), Key("."), Key("↵", "ENTER", 1.5f))
            )
            "PC" -> listOf(
                listOf(Key("Esc", "ESC"), Key("Tab", "TAB"), Key("Caps", "CAPS"), Key("Ins", "INS"),
                    Key("Del", "DEL"), Key("PrtSc", "PRTSC")),
                (1..6).map { Key("F$it") },
                (7..12).map { Key("F$it") },
                listOf(Key("Ctrl", "^CTRL"), Key("Shift", "^SHIFT"), Key("Alt", "^ALT"),
                    Key("Win", "^WIN"), Key("Menu", "MENU")),
                listOf(Key("Home", "HOME"), Key("End", "END"), Key("PgUp", "PGUP"), Key("PgDn", "PGDN")),
                listOf("-", "=", "[", "]", "\\", ";", "'", "`", "/").map { Key(it) },
                listOf(Key("←", "LEFT"), Key("↑", "UP"), Key("↓", "DOWN"), Key("→", "RIGHT"))
            )
            else -> Prefs.get(this).lines()
                .mapNotNull { line ->
                    val parts = line.split("|", limit = 2)
                    if (parts.size == 2 && parts[0].isNotBlank()) Key(parts[0].trim(), parts[1].trim()) else null
                }
                .chunked(4)
        }
        rows.forEach { row ->
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            row.forEach { key ->
                val b = btn(key.label, key.weight) { press(key.action) }
                if (key.action.startsWith("^")) {
                    val n = key.action.substring(1)
                    latchButtons.getOrPut(n) { mutableListOf() }.add(b)
                    b.background = bg(if (n in latched) ACTIVE else NORMAL)
                }
                r.addView(b)
            }
            content.addView(r)
        }
    }

    private fun refreshLatches() {
        latchButtons.forEach { (n, list) -> list.forEach { it.background = bg(if (n in latched) ACTIVE else NORMAL) } }
    }

    private fun press(action: String) {
        if (action.startsWith("^")) {
            val n = action.substring(1)
            if (!latched.add(n)) latched.remove(n)
            refreshLatches()
            return
        }
        val a = HidKeys.parse(action) ?: return
        if (pcMode) {
            if (!HidManager.isConnected) {
                Toast.makeText(this, "Not connected to a PC - open RemoteKeys app", Toast.LENGTH_SHORT).show()
                return
            }
            val extra = latched.sumOf { HidKeys.modifiers[it] ?: 0 }
            HidManager.tap(a, extra)
        } else {
            phoneInput(action)
        }
        if (latched.isNotEmpty()) { latched.clear(); refreshLatches() }
    }

    private val androidKeys = mapOf(
        "ENTER" to KeyEvent.KEYCODE_ENTER, "BKSP" to KeyEvent.KEYCODE_DEL, "TAB" to KeyEvent.KEYCODE_TAB,
        "ESC" to KeyEvent.KEYCODE_ESCAPE, "DEL" to KeyEvent.KEYCODE_FORWARD_DEL,
        "UP" to KeyEvent.KEYCODE_DPAD_UP, "DOWN" to KeyEvent.KEYCODE_DPAD_DOWN,
        "LEFT" to KeyEvent.KEYCODE_DPAD_LEFT, "RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "HOME" to KeyEvent.KEYCODE_MOVE_HOME, "END" to KeyEvent.KEYCODE_MOVE_END,
        "PGUP" to KeyEvent.KEYCODE_PAGE_UP, "PGDN" to KeyEvent.KEYCODE_PAGE_DOWN
    )

    /** Phone mode: types into the phone's own text field (basic keys only). */
    private fun phoneInput(action: String) {
        val ic = currentInputConnection ?: return
        val up = action.uppercase()
        val code = androidKeys[up]
        when {
            code != null -> {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            }
            up == "SPACE" -> ic.commitText(" ", 1)
            action.length == 1 -> ic.commitText(if ("SHIFT" in latched) action.uppercase() else action.lowercase(), 1)
        }
    }
}
