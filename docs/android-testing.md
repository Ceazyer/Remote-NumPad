# Android 1.2.2 installation and test checklist

## Install the debug APK

Build it on the development PC from the repository root:

```powershell
cd .\android
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

With Android platform-tools installed and an authorized phone connected by USB, install from the repository root:

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adb devices
& $adb install -r .\android\app\build\outputs\apk\debug\app-debug.apk
```

Alternatively, copy `android/app/build/outputs/apk/debug/app-debug.apk` to the phone and open it. The app uses a debug signing key; Android may ask to allow installation from that file manager. It targets API 36 and supports Android API 26 and later. It is not a store/release-signed build.

## Safe connection setup

1. Start the Windows Remote NumPad service from `publish/android-1.2.2-win-x64/RemoteNumPad.exe`. The earlier `publish/win-x64/RemoteNumPad.exe` build does not support the Android v2 handshake. Keep the phone and PC on the same trusted private Wi-Fi. The PC displays its LAN address; use that IPv4 address and port `8765`. When finished, type `Q` and press Enter in the receiver window to exit.
2. Before sending keys, bring a disposable test target such as Notepad or a throwaway workbook to the foreground. The Windows service injects into whichever app currently has keyboard focus; it cannot protect an existing workbook from test input.
3. Open Android **设置**, enter the PC's private IPv4 address and port, and choose **保存并连接**. Wait for the status to show connected. The keypad never opens the soft keyboard.
4. For visible phone-side counts, enable **显示本次启动的链路计数** in that settings dialog. This is off by default and counts only clicks, durable enqueues, sends, and ACKs for the current app process.
5. Optional Windows-side cumulative diagnostics: start the Windows service from PowerShell as shown below:

```powershell
$env:REMOTENUMPAD_DIAGNOSTICS = '1'
& .\RemoteNumPad.exe
```

The server prints cumulative v2 received-command, complete-injection, and ACK-send counters. They contain counts only, not entered values, workbook data, device IDs, or host addresses. A received-command total can include retries; compare it with injection success and ACK totals rather than interpreting it alone.

If the phone continues to show **正在重新连接电脑**, check that the new Windows service is still running and listening on port `8765`, that the phone is using the PC's current Wi-Fi IPv4 address without `http://` or `ws://`, and that the network permits phone-to-PC traffic. If the phone remains on **连接已建立，正在校验服务**, verify that the running EXE is the new v2-compatible build. The PC's Wi-Fi IPv4 address can change when the network changes.

For a quick network check, open `http://<PC-Wi-Fi-IPv4>:8765` in the phone's browser. If the Remote NumPad page does not load, confirm both devices are on the same Wi-Fi and check for client isolation or inbound firewall policy. If it loads, return to the native app and re-enter the same numeric IPv4 address and port in **设置**.

## Manual acceptance

Record starting and ending counters and verify the actual visible test-target text after each case:

1. Tap the same digit rapidly 100 times. Expected: the target receives 100 copies in order, the phone reports `点击 100 · 入队 100`, and after ACK completion `已发` and `确认` both reach 100.
2. Tap an alternating sequence of digits 100 times. Expected: exact tap order and count are preserved; no digit is missing, duplicated, or reordered.
3. In a throwaway workbook only, enter a short number followed by **Enter**, then test **下一格**. Check both the typed value and selected-cell movement.
4. During a disposable input test, disconnect Wi-Fi or stop the PC service. New clicks should remain queued and visible, not disappear. Restore the same server and explicitly confirm the review prompt before continuing.
5. With pending input, restart the PC service or terminate/reopen the Android app. A changed server instance or recovered pending queue must require review before sending. Never blindly approve old input if the selected cell may have changed.
6. If a command reports a complete failure, check the foreground test target and retry explicitly. For an **uncertain** result, inspect the target first, then skip the uncertain item or clear the queue; do not automatically replay it.

To run native UI-touch and SQLite persistence instrumentation tests on an authorized connected device/emulator:

```powershell
cd .\android
..\.tools\gradle-9.6.0\bin\gradle.bat connectedDebugAndroidTest --no-daemon
```

Or use the project wrapper with `.gradlew.bat connectedDebugAndroidTest` after wrapper dependencies are available. These tests include 100 rapid synthetic taps and SQLite reopen checks; they do not replace the real touchscreen acceptance above.

## Limits and current status

- LAN WebSocket uses plaintext `ws://`, has no authentication, and should only be used on a trusted private network.
- An `injected` ACK confirms that all expected `SendInput` events entered the Windows input stream. It does not confirm the target app, Excel/WPS focus, cell selection, or resulting workbook contents.
- Android queue/SQLite JVM tests and server protocol tests are automated. An API 36 emulator is configured for layout, theme and synthetic-touch instrumentation checks. Real touchscreen speed, Excel/WPS behavior, and hardware/network interruption tests remain for the user to run.
