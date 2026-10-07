namespace RemoteNumPad.Services;

internal static class ConsoleExitInput
{
    internal static async Task ListenAsync(
        TextReader input,
        Action requestStop,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(input);
        ArgumentNullException.ThrowIfNull(requestStop);

        while (!cancellationToken.IsCancellationRequested)
        {
            string? line;
            try
            {
                line = await input.ReadLineAsync(cancellationToken);
            }
            catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
            {
                return;
            }
            catch (IOException)
            {
                return;
            }

            if (line is null)
            {
                return;
            }

            var command = line.Trim();
            if (command.Equals("q", StringComparison.OrdinalIgnoreCase) ||
                command.Equals("exit", StringComparison.OrdinalIgnoreCase))
            {
                requestStop();
                return;
            }
        }
    }
}
