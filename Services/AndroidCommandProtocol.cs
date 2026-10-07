using System.Net.WebSockets;
using System.Text;
using System.Text.Json;

namespace RemoteNumPad.Services;

internal sealed record WebSocketMessageReadResult(WebSocketMessageType MessageType, string? Text);

internal static class WebSocketMessageReader
{
    private static readonly UTF8Encoding StrictUtf8 = new(false, true);

    public static async Task<WebSocketMessageReadResult> ReadAsync(
        WebSocket socket,
        int maximumBytes,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(socket);
        if (maximumBytes <= 0)
        {
            throw new ArgumentOutOfRangeException(nameof(maximumBytes));
        }

        var buffer = new byte[Math.Min(1024, maximumBytes)];
        using var payload = new MemoryStream();
        WebSocketMessageType? messageType = null;
        WebSocketReceiveResult fragment;

        do
        {
            fragment = await socket.ReceiveAsync(new ArraySegment<byte>(buffer), cancellationToken);
            messageType ??= fragment.MessageType;

            if (fragment.MessageType == WebSocketMessageType.Close)
            {
                return new WebSocketMessageReadResult(WebSocketMessageType.Close, null);
            }

            if (payload.Length + fragment.Count > maximumBytes)
            {
                throw new InvalidDataException("WebSocket message exceeds the allowed size.");
            }

            payload.Write(buffer, 0, fragment.Count);
        }
        while (!fragment.EndOfMessage);

        var receivedType = messageType ?? throw new InvalidDataException("WebSocket message has no type.");
        var text = receivedType == WebSocketMessageType.Text
            ? StrictUtf8.GetString(payload.GetBuffer(), 0, checked((int)payload.Length))
            : null;

        return new WebSocketMessageReadResult(receivedType, text);
    }
}

internal sealed class AndroidCommandProtocol
{
    private const int ProtocolVersion = 2;
    private const int MaximumClientIdLength = 80;

    private readonly KeyboardService _keyboardService;
    private readonly object _gate = new();
    private readonly Dictionary<string, ClientSequence> _clients = new(StringComparer.Ordinal);
    private readonly string _serverInstanceId;
    private long _receivedCommandCount;
    private long _successfulInjectionCount;
    private long _acknowledgementSentCount;

    internal long ReceivedCommandCount => Interlocked.Read(ref _receivedCommandCount);
    internal long SuccessfulInjectionCount => Interlocked.Read(ref _successfulInjectionCount);
    internal long AcknowledgementSentCount => Interlocked.Read(ref _acknowledgementSentCount);

    public AndroidCommandProtocol(KeyboardService keyboardService)
        : this(keyboardService, Guid.NewGuid().ToString("N"))
    {
    }

    internal AndroidCommandProtocol(KeyboardService keyboardService, string serverInstanceId)
    {
        _keyboardService = keyboardService ?? throw new ArgumentNullException(nameof(keyboardService));
        _serverInstanceId = serverInstanceId;
    }

    public string? Handle(string message)
    {
        if (string.IsNullOrWhiteSpace(message) || !message.AsSpan().TrimStart().StartsWith("{"))
        {
            _ = _keyboardService.SendCommand(message ?? string.Empty);
            return null;
        }

        JsonDocument document;
        try
        {
            document = JsonDocument.Parse(message);
        }
        catch (JsonException)
        {
            return Error("invalid_json");
        }

        using (document)
        {
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object ||
                !root.TryGetProperty("v", out var version) ||
                !version.TryGetInt32(out var versionValue) ||
                versionValue != ProtocolVersion ||
                !TryReadString(root, "type", out var type))
            {
                return Error("invalid_message");
            }

            if (!TryReadClientId(root, out var clientId))
            {
                return Error("invalid_client_id");
            }

            return type switch
            {
                "hello" => Welcome(clientId),
                "command" => HandleCommand(root, clientId),
                _ => Error("unknown_message_type", clientId)
            };
        }
    }

    internal bool RecordResponseSent(string response)
    {
        try
        {
            using var document = JsonDocument.Parse(response);
            if (!document.RootElement.TryGetProperty("type", out var type) ||
                type.GetString() != "ack")
            {
                return false;
            }

            Interlocked.Increment(ref _acknowledgementSentCount);
            return true;
        }
        catch (JsonException)
        {
            return false;
        }
    }

    private string HandleCommand(JsonElement root, string clientId)
    {
        Interlocked.Increment(ref _receivedCommandCount);
        if (!root.TryGetProperty("seq", out var sequenceElement) ||
            !sequenceElement.TryGetInt64(out var sequence) ||
            sequence <= 0 ||
            !TryReadString(root, "command", out var command))
        {
            return Error("invalid_command", clientId);
        }

        command = command.Trim().ToUpperInvariant();
        if (command.Length == 0 || command.Length > 64 || KeyboardService.GetCommandActions(command).Count == 0)
        {
            return Ack(clientId, sequence, "failed", "unknown_command");
        }

        lock (_gate)
        {
            _clients.TryGetValue(clientId, out var previous);
            var expectedSequence = previous is null ? sequence : previous.Sequence + 1;

            if (previous is not null && sequence == previous.Sequence)
            {
                return string.Equals(command, previous.Command, StringComparison.Ordinal)
                    ? previous.Acknowledgement
                    : Ack(clientId, sequence, "failed", "sequence_conflict");
            }

            if (sequence < expectedSequence)
            {
                return Ack(clientId, sequence, "failed", "stale_sequence");
            }

            if (sequence != expectedSequence)
            {
                return Ack(clientId, sequence, "failed", "sequence_gap");
            }

            var result = _keyboardService.SendCommand(command);
            if (result.Status == KeyboardCommandStatus.Injected)
            {
                Interlocked.Increment(ref _successfulInjectionCount);
            }

            var acknowledgement = result.Status switch
            {
                KeyboardCommandStatus.Injected => Ack(clientId, sequence, "injected"),
                KeyboardCommandStatus.Uncertain => Ack(clientId, sequence, "uncertain", result.ErrorCode),
                KeyboardCommandStatus.Failed => Ack(clientId, sequence, "failed", result.ErrorCode),
                _ => Ack(clientId, sequence, "failed", result.ErrorCode ?? "unknown_command")
            };

            // A full failure inserted no events and may be explicitly retried by the client.
            // Successful and partial insertions are cached so a lost ACK never duplicates input.
            if (result.Status is KeyboardCommandStatus.Injected or KeyboardCommandStatus.Uncertain)
            {
                _clients[clientId] = new ClientSequence(sequence, command, acknowledgement);
            }

            return acknowledgement;
        }
    }

    private string Welcome(string clientId)
    {
        return JsonSerializer.Serialize(new
        {
            v = ProtocolVersion,
            type = "welcome",
            clientId,
            serverInstanceId = _serverInstanceId
        });
    }

    private static string Ack(string clientId, long sequence, string result, string? error = null)
    {
        return JsonSerializer.Serialize(new
        {
            v = ProtocolVersion,
            type = "ack",
            clientId,
            seq = sequence,
            result,
            error
        });
    }

    private static string Error(string code, string? clientId = null)
    {
        return JsonSerializer.Serialize(new
        {
            v = ProtocolVersion,
            type = "error",
            clientId,
            error = code
        });
    }

    private static bool TryReadString(JsonElement root, string property, out string value)
    {
        value = string.Empty;
        if (!root.TryGetProperty(property, out var element) || element.ValueKind != JsonValueKind.String)
        {
            return false;
        }

        value = element.GetString() ?? string.Empty;
        return value.Length > 0;
    }

    private static bool TryReadClientId(JsonElement root, out string clientId)
    {
        clientId = string.Empty;
        if (!TryReadString(root, "clientId", out var value) ||
            value.Length > MaximumClientIdLength ||
            value.Any(character => !char.IsAsciiLetterOrDigit(character) && character is not '-' and not '_'))
        {
            return false;
        }

        clientId = value;
        return true;
    }

    private sealed record ClientSequence(long Sequence, string Command, string Acknowledgement);
}
