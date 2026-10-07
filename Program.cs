using System.Windows.Forms;
using RemoteNumPad.Desktop;
using RemoteNumPad.Services;

namespace RemoteNumPad;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        ApplicationConfiguration.Initialize();
        using var instance = new Mutex(true, @"Local\RemoteNumPad.Receiver", out var primary);
        if (!primary)
        {
            for (var retry = 0; retry < 10; retry++)
            {
                try { using var signal = EventWaitHandle.OpenExisting(@"Local\RemoteNumPad.ShowPanel"); signal.Set(); return; }
                catch (WaitHandleCannotBeOpenedException) { Thread.Sleep(100); }
            }
            MessageBox.Show("接收端正在启动，请稍后从托盘打开。", "Remote NumPad");
            return;
        }
        using var showSignal = new EventWaitHandle(false, EventResetMode.AutoReset, @"Local\RemoteNumPad.ShowPanel");
        var server = new ReceiverServer();
        try
        {
            using var window = new ReceiverWindow(server);
            var registration = ThreadPool.RegisterWaitForSingleObject(showSignal, (_, _) => {
                if (window.IsHandleCreated && !window.IsDisposed)
                    window.BeginInvoke(new Action(window.ShowPanel));
            }, null, Timeout.Infinite, false);
            try { Application.Run(window); }
            finally { registration.Unregister(null); }
        }
        finally { Task.Run(async () => await server.DisposeAsync()).GetAwaiter().GetResult(); instance.ReleaseMutex(); }
    }
}
