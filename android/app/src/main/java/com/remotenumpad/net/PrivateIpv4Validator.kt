package com.remotenumpad.net

object PrivateIpv4Validator {
    fun isAllowed(address: String): Boolean {
        val octets = address.split('.')
        if (octets.size != 4) return false
        val parts = octets.map { part ->
            if (part.isEmpty() || part.length > 1 && part[0] == '0' || part.any { it !in '0'..'9' }) return false
            part.toIntOrNull()?.takeIf { it in 0..255 } ?: return false
        }

        val first = parts[0]
        val second = parts[1]
        return when {
            first == 10 -> true
            first == 172 && second in 16..31 -> true
            first == 192 && second == 168 -> true
            else -> false
        }
    }
}
