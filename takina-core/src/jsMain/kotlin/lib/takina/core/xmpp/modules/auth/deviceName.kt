package lib.takina.core.xmpp.modules.auth

import kotlinx.browser.window

actual fun getDeviceName(): String = window.navigator.userAgent