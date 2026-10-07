using System;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using RemoteNumPad.Services;
using Xunit;

namespace RemoteNumPad.AndroidProtocol.Tests;

public class ConsoleExitInputTests
{
    [Theory]
    [InlineData("q")]
    [InlineData(" Q ")]
    [InlineData("exit")]
    public async Task QuitCommandStopsReceiverAfterIgnoringOtherInput(string quitCommand)
    {
        using var input = new StringReader($"ignored{Environment.NewLine}{quitCommand}{Environment.NewLine}");
        var stopCount = 0;

        await ConsoleExitInput.ListenAsync(input, () => stopCount++, CancellationToken.None);

        Assert.Equal(1, stopCount);
    }

    [Fact]
    public async Task ClosedConsoleInputDoesNotStopReceiver()
    {
        using var input = new StringReader(string.Empty);
        var stopCount = 0;

        await ConsoleExitInput.ListenAsync(input, () => stopCount++, CancellationToken.None);

        Assert.Equal(0, stopCount);
    }
}
