using RemoteNumPad.Services;
using Xunit;

namespace RemoteNumPad.NextCell.Tests;

public sealed class NextCellTests
{
    [Fact]
    public void NextCellUsesTheSendableTabInputPath()
    {
        var actions = KeyboardService.GetCommandActions("NEXT_CELL");

        Assert.Single(actions);
        Assert.Equal("Shortcut", actions[0].Kind);
        Assert.Equal(new[] { "TAB" }, actions[0].Keys);
    }
}
