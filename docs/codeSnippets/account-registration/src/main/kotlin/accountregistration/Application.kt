package accountregistration

import lib.takina.core.builder.createTakina

fun main() {
	val takina = createTakina {
		register {
			this.domain = "sure.im"
			this.registrationFormHandler {
				it.getFieldByVar("username")!!.fieldValue = "myusername"
				it.getFieldByVar("password")!!.fieldValue = "mysecretpassword"
				it.getFieldByVar("email")!!.fieldValue = "myusername@mailserver.com"
				it.getFieldByVar("captcha")!!.fieldValue = "999"
			}
			this.registrationHandler { it }
		}
	}
	takina.connectAndWait()
	takina.disconnect()
}