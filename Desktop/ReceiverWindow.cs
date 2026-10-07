using System.Diagnostics;
using System.Reflection;
using System.Windows.Forms;
using QRCoder;
using RemoteNumPad.Services;

namespace RemoteNumPad.Desktop;

public sealed class ReceiverWindow : Form
{
    private readonly ReceiverServer server;
    private readonly string settingsPath;
    private readonly Func<IReadOnlyList<NetworkAddress.LanAddress>> addressProvider;
    private DesktopSettings settings;
    private readonly ComboBox addresses = new() { Name="addresses", DropDownStyle=ComboBoxStyle.DropDownList, Dock=DockStyle.Fill };
    private readonly NumericUpDown port = new() { Name="port", Minimum=1, Maximum=65535, Dock=DockStyle.Fill };
    private readonly Label status = new() { Name="status", AutoSize=true };
    private readonly Label link = new() { Name="connectionUrl", AutoSize=false, Dock=DockStyle.Fill, AutoEllipsis=true, Height=28 };
    private readonly Label message = new() { Name="message", AutoSize=false, Dock=DockStyle.Fill, ForeColor=Color.FromArgb(146,89,24) };
    private readonly PictureBox qr = new() { Name="connectionQr", Dock=DockStyle.Fill, SizeMode=PictureBoxSizeMode.Zoom, BackColor=Color.White };
    private readonly Label qrStatus = new() { Name="qrStatus", Dock=DockStyle.Fill, TextAlign=ContentAlignment.MiddleCenter, Text="接收未启动" };
    private readonly TextBox logView = new() { Name="logs", ReadOnly=true, Multiline=true, ScrollBars=ScrollBars.Vertical, Dock=DockStyle.Fill, BorderStyle=BorderStyle.FixedSingle };
    private readonly System.Windows.Forms.Timer timer = new() { Interval=500 };
    private readonly NotifyIcon tray;
    private readonly ToolStripMenuItem toggleItem = new("启动接收");
    private readonly List<Button> actions = new();
    private string? qrValue;
    private bool exiting;
    private bool busy;
    private bool hideNotified;
    private int ticks;
    private readonly TableLayoutPanel layout;

    public ReceiverWindow(ReceiverServer server, string? settingsPath = null,
        Func<IReadOnlyList<NetworkAddress.LanAddress>>? addressProvider = null)
    {
        this.server = server; this.settingsPath = settingsPath ?? DesktopSettings.DefaultPath;
        this.addressProvider = addressProvider ?? NetworkAddress.AvailablePrivateAddresses;
        settings = DesktopSettings.Load(this.settingsPath);
        Text = "Remote NumPad · 接收端 1.3.0"; Name = "receiverWindow";
        BackColor = Color.FromArgb(242,243,247); ForeColor = Color.FromArgb(36,42,54);
        Font = new Font("Microsoft YaHei UI", 9); AutoScaleMode=AutoScaleMode.Dpi;
        ClientSize = new Size(760,590); MinimumSize = new Size(710,580); StartPosition=FormStartPosition.CenterScreen;
        using var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("RemoteNumPad.AppIcon")!;
        using var loaded = new Icon(stream); Icon = (Icon)loaded.Clone();
        tray = new NotifyIcon { Icon=Icon, Text="Remote NumPad · 接收未启动", Visible=true };
        var menu = new ContextMenuStrip();
        menu.Items.Add("打开主面板", null, (_,_) => ShowPanel());
        toggleItem.Click += async (_,_) => await PerformAsync(async () => { if(server.Snapshot.Running) await server.StopAsync(); else await ApplyAsync(); });
        menu.Items.Add(toggleItem);
        menu.Items.Add("重启接收", null, async (_,_) => await PerformAsync(RestartAsync));
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add("复制连接地址", null, (_,_) => CopyAddress());
        menu.Items.Add("打开网页", null, (_,_) => OpenBrowser());
        menu.Items.Add("查看连接二维码", null, (_,_) => ShowPanel());
        menu.Items.Add("修改端口", null, (_,_) => { ShowPanel(); port.Focus(); });
        menu.Items.Add("查看日志", null, (_,_) => { ShowPanel(); logView.Focus(); });
        menu.Items.Add("关于", null, (_,_) => About());
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add("退出程序", null, async (_,_) => await ExitAsync());
        tray.ContextMenuStrip=menu; tray.DoubleClick += (_,_) => ShowPanel();

        layout = new TableLayoutPanel { Dock=DockStyle.Fill, Padding=new Padding(24), ColumnCount=1, RowCount=7 };
        foreach(var height in new[]{46f,30f,56f,265f,64f,40f}) layout.RowStyles.Add(new RowStyle(SizeType.Absolute,height));
        layout.RowStyles.Add(new RowStyle(SizeType.Percent,100));
        Controls.Add(layout);
        var heading = new Label { Text="Remote NumPad", Font=new Font(Font.FontFamily,20,FontStyle.Bold), Dock=DockStyle.Fill };
        layout.Controls.Add(heading,0,0);
        layout.Controls.Add(status,0,1);
        var network = new TableLayoutPanel { Dock=DockStyle.Fill, ColumnCount=5, RowCount=1 };
        network.RowStyles.Add(new RowStyle(SizeType.Percent,100));
        network.ColumnStyles.Add(new(SizeType.Percent,100)); network.ColumnStyles.Add(new(SizeType.Absolute,70));
        network.ColumnStyles.Add(new(SizeType.Absolute,48)); network.ColumnStyles.Add(new(SizeType.Absolute,85)); network.ColumnStyles.Add(new(SizeType.Absolute,125));
        network.Controls.Add(addresses,0,0); network.Controls.Add(Action("刷新",RefreshAddresses),1,0);
        network.Controls.Add(new Label { Text="端口", Dock=DockStyle.Fill, TextAlign=ContentAlignment.MiddleCenter },2,0);
        port.Value=settings.Port; network.Controls.Add(port,3,0);
        network.Controls.Add(Action("应用并重启",async () => await PerformAsync(async () => {
            if(server.Snapshot.Running && server.Snapshot.Port != (int)port.Value &&
                MessageBox.Show(this,"修改端口会断开手机连接。应用后请重新扫码，确认表格目标位置再恢复待发送输入。","修改端口",MessageBoxButtons.OKCancel,MessageBoxIcon.Information)!=DialogResult.OK) return;
            await ApplyAsync();
        })),4,0);
        layout.Controls.Add(network,0,2);
        var connection = new TableLayoutPanel { Dock=DockStyle.Fill, ColumnCount=2 };
        connection.ColumnStyles.Add(new(SizeType.Percent,100)); connection.ColumnStyles.Add(new(SizeType.Absolute,230));
        var info = new TableLayoutPanel { Dock=DockStyle.Fill, ColumnCount=1, RowCount=6, Padding=new Padding(0,18,16,8) };
        info.RowStyles.Add(new(SizeType.Absolute,26)); info.RowStyles.Add(new(SizeType.Absolute,46));
        info.RowStyles.Add(new(SizeType.Absolute,62)); info.RowStyles.Add(new(SizeType.Absolute,48)); info.RowStyles.Add(new(SizeType.Percent,100));
        info.Controls.Add(new Label { Text="手机连接地址 · 安卓与浏览器共用", AutoSize=true },0,0);
        info.Controls.Add(link,0,1);
        var copyRow=new FlowLayoutPanel { Dock=DockStyle.Fill }; copyRow.Controls.Add(Action("复制地址",CopyAddress)); copyRow.Controls.Add(Action("打开网页",OpenBrowser)); info.Controls.Add(copyRow,0,2);
        foreach(Control button in copyRow.Controls) { button.Dock=DockStyle.None; button.Height=48; }
        info.Controls.Add(Action("查找可用端口",() => { port.Value=ReceiverServer.SuggestAvailablePort(); message.Text="已填写建议端口，请点击应用；最终以实际监听结果为准。"; }),0,3);
        info.Controls.Add(new Label { Text="请切回 Excel / WPS 再录入。\n只在可信局域网使用，扫码不是身份认证。", Dock=DockStyle.Fill, ForeColor=Color.FromArgb(98,105,119) },0,4);
        connection.Controls.Add(info,0,0);
        var qrPanel = new Panel { Dock=DockStyle.Fill, Padding=new Padding(8) }; qrPanel.Controls.Add(qr); qr.Controls.Add(qrStatus);
        connection.Controls.Add(qrPanel,1,0); layout.Controls.Add(connection,0,3);
        var controls = new FlowLayoutPanel { Dock=DockStyle.Fill };
        controls.Controls.Add(Action("启动接收",async () => await PerformAsync(ApplyAsync)));
        controls.Controls.Add(Action("停止接收",async () => await PerformAsync(server.StopAsync)));
        controls.Controls.Add(Action("重启接收",async () => await PerformAsync(RestartAsync)));
        controls.Controls.Add(Action("隐藏到托盘",HideToTray));
        controls.Controls.Add(Action("退出程序",async () => await ExitAsync()));
        foreach(Control button in controls.Controls) { button.Dock=DockStyle.None; button.Height=48; }
        layout.Controls.Add(controls,0,4); layout.Controls.Add(message,0,5);
        layout.Controls.Add(logView,0,6);
        addresses.SelectedIndexChanged += (_,_) => { RefreshState(); SaveIfRunning(); };
        timer.Tick += (_,_) => { RefreshState(); if(++ticks%10==0) RefreshAddresses(); };
        Shown += async (_,_) => { timer.Start(); await PerformAsync(ApplyAsync); };
        Resize += (_,_) => { if(WindowState==FormWindowState.Minimized) HideToTray(); };
        RefreshAddresses(); RefreshState();
    }
    private Button Action(string text, Action handler) { return Action(text, () => { handler(); return Task.CompletedTask; }); }
    private Button Action(string text, Func<Task> handler) {
        var button = new SoftActionButton { Text=text, Name=text, Width=125, Dock=DockStyle.Fill };
        button.Click += async (_,_) => { try { await handler(); } catch(Exception) { message.Text="操作失败，请查看连接状态或重试。"; } };
        actions.Add(button); return button;
    }
    private string? SelectedAddress => (addresses.SelectedItem as NetworkAddress.LanAddress)?.Address;
    private void RefreshAddresses() {
        var available = addressProvider();
        var previous = SelectedAddress ?? settings.PreferredAddress;
        if(available.Select(a=>a.ToString()).SequenceEqual(addresses.Items.Cast<object>().Select(a=>a.ToString()))) return;
        addresses.Items.Clear(); foreach(var address in available) addresses.Items.Add(address);
        var selected=available.Select((a,i)=>(a,i)).FirstOrDefault(a=>a.a.Address==previous);
        if(available.Count>0) addresses.SelectedIndex=selected.a==null ? 0 : selected.i;
        RefreshState();
    }
    public void ShowPanel() { Show(); WindowState=FormWindowState.Normal; Activate(); }
    private void HideToTray() {
        Hide();
        if(!hideNotified) { hideNotified=true; tray.ShowBalloonTip(2500,"Remote NumPad","窗口已隐藏，接收仍按当前状态运行。右键托盘选择退出才能结束程序。",ToolTipIcon.Info); }
    }
    private async Task PerformAsync(Func<Task> operation) {
        if(busy || exiting) return; busy=true; foreach(var action in actions) action.Enabled=false;
        try { await operation(); } finally { busy=false; foreach(var action in actions) action.Enabled=true; RefreshState(); }
    }
    private async Task ApplyAsync() { var success=await server.ApplyPortAsync((int)port.Value); port.Value=server.Snapshot.Port; if(success) SaveIfRunning(); }
    private async Task RestartAsync() { var current=server.Snapshot.Port; await server.StopAsync(); if(await server.ApplyPortAsync(current)) SaveIfRunning(); }
    private void SaveIfRunning() {
        if(!server.Snapshot.Running) return;
        settings=new DesktopSettings(server.Snapshot.Port,SelectedAddress);
        try { settings.Save(settingsPath); } catch(Exception) { message.Text="接收已运行，但本机设置未能保存。"; }
    }
    private void RefreshState() {
        var state=server.Snapshot;
        status.Text=$"{(state.Running ? "接收运行中" : "接收已停止")}  ·  端口 {state.Port}  ·  在线连接 {state.Connections}";
        status.ForeColor=state.Running ? Color.FromArgb(40,115,94) : Color.FromArgb(146,89,24);
        toggleItem.Text=state.Running ? "停止接收" : "启动接收";
        tray.Text=$"Remote NumPad · {(state.Running ? "运行" : "停止")} · {state.Port} · 在线 {state.Connections}";
        if(state.Error!=null) message.Text=state.Error;
        else if(SelectedAddress==null) message.Text="未找到私有 IPv4 地址，请检查局域网连接或刷新网卡。";
        else if(!busy) message.Text="更换端口后重新扫码；未确认输入请按手机恢复提示处理。";
        var next = SelectedAddress==null ? null : server.ConnectionUrl(SelectedAddress);
        link.Text=next ?? "接收未启动，或尚未找到局域网地址";
        qrStatus.Visible=next==null;
        if(next!=qrValue) {
            qrValue=next; var previous=qr.Image; qr.Image=null;
            if(next!=null) {
                var bytes=PngByteQRCodeHelper.GetQRCode(next,QRCodeGenerator.ECCLevel.M,6);
                using var data=new MemoryStream(bytes); using var image=Image.FromStream(data); qr.Image=new Bitmap(image);
            }
            previous?.Dispose();
        }
        var text=string.Join(Environment.NewLine,server.Logs); if(logView.Text!=text) logView.Text=text;
    }
    private void CopyAddress() { if(qrValue!=null) Clipboard.SetText(qrValue); else message.Text="请先启动接收并选择局域网地址。"; }
    private void OpenBrowser() { if(qrValue!=null) Process.Start(new ProcessStartInfo(qrValue) { UseShellExecute=true }); }
    private void About() => MessageBox.Show(this,"Remote NumPad 1.3.0\n原生 Windows 接收面板 · MIT 开源\n扫码用于填写地址，不是身份认证。", "关于", MessageBoxButtons.OK,MessageBoxIcon.Information);
    private async Task ExitAsync() { if(exiting) return; exiting=true; timer.Stop(); tray.Visible=false; await server.DisposeAsync(); Close(); }
    protected override void OnFormClosing(FormClosingEventArgs e) {
        if(!exiting && e.CloseReason==CloseReason.UserClosing) { e.Cancel=true; HideToTray(); return; }
        if(!exiting) { exiting=true; Task.Run(async () => await server.DisposeAsync()).GetAwaiter().GetResult(); }
        base.OnFormClosing(e);
    }
    protected override void Dispose(bool disposing) {
        if(disposing) { timer.Dispose(); tray.Visible=false; tray.Dispose(); qr.Image?.Dispose(); }
        base.Dispose(disposing);
    }
}
