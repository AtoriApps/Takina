package lib.takina.core.xmpp.modules.omemo

import lib.takina.core.exceptions.TakinaException

class OMEMOException : TakinaException {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)

}