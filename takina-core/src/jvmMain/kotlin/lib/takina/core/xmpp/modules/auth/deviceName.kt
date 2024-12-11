package lib.takina.core.xmpp.modules.auth

import java.net.InetAddress

actual fun getDeviceName(): String = InetAddress.getLocalHost().hostName