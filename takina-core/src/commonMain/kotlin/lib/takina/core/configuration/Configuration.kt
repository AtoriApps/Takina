
package lib.takina.core.configuration

import lib.takina.core.exceptions.TakinaException
import lib.takina.core.xmpp.BareJID
import lib.takina.core.xmpp.forms.JabberDataForm

interface SaslConfig

interface UserJIDProvider {

    val userJID: BareJID

}

interface DomainProvider {

    val domain: String

}

data class Registration(
    override val domain: String,
    val formHandler: ((JabberDataForm) -> Unit)?,
    val formHandlerWithResponse: ((JabberDataForm) -> JabberDataForm)?,
) : DomainProvider

interface ConnectionConfig

data class Configuration(
    val sasl: SaslConfig?,
    val connection: ConnectionConfig,
    val registration: Registration? = null,
)

val Configuration.declaredDomain: String
    get() = if (this.sasl is DomainProvider) {
        this.sasl.domain
    } else if (this.registration != null) {
        this.registration.domain
    } else throw TakinaException("Cannot determine domain.")

val Configuration.declaredUserJID: BareJID?
    get() = if (this.sasl is UserJIDProvider) {
        this.sasl.userJID
    } else null