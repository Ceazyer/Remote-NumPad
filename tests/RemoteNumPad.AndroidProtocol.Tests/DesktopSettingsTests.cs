using System;
using System.IO;
using RemoteNumPad.Desktop;
using Xunit;

namespace RemoteNumPad.AndroidProtocol.Tests;
public class DesktopSettingsTests {
    [Fact] public void SavedPortAndAddressRoundTripAndBadFilesUseDefaults() {
        var directory = Path.Combine(Path.GetTempPath(), "numpad-settings-" + Guid.NewGuid());
        Directory.CreateDirectory(directory);
        var file = Path.Combine(directory, "settings.json");
        try {
            new DesktopSettings(8888, "192.168.1.20").Save(file);
            Assert.Equal(8888, DesktopSettings.Load(file).Port);
            Assert.Equal("192.168.1.20", DesktopSettings.Load(file).PreferredAddress);
            File.WriteAllText(file, "{\"Port\":0}");
            Assert.Equal(8765, DesktopSettings.Load(file).Port);
            File.WriteAllText(file, "broken");
            Assert.Equal(8765, DesktopSettings.Load(file).Port);
        } finally { Directory.Delete(directory, true); }
    }
}
