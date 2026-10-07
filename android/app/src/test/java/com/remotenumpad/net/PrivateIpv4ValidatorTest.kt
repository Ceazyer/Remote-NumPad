package com.remotenumpad.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateIpv4ValidatorTest {
    @Test
    fun acceptsOnlyPrivateIpv4AddressesForTheLanEndpoint() {
        listOf("10.0.0.7", "172.16.1.2", "172.31.255.254", "192.168.1.10").forEach {
            assertTrue("Expected private address $it", PrivateIpv4Validator.isAllowed(it))
        }
        listOf("8.8.8.8", "172.32.0.1", "192.169.1.2", "localhost", "0.0.0.0", "192.168.1.999")
            .forEach { assertFalse("Expected address $it to be rejected", PrivateIpv4Validator.isAllowed(it)) }
    }
}
