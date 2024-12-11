package lib.takina.core.xmpp.modules

import lib.takina.DummyTakina
import lib.takina.core.xmpp.forms.FieldType
import lib.takina.core.xmpp.forms.JabberDataForm
import lib.takina.core.xmpp.stanzas.IQType
import lib.takina.core.xmpp.stanzas.iq
import lib.takina.core.xmpp.toBareJID
import lib.takina.requestResponse
import kotlin.test.*

class InBandRegistrationModuleTest {

	val takina = DummyTakina().apply {
		connect()
	}
	val module = takina.getModule<InBandRegistrationModule>(InBandRegistrationModule.TYPE)

	@Test
	fun test_registration_plain() {
		takina.requestResponse {
			request {
				it.getModule<InBandRegistrationModule>(InBandRegistrationModule.TYPE)
					.requestRegistrationForm("example.com".toBareJID())
			}
			expectedRequest {
				iq {
					"query" {
						xmlns = "jabber:iq:register"
					}
				}
			}
			response {
				iq {
					type = IQType.Result
					attributes["from"] = "example.com"
					"query" {
						xmlns = "jabber:iq:register"
						"instructions" { +"Choose a username and password for use with this service. Please also provide your email address." }
						"username" {}
						"password" {}
						"email" {}
					}
				}
			}
			validate { it: Result<JabberDataForm>? ->
				assertNotNull(it).let {
					it.onFailure {
						fail()
					}
					it.onSuccess { form ->
						assertNotNull(form.getFieldByVar("username")).let {
							assertTrue(it.fieldRequired)
							assertEquals(FieldType.TextSingle, it.fieldType)
						}
						assertNotNull(form.getFieldByVar("password")).let {
							assertTrue(it.fieldRequired)
							assertEquals(FieldType.TextPrivate, it.fieldType)
						}
						assertNotNull(form.getFieldByVar("email")).let {
							assertTrue(it.fieldRequired)
							assertEquals(FieldType.TextSingle, it.fieldType)
						}
					}
				}
			}
		}

	}

}