
package lib.takina.core.exceptions

import lib.takina.core.xmpp.modules.auth.SASLModule

open class TakinaException : RuntimeException {

	constructor() : super()
	constructor(message: String?) : super(message)
	constructor(message: String?, cause: Throwable?) : super(message, cause)
	constructor(cause: Throwable?) : super(cause)
}

class AuthenticationException(val error: SASLModule.SASLError, message: String?) : TakinaException(message)