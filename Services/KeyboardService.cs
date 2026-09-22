using System.ComponentModel;
using System.Runtime.InteropServices;

namespace RemoteNumPad.Services;

public enum KeyboardActionKind
{
    Key,
    Shortcut,
    Text
}

public sealed record KeyboardAction(
    KeyboardActionKind ActionKind,
    IReadOnlyList<string> Keys,
    string? Text)
{
    public string Kind => ActionKind.ToString();
}

public sealed class KeyboardService
{
    private const uint InputKeyboard = 1;
    private const uint KeyEventKeyUp = 0x0002;
    private const uint KeyEventUnicode = 0x0004;

    internal static int NativeInputSize => Marshal.SizeOf<NativeInput>();

    public void SendCommand(string command)
    {
        foreach (var action in GetCommandActions(command))
        {
            switch (action.ActionKind)
            {
                case KeyboardActionKind.Key:
                    SendKey(action.Keys[0]);
                    break;
                case KeyboardActionKind.Shortcut:
                    SendShortcut(action.Keys.ToArray());
                    break;
                case KeyboardActionKind.Text:
                    SendText(action.Text ?? string.Empty);
                    break;
            }
        }
    }

    public void SendKey(string key)
    {
        var virtualKeyCode = GetVirtualKeyCode(key);
        if (virtualKeyCode is null)
        {
            return;
        }

        SendKeyboardInputs(new[]
        {
            CreateKeyboardInput(virtualKeyCode.Value, 0),
            CreateKeyboardInput(virtualKeyCode.Value, KeyEventKeyUp)
        });
    }

    public void SendShortcut(params string[] keys)
    {
        if (keys.Length == 0)
        {
            return;
        }

        var virtualKeys = new ushort[keys.Length];
        for (var index = 0; index < keys.Length; index++)
        {
            var virtualKey = GetInputVirtualKeyCode(keys[index]);
            if (virtualKey is null)
            {
                return;
            }

            virtualKeys[index] = virtualKey.Value;
        }

        var inputs = new NativeInput[virtualKeys.Length * 2];
        for (var index = 0; index < virtualKeys.Length; index++)
        {
            inputs[index] = CreateKeyboardInput(virtualKeys[index], 0);
            inputs[inputs.Length - 1 - index] = CreateKeyboardInput(virtualKeys[index], KeyEventKeyUp);
        }

        SendKeyboardInputs(inputs);
    }

    public void SendText(string text)
    {
        if (string.IsNullOrEmpty(text))
        {
            return;
        }

        var inputs = new NativeInput[text.Length * 2];
        for (var index = 0; index < text.Length; index++)
        {
            var character = text[index];
            inputs[index * 2] = CreateKeyboardInput(0, KeyEventUnicode, character);
            inputs[index * 2 + 1] = CreateKeyboardInput(0, KeyEventUnicode | KeyEventKeyUp, character);
        }

        SendKeyboardInputs(inputs);
    }

    public static IReadOnlyList<KeyboardAction> GetCommandActions(string command)
    {
        var normalized = command.Trim().ToUpperInvariant();
        if (IsSingleKeyCommand(normalized))
        {
            return new[] { new KeyboardAction(KeyboardActionKind.Key, new[] { normalized }, null) };
        }

        return normalized switch
        {
            "PREV_CELL" => new[] { Shortcut("SHIFT", "TAB") },
            "NEXT_CELL" => new[] { Shortcut("TAB") },
            "UNDO" => new[] { Shortcut("CONTROL", "Z") },
            "COPY" => new[] { Shortcut("CONTROL", "C") },
            "PASTE" => new[] { Shortcut("CONTROL", "V") },
            "AUTO_SUM" => new[] { Shortcut("ALT", "EQUALS"), Key("ENTER") },
            "FORMULA_AVERAGE" => new[] { Text("=AVERAGE(") },
            "FORMULA_MAX" => new[] { Text("=MAX(") },
            "FORMULA_MIN" => new[] { Text("=MIN(") },
            "FORMULA_ROUND" => new[] { Text("=ROUND(") },
            "FORMULA_IF" => new[] { Text("=IF(") },
            _ => Array.Empty<KeyboardAction>()
        };
    }

    public static ushort? GetVirtualKeyCode(string key)
    {
        var normalized = key.Trim().ToUpperInvariant();

        return normalized switch
        {
            "0" => 0x60,
            "1" => 0x61,
            "2" => 0x62,
            "3" => 0x63,
            "4" => 0x64,
            "5" => 0x65,
            "6" => 0x66,
            "7" => 0x67,
            "8" => 0x68,
            "9" => 0x69,
            "." => 0x6E,
            "-" => 0x6D,
            "ENTER" => 0x0D,
            "BACKSPACE" => 0x08,
            "DELETE" => 0x2E,
            "EDIT" => 0x71,
            "UP" => 0x26,
            "DOWN" => 0x28,
            "LEFT" => 0x25,
            "RIGHT" => 0x27,
            _ => null
        };
    }

    private static bool IsSingleKeyCommand(string command)
    {
        return GetVirtualKeyCode(command) is not null;
    }

    private static KeyboardAction Key(string key)
    {
        return new KeyboardAction(KeyboardActionKind.Key, new[] { key }, null);
    }

    private static KeyboardAction Shortcut(params string[] keys)
    {
        return new KeyboardAction(KeyboardActionKind.Shortcut, keys, null);
    }

    private static KeyboardAction Text(string text)
    {
        return new KeyboardAction(KeyboardActionKind.Text, Array.Empty<string>(), text);
    }

    private static ushort? GetInputVirtualKeyCode(string key)
    {
        var normalized = key.Trim().ToUpperInvariant();
        return GetVirtualKeyCode(normalized) ?? normalized switch
        {
            "TAB" => 0x09,
            "SHIFT" => 0x10,
            "CONTROL" => 0x11,
            "ALT" => 0x12,
            "Z" => 0x5A,
            "C" => 0x43,
            "V" => 0x56,
            "EQUALS" => 0xBB,
            _ => null
        };
    }

    private static NativeInput CreateKeyboardInput(ushort virtualKeyCode, uint flags, ushort scanCode = 0)
    {
        return new NativeInput
        {
            Type = InputKeyboard,
            Data = new NativeInputUnion
            {
                Keyboard = new NativeKeyboardInput
                {
                    VirtualKey = virtualKeyCode,
                    ScanCode = scanCode,
                    Flags = flags,
                    Time = 0,
                    ExtraInfo = IntPtr.Zero
                }
            }
        };
    }

    private static void SendKeyboardInputs(NativeInput[] inputs)
    {
        try
        {
            var sent = SendInput((uint)inputs.Length, inputs, Marshal.SizeOf<NativeInput>());
            if (sent != inputs.Length)
            {
                throw new Win32Exception(Marshal.GetLastWin32Error(), "Windows could not simulate the keyboard input.");
            }
        }
        catch (Win32Exception exception)
        {
            Console.Error.WriteLine($"Keyboard input failed: {exception.Message}");
        }
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint numberOfInputs, NativeInput[] inputs, int inputSize);

    [StructLayout(LayoutKind.Sequential)]
    private struct NativeInput
    {
        public uint Type;
        public NativeInputUnion Data;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct NativeInputUnion
    {
        [FieldOffset(0)]
        public NativeKeyboardInput Keyboard;

        [FieldOffset(0)]
        public NativeMouseInput Mouse;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct NativeKeyboardInput
    {
        public ushort VirtualKey;
        public ushort ScanCode;
        public uint Flags;
        public uint Time;
        public IntPtr ExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct NativeMouseInput
    {
        public int X;
        public int Y;
        public uint MouseData;
        public uint Flags;
        public uint Time;
        public IntPtr ExtraInfo;
    }
}
