using System.Net;
using System.Net.Sockets;
using System.Net.Http;
using System.Threading.Tasks;
using System.Net.WebSockets;
using System.Text;
using System.Threading;
using System;
using RemoteNumPad.Services;
using Xunit;

namespace RemoteNumPad.AndroidProtocol.Tests;

public sealed class ReceiverServerTests
{
    [Fact]
    public async Task LiveConnectionsAreCountedAndStopClosesSocketsWithoutKeyboardInjection()
    {
        var keyboard = new KeyboardService(count => count);
        await using var server = new ReceiverServer(keyboard);
        var port = FreePort();
        Assert.True(await server.ApplyPortAsync(port));
        using var socket = new ClientWebSocket();
        using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(10));
        await socket.ConnectAsync(new Uri($"ws://127.0.0.1:{port}/ws"), timeout.Token);
        await socket.SendAsync(Encoding.UTF8.GetBytes("{\"v\":2,\"type\":\"hello\",\"clientId\":\"test-socket\"}"),
            WebSocketMessageType.Text, true, timeout.Token);
        var bytes = new byte[4096];
        var welcome = await socket.ReceiveAsync(bytes, timeout.Token);
        Assert.Contains("welcome", Encoding.UTF8.GetString(bytes, 0, welcome.Count));
        Assert.Equal(1, server.Snapshot.Connections);
        Assert.Equal(0, keyboard.SuccessfulCommandCount);
        await server.StopAsync();
        Assert.Equal(0, server.Snapshot.Connections);
        Assert.False(server.Snapshot.Running);
    }

    [Fact]
    public async Task StartsOnSelectedPortAndServesTheEmbeddedPanel()
    {
        await using var server = new ReceiverServer(new KeyboardService(count => count));
        var port = FreePort();
        Assert.True(await server.ApplyPortAsync(port));
        Assert.True(server.Snapshot.Running);
        Assert.Equal(port, server.Snapshot.Port);
        using var http = new HttpClient();
        Assert.Contains("录入面板", await http.GetStringAsync($"http://127.0.0.1:{port}/"));
        await server.StopAsync();
        Assert.False(server.Snapshot.Running);
        using var released = new TcpListener(IPAddress.Any, port);
        released.Start();
    }

    [Fact]
    public async Task OccupiedPortDoesNotCloseTheAppOrFalselyReportRunning()
    {
        using var occupied = new TcpListener(IPAddress.Any, 0);
        occupied.Start();
        await using var server = new ReceiverServer(new KeyboardService(count => count));
        Assert.False(await server.ApplyPortAsync(((IPEndPoint)occupied.LocalEndpoint).Port));
        Assert.False(server.Snapshot.Running);
        Assert.False(string.IsNullOrWhiteSpace(server.Snapshot.Error));
    }

    [Fact]
    public async Task PortChangeUpdatesTheConnectAddressOnlyAfterSuccessfulStartAndRollsBackOnFailure()
    {
        await using var server = new ReceiverServer(new KeyboardService(count => count));
        var first = FreePort();
        Assert.True(await server.ApplyPortAsync(first));
        var second = FreePort();
        Assert.True(await server.ApplyPortAsync(second));
        Assert.Equal($"http://192.168.1.20:{second}/?app=remotenumpad&v=1", server.ConnectionUrl("192.168.1.20"));
        using var occupied = new TcpListener(IPAddress.Any, 0);
        occupied.Start();
        Assert.False(await server.ApplyPortAsync(((IPEndPoint)occupied.LocalEndpoint).Port));
        Assert.True(server.Snapshot.Running);
        Assert.Equal(second, server.Snapshot.Port);
        Assert.Contains($":{second}/", server.ConnectionUrl("192.168.1.20"));
        await server.StopAsync();
        Assert.Null(server.ConnectionUrl("192.168.1.20"));
    }

    [Theory]
    [InlineData(0)]
    [InlineData(-1)]
    [InlineData(65536)]
    public async Task InvalidPortsNeverStartAListener(int port)
    {
        await using var server = new ReceiverServer(new KeyboardService(count => count));
        Assert.False(await server.ApplyPortAsync(port));
        Assert.False(server.Snapshot.Running);
    }

    private static int FreePort()
    {
        using var socket = new TcpListener(IPAddress.Any, 0);
        socket.Start();
        return ((IPEndPoint)socket.LocalEndpoint).Port;
    }
}
