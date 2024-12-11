
package lib.takina.core.connector.socket

import lib.takina.core.connector.ConnectorException
import lib.takina.core.logger.LoggerFactory
import lib.takina.core.xml.parser.StreamParser
import java.io.Reader
import java.io.Writer

class SocketWorker(private val parser: StreamParser) : Thread() {

	private val log = LoggerFactory.logger("lib.takina.core.connector.socket.SocketWorker")

	var onActiveChange: ((Boolean) -> Unit)? = null

	var isActive = false
		private set(value) {
			val tmp = field
			field = value
			if (tmp != field) onActiveChange?.invoke(field)
		}

	lateinit var  reader: Reader
		private set
	lateinit var writer: Writer
		private set

	var onError: ((Exception) -> Unit)? = null

	init {
		name = "Socket-Worker-Thread"
		isDaemon = true
	}

	internal fun setReaderAndWriter(reader: Reader, writer: Writer){
		this.reader = reader
		this.writer=writer
	}

	override fun run() {
		log.fine { "Socket Worker Started" }
		val buffer = CharArray(10240)
		try {
			isActive = true
			while (isActive && !interrupted() && isAlive) {
				val len = reader.read(buffer)
				if (len == -1) {
					log.finest { "Nothing more to read" }
					break
				} else if (len == 0) {
				}

				if (isActive && !interrupted() && isAlive) parser.parse(buffer, 0, len - 1)
			}
			if (isActive) {
				log.warning { "Unexpected stop!" }
				onError?.invoke(ConnectorException("Unexpected stop!"))
			}
		} catch (e: Exception) {
			log.fine { "Exception in worker: isActive=$isActive interrupted=${!interrupted()}" }
			if (isActive) {
				log.fine(e) { "Exception in Socket Worker" }
				onError?.invoke(e)
			}
		} finally {
			isActive = false
			log.fine { "Socket Worker Stopped" }
		}
	}

	override fun interrupt() {
		isActive = false
		super.interrupt()
	}

}