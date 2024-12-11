
package lib.takina.core.xml

class XmlException : lib.takina.core.exceptions.TakinaException { constructor() : super()
	constructor(message: String?) : super(message)
	constructor(message: String?, cause: Throwable?) : super(message, cause)
	constructor(cause: Throwable?) : super(cause)
}