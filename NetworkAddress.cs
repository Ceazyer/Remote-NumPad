using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace RemoteNumPad;

public static class NetworkAddress
{
    public sealed record LanAddress(string Address, string Name) {
        public override string ToString() => $"{Name} · {Address}";
    }
    public static IReadOnlyList<LanAddress> AvailablePrivateAddresses() => NetworkInterface.GetAllNetworkInterfaces()
        .Where(n => n.OperationalStatus == OperationalStatus.Up && n.NetworkInterfaceType != NetworkInterfaceType.Loopback)
        .OrderBy(n => n.NetworkInterfaceType == NetworkInterfaceType.Wireless80211 ? 0 : 1)
        .SelectMany(n => n.GetIPProperties().UnicastAddresses
            .Where(a => a.Address.AddressFamily == AddressFamily.InterNetwork && IsPrivateIpv4(a.Address.ToString()))
            .Select(a => new LanAddress(a.Address.ToString(), n.Name))).ToArray();

    public static string? FindPrivateIpv4Address()
    {
        foreach (var networkInterface in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (networkInterface.OperationalStatus != OperationalStatus.Up ||
                networkInterface.NetworkInterfaceType == NetworkInterfaceType.Loopback)
            {
                continue;
            }

            foreach (var address in networkInterface.GetIPProperties().UnicastAddresses)
            {
                if (address.Address.AddressFamily == AddressFamily.InterNetwork &&
                    IsPrivateIpv4(address.Address.ToString()))
                {
                    return address.Address.ToString();
                }
            }
        }

        return null;
    }

    public static bool IsPrivateIpv4(string address)
    {
        if (!IPAddress.TryParse(address, out var parsed) ||
            parsed.AddressFamily != AddressFamily.InterNetwork)
        {
            return false;
        }

        var bytes = parsed.GetAddressBytes();
        return bytes[0] == 10 ||
               (bytes[0] == 172 && bytes[1] is >= 16 and <= 31) ||
               (bytes[0] == 192 && bytes[1] == 168);
    }
}
