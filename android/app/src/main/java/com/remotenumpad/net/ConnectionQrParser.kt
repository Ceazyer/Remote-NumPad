package com.remotenumpad.net

data class ConnectionEndpoint(val host: String, val port: Int)
object ConnectionQrParser {
    fun parse(raw: String): ConnectionEndpoint? = try {
        if (raw.length > 512 || raw != raw.trim()) null else {
            val uri = java.net.URI(raw)
            val host = uri.host ?: ""
            if (uri.scheme != "http" || uri.rawUserInfo != null || uri.rawFragment != null ||
                uri.rawPath != "/" || uri.rawQuery != "app=remotenumpad&v=1" ||
                !PrivateIpv4Validator.isAllowed(host) || uri.port !in 1..65535) null
            else ConnectionEndpoint(host, uri.port)
        }
    } catch (_: Exception) { null }
}
