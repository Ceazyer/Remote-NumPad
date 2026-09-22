#nullable enable

using System.Collections;
using System.Collections.Generic;
using System.Reflection;
using RemoteNumPad.Services;
using Xunit;

namespace RemoteNumPad.V02Backend.Tests;

public sealed class KeyboardCommandTests
{
    [Theory]
    [InlineData("-", 0x6D)]
    [InlineData("DELETE", 0x2E)]
    [InlineData("EDIT", 0x71)]
    [InlineData("UP", 0x26)]
    [InlineData("DOWN", 0x28)]
    [InlineData("LEFT", 0x25)]
    [InlineData("RIGHT", 0x27)]
    public void MapsExcelPanelKeysToWindowsVirtualKeyCodes(string key, ushort expected)
    {
        Assert.Equal(expected, KeyboardService.GetVirtualKeyCode(key));
    }

    [Fact]
    public void SemanticNavigationCommandsExposeShortcutActions()
    {
        var actions = InvokeCommandActions("PREV_CELL");

        Assert.Single(actions);
        Assert.Equal("Shortcut", GetProperty(actions[0], "Kind"));
        Assert.Equal(new[] { "SHIFT", "TAB" }, GetStringListProperty(actions[0], "Keys"));
    }

    [Fact]
    public void AutoSumCommandExposesAltEqualThenEnterActions()
    {
        var actions = InvokeCommandActions("AUTO_SUM");

        Assert.Equal(2, actions.Count);
        Assert.Equal(new[] { "ALT", "EQUALS" }, GetStringListProperty(actions[0], "Keys"));
        Assert.Equal(new[] { "ENTER" }, GetStringListProperty(actions[1], "Keys"));
    }

    [Theory]
    [InlineData("FORMULA_AVERAGE", "=AVERAGE(")]
    [InlineData("FORMULA_MAX", "=MAX(")]
    [InlineData("FORMULA_MIN", "=MIN(")]
    [InlineData("FORMULA_ROUND", "=ROUND(")]
    [InlineData("FORMULA_IF", "=IF(")]
    public void FormulaCommandsExposeUnicodeTextActions(string command, string expectedText)
    {
        var actions = InvokeCommandActions(command);

        Assert.Single(actions);
        Assert.Equal("Text", GetProperty(actions[0], "Kind"));
        Assert.Equal(expectedText, GetProperty(actions[0], "Text"));
    }

    private static IList<object> InvokeCommandActions(string command)
    {
        var method = typeof(KeyboardService).GetMethod(
            "GetCommandActions",
            BindingFlags.Public | BindingFlags.Static);
        Assert.NotNull(method);

        var result = method!.Invoke(null, new object[] { command });
        Assert.NotNull(result);

        var actions = Assert.IsAssignableFrom<IEnumerable>(result);
        var values = new List<object>();
        foreach (var action in actions)
        {
            values.Add(action!);
        }

        return values;
    }

    private static object? GetProperty(object action, string name)
    {
        return action.GetType().GetProperty(name)?.GetValue(action);
    }

    private static IReadOnlyList<string> GetStringListProperty(object action, string name)
    {
        var value = Assert.IsAssignableFrom<IEnumerable>(GetProperty(action, name));
        var strings = new List<string>();
        foreach (var item in value)
        {
            strings.Add(Assert.IsType<string>(item));
        }

        return strings;
    }
}
