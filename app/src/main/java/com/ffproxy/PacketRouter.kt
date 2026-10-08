package com.ffproxy

object PacketRouter {

    private const val LOCAL_PROXY_PORT = FFVpnService.LOCAL_PROXY_PORT
    private const val FF_PORT = FFVpnService.FF_SERVER_PORT

    fun route(packet: ByteArray): ByteArray? {
        if (packet.size < 28) return null

        // only IPv4
        val ipVersion = (packet[0].toInt() shr 4) and 0xF
        if (ipVersion != 4) return null

        // only UDP
        val proto = packet[9].toInt() and 0xFF
        if (proto != 17) return null

        val ihl = (packet[0].toInt() and 0xF) * 4
        if (packet.size < ihl + 8) return null

        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or
                      (packet[ihl + 3].toInt() and 0xFF)

        // ONLY intercept FF game port — let everything else pass untouched
        if (dstPort != FF_PORT) return null

        val modified = packet.copyOf()
        // rewrite dst IP to loopback proxy
        modified[16] = 10; modified[17] = 0; modified[18] = 0; modified[19] = 1
        // rewrite dst port to local proxy
        modified[ihl + 2] = (LOCAL_PROXY_PORT shr 8).toByte()
        modified[ihl + 3] = (LOCAL_PROXY_PORT and 0xFF).toByte()
        recomputeChecksum(modified, ihl)
        return modified
    }

    private fun recomputeChecksum(packet: ByteArray, ihl: Int) {
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
