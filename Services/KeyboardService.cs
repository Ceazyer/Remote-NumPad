using System.ComponentModel;
using System.Runtime.InteropServices;

namespace RemoteNumPad.Services;

public sealed class KeyboardService
{
    private const uint InputKeyboard = 1;
    private const uint KeyEventKeyUp = 0x0002;

    internal static int NativeInputSize => Marshal.SizeOf<NativeInput>();

    public void SendKey(string key)
    {
        var virtualKeyCode = GetVirtualKeyCode(key);
        if (virtualKeyCode is null)
        {
            return;
        }

        var inputs = new[]
        {
            CreateKeyboardInput(virtualKeyCode.Value, 0),
            CreateKeyboardInput(virtualKeyCode.Value, KeyEventKeyUp)
        };

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
            "ENTER" => 0x0D,
            "BACKSPACE" => 0x08,
            _ => null
        };
    }

    private static NativeInput CreateKeyboardInput(ushort virtualKeyCode, uint flags)
    {
        return new NativeInput
        {
            Type = InputKeyboard,
            Data = new NativeInputUnion
            {
                Keyboard = new NativeKeyboardInput
                {
                    VirtualKey = virtualKeyCode,
                    ScanCode = 0,
                    Flags = flags,
                    Time = 0,
                    ExtraInfo = IntPtr.Zero
                }
            }
        };
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

