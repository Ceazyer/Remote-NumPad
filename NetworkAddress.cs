using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace RemoteNumPad;

public static class NetworkAddress
{
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

