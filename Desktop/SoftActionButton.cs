using System.Drawing.Drawing2D;
using System.Windows.Forms;

namespace RemoteNumPad.Desktop;

public class SoftActionButton : Button
{
    private bool pressed;
    public SoftActionButton() {
        FlatStyle = FlatStyle.Flat; FlatAppearance.BorderSize = 0;
        BackColor = Color.FromArgb(242, 243, 247); ForeColor = Color.FromArgb(153, 96, 15);
        SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer, true);
        Cursor = Cursors.Hand; Height = 48; Margin = new Padding(4);
    }
    private static GraphicsPath Rounded(RectangleF box, float radius) {
        var p = new GraphicsPath(); var d = radius * 2;
        p.AddArc(box.Left, box.Top, d, d, 180, 90); p.AddArc(box.Right-d, box.Top, d, d, 270, 90);
        p.AddArc(box.Right-d, box.Bottom-d, d, d, 0, 90); p.AddArc(box.Left, box.Bottom-d, d, d, 90, 90); p.CloseFigure(); return p;
    }
    protected override void OnPaint(PaintEventArgs e) {
        var g = e.Graphics; g.SmoothingMode = SmoothingMode.AntiAlias; g.Clear(BackColor);
        var face = new RectangleF(7, 7, Width-14, Height-14);
        for (int spread = 5; spread >= 1; spread--) {
            var shadow = face; shadow.Offset(pressed ? -1 : 3, pressed ? -1 : 3); shadow.Inflate(spread/2f, spread/2f);
            using var path = Rounded(shadow, 10); using var brush = new SolidBrush(Color.FromArgb(10, 139, 149, 171)); g.FillPath(brush, path);
            shadow = face; shadow.Offset(-3, -3); shadow.Inflate(spread/2f, spread/2f);
            using var lightPath = Rounded(shadow, 10); using var light = new SolidBrush(Color.FromArgb(35, Color.White)); g.FillPath(light, lightPath);
        }
        using var fillPath = Rounded(face, 10); using var fill = new SolidBrush(BackColor); g.FillPath(fill, fillPath);
        using var rim = new Pen(pressed ? Color.FromArgb(180, 190, 207) : Color.FromArgb(224, 228, 236), pressed ? 2 : 1); g.DrawPath(rim, fillPath);
        TextRenderer.DrawText(g, Text, Font, Rectangle.Round(face), Enabled ? ForeColor : SystemColors.GrayText,
            TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis);
        if (Focused) ControlPaint.DrawFocusRectangle(g, Rectangle.Round(RectangleF.Inflate(face, -4, -4)));
    }
    protected override void OnMouseDown(MouseEventArgs e) { pressed = true; Invalidate(); base.OnMouseDown(e); }
    protected override void OnMouseUp(MouseEventArgs e) { pressed = false; Invalidate(); base.OnMouseUp(e); }
    protected override void OnMouseLeave(EventArgs e) { pressed = false; Invalidate(); base.OnMouseLeave(e); }
}
