using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using RemoteNumPad.Services;
using Xunit;

namespace RemoteNumPad.AndroidProtocol.Tests;

public sealed class ProtocolReliabilityTests
{
    [Fact]
    public async Task FragmentedTextMessageIsAssembledBeforeParsing()
    {
        using var socket = new FragmentedWebSocket(
            WebSocketMessageType.Text,
            Encoding.UTF8.GetBytes("{\"v\":2,"),
            Encoding.UTF8.GetBytes("\"type\":\"hello\"}"));

        var message = await WebSocketMessageReader.ReadAsync(socket, 4096, CancellationToken.None);

        Assert.Equal("{\"v\":2,\"type\":\"hello\"}", message.Text);
        Assert.Equal(WebSocketMessageType.Text, message.MessageType);
    }

    [Fact]
    public async Task OversizedWebSocketMessageIsRejectedBeforeItCanBeParsed()
    {
        using var socket = new FragmentedWebSocket(
            WebSocketMessageType.Text,
            Encoding.UTF8.GetBytes("123"),
            Encoding.UTF8.GetBytes("45"));

        await Assert.ThrowsAsync<InvalidDataException>(
            () => WebSocketMessageReader.ReadAsync(socket, 4, CancellationToken.None));
    }

    [Fact]
    public async Task InvalidUtf8IsRejectedInsteadOfBeingDecodedWithReplacementCharacters()
    {
        using var socket = new FragmentedWebSocket(WebSocketMessageType.Text, new byte[] { 0xC3, 0x28 });

        await Assert.ThrowsAsync<DecoderFallbackException>(
            () => WebSocketMessageReader.ReadAsync(socket, 4096, CancellationToken.None));
    }

    [Fact]
    public void RetriedSequenceReturnsSameAckWithoutInjectingTwice()
    {
        var keyboard = new KeyboardService(expected => expected);
        var protocol = new AndroidCommandProtocol(keyboard);
        var hello = protocol.Handle("{\"v\":2,\"type\":\"hello\",\"clientId\":\"phone-1\"}");

        Assert.Equal("welcome", ReadString(hello, "type"));
        var request = "{\"v\":2,\"type\":\"command\",\"clientId\":\"phone-1\",\"seq\":1,\"command\":\"7\"}";

        var firstAck = protocol.Handle(request);
        var retryAck = protocol.Handle(request);

        Assert.Equal("injected", ReadString(firstAck, "result"));
        Assert.Equal(firstAck, retryAck);
        Assert.Equal(1, keyboard.SuccessfulCommandCount);
    }

    [Fact]
    public void SequenceGapIsRejectedWithoutInjecting()
    {
        var keyboard = new KeyboardService(expected => expected);
        var protocol = new AndroidCommandProtocol(keyboard);
        protocol.Handle("{\"v\":2,\"type\":\"hello\",\"clientId\":\"phone-1\"}");
        protocol.Handle("{\"v\":2,\"type\":\"command\",\"clientId\":\"phone-1\",\"seq\":1,\"command\":\"7\"}");

        var ack = protocol.Handle("{\"v\":2,\"type\":\"command\",\"clientId\":\"phone-1\",\"seq\":3,\"command\":\"7\"}");

        Assert.Equal("failed", ReadString(ack, "result"));
        Assert.Equal("sequence_gap", ReadString(ack, "error"));
        Assert.Equal(1, keyboard.SuccessfulCommandCount);
    }

    [Theory]
    [InlineData(2, "injected")]
    [InlineData(0, "failed")]
    [InlineData(1, "uncertain")]
    public void CommandAcknowledgementReflectsSendInputCount(int sentCount, string expected)
    {
        var keyboard = new KeyboardService(_ => sentCount);

        var result = keyboard.SendCommand("7");

        Assert.Equal(expected, result.Status.ToString().ToLowerInvariant());
    }

    [Fact]
    public void LegacyRawCommandIsStillInjected()
    {
        var keyboard = new KeyboardService(expected => expected);
        var protocol = new AndroidCommandProtocol(keyboard);

        var response = protocol.Handle("7");

        Assert.Null(response);
        Assert.Equal(1, keyboard.SuccessfulCommandCount);
    }

    [Theory]
    [InlineData("SAVE", 4)]
    [InlineData("SAVE_AS", 2)]
    public void SaveCommandsReachTheInputBoundaryAndReceiveAcknowledgement(string command, int expectedEvents)
    {
        var batches = new System.Collections.Generic.List<int>();
        var keyboard = new KeyboardService(count => { batches.Add(count); return count; });
        var protocol = new AndroidCommandProtocol(keyboard);
        protocol.Handle("{\"v\":2,\"type\":\"hello\",\"clientId\":\"phone-save\"}");
        var ack = protocol.Handle($"{{\"v\":2,\"type\":\"command\",\"clientId\":\"phone-save\",\"seq\":1,\"command\":\"{command}\"}}");
        Assert.Equal("injected", ReadString(ack, "result"));
        Assert.Equal(new[] { expectedEvents }, batches);
        Assert.Equal(1, keyboard.SuccessfulCommandCount);
    }

    [Fact]
    public void NewServerInstanceAcceptsPersistedClientSequenceAfterPreviousServerRestart()
    {
        var keyboard = new KeyboardService(expected => expected);
        var protocol = new AndroidCommandProtocol(keyboard, "server-instance-2");
        protocol.Handle("{\"v\":2,\"type\":\"hello\",\"clientId\":\"phone-1\"}");

        var ack = protocol.Handle("{\"v\":2,\"type\":\"command\",\"clientId\":\"phone-1\",\"seq\":42,\"command\":\"4\"}");

        Assert.Equal("injected", ReadString(ack, "result"));
        Assert.Equal(1, keyboard.SuccessfulCommandCount);
    }

    [Fact]
    public void FailureInLaterPartOfCompoundCommandIsReportedAsUncertain()
    {
        var batch = 0;
        var keyboard = new KeyboardService(count => ++batch == 1 ? count : 0);

        var result = keyboard.SendCommand("AUTO_SUM");

        Assert.Equal(KeyboardCommandStatus.Uncertain, result.Status);
        Assert.Equal("sendinput_partial", result.ErrorCode);
        Assert.Equal(0, keyboard.SuccessfulCommandCount);
    }

    [Fact]
    public void ConcurrentRetryOfSameSequenceIsAcknowledgedWithoutConcurrentReinjection()
    {
        var sendCalls = 0;
        var keyboard = new KeyboardService(count =>
        {
            Interlocked.Increment(ref sendCalls);
            return count;
        });
        var protocol = new AndroidCommandProtocol(keyboard);
        protocol.Handle("{\"v\":2,\"type\":\"hello\",\"clientId\":\"phone-1\"}");
        const string request = "{\"v\":2,\"type\":\"command\",\"clientId\":\"phone-1\",\"seq\":1,\"command\":\"8\"}";
        var responses = new string[32];

        Parallel.For(0, responses.Length, index => responses[index] = protocol.Handle(request)!);

        Assert.Single(responses.Distinct());
        Assert.All(responses, response => Assert.Equal("injected", ReadString(response, "result")));
        Assert.Equal(1, sendCalls);
    }

    [Fact]
    public void DiagnosticsCountReceivedRetriesSeparatelyFromSuccessfulInjectionAndSentAcks()
    {
        var keyboard = new KeyboardService(expected => expected);
        var protocol = new AndroidCommandProtocol(keyboard);
        protocol.Handle("{\"v\":2,\"type\":\"hello\",\"clientId\":\"phone-1\"}");
        const string request = "{\"v\":2,\"type\":\"command\",\"clientId\":\"phone-1\",\"seq\":1,\"command\":\"6\"}";
        var firstAck = protocol.Handle(request)!;
        var retryAck = protocol.Handle(request)!;

        protocol.RecordResponseSent(firstAck);
        protocol.RecordResponseSent(retryAck);

        Assert.Equal(2, protocol.ReceivedCommandCount);
        Assert.Equal(1, keyboard.SuccessfulCommandCount);
        Assert.Equal(2, protocol.AcknowledgementSentCount);
    }

    [Fact]
    public void FullSendInputFailureDoesNotConsumeTheSequenceAndCanBeExplicitlyRetried()
    {
        var attempt = 0;
        var keyboard = new KeyboardService(count => ++attempt == 1 ? 0 : count);
        var protocol = new AndroidCommandProtocol(keyboard);
        protocol.Handle("{\"v\":2,\"type\":\"hello\",\"clientId\":\"phone-1\"}");
        const string request = "{\"v\":2,\"type\":\"command\",\"clientId\":\"phone-1\",\"seq\":1,\"command\":\"8\"}";

        var failed = protocol.Handle(request);
        var retried = protocol.Handle(request);

        Assert.Equal("failed", ReadString(failed, "result"));
        Assert.Equal("injected", ReadString(retried, "result"));
        Assert.Equal(1, keyboard.SuccessfulCommandCount);
    }

    private static string ReadString(string? json, string property)
    {
        using var document = JsonDocument.Parse(json ?? throw new InvalidOperationException("Expected a protocol response."));
        return document.RootElement.GetProperty(property).GetString()!;
    }

    private sealed class FragmentedWebSocket : WebSocket
    {
        private readonly Queue<byte[]> _fragments;
        private readonly WebSocketMessageType _messageType;
        private WebSocketState _state = WebSocketState.Open;

        public FragmentedWebSocket(WebSocketMessageType messageType, params byte[][] fragments)
        {
            _messageType = messageType;
            _fragments = new Queue<byte[]>(fragments);
        }

        public override WebSocketCloseStatus? CloseStatus => null;
        public override string? CloseStatusDescription => null;
        public override WebSocketState State => _state;
        public override string? SubProtocol => null;

        public override void Abort() => _state = WebSocketState.Aborted;
        public override void Dispose() => _state = WebSocketState.Closed;

        public override Task CloseAsync(WebSocketCloseStatus closeStatus, string? statusDescription, CancellationToken cancellationToken)
        {
            _state = WebSocketState.Closed;
            return Task.CompletedTask;
        }

        public override Task CloseOutputAsync(WebSocketCloseStatus closeStatus, string? statusDescription, CancellationToken cancellationToken)
        {
            _state = WebSocketState.CloseSent;
            return Task.CompletedTask;
        }

        public override Task<WebSocketReceiveResult> ReceiveAsync(ArraySegment<byte> buffer, CancellationToken cancellationToken)
        {
            if (_fragments.Count == 0)
            {
                throw new InvalidOperationException("No message fragment remains.");
            }

            var fragment = _fragments.Dequeue();
            fragment.AsSpan().CopyTo(buffer.AsSpan());
            return Task.FromResult(new WebSocketReceiveResult(
                fragment.Length,
                _messageType,
                _fragments.Count == 0));
        }

        public override Task SendAsync(ArraySegment<byte> buffer, WebSocketMessageType messageType, bool endOfMessage, CancellationToken cancellationToken)
        {
            return Task.CompletedTask;
        }
    }
}
