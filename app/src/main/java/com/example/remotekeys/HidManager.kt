package com.example.remotekeys

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import java.util.concurrent.Executors

/** Makes the phone act as a Bluetooth keyboard + media-key remote. */
@SuppressLint("MissingPermission")
object HidManager {
    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var adapter: BluetoothAdapter? = null

    var status: String = "Not started"
        private set
    var onStatus: ((String) -> Unit)? = null

    val isConnected: Boolean get() = hid != null && host != null

    // Report 1 = keyboard (modifiers, reserved, 6 keys). Report 2 = consumer control (media/volume).
    private val descriptor: ByteArray = intArrayOf(
        0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x85, 0x01,
        0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01,
        0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
        0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
        0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65,
        0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81, 0x00,
        0xC0,
        0x05, 0x0C, 0x09, 0x01, 0xA1, 0x01, 0x85, 0x02,
        0x15, 0x00, 0x26, 0xFF, 0x03, 0x19, 0x00, 0x2A, 0xFF, 0x03,
        0x75, 0x10, 0x95, 0x01, 0x81, 0x00,
        0xC0
    ).map { it.toByte() }.toByteArray()

    private fun setStatus(s: String) {
        status = s
        onStatus?.invoke(s)
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            setStatus(if (registered) "Ready - connect to a PC" else "Not registered")
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                host = device
                setStatus("Connected to ${device.name ?: device.address}")
            } else if (state == BluetoothProfile.STATE_DISCONNECTED && device == host) {
                host = null
                setStatus("Disconnected")
            }
        }
    }

    fun start(ctx: Context) {
        if (hid != null) return
        try {
            val mgr = ctx.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            adapter = mgr.adapter
            val ad = adapter
            if (ad == null || !ad.isEnabled) { setStatus("Turn Bluetooth on"); return }
            ad.getProfileProxy(ctx.applicationContext, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile != BluetoothProfile.HID_DEVICE) return
                    try {
                        val h = proxy as BluetoothHidDevice
                        hid = h
                        val sdp = BluetoothHidDeviceAppSdpSettings(
                            "RemoteKeys", "Remote keyboard", "RemoteKeys",
                            BluetoothHidDevice.SUBCLASS1_COMBO, descriptor
                        )
                        h.registerApp(sdp, null, null, Executors.newSingleThreadExecutor(), callback)
                    } catch (e: SecurityException) {
                        setStatus("Bluetooth permission missing - open the app")
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    hid = null; host = null
                    setStatus("HID service lost")
                }
            }, BluetoothProfile.HID_DEVICE)
        } catch (e: SecurityException) {
            setStatus("Bluetooth permission missing - open the app")
        }
    }

    fun pairedDevices(): List<BluetoothDevice> =
        try { adapter?.bondedDevices?.toList() ?: emptyList() } catch (e: SecurityException) { emptyList() }

    fun connect(device: BluetoothDevice) {
        setStatus("Connecting...")
        try { hid?.connect(device) } catch (e: SecurityException) { setStatus("Permission missing") }
    }

    private fun sendKeyboard(mods: Int, key: Int) {
        val h = hid ?: return
        val d = host ?: return
        val r = ByteArray(8)
        r[0] = mods.toByte()
        r[2] = key.toByte()
        h.sendReport(d, 1, r)
    }

    private fun sendConsumer(code: Int) {
        val h = hid ?: return
        val d = host ?: return
        h.sendReport(d, 2, byteArrayOf((code and 0xFF).toByte(), ((code shr 8) and 0xFF).toByte()))
    }

    /** Press and release a key / combo. */
    fun tap(a: HidKeys.Action, extraMods: Int = 0) {
        if (a.consumer != 0) {
            sendConsumer(a.consumer); sendConsumer(0)
        } else {
            sendKeyboard(a.mods or extraMods, a.key); sendKeyboard(0, 0)
        }
    }
}
