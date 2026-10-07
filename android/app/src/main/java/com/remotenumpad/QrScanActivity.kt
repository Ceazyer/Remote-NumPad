package com.remotenumpad

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.remotenumpad.ui.SoftKeyButton
import com.remotenumpad.ui.SoftPalette

/** Offline QR only; no screenshots, gallery access, uploaded frames or automatic URL opening. */
class QrScanActivity : Activity() {
    private lateinit var scanner: DecoratedBarcodeView
    private var finishedScan = false
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val dark = getSharedPreferences(MainActivity.PREFERENCES_NAME, 0).getBoolean(MainActivity.PREF_DARK_THEME, false)
        val palette = SoftPalette.forDark(dark)
        setTheme(if (dark) R.style.AppThemeDark else R.style.AppTheme)
        scanner = DecoratedBarcodeView(this).apply {
            setDecoderFactory(DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE)))
            setStatusText("将电脑接收端的连接二维码放入框内")
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.surface)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(TextView(this@QrScanActivity).apply {
                text = "扫描电脑连接二维码"
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(palette.text)
            }, LinearLayout.LayoutParams(-1, dp(64)))
            addView(scanner, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(SoftKeyButton(this@QrScanActivity).apply {
                text = "取消扫码，使用手动连接"; applyAppearance(palette, accent = true); setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(-1, dp(64)))
        }
        setContentView(root)
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(dp(12) + bars.left, dp(12) + bars.top, dp(12) + bars.right, dp(12) + bars.bottom)
            }
            insets
        }
        root.requestApplyInsets()
        scanner.decodeSingle(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                if (finishedScan) return
                finishedScan = true
                scanner.pause()
                setResult(RESULT_OK, Intent().putExtra("connection_qr", result.text))
                finish()
            }
        })
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onResume() {
        super.onResume()
        if (!finishedScan && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scanner.resume()
    }
    override fun onPause() { scanner.pause(); super.onPause() }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, grants: IntArray) {
        super.onRequestPermissionsResult(code, permissions, grants)
        if (code == 1 && grants.firstOrNull() == PackageManager.PERMISSION_GRANTED) scanner.resume()
        else if (code == 1) {
            android.widget.Toast.makeText(this, "未授权相机，请使用手动连接", android.widget.Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
