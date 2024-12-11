
package lib.takina.core.xmpp

import kotlinx.datetime.Clock
import kotlin.math.absoluteValue

class IdGenerator {

	private val ALPHABET: String = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

	private var buffer = IntArray(16, init = { 0 })

	private val seed = createSeed()

	private fun createSeed(): IntArray {
		var t = Clock.System.now()
			.toEpochMilliseconds()
		var result = IntArray(0)
		while (t > 0) {
			val c = t % ALPHABET.length
			t -= c
			t /= ALPHABET.length
			result += arrayListOf(c.toInt())
		}
		return result
	}

	private var counter: Long = 0

	private fun increment() {
		for (ix in buffer.indices) {
			buffer[ix] = buffer[ix] + 1
			if (buffer[ix] < ALPHABET.length) {
				break
			}
			buffer[ix] = 0
			if (ix == buffer.size - 1) {
				buffer = IntArray(buffer.size + 1, init = { 0 })
				break
			}
		}
	}

	/**
	 * Lehmer generator parameters uses the prime modulus 2^32−5:
	 */
	private fun f(state: Long): Long {
		return (state * 279470273) % 4294967291
	}

	private fun hash(): IntArray {
		counter += 1

//		val result =  buffer.copyOf(buffer.size + 1)

		val result = seed + buffer + IntArray(1, init = { 0 })

		val iv = f(counter)
		result[result.size - 1] = (iv % ALPHABET.length).absoluteValue.toInt()

		var prng: Long = iv

		for (ix in result.indices) {
			prng = f(prng)
			val c: Int = (prng % ALPHABET.length).toInt()
			var a = result[ix]
			a = (a + c).absoluteValue % ALPHABET.length
			result[ix] = a
			prng = prng xor a.toLong()
		}
		return result
	}

	fun nextId(): String {
		var result = ""
		for (a in hash()) {
			result += ALPHABET[a]
		}
		increment()
		return result
	}

}

private val generator by lazy { IdGenerator() }

fun nextUID(): String = generator.nextId()
fun nextUIDLongs(): String = generator.nextId() + generator.nextId()
