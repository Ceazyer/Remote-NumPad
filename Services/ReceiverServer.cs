using System.Collections.Concurrent;
using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Reflection;
using System.Text;

namespace RemoteNumPad.Services;

public sealed record ReceiverSnapshot(bool Running, int Port, int Connections, string? Error);

public sealed class ReceiverServer : IAsyncDisposable
{
    private readonly SemaphoreSlim lifecycle = new(1, 1);
    private readonly object stateGate = new();
    private readonly KeyboardService keyboard;
    private readonly AndroidCommandProtocol protocol;
    private readonly ConcurrentQueue<string> logs = new();
    private WebApplication? app;
    private bool running;
    private int port = 8765;
    private int connections;
    private string? error;
    private bool disposed;

    public ReceiverServer() : this(new KeyboardService()) { }
    internal ReceiverServer(KeyboardService keyboard)
    {
        this.keyboard = keyboard;
        protocol = new AndroidCommandProtocol(keyboard);
    }

    public ReceiverSnapshot Snapshot { get { lock (stateGate) return new(running, port, Volatile.Read(ref connections), error); } }
    public string[] Logs => logs.ToArray();
    private void Log(string message)
    {
        logs.Enqueue($"{DateTime.Now:HH:mm:ss}  {message}");
        while (logs.Count > 100) logs.TryDequeue(out _);
    }
    private void State(bool active, int actualPort, string? message = null)
    {
        lock (stateGate) { running = active; port = actualPort; error = message; }
    }

    public string? ConnectionUrl(string address)
    {
        var state = Snapshot;
        return state.Running && NetworkAddress.IsPrivateIpv4(address)
            ? $"http://{address}:{state.Port}/?app=remotenumpad&v=1" : null;
    }

    public static int SuggestAvailablePort()
    {
        using var listener = new TcpListener(IPAddress.Any, 0);
        listener.Start();
        return ((IPEndPoint)listener.LocalEndpoint).Port;
    }

    public async Task<bool> ApplyPortAsync(int nextPort)
    {
        await lifecycle.WaitAsync().ConfigureAwait(false);
        try
        {
            if (disposed) return false;
            var previous = Snapshot;
            if (nextPort is < 1 or > 65535)
            {
                State(previous.Running, previous.Port, "端口必须为 1～65535。");
                return false;
            }
            if (previous.Running && previous.Port == nextPort) return true;
            await StopLockedAsync().ConfigureAwait(false);
            if (await StartLockedAsync(nextPort).ConfigureAwait(false)) return true;
            var failure = Snapshot.Error;
            if (previous.Running && await StartLockedAsync(previous.Port).ConfigureAwait(false))
            {
                State(true, previous.Port, $"新端口未能启用，已恢复原端口。{failure}");
                Log("端口修改失败，原接收端已恢复。");
            }
            return false;
        }
        finally { lifecycle.Release(); }
    }

    private async Task<bool> StartLockedAsync(int nextPort)
    {
        WebApplication? candidate = null;
        try
        {
            candidate = BuildApplication(nextPort);
            await candidate.StartAsync().ConfigureAwait(false);
            app = candidate;
            State(true, nextPort);
            Log($"接收已启动，端口 {nextPort}。");
            return true;
        }
        catch (Exception exception) when (exception is IOException or SocketException or InvalidOperationException)
        {
            if (candidate != null) await candidate.DisposeAsync().ConfigureAwait(false);
            State(false, Snapshot.Port, $"端口 {nextPort} 无法监听：可能被占用或受系统限制，请更换端口。");
            Log($"启动失败，端口 {nextPort}。未结束其他程序。");
            return false;
        }
    }

    private WebApplication BuildApplication(int selectedPort)
    {
        var builder = WebApplication.CreateBuilder(new WebApplicationOptions {
            Args = Array.Empty<string>(), ContentRootPath = AppContext.BaseDirectory });
        builder.Logging.ClearProviders();
        builder.WebHost.ConfigureKestrel(options => options.Listen(IPAddress.Any, selectedPort));
        builder.Services.AddSingleton(keyboard);
        builder.Services.AddSingleton(protocol);
        var server = builder.Build();
        server.UseWebSockets();
        foreach (var (url, resource, contentType) in new[] {
            ("/", "index.html", "text/html"), ("/style.css", "style.css", "text/css"), ("/app.js", "app.js", "text/javascript") })
        {
            var content = ReadResource("RemoteNumPad.wwwroot." + resource);
            server.MapGet(url, () => Results.Content(content, contentType, Encoding.UTF8));
        }
        server.Map("/ws", async context =>
        {
            if (!context.WebSockets.IsWebSocketRequest) { context.Response.StatusCode = 400; return; }
            using var socket = await context.WebSockets.AcceptWebSocketAsync();
            using var cancellation = CancellationTokenSource.CreateLinkedTokenSource(context.RequestAborted, server.Lifetime.ApplicationStopping);
            using var abort = cancellation.Token.Register(socket.Abort);
            Interlocked.Increment(ref connections);
            try
            {
                while (socket.State == WebSocketState.Open)
                {
                    var message = await WebSocketMessageReader.ReadAsync(socket, 4096, cancellation.Token);
                    if (message.MessageType == WebSocketMessageType.Close)
                    {
                        await socket.CloseOutputAsync(WebSocketCloseStatus.NormalClosure, "Connection closed", cancellation.Token);
                        break;
                    }
                    if (message.MessageType != WebSocketMessageType.Text || message.Text is null)
                    {
                        await socket.CloseOutputAsync(WebSocketCloseStatus.InvalidMessageType, "Text messages required", cancellation.Token);
                        break;
                    }
                    var response = protocol.Handle(message.Text);
                    if (response != null)
                    {
                        await socket.SendAsync(Encoding.UTF8.GetBytes(response), WebSocketMessageType.Text, true, cancellation.Token);
                        protocol.RecordResponseSent(response);
                    }
                }
            }
            catch (Exception exception) when (exception is WebSocketException or OperationCanceledException or InvalidDataException or DecoderFallbackException)
            {
                // No command payloads, image data, IPs or clipboard content are logged.
            }
            finally { Interlocked.Decrement(ref connections); }
        });
        return server;
    }

    private static string ReadResource(string name)
    {
        using var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(name)
            ?? throw new InvalidOperationException("Missing embedded frontend.");
        using var reader = new StreamReader(stream, Encoding.UTF8);
        return reader.ReadToEnd();
    }

    private async Task StopLockedAsync()
    {
        var previous = app;
        app = null;
        State(false, Snapshot.Port);
        if (previous == null) return;
        using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        try { await previous.StopAsync(timeout.Token).ConfigureAwait(false); }
        catch (OperationCanceledException) { }
        finally { await previous.DisposeAsync().ConfigureAwait(false); }
        Log("接收已停止，端口已释放。");
    }

    public async Task StopAsync()
    {
        await lifecycle.WaitAsync().ConfigureAwait(false);
        try { await StopLockedAsync().ConfigureAwait(false); }
        finally { lifecycle.Release(); }
    }
    public async ValueTask DisposeAsync()
    {
        await lifecycle.WaitAsync().ConfigureAwait(false);
        try { if (!disposed) { disposed = true; await StopLockedAsync().ConfigureAwait(false); } }
        finally { lifecycle.Release(); }
    }
}
