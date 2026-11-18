package com.abdulaziz.netpass.domain

object GatewayHostValidator {

    const val DEFAULT_GATEWAY = "10.0.0.1"

    private val IPV4_REGEX = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
    private val HOSTNAME_LABEL_REGEX = Regex("""^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$""")

    fun sanitizeAndValidate(rawHost: String): String? {
        val trimmed = rawHost.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.any { it.isWhitespace() || it.isISOControl() }) return null
        if (trimmed.contains('/') || trimmed.contains('?') || trimmed.contains('#') ||
            trimmed.contains('@') || trimmed.contains('\\')
        ) {
            return null
        }

        val colonCount = trimmed.count { it == ':' }
        if (colonCount > 1) return null

        val hostPart: String
        val portPart: String?
        if (colonCount == 1) {
            val idx = trimmed.indexOf(':')
            hostPart = trimmed.substring(0, idx)
            portPart = trimmed.substring(idx + 1)
            val portNum = portPart.toIntOrNull() ?: return null
            if (portNum !in 1..65535) return null
        } else {
            hostPart = trimmed
            portPart = null
        }

        val normalizedHost = hostPart.lowercase()
        if (!isValidIpv4(normalizedHost) && !isValidHostname(normalizedHost)) {
            return null
        }

        return if (portPart != null) "$normalizedHost:$portPart" else normalizedHost
    }

    fun resolveTargetHost(
        customGateway: String,
        detectedGateway: String,
        defaultGateway: String = DEFAULT_GATEWAY
    ): String? {
        if (customGateway.isNotBlank()) {
            return sanitizeAndValidate(customGateway)
        }
        val validDetected = sanitizeAndValidate(detectedGateway)
        if (validDetected != null) {
            return validDetected
        }
        return sanitizeAndValidate(defaultGateway)
    }

    private fun isValidIpv4(host: String): Boolean {
        val match = IPV4_REGEX.matchEntire(host) ?: return false
        return (1..4).all { idx ->
            val octet = match.groupValues[idx]
            val value = octet.toIntOrNull() ?: return false
            value in 0..255 && (octet.length == 1 || !octet.startsWith('0'))
        }
    }

    private fun isValidHostname(host: String): Boolean {
        if (host.length !in 1..253) return false
        if (host.startsWith('.') || host.endsWith('.')) return false
        val labels = host.split('.')
        // Purely numeric TLD is not a valid DNS hostname; malformed IPv4 like 999.1.1.1 must fail here.
        if (labels.last().all { it.isDigit() }) return false
        return labels.all { HOSTNAME_LABEL_REGEX.matches(it) }
    }
}
