package com.example.remotekeys

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import android.widget.*

class MainActivity : Activity() {

    private lateinit var status: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun hasBtPermission() =
        Build.VERSION.SDK_INT < 31 ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun startHid() {
        if (hasBtPermission()) HidManager.start(this)
        else requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (hasBtPermission()) HidManager.start(this)
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        status = TextView(this).apply { textSize = 16f; text = "Status: ${HidManager.status}" }
        root.addView(status)
        HidManager.onStatus = { s -> runOnUiThread { status.text = "Status: $s" } }

        root.addView(TextView(this).apply {
            textSize = 18f; text = "\nSetup"
        })
        root.addView(button("1. Allow Bluetooth & start") { startHid() })
        root.addView(button("2. Enable RemoteKeys keyboard") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        root.addView(button("3. Pair: open Bluetooth settings") {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        })
        root.addView(TextView(this).apply {
            text = "On the PC: Bluetooth > Add device > pick this phone. Then come back here."
        })
        root.addView(button("4. Connect to my PC") {
            val devs = HidManager.pairedDevices()
            if (devs.isEmpty()) {
                Toast.makeText(this, "No paired devices. Pair first (step 3).", Toast.LENGTH_LONG).show()
            } else {
                val names = devs.map { try { it.name ?: it.address } catch (e: SecurityException) { it.address } }
                AlertDialog.Builder(this)
                    .setTitle("Choose your PC")
                    .setItems(names.toTypedArray()) { _, i -> HidManager.connect(devs[i]) }
                    .show()
            }
        })
        root.addView(button("5. Switch to RemoteKeys keyboard") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        root.addView(TextView(this).apply {
            textSize = 18f
            text = "\nCustom buttons"
        })
        root.addView(TextView(this).apply {
            text = "One per line:  Label|COMBO\nExamples: Copy|CTRL+C   Mute|MUTE   Zoom|CTRL+SHIFT+A\n" +
                "Names: CTRL SHIFT ALT WIN, A-Z, 0-9, F1-F12, ENTER ESC TAB SPACE BKSP DEL INS HOME END " +
                "PGUP PGDN UP DOWN LEFT RIGHT MENU PRTSC CAPS, PLAY NEXT PREV STOP FF REW VOLUP VOLDOWN MUTE"
        })
        val editor = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 6
            setText(Prefs.get(this@MainActivity))
        }
        root.addView(editor)
        root.addView(button("Save custom buttons") {
            Prefs.set(this, editor.text.toString())
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        })
        root.addView(button("Reset to defaults") {
            editor.setText(Prefs.DEFAULT); Prefs.set(this, Prefs.DEFAULT)
        })

        root.addView(TextView(this).apply { textSize = 18f; text = "\nTest box" })
        root.addView(EditText(this).apply { hint = "Tap here to test the keyboard (PHONE mode)" })

        startHid()
    }

    override fun onResume() {
        super.onResume()
        status.text = "Status: ${HidManager.status}"
    }
}
