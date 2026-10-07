package com.remotenumpad

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.ContextThemeWrapper
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.HapticFeedbackConstants
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ScrollView
import com.remotenumpad.net.PrivateIpv4Validator
import com.remotenumpad.net.RemoteNumPadConnection
import com.remotenumpad.net.ConnectionEndpoint
import com.remotenumpad.net.ConnectionQrParser
import com.remotenumpad.queue.HapticPolicy
import com.remotenumpad.queue.EnqueueResult
import com.remotenumpad.queue.CommandCatalog
import com.remotenumpad.queue.InputCommand
import com.remotenumpad.queue.QueueSnapshot
import com.remotenumpad.queue.QueueStatus
import com.remotenumpad.ui.BottomKeypadLayout
import com.remotenumpad.ui.AuxiliaryPanel
import com.remotenumpad.ui.DirectionTip
import com.remotenumpad.ui.CommandGrid
import com.remotenumpad.ui.SoftKeyButton
import com.remotenumpad.ui.SoftPalette

class MainActivity : android.app.Activity() {
    private enum class InputPage { INPUT, TOOLS }

    private lateinit var connection: RemoteNumPadConnection
    private lateinit var statusText: TextView
    private lateinit var queueText: TextView
    private lateinit var diagnosticsText: TextView
    private lateinit var commandPanel: FrameLayout
    private var currentPage = InputPage.INPUT
    private var activePrompt: AlertDialog? = null
    private var lastPromptKey: String? = null
    private var lastToastError: String? = null
    private var darkTheme = false
    private var palette = SoftPalette.forDark(false)
    private var statusTone = "warn"
    private var pendingEndpoint: ConnectionEndpoint? = null

    private val preferences by lazy { getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        darkTheme = preferences.getBoolean(PREF_DARK_THEME, false)
        palette = SoftPalette.forDark(darkTheme)
        setTheme(if (darkTheme) R.style.AppThemeDark else R.style.AppTheme)
        setContentView(R.layout.activity_main)
        configureSystemInsets()

        statusText = findViewById(R.id.connection_status)
        queueText = findViewById(R.id.queue_count)
        diagnosticsText = findViewById(R.id.diagnostics_count)
        commandPanel = findViewById(R.id.command_panel)

        connection = RemoteNumPadConnection(
            applicationContext,
            onState = ::renderState,
            onStorageError = { code ->
                Toast.makeText(this, storageErrorMessage(code), Toast.LENGTH_LONG).show()
            },
            onConnected = { endpoint ->
                if (pendingEndpoint == endpoint) {
                    preferences.edit().putString(PREF_SERVER_HOST, endpoint.host).putInt(PREF_SERVER_PORT, endpoint.port).apply()
                    pendingEndpoint = null
                }
            }
        )

        findViewById<Button>(R.id.settings_button).setOnClickListener { showConnectionSettings() }
        findViewById<Button>(R.id.theme_button).setOnClickListener {
            darkTheme = !darkTheme
            preferences.edit().putBoolean(PREF_DARK_THEME, darkTheme).apply()
            palette = SoftPalette.forDark(darkTheme)
            applyTheme()
            renderPage()
        }
        bindTab(R.id.tab_keypad, InputPage.INPUT)
        bindTab(R.id.tab_editing, InputPage.TOOLS)
        currentPage = InputPage.entries.firstOrNull { it.name == savedInstanceState?.getString("page") } ?: InputPage.INPUT
        applyTheme()
        renderPage()
        updateDiagnosticsVisibility()
    }

    override fun onStart() {
        super.onStart()
        connection.start(
            pendingEndpoint?.host ?: preferences.getString(PREF_SERVER_HOST, null),
            pendingEndpoint?.port ?: preferences.getInt(PREF_SERVER_PORT, RemoteNumPadConnection.DEFAULT_PORT)
        )
    }

    override fun onStop() {
        connection.stop()
        super.onStop()
    }

    override fun onDestroy() {
        connection.dispose()
        activePrompt?.dismiss()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configureChrome()
        renderPage()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", currentPage.name)
        super.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION")
    private fun configureSystemInsets() {
        val root = findViewById<View>(R.id.main_root)
        val baseLeft = dp(12)
        val baseTop = dp(4)
        val baseRight = dp(12)
        val baseBottom = dp(4)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
        window.decorView.systemUiVisibility = if (darkTheme) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        root.setOnApplyWindowInsetsListener { view, insets ->
            val left: Int
            val top: Int
            val right: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val types = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                val value = insets.getInsets(types)
                left = value.left
                top = value.top
                right = value.right
                bottom = value.bottom
            } else {
                left = insets.systemWindowInsetLeft
                top = insets.systemWindowInsetTop
                right = insets.systemWindowInsetRight
                bottom = insets.systemWindowInsetBottom
            }
            view.setPadding(
                baseLeft + left,
                baseTop + top,
                baseRight + right,
                baseBottom + bottom
            )
            insets
        }
        root.requestApplyInsets()
    }

    private fun bindTab(viewId: Int, page: InputPage) {
        findViewById<Button>(viewId).setOnClickListener {
            currentPage = page
            renderPage()
        }
    }

    @Suppress("DEPRECATION")
    private fun applyTheme() {
        findViewById<View>(R.id.main_root).setBackgroundColor(palette.surface)
        findViewById<TextView>(R.id.brand_title).setTextColor(palette.text)
        queueText.setTextColor(palette.muted)
        diagnosticsText.setTextColor(palette.muted)
        statusText.setTextColor(statusColor())
        findViewById<TextView>(R.id.compact_status).setTextColor(palette.muted)
        findViewById<SoftKeyButton>(R.id.theme_button).apply {
            applyAppearance(palette, iconResource = if (darkTheme) R.drawable.ic_sun else R.drawable.ic_moon)
            contentDescription = if (darkTheme) "切换到白色主题" else "切换到黑色主题"
            tooltipText = contentDescription
        }
        findViewById<SoftKeyButton>(R.id.settings_button).applyAppearance(palette, iconResource = R.drawable.ic_settings)
        window.decorView.systemUiVisibility = if (darkTheme) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.statusBarColor = palette.surface
            window.navigationBarColor = palette.surface
        }
        configureChrome()
    }

    private fun configureChrome() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        findViewById<LinearLayout>(R.id.main_root).orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        findViewById<View>(R.id.top_chrome).layoutParams = if (landscape) LinearLayout.LayoutParams(dp(252), -1)
            else LinearLayout.LayoutParams(-1, -2)
        commandPanel.layoutParams = if (landscape) LinearLayout.LayoutParams(0, -1, 1f)
            else LinearLayout.LayoutParams(-1, 0, 1f)
        listOf(R.id.status_panel, R.id.page_tabs).forEach { id ->
            val compact = landscape || resources.configuration.screenHeightDp < 620
            findViewById<View>(id).layoutParams = LinearLayout.LayoutParams(-1, dp(if (compact) 48 else 60))
        }
        val compactPortrait = !landscape && resources.configuration.screenHeightDp < 620
        findViewById<LinearLayout>(R.id.chrome_header).orientation = if (compactPortrait) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        findViewById<View>(R.id.status_identity).visibility = if (compactPortrait) View.GONE else View.VISIBLE
        findViewById<View>(R.id.compact_status).visibility = if (compactPortrait) View.VISIBLE else View.GONE
        if (compactPortrait) {
            findViewById<View>(R.id.status_panel).layoutParams = LinearLayout.LayoutParams(dp(96), dp(48))
            findViewById<View>(R.id.page_tabs).layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        }
        listOf(R.id.theme_button, R.id.settings_button, R.id.tab_keypad, R.id.tab_editing).forEach { id ->
            val view = findViewById<View>(id)
            view.layoutParams = view.layoutParams.apply {
                height = dp(48)
                if (id == R.id.theme_button || id == R.id.settings_button) width = dp(48)
            }
        }
        findViewById<View>(R.id.navigation_panel).layoutParams = LinearLayout.LayoutParams(-1,
            dp(if (landscape || resources.configuration.screenHeightDp < 720) 144 else 168))
        updateDiagnosticsVisibility()
    }

    private fun renderPage() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val navigation = AuxiliaryPanel(this).apply {
            addView(createGrid(CommandCatalog.fileControls, 2, false))
            CommandCatalog.navigation.filterNotNull().forEach { descriptor ->
                addView(createCommandButton(descriptor, false).apply {
                    setOnClickListener { enqueueWithFeedback(descriptor.command, this) }
                })
            }
        }
        findViewById<FrameLayout>(R.id.navigation_panel).apply {
            removeAllViews()
            addView(navigation, FrameLayout.LayoutParams(-1, -1))
        }

        val page: View = if (currentPage == InputPage.INPUT) {
            BottomKeypadLayout(this).apply { addView(createGrid(CommandCatalog.keypad, 4, true)) }
        } else {
            val editSection = createSection("编辑", CommandCatalog.editing, 2)
            val formulaSection = createSection("公式", CommandCatalog.formulas, if (landscape) 3 else 2)
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(editSection, LinearLayout.LayoutParams(-1, dp(80)))
                addView(formulaSection, LinearLayout.LayoutParams(-1, 0, 1f))
            }
        }
        commandPanel.removeAllViews()
        commandPanel.addView(page, FrameLayout.LayoutParams(-1, -1))
        styleTabs()
    }

    private fun createSection(title: String, commands: List<InputCommand?>, columns: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(palette.muted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), 0, 0, 0)
            }, LinearLayout.LayoutParams(-1, dp(24)))
            addView(createGrid(commands, columns, false), LinearLayout.LayoutParams(-1, 0, 1f))
        }

    private fun createGrid(commands: List<InputCommand?>, columns: Int, largeDigits: Boolean): CommandGrid {
        val descriptors = commands.filterNotNull()
        val grid = CommandGrid(this, columns, descriptors.map { it.columnSpan },
            if (descriptors.firstOrNull()?.command == "COPY") 0 else 2)
        var previousFocusableId = View.NO_ID
        descriptors.forEach { descriptor ->
            val button = createCommandButton(descriptor, largeDigits)
            if (previousFocusableId != View.NO_ID) {
                button.accessibilityTraversalAfter = previousFocusableId
            }
            previousFocusableId = button.id
            button.setOnClickListener { enqueueWithFeedback(descriptor.command, button) }
            grid.addView(button)
        }

        return grid
    }

    private fun createCommandButton(command: InputCommand, largeDigits: Boolean): Button {
        val isDigit = command.command.length == 1 && command.command[0] in '0'..'9'
        val icon = when (command.command) {
            "UNDO" -> R.drawable.ic_undo
            "PREV_CELL" -> R.drawable.ic_prev
            "EDIT" -> R.drawable.ic_edit
            "DELETE" -> R.drawable.ic_delete
            "BACKSPACE" -> R.drawable.ic_backspace
            "-" -> R.drawable.ic_minus
            "NEXT_CELL" -> R.drawable.ic_next
            "ENTER" -> R.drawable.ic_enter
            "LEFT" -> R.drawable.ic_left
            "UP" -> R.drawable.ic_up
            "DOWN" -> R.drawable.ic_down
            "RIGHT" -> R.drawable.ic_right
            "COPY" -> R.drawable.ic_copy
            "PASTE" -> R.drawable.ic_paste
            "SAVE" -> R.drawable.ic_save
            "SAVE_AS" -> R.drawable.ic_save_as
            else -> 0
        }
        val mark = when (command.command) {
            "AUTO_SUM" -> "Σ"
            "FORMULA_AVERAGE" -> "AVG"
            "FORMULA_MAX" -> "MAX"
            "FORMULA_MIN" -> "MIN"
            "FORMULA_ROUND" -> "ROUND"
            "FORMULA_IF" -> "IF"
            else -> null
        }
        return SoftKeyButton(this).apply {
            id = if (command.command == "7" && currentPage == InputPage.INPUT) R.id.key_7 else View.generateViewId()
            text = command.label
            contentDescription = command.label
            isAllCaps = false
            gravity = Gravity.CENTER
            minHeight = dp(48)
            minWidth = dp(48)
            minimumHeight = dp(48)
            minimumWidth = dp(48)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isDigit && largeDigits || command.command == ".") 32f else 17f)
            applyAppearance(palette, accent = largeDigits && !isDigit && command.command != "." || command.command in setOf("COPY", "PASTE", "SAVE", "SAVE_AS", "EDIT", "UNDO"),
                iconResource = icon, mark = mark, caption = command.label,
                showCaption = command.command in setOf("COPY", "PASTE", "SAVE", "SAVE_AS"),
                tip = when (command.command) {
                    "UP" -> DirectionTip.DOWN
                    "DOWN" -> DirectionTip.UP
                    "LEFT" -> DirectionTip.RIGHT
                    "RIGHT" -> DirectionTip.LEFT
                    else -> null
                })
            stateListAnimator = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tooltipText = command.label
            }
        }
    }

    private fun styleTabs() {
        val selectedId = if (currentPage == InputPage.INPUT) R.id.tab_keypad else R.id.tab_editing
        listOf(R.id.tab_keypad, R.id.tab_editing).forEach { id ->
            findViewById<SoftKeyButton>(id).apply {
                isAllCaps = false
                applyAppearance(palette, accent = id == selectedId)
                isSelected = id == selectedId
            }
        }
    }

    private fun renderState(snapshot: QueueSnapshot, connectionMessage: String) {
        statusText.text = connectionMessage
        statusTone = when {
            snapshot.status == QueueStatus.UNCERTAIN || snapshot.status == QueueStatus.PROTOCOL_ERROR -> "error"
            snapshot.status == QueueStatus.REVIEW_REQUIRED || snapshot.status == QueueStatus.SEND_FAILED -> "warn"
            connectionMessage == "已连接" -> "ok"
            else -> "warn"
        }
        statusText.setTextColor(statusColor())
        queueText.text = "待发送 ${snapshot.pendingCount} / ${com.remotenumpad.queue.CommandQueueEngine.MAX_PENDING_COMMANDS}"
        diagnosticsText.text = "点击 ${snapshot.clickedCount} · 入队 ${snapshot.enqueuedCount} · 已发 ${snapshot.sentCount} · 确认 ${snapshot.acknowledgedCount}"
        updateCompactStatus()

        if (snapshot.errorCode == "queue_full" && lastToastError != snapshot.errorCode) {
            Toast.makeText(this, "待发送队列已满，本次按键未入队", Toast.LENGTH_LONG).show()
        }
        lastToastError = snapshot.errorCode
        updateDecisionPrompt(snapshot)
    }

    private fun updateDecisionPrompt(snapshot: QueueSnapshot) {
        val promptKey = when (snapshot.status) {
            QueueStatus.REVIEW_REQUIRED -> "review"
            QueueStatus.SEND_FAILED -> "failed"
            QueueStatus.UNCERTAIN -> "uncertain"
            QueueStatus.PROTOCOL_ERROR -> "protocol"
            else -> null
        }

        if (promptKey == null) {
            lastPromptKey = null
            return
        }
        if (activePrompt?.isShowing == true || lastPromptKey == promptKey || isFinishing) return
        lastPromptKey = promptKey

        val title: String
        val message: String
        val positive: String
        val onPositive: () -> Unit
        val negative: String
        when (snapshot.status) {
            QueueStatus.REVIEW_REQUIRED -> {
                title = "待发送输入已暂停"
                message = "还有 ${snapshot.pendingCount} 条输入没有得到确认。请先确认电脑上的 Excel/WPS 仍停留在原目标单元格；继续会按原序号发送，清空则会删除这些待发送项。"
                positive = "确认后继续"
                onPositive = connection::resumeAfterReview
                negative = "清空待发送"
            }
            QueueStatus.SEND_FAILED -> {
                title = "电脑没有插入这个按键"
                message = "Windows 报告本次键盘注入失败，后续输入已暂停。可在确认目标单元格无变化后重试当前按键，或清空待发送项。"
                positive = "重试当前键"
                onPositive = connection::retryFailedCurrent
                negative = "清空待发送"
            }
            QueueStatus.UNCERTAIN -> {
                title = "按键插入结果不确定"
                message = "Windows 只报告部分键盘事件已插入。为避免重复输入，不会自动重发。请先在测试单元格核对结果，再选择跳过当前项或清空待发送队列。"
                positive = "核对后跳过此键"
                onPositive = connection::skipUncertainAfterManualCheck
                negative = "清空待发送"
            }
            else -> {
                title = "通信校验失败"
                message = "服务端返回了无法识别的响应，输入已暂停且仍保留在本机。为避免误写，请清空队列后重新连接。"
                positive = "清空待发送"
                onPositive = connection::clearPending
                negative = "稍后处理"
            }
        }

        val dialog = AlertDialog.Builder(dialogContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positive) { _, _ -> onPositive() }
            .setNegativeButton(negative) { _, _ ->
                if (snapshot.status != QueueStatus.PROTOCOL_ERROR) connection.clearPending()
            }
            .setNeutralButton("稍后") { _, _ -> }
            .create()
        activePrompt = dialog
        dialog.setOnDismissListener { activePrompt = null }
        dialog.show()
        styleDialog(dialog)
    }

    private fun showConnectionSettings() {
        var connectionDialog: AlertDialog? = null
        val themedContext = dialogContext()
        val box = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }
        val hostField = EditText(themedContext).apply {
            hint = "例如 192.168.1.20"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_NEXT
            setText(preferences.getString(PREF_SERVER_HOST, ""))
            contentDescription = "电脑局域网 IPv4 地址"
        }
        val portField = EditText(themedContext).apply {
            hint = "端口（默认 8765）"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            setText(preferences.getInt(PREF_SERVER_PORT, RemoteNumPadConnection.DEFAULT_PORT).toString())
            contentDescription = "电脑服务端口"
        }
        val privacyNote = TextView(themedContext).apply {
            text = "仅允许 10.x、172.16–31.x、192.168.x 局域网地址。连接使用明文 WebSocket，请只在可信局域网中使用。"
            setTextColor(palette.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(8), 0, dp(8))
        }
        val diagnosticsToggle = CheckBox(themedContext).apply {
            text = "显示本次启动的链路计数"
            isChecked = preferences.getBoolean(PREF_DIAGNOSTICS, false)
            setOnCheckedChangeListener { _, checked ->
                preferences.edit().putBoolean(PREF_DIAGNOSTICS, checked).apply()
                updateDiagnosticsVisibility()
            }
        }
        val hapticsToggle = CheckBox(themedContext).apply {
            text = "按键轻触反馈（入队成功后，遵循系统设置）"
            isChecked = preferences.getBoolean(PREF_HAPTICS, false)
            setOnCheckedChangeListener { _, checked -> preferences.edit().putBoolean(PREF_HAPTICS, checked).apply() }
        }
        val scanButton = SoftKeyButton(themedContext).apply {
            text = "扫描电脑连接二维码"
            contentDescription = "扫描电脑连接二维码"
            applyAppearance(palette, accent = true)
            setOnClickListener {
                connectionDialog?.dismiss()
                startActivityForResult(Intent(this@MainActivity, QrScanActivity::class.java), REQUEST_SCAN)
            }
        }
        val versionText = TextView(themedContext).apply {
            text = "Remote NumPad Android · 版本 1.3.0"
            setTextColor(palette.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }

        box.addView(hostField)
        box.addView(portField)
        box.addView(scanButton, LinearLayout.LayoutParams(-1, dp(56)))
        box.addView(privacyNote)
        box.addView(hapticsToggle)
        box.addView(diagnosticsToggle)
        box.addView(versionText)

        val dialog = AlertDialog.Builder(themedContext)
            .setTitle("电脑连接设置")
            .setView(ScrollView(themedContext).apply { addView(box) })
            .setPositiveButton("保存并连接", null)
            .setNegativeButton("取消", null)
            .create()
        connectionDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val host = hostField.text.toString().trim()
                val port = portField.text.toString().toIntOrNull()
                when {
                    !PrivateIpv4Validator.isAllowed(host) -> hostField.error = "请输入电脑的私有 IPv4 地址"
                    port == null || port !in 1..65535 -> portField.error = "请输入有效端口"
                    else -> {
                        dialog.dismiss()
                        requestConnection(ConnectionEndpoint(host, port))
                    }
                }
            }
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        dialog.show()
        styleDialog(dialog)
    }

    private fun enqueueWithFeedback(command: String, button: View) {
        connection.enqueue(command) {
            if (!isFinishing && HapticPolicy.shouldFeedback(preferences.getBoolean(PREF_HAPTICS, false), EnqueueResult.ADDED))
                button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SCAN || resultCode != RESULT_OK) return
        val endpoint = ConnectionQrParser.parse(data?.getStringExtra("connection_qr") ?: "")
        if (endpoint == null) {
            Toast.makeText(this, "不是有效的 Remote NumPad 局域网连接二维码", Toast.LENGTH_LONG).show()
            return
        }
        val dialog = AlertDialog.Builder(dialogContext()).setTitle("连接此电脑？")
            .setMessage("电脑地址：${endpoint.host}\n端口：${endpoint.port}\n\n只在可信局域网使用。握手成功后才保存设置。")
            .setPositiveButton("连接") { _, _ -> requestConnection(endpoint) }
            .setNegativeButton("取消", null).create()
        dialog.show(); styleDialog(dialog)
    }

    private fun requestConnection(endpoint: ConnectionEndpoint) {
        connection.hasPending { pending ->
            if (isFinishing) return@hasPending
            fun connect() { pendingEndpoint = endpoint; connection.start(endpoint.host, endpoint.port) }
            if (!pending) { connect(); return@hasPending }
            val dialog = AlertDialog.Builder(dialogContext()).setTitle("存在待发送输入")
                .setMessage("未确认输入不能自动转移到另一台电脑。返回处理原连接，或明确清空后更换。只有确认仍是同一台电脑、目标单元格正确时，才保留队列连接；之后仍须按恢复提示确认。")
                .setPositiveButton("清空并连接") { _, _ -> connection.clearPending(); connect() }
                .setNeutralButton("同电脑保留并连接") { _, _ -> connect() }
                .setNegativeButton("返回处理", null).create()
            dialog.show(); styleDialog(dialog)
        }
    }

    private fun dialogContext(): Context = ContextThemeWrapper(this, if (darkTheme) R.style.AppThemeDark else R.style.AppTheme)

    private fun styleDialog(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
            setColor(palette.surface)
            cornerRadius = dp(24).toFloat()
        })
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL).forEach {
            dialog.getButton(it)?.setTextColor(palette.accent)
        }
    }

    private fun statusColor(): Int = when (statusTone) {
        "ok" -> palette.ok
        "error" -> palette.error
        else -> palette.warn
    }

    private fun updateDiagnosticsVisibility() {
        val compactPortrait = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE && resources.configuration.screenHeightDp < 620
        diagnosticsText.visibility = if (preferences.getBoolean(PREF_DIAGNOSTICS, false) && !compactPortrait) View.VISIBLE else View.GONE
        updateCompactStatus()
    }

    private fun updateCompactStatus() {
        findViewById<TextView>(R.id.compact_status).text = if (preferences.getBoolean(PREF_DIAGNOSTICS, false))
            "${statusText.text} · ${diagnosticsText.text}" else "${statusText.text} · ${queueText.text}"
    }

    private fun storageErrorMessage(code: String): String = when (code) {
        "queue_read_failed" -> "无法读取本机待发送队列，未连接电脑"
        else -> "本次按键未能保存到本机队列，请重试"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        const val PREFERENCES_NAME = "remote-numpad-private-settings"
        const val PREF_SERVER_HOST = "server_host"
        const val PREF_SERVER_PORT = "server_port"
        const val PREF_DIAGNOSTICS = "diagnostics_enabled"
        const val PREF_DARK_THEME = "dark_theme"
        const val PREF_HAPTICS = "haptics_enabled"
        private const val REQUEST_SCAN = 300
    }
}
