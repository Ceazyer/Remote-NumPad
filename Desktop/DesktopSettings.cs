namespace RemoteNumPad.Desktop;
public sealed record DesktopSettings(int Port = 8765, string? PreferredAddress = null) {
    public static string DefaultPath => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "RemoteNumPad", "settings.json");
    public void Save(string path) {
        if (Port is < 1 or > 65535) throw new ArgumentOutOfRangeException(nameof(Port));
        Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(path))!);
        var temporary = path + ".tmp";
        File.WriteAllText(temporary, System.Text.Json.JsonSerializer.Serialize(this));
        File.Move(temporary, path, true);
    }
    public static DesktopSettings Load(string path) {
        try {
            var settings = System.Text.Json.JsonSerializer.Deserialize<DesktopSettings>(File.ReadAllText(path));
            return settings?.Port is >= 1 and <= 65535 ? settings : new();
        } catch (Exception exception) when (exception is IOException or UnauthorizedAccessException or System.Text.Json.JsonException) { return new(); }
    }
}
