using System.Net.WebSockets;
using System.Reflection;
using System.Text;
using RemoteNumPad;
using RemoteNumPad.Services;

var builder = WebApplication.CreateBuilder(args);
builder.WebHost.ConfigureKestrel(options => options.ListenAnyIP(8765));
builder.Services.AddSingleton<KeyboardService>();

var app = builder.Build();
app.UseWebSockets();

app.MapGet("/", () => Results.Content(
    ReadEmbeddedResource("RemoteNumPad.wwwroot.index.html"),
    "text/html",
    Encoding.UTF8));
app.MapGet("/style.css", () => Results.Content(
    ReadEmbeddedResource("RemoteNumPad.wwwroot.style.css"),
    "text/css",
    Encoding.UTF8));
app.MapGet("/app.js", () => Results.Content(
    ReadEmbeddedResource("RemoteNumPad.wwwroot.app.js"),
    "text/javascript",
    Encoding.UTF8));

app.Map("/ws", async context =>
{
    var keyboardService = context.RequestServices.GetRequiredService<KeyboardService>();
    if (!context.WebSockets.IsWebSocketRequest)
    {
        context.Response.StatusCode = StatusCodes.Status400BadRequest;
        return;
    }

    using var socket = await context.WebSockets.AcceptWebSocketAsync();
    var buffer = new byte[1024];

    try
    {
        while (socket.State == WebSocketState.Open)
        {
            var result = await socket.ReceiveAsync(buffer, CancellationToken.None);
            if (result.MessageType == WebSocketMessageType.Close)
            {
                if (socket.State == WebSocketState.Open)
                {
                    await socket.CloseAsync(
                        WebSocketCloseStatus.NormalClosure,
                        "Connection closed",
                        CancellationToken.None);
                }

                break;
            }

            if (result.MessageType != WebSocketMessageType.Text)
            {
                continue;
            }

            var command = Encoding.UTF8.GetString(buffer, 0, result.Count);
            Console.WriteLine($"Received Command: {command}");
            keyboardService.SendCommand(command);
        }
    }
    catch (WebSocketException)
    {
        // A phone can disappear without completing the WebSocket close handshake.
    }
    catch (OperationCanceledException)
    {
        // The request was cancelled while the phone was disconnected.
    }
});

Console.WriteLine("Remote NumPad Server Started");
Console.WriteLine();
Console.WriteLine("Open this address on your phone:");
var privateAddress = NetworkAddress.FindPrivateIpv4Address();
Console.WriteLine(privateAddress is null
    ? "No private IPv4 address found. Check the computer's network settings."
    : $"http://{privateAddress}:8765");

await app.RunAsync();

static string ReadEmbeddedResource(string resourceName)
{
    using var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(resourceName)
        ?? throw new InvalidOperationException($"Embedded resource not found: {resourceName}");
    using var reader = new StreamReader(stream, Encoding.UTF8);
    return reader.ReadToEnd();
}
