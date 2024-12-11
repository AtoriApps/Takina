package lib.takina.core.xmpp.forms

import lib.takina.core.xml.ElementWrapperSerializer

object JabberDataFormSerializer :
    ElementWrapperSerializer<JabberDataForm>(elementProvider = { it.element },
        objectFactory = { JabberDataForm(it) }
    )
