package com.remotenumpad.net

import org.junit.Assert.*
import org.junit.Test

class ConnectionQrParserTest {
    @Test fun parsesOnlyTheMarkedPrivateConnectionAddress() {
        assertEquals(ConnectionEndpoint("192.168.1.20", 8888),
            ConnectionQrParser.parse("http://192.168.1.20:8888/?app=remotenumpad&v=1"))
        assertEquals(ConnectionEndpoint("10.0.0.2", 8765),
            ConnectionQrParser.parse("http://10.0.0.2:8765/?app=remotenumpad&v=1"))
    }
    @Test fun rejectsForeignUrlsPublicAddressesCredentialsAndInvalidPorts() {
        listOf("", "javascript:alert(1)", "http://example.com:8765/?app=remotenumpad&v=1",
            "http://8.8.8.8:8765/?app=remotenumpad&v=1", "http://127.0.0.1:8765/?app=remotenumpad&v=1",
            "http://user@192.168.1.20:8765/?app=remotenumpad&v=1", "http://192.168.1.20:0/?app=remotenumpad&v=1",
            "http://192.168.1.20:65536/?app=remotenumpad&v=1", "http://192.168.1.20:8765/",
            "http://192.168.1.20:8765/evil?app=remotenumpad&v=1",
            "http://192.168.1.20:8765/?app=remotenumpad&v=2",
            "http://192.168.1.20:8765/?app=remotenumpad&v=1&command=DELETE")
            .forEach { assertNull(it, ConnectionQrParser.parse(it)) }
    }
}
