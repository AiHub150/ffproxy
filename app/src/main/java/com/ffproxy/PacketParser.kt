package com.ffproxy

object PacketParser {

    private var sessionKey = ByteArray(4) { 0 }
    private var handshakeDone = false
    private var pktSeq = 0

    fun parseInbound(data: ByteArray) {
        if (!handshakeDone) { captureHandshake(data); return }
        val plain = xorDecrypt(data, sessionKey, pktSeq++)
        parseEntityUpdate(plain)
    }

    fun parseOutbound(data: ByteArray) {
        if (!handshakeDone) return
        val plain = xorDecrypt(data, sessionKey, pktSeq)
        parseEntityUpdate(plain)
    }

    private fun captureHandshake(data: ByteArray) {
        if (data.size < 16) return
        sessionKey[0] = data[8]
        sessionKey[1] = data[9]
        sessionKey[2] = data[10]
        sessionKey[3] = data[11]
        handshakeDone = true
    }

    private fun xorDecrypt(data: ByteArray, key: ByteArray, seq: Int): ByteArray {
        val out = data.copyOf()
        for (i in out.indices) {
            out[i] = (out[i].toInt() xor key[i % key.size].toInt() xor (seq and 0xFF)).toByte()
        }
        return out
    }

    private fun parseEntityUpdate(plain: ByteArray) {
        if (plain.size < 20) return
        val type = plain[0].toInt() and 0xFF
        if (type != 0x21) return
        val count = plain[4].toInt() and 0xFF
        var off = 8
        for (i in 0 until count) {
            if (off + 13 > plain.size) break
            val x = bytesToFloat(plain, off)
            val y = bytesToFloat(plain, off + 4)
            val z = bytesToFloat(plain, off + 8)
            val hp = (plain[off + 12].toInt() and 0xFF) * 2f
            EntityStore.update(i, x, y, z, hp)
            off += 16
        }
        EntityStore.count = count
    }

    private fun bytesToFloat(b: ByteArray, off: Int): Float {
        val bits = ((b[off].toInt() and 0xFF) shl 24) or
                   ((b[off+1].toInt() and 0xFF) shl 16) or
                   ((b[off+2].toInt() and 0xFF) shl 8) or
                   (b[off+3].toInt() and 0xFF)
        return java.lang.Float.intBitsToFloat(bits)
    }
}
