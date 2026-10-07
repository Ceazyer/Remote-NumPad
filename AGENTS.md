# AGENTS.md
<!-- agents-md-version: 1 -->

## CRITICAL

- MUST use .NET/NuGet and the Android Gradle wrapper; do not introduce npm or competing dependency managers.
- MUST update affected docs and CHANGELOG Unreleased for each bugfix/small feature; keep progress in docs/project-status.md.
- MUST select affected checks from docs/development-rules.md, record evidence in docs/verification-log.md, and run git diff --check before handoff. This checks whitespace, not code lint; use Android lint for Android changes.
- NEVER build, package, or run application tests for docs-only changes. Do not repeat valid same-state checks without a recorded reason.
- NEVER run SendKeyDoesNotThrowWhenWindowsRejectsInput against user documents; use the safety filter below.
- NEVER read/quote private keys/passwords, bypass hooks, force-push, discard user edits, or hand-edit generated build outputs.
- NEVER uninstall/clear a user's Android app, silently replay/transfer pending input, regenerate an existing signing key, or kill another program to free a port.
- Push only on explicit manual push authorization OR the user's explicit confirmation of a specific formal version: one source push per version. Old completed releases do not authorize new pushes.
- Feishu development is cancelled. Cloud/Feishu/iOS work needs new approval; preserve approved UI and input safety rules.

## Domain & Context

- Goal: LAN-only Excel/WPS remote numeric entry through a Windows receiver, native Android app and compatible browser keypad. Application, MIT licensed.
- Architecture: Windows WinForms + ASP.NET Core/Kestrel (.NET 8); Android Kotlin Views + OkHttp + SQLite; browser HTML/CSS/plain JS. No WebView or workspace orchestrator.
- Read docs/project-status.md first; load only task-relevant references. Verify stale claims against source and correct the owning doc.

| Need | Read |
|---|---|
| User setup | README.md, docs/android-testing.md |
| Workflow / release gate | docs/development-rules.md |
| Previous evidence / affected tests | docs/verification-log.md |
| Queue, ACK, reconnect | docs/android-protocol.md |
| Layout / themes / hit shapes | docs/soft-ui-design.md |
| Signing / licenses | docs/release-signing.md, THIRD_PARTY_NOTICES.md |

## Data & State

- Android: persist accepted keys before sending; FIFO capacity 200, one in-flight, seq/ACK and manual recovery. Browser: raw commands, no durable queue/ACK.
- ACK injected means Windows events inserted, NOT Excel/WPS acceptance, focus, cell or final text. Haptics mean durable enqueue only.
- Receiver dedup is process-memory only; restart/reconnect pending input needs review. Desktop settings live in local app-data, not source.

## Execution Context

- Commands run at repository root in PowerShell on Windows. Use .NET 8 SDK (PATH or .tools/dotnet8/dotnet.exe), Android Studio JBR and SDK platform 36.
- Browser viewport checks need Node with global WebSocket (Node 22+) and Edge at the path in tests/mobile-viewport.test.cjs; they launch a browser, not a real receiver.
- Instrumentation needs an owned disposable emulator selected explicitly. Build caches may be absent; do not assume --no-restore works.

## Commands

Select commands for the change; this is not an always-run checklist. Inputs: RemoteNumPad.csproj, android/app/build.gradle.kts, android/gradle/wrapper/gradle-wrapper.properties.

```powershell
dotnet restore .\RemoteNumPad.csproj # ON FAIL: inspect SDK/NuGet errors; correct the identified source/network issue without printing registry credentials.
git diff --check # ON FAIL: fix only reported whitespace in touched files, then recheck.
dotnet build .\RemoteNumPad.csproj # ON FAIL: fix the first compiler error; use .tools/dotnet8/dotnet.exe if the required SDK is absent from PATH.
dotnet test .\tests\RemoteNumPad.Tests\RemoteNumPad.Tests.csproj --filter "FullyQualifiedName!~SendKeyDoesNotThrowWhenWindowsRejectsInput" # ON FAIL: isolate the failing safe regression; never remove this filter.
dotnet test .\tests\RemoteNumPad.AndroidProtocol.Tests\RemoteNumPad.AndroidProtocol.Tests.csproj --filter "FullyQualifiedName~ReceiverServerTests" # ON FAIL: inspect the specific failure and test-owned sockets; do not kill unrelated processes.
.\android\gradlew.bat -p android testDebugUnitTest --tests "*CommandQueueEngineTest*" # ON FAIL: check JAVA_HOME/SDK, then isolate the named JVM assertion; never clear user queues.
.\android\gradlew.bat -p android :app:lintDebug # ON FAIL: inspect android/app/build/reports and fix the reported source issue, not generated reports.
node --test .\tests\mobile-viewport.test.cjs # ON FAIL: check Node WebSocket/Edge prerequisites, then the failed viewport assertion without sending real keys.
.\scripts\build-release.ps1 # ON FAIL: stop publication; inspect public setup docs and build errors without exposing secrets or replacing the key. RELEASE GATE ONLY.
```

## Structure

- Program.cs: STA/single-instance entry; Desktop/: GUI, tray, port settings, QR.
- Services/: server lifecycle, protocol, keyboard injection; NetworkAddress.cs: private IPv4 discovery.
- android/app/src/main/java/com/remotenumpad/: activities; net/: connection/QR; queue/: durable delivery; ui/: soft keys.
- wwwroot/: embedded browser assets; assets/app-icon-light/: original icons; scripts/: release/icon tooling.
- tests/: .NET, GuiSmoke and Node checks; android/app/src/test/ and src/androidTest/: JVM/instrumentation.
- docs/: topic references; LICENSE, LICENSES/, THIRD_PARTY_NOTICES.md: shipped licenses.
- bin/, obj/, android/**/build/, android/.gradle/, publish/: generated - do not edit. .tools/: ignored tooling; .private/: secret-bearing - do not inspect.

## Patterns

- C#: use PascalCase types/files/methods, nullable handling and async/await for new code; keep lifecycle/injection serialized. ReceiverServer owns Kestrel; ReceiverWindow owns GUI.
- Android: use PascalCase Kotlin types/files, camelCase functions, serial persistence/network worker and main-thread native Views updates. Never couple rapid input to debounce or page/theme rebuilds.
- Web: plain browser JS and authored SVG; tests use CJS .cjs. Do not add a UI framework just for styling.
- ConsoleExitInput is legacy, not GUI exit. Embedded wwwroot changes need a rebuilt receiver for packaged verification, not a hot-reload assumption.

## Testing Strategy

- Bug repair: reproduce/add focused regression first, fix minimally, run affected checks once, record source/test/dependency/environment state and limits.
- Reuse evidence only when those inputs and scope match; changed inputs invalidate affected checks, not unrelated ones. Live phone/Excel/network observations are not reusable health guarantees.
- Use fake keyboard injection/disposable data. Synthetic touch/emulator passes do not replace real phone, haptic, camera or workbook acceptance.

## Security

- LAN ws is plaintext/unauthenticated; QR is not authentication. Never expose publicly or log keys, clipboard/workbook contents, camera frames or credentials.
- Exclude .private/, signing files, local.properties, NuGet.Config, databases/logs, publish/ and personal execution docs from source push. Inspect tracked/staged paths; .gitignore alone is not proof.
- Never run scripts/new-android-release-key.ps1 for normal fixes/existing keys. Preserve formal signing continuity and release R8 mapping privately.

## Env

- Android: JAVA_HOME and ANDROID_HOME or ignored local.properties. Release tooling supplies REMOTE_NUMPAD_STORE_FILE, REMOTE_NUMPAD_STORE_PASS, REMOTE_NUMPAD_KEY_ALIAS and REMOTE_NUMPAD_KEY_PASS privately; never request values in chat.

## Git

- Preserve user edits; if a branch is needed use codex/<topic>. For authorized commits use feat:/fix:/docs:/chore: plus concise subject (e.g. docs: record verification policy).
- Formal confirmation authorizes one safe source push for that version, not a GitHub binary release, history rewrite or background job; see docs/development-rules.md.
- No configured CI/PR template/hooks were found. Do not invent remote checks; document selected local evidence. Documentation governance is not a new formal version.
