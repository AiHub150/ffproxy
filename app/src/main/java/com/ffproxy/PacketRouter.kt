package com.ffproxy

object PacketRouter {

    private const val LOCAL_PROXY_PORT = FFVpnService.LOCAL_PROXY_PORT

    fun route(packet: ByteArray): ByteArray? {
        if (packet.size < 28) return null
        val ipVersion = (packet[0].toInt() shr 4) and 0xF
        if (ipVersion != 4) return null
        val proto = packet[9].toInt() and 0xFF
        if (proto != 17) return null
        val dstPort = ((packet[22].toInt() and 0xFF) shl 8) or (packet[23].toInt() and 0xFF)
        if (dstPort != FFVpnService.FF_SERVER_PORT) return null

        val modified = packet.copyOf()
        modified[16] = 10; modified[17] = 0; modified[18] = 0; modified[19] = 1
        modified[22] = (LOCAL_PROXY_PORT shr 8).toByte()
        modified[23] = (LOCAL_PROXY_PORT and 0xFF).toByte()
        recomputeChecksum(modified)
        return modified
    }

    private fun recomputeChecksum(packet: ByteArray) {
        val ihl = (packet[0].toInt() and 0xF) * 4
        packet[10] = 0; packet[11] = 0
        var sum = 0
        for (i in 0 until ihl step 2) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val checksum = sum.inv() and 0xFFFF
        packet[10] = (checksum shr 8).toByte()
        packet[11] = (checksum and 0xFF).toByte()
    }
}
