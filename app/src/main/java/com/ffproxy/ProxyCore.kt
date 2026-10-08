// lang: Kotlin, file: ProxyCore.kt — add TCP relay thread inside start()

import java.net.ServerSocket
import java.net.Socket

// TCP relay — handles FF config/version/auth traffic that isn't UDP game data
Thread {
    val tcpServer = ServerSocket(0)   // OS picks port — not needed externally
    svc.protect(tcpServer)            // bypass the tunnel on outbound leg
    while (running.get()) {
        try {
            val client = tcpServer.accept()
            Thread {
                try {
                    // read original dst from SO_ORIGINAL_DST or tun packet header
                    // for now forward to FF's known config host
                    val remote = Socket("202.81.96.54", 443)
                    svc.protect(remote)
                    val c2r = Thread { client.inputStream.copyTo(remote.outputStream) }
                    val r2c = Thread { remote.inputStream.copyTo(client.outputStream) }
                    c2r.start(); r2c.start()
                    c2r.join(); r2c.join()
                    remote.close(); client.close()
                } catch (_: Exception) {}
            }.start()
        } catch (_: Exception) {}
    }
}.start()
