using RemoteNumPad;
using Xunit;

namespace RemoteNumPad.Tests;

public sealed class NetworkAddressTests
{
    [Theory]
    [InlineData("192.168.1.100", true)]
    [InlineData("10.0.0.8", true)]
    [InlineData("172.16.0.3", true)]
    [InlineData("172.31.255.254", true)]
    [InlineData("127.0.0.1", false)]
    [InlineData("8.8.8.8", false)]
    public void RecognizesPrivateIpv4Addresses(string address, bool expected)
    {
        Assert.Equal(expected, NetworkAddress.IsPrivateIpv4(address));
    }
}

