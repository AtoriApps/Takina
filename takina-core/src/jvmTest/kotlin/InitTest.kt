import org.atoriapps.takina.core.components.恩情Component
import org.atoriapps.takina.core.createTakina
import org.atoriapps.takina.core.events.AllConnectedEvent
import org.atoriapps.takina.core.utils.LogUtils
import org.atoriapps.takina.core.xmpp.toJid
import kotlin.test.Test

class InitTest {
    companion object {
        private const val TAG = "InitTest"
    }

    @Test
    fun main() {
        val takina = createTakina {
            addAccount {
                jid = "金日成@DPRK".toJid()
                password { "1919810" }
            }

            addAccount {
                jid = "金正恩@DPRK".toJid()
                password { "114514" }
            }

            onConfigureComponent<恩情Component> {
                LogUtils.warn(TAG, "就你这狗皮将军，给我叫两声")
                喊话()
            }
        }

        takina.events.on<AllConnectedEvent> {
            LogUtils.info(TAG, "所有账号已连接")
        }

        takina.connectAll()

        LogUtils.info(TAG, "已连接，开始配置")

        takina.disconnectAll()
    }
}