package lib.takina

import lib.takina.core.AbstractTakina
import lib.takina.core.requests.RequestBuilder
import lib.takina.core.xml.Element
import lib.takina.core.xmpp.stanzas.IQ
import kotlin.test.fail

class IQTestBuilder<V>(private val takina: DummyTakina) {

	private var request: ((AbstractTakina) -> RequestBuilder<V, IQ>)? = null
	private var requestValidator: (() -> IQ)? = null
	private var response: (() -> IQ)? = null
	private var responseValidator: ((Result<V>?) -> Unit)? = null

	fun request(request: (AbstractTakina) -> RequestBuilder<V, IQ>) {
		this.request = request
	}

	fun response(response: () -> IQ) {
		this.response = response
	}

	fun validate(validator: (Result<V>?) -> Unit) {
		this.responseValidator = validator
	}

	fun expectedRequest(validator: () -> IQ) {
		this.requestValidator = validator
	}

	internal fun run() {
		var result: Result<V>? = null
		val req = request!!.invoke(takina).response {
			result = it
		}.send()

		requestValidator?.let {
			assertContains(it.invoke(), takina.peekLastSend(), "Invalid request")
		}


		response?.invoke()?.also {
			it.attributes["id"] = req.id
		}?.let {
			takina.processReceivedXmlElement(it)
		}

		responseValidator?.invoke(result)
	}

}

fun <V> DummyTakina.requestResponse(init: IQTestBuilder<V>.() -> Unit) {
	val n = IQTestBuilder<V>(this)
	n.init()
	n.run()
}

fun assertContains(expected: Element, actual: Element?, message: String? = null) {

	fun check(expected: Element, actual: Element): Boolean {
		if (expected.name != actual.name) return false
		if (expected.value != null && expected.value != actual.value) return false
		if (!expected.attributes.filter { it.key != "id" }
				.all { e -> actual.attributes[e.key] == e.value }) return false
		return expected.children.all { e ->
			actual.children.any { a -> check(e, a) }
		}
	}

	fun messagePrefix(message: String?) = if (message == null) "" else "$message. "

	if (expected == actual) return
	if (actual == null) fail(messagePrefix(message) + "Expected all of ${expected.getAsString()}, actual is NULL.")
	if (!check(expected, actual)) {
		fail(messagePrefix(message) + "Expected all of ${expected.getAsString()}, actual ${actual.getAsString()}.")
	}
}