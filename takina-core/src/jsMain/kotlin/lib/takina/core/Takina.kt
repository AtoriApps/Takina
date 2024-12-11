
package lib.takina.core

import kotlinx.browser.window
import lib.takina.core.builder.ConfigurationBuilder
import lib.takina.core.connector.WebSocketConnector

actual class Takina actual constructor(configuration: ConfigurationBuilder) : AbstractTakina(configuration) {

    override fun createConnector(): lib.takina.core.connector.AbstractConnector {
        return WebSocketConnector(this)
    }

    override fun reconnect(immediately: Boolean) {
        if (immediately) {
            state = State.Connecting
            startConnector()
        } else {
            window.setTimeout({
                state = State.Connecting
                startConnector()
            }, 3000)
        }
    }

}
