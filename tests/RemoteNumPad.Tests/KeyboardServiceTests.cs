using System;
using RemoteNumPad.Services;
using Xunit;

namespace RemoteNumPad.Tests;

public sealed class KeyboardServiceTests
{
    [Fact]
    public void NativeInputLayoutMatchesWindowsInputStructure()
    {
        Assert.Equal(IntPtr.Size == 8 ? 40 : 28, KeyboardService.NativeInputSize);
    }

    [Theory]
    [InlineData("0", 0x60)]
    [InlineData("1", 0x61)]
    [InlineData("9", 0x69)]
    [InlineData(".", 0x6E)]
    [InlineData("ENTER", 0x0D)]
    [InlineData("BACKSPACE", 0x08)]
    public void MapsSupportedKeysToWindowsVirtualKeyCodes(string key, ushort expected)
    {
        Assert.Equal(expected, KeyboardService.GetVirtualKeyCode(key));
    }

    [Fact]
    public void UnsupportedKeyDoesNotHaveAVirtualKeyCode()
    {
        Assert.Null(KeyboardService.GetVirtualKeyCode("TAB"));
    }

    [Fact]
    public void SendKeyDoesNotThrowWhenWindowsRejectsInput()
    {
        var service = new KeyboardService();

        var exception = Record.Exception(() => service.SendKey("1"));

        Assert.Null(exception);
    }
}

