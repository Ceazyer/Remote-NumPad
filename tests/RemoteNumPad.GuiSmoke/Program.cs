using System.Drawing.Imaging;
using System.Reflection;
using QRCoder;
using RemoteNumPad;
using RemoteNumPad.Desktop;
using RemoteNumPad.Services;

internal static class Program {
    [STAThread] static int Main(string[] args) {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        var output = Path.GetFullPath(args.FirstOrDefault() ?? ".tools/gui-smoke");
        Directory.CreateDirectory(output);
        var server = new ReceiverServer(new KeyboardService(count => count));
        try {
            var port = ReceiverServer.SuggestAvailablePort();
            if(!server.ApplyPortAsync(port).GetAwaiter().GetResult()) throw new Exception("Start failed");
            new DesktopSettings(port,"192.168.1.20").Save(Path.Combine(output,"settings.json"));
            using var window = new ReceiverWindow(server, Path.Combine(output,"settings.json"),
                () => new[] { new NetworkAddress.LanAddress("192.168.1.20","测试网卡") });
            window.StartPosition=FormStartPosition.Manual;
            window.ShowInTaskbar=false; window.Location=new Point(-20000,-20000);
            window.Show(); Application.DoEvents(); window.PerformLayout();
            foreach(var name in new[]{"复制地址","打开网页","启动接收","停止接收","重启接收","隐藏到托盘","退出程序"}) {
                var button=window.Controls.Find(name,true).Single();
                Console.WriteLine($"GUI control {name}: {button.Bounds}, visible={button.Visible}");
                if(button.Width<100 || button.Height<36 || !button.Visible) throw new Exception("Unusable GUI control: "+name);
            }
            using var picture = new Bitmap(window.Width,window.Height);
            window.DrawToBitmap(picture,new Rectangle(0,0,window.Width,window.Height));
            picture.Save(Path.Combine(output,"pc-panel.png"),ImageFormat.Png);
            var lookup = window.Controls.Find("connectionQr",true).OfType<PictureBox>().Single();
            if(lookup.Image==null) throw new Exception("QR missing while running");
            var second = ReceiverServer.SuggestAvailablePort();
            if(!Task.Run(() => server.ApplyPortAsync(second)).GetAwaiter().GetResult()) throw new Exception("Change failed");
            typeof(ReceiverWindow).GetMethod("RefreshState",BindingFlags.Instance|BindingFlags.NonPublic)!.Invoke(window,null);
            if(!window.Controls.Find("connectionUrl",true).Single().Text.Contains($":{second}/")) throw new Exception("Stale URL after port change");
            var closing=new FormClosingEventArgs(CloseReason.UserClosing,false);
            typeof(ReceiverWindow).GetMethod("OnFormClosing",BindingFlags.Instance|BindingFlags.NonPublic)!.Invoke(window,new object[]{closing});
            if(!closing.Cancel || !server.Snapshot.Running) throw new Exception("X must hide, not stop");
            Task.Run(server.StopAsync).GetAwaiter().GetResult();
            typeof(ReceiverWindow).GetMethod("RefreshState",BindingFlags.Instance|BindingFlags.NonPublic)!.Invoke(window,null);
            if(lookup.Image!=null) throw new Exception("Stopped QR must be disabled");
            var qr=PngByteQRCodeHelper.GetQRCode("http://192.168.1.20:8888/?app=remotenumpad&v=1",QRCodeGenerator.ECCLevel.M,6);
            File.WriteAllBytes(Path.Combine(output,"connection-qr.png"),qr);
            Console.WriteLine("GUI: QR, port refresh, close-to-tray and stopped state verified."); return 0;
        } finally { Task.Run(async () => await server.DisposeAsync()).GetAwaiter().GetResult(); }
    }
}
