
package lib.takina.core.xmpp.modules.commands

import lib.takina.DummyTakina
import lib.takina.assertContains
import lib.takina.core.xml.element
import lib.takina.core.xmpp.BareJID
import lib.takina.core.xmpp.forms.Field
import lib.takina.core.xmpp.forms.FieldType
import lib.takina.core.xmpp.forms.FormType
import lib.takina.core.xmpp.forms.JabberDataForm
import lib.takina.core.xmpp.modules.discovery.DiscoveryModule
import lib.takina.core.xmpp.stanzas.IQType
import lib.takina.core.xmpp.stanzas.iq
import lib.takina.core.xmpp.toBareJID
import lib.takina.core.xmpp.toJID
import kotlin.test.*

class CommandsModuleTest {

	val takina = DummyTakina().apply {
		connect()
	}

	@Test
	fun retrieveCommandInfo() {
		val module = takina.getModule<CommandsModule>(CommandsModule.TYPE)

		var response: DiscoveryModule.Info? = null
		val reqId = module.retrieveCommandInfo("responder@domain".toJID(), "config")
			.response {
				it.onSuccess { response = it }
			}
			.send().id

		assertContains(iq {
			type = IQType.Get
			to = "responder@domain".toJID()
			"query" {
				xmlns = "http://jabber.org/protocol/disco#info"
				attributes["node"] = "config"
			}
		}, takina.peekLastSend(), "Invalid output stanza,")

		takina.addReceived(iq {
			type = IQType.Result
			attributes["id"] = reqId
			to = "requester@domain".toJID()
			from = "responder@domain".toJID()
			"query" {
				xmlns = "http://jabber.org/protocol/disco#info"
				attributes["node"] = "config"
				"identity" {
					attributes["name"] = "Configure Service"
					attributes["category"] = "automation"
					attributes["type"] = "command-node"
				}
				"feature" { attributes["var"] = "http://jabber.org/protocol/commands" }
				"feature" { attributes["var"] = "jabber:x:data" }
			}
		})

		assertNotNull(response).let { info ->
			assertEquals("config", info.node)
			assertEquals(2, info.features.size)
			assertEquals(1, info.identities.size)

			assertEquals("http://jabber.org/protocol/commands", info.features[0])
			assertEquals("jabber:x:data", info.features[1])

			assertEquals("automation", info.identities[0].category)
			assertEquals("Configure Service", info.identities[0].name)
			assertEquals("command-node", info.identities[0].type)
		}
	}

	@Test
	fun retrieveCommandsList() {
		val module = takina.getModule<CommandsModule>(CommandsModule.TYPE)

		var response: DiscoveryModule.Items? = null
		val reqId = module.retrieveCommandList("responder@domain".toJID())
			.response {
				it.onSuccess {
					response = it
				}
			}
			.send().id

		assertContains(iq {
			type = IQType.Get
			to = "responder@domain".toJID()
			"query" {
				xmlns = "http://jabber.org/protocol/disco#items"
				attributes["node"] = "http://jabber.org/protocol/commands"
			}
		}, takina.peekLastSend(), "Invalid output stanza,")

		takina.addReceived(iq {
			type = IQType.Result
			attributes["id"] = reqId
			to = "requester@domain".toJID()
			from = "responder@domain".toJID()
			"query" {
				xmlns = "http://jabber.org/protocol/disco#items"
				attributes["node"] = "http://jabber.org/protocol/commands"
				"item" {
					attributes["jid"] = "responder@domain"
					attributes["node"] = "list"
					attributes["name"] = "List Service Configurations"
				}
				"item" {
					attributes["jid"] = "responder@domain"
					attributes["node"] = "config"
					attributes["name"] = "Configure Service"
				}
				"item" {
					attributes["jid"] = "responder@domain"
					attributes["node"] = "reset"
					attributes["name"] = "Reset Service Configuration"
				}
			}
		})

		assertNotNull(response).let { resp ->
			assertEquals("http://jabber.org/protocol/commands", resp.node)
			assertEquals("responder@domain", resp.jid.toString())
			assertEquals(3, resp.items.size)

			assertEquals("responder@domain", resp.items[0].jid.toString())
			assertEquals("list", resp.items[0].node)
			assertEquals("List Service Configurations", resp.items[0].name)

			assertEquals("responder@domain", resp.items[1].jid.toString())
			assertEquals("config", resp.items[1].node)
			assertEquals("Configure Service", resp.items[1].name)

			assertEquals("responder@domain", resp.items[2].jid.toString())
			assertEquals("reset", resp.items[2].node)
			assertEquals("Reset Service Configuration", resp.items[2].name)
		}

	}

	@Test
	fun simpleExecutionTest() {
		val module = takina.getModule<CommandsModule>(CommandsModule.TYPE)

		var result: AdHocResult? = null
		val reqId = module.executeCommand("responder@domain".toJID(), "list")
			.response {
				it.onSuccess { result = it }
			}
			.send().id

		assertContains(iq {
			type = IQType.Set
			to = "responder@domain".toJID()
			"command" {
				xmlns = "http://jabber.org/protocol/commands"
				attributes["node"] = "list"
				attributes["action"] = "execute"
			}
		}, takina.peekLastSend(), "Invalid output stanza,")

		takina.addReceived(iq {
			attributes["id"] = reqId
			type = IQType.Result
			from = "responder@domain".toJID()
			to = "requester@domain".toJID()
			"command" {
				xmlns = "http://jabber.org/protocol/commands"
				attributes["sessionid"] = "list:20020923T213616Z-700"
				attributes["node"] = "list"
				attributes["status"] = "completed"
				"x" {
					xmlns = "jabber:x:data"
					attributes["type"] = "result"
					"title" + { "Configure Service" }
					"instructions" + { "Please select the service to configure." }
					"field" {
						attributes["var"] = "service"
						attributes["label"] = "Service"
						attributes["type"] = "list-single"
						"option" { "value" { +"httpd" } }
						"option" { "value" { +"jabberd" } }
					}
				}

			}
		})

		assertNotNull(result).let { resp ->
			assertEquals(Status.Completed, resp.status)
			assertEquals("list", resp.node)
			assertNull(resp.defaultAction)
			assertEquals("list:20020923T213616Z-700", resp.sessionId)
			assertEquals("responder@domain", resp.jid.toString())
			assertTrue(resp.actions.isEmpty())
			assertNotNull(resp.form).let { form ->
				assertNotNull(form.getFieldByVar("service")).let {
					assertEquals(FieldType.ListSingle, it.fieldType)
					assertEquals("Service", it.fieldLabel)
				}
			}
		}
	}

	@Test
	fun multipleStagesExecutionTest() {
		val module = takina.getModule<CommandsModule>(CommandsModule.TYPE)

		var result: AdHocResult? = null
		var reqId = module.executeCommand("responder@domain".toJID(), "config")
			.response {
				it.onSuccess { result = it }
			}
			.send().id

		takina.addReceived(iq {
			attributes["id"] = reqId
			type = IQType.Result
			from = "responder@domain".toJID()
			to = "requester@domain".toJID()
			"command" {
				xmlns = "http://jabber.org/protocol/commands"
				attributes["sessionid"] = "config:20020923T213616Z-700"
				attributes["node"] = "config"
				attributes["status"] = "executing"
				"actions" {
					attributes["execute"] = "next"
					"next" {}
				}
				"x" {
					xmlns = "jabber:x:data"
					attributes["type"] = "form"
					"title" + { "Configure Service" }
					"instructions" + { "Please select the service to configure." }
					"field" {
						attributes["var"] = "service"
						attributes["label"] = "Service"
						attributes["type"] = "list-single"
						"option" { "value" { +"httpd" } }
						"option" { "value" { +"jabberd" } }
						"option" { "value" { +"postgresql" } }
					}
				}

			}
		})

		val frm: JabberDataForm = assertNotNull(result).let { resp ->
			assertEquals(Status.Executing, resp.status)
			assertEquals("config", resp.node)
			assertEquals(Action.Next, resp.defaultAction)
			assertEquals("config:20020923T213616Z-700", resp.sessionId)
			assertEquals("responder@domain", resp.jid.toString())
			assertEquals(1, resp.actions.size)
			assertEquals(Action.Next, resp.actions[0])
			assertNotNull(resp.form).let { form ->
				assertNotNull(form.getFieldByVar("service")).let {
					assertEquals(FieldType.ListSingle, it.fieldType)
					assertEquals("Service", it.fieldLabel)
				}
			}
			resp.form!!
		}


		result = null

		frm.getFieldByVar("service")!!.fieldValue = "httpd"
		reqId = module.executeCommand(
			"responder@domain".toJID(), "config", frm.createSubmitForm(), null, "config:20020923T213616Z-700"
		)
			.response {
				it.onSuccess { result = it }
			}
			.send().id


		assertContains(iq {
			type = IQType.Set
			to = "responder@domain".toJID()
			"command" {
				xmlns = "http://jabber.org/protocol/commands"
				attributes["sessionid"] = "config:20020923T213616Z-700"
				attributes["node"] = "config"
				"x" {
					xmlns = "jabber:x:data"
					attributes["type"] = "submit"
					"field" {
						attributes["var"] = "service"
						"value" { +"httpd" }
					}
				}
			}
		}, takina.peekLastSend(), "Invalid output stanza,")

		takina.addReceived(iq {
			attributes["id"] = reqId
			type = IQType.Result
			from = "responder@domain".toJID()
			to = "requester@domain".toJID()
			"command" {
				xmlns = "http://jabber.org/protocol/commands"
				attributes["sessionid"] = "config:20020923T213616Z-700"
				attributes["node"] = "config"
				attributes["status"] = "executing"
				"actions" {
					attributes["execute"] = "complete"
					"prev" {}
					"complete" {}
				}
				"x" {
					xmlns = "jabber:x:data"
					attributes["type"] = "form"
					"title" + { "Configure Service" }
					"instructions" + { "Please select the service to configure." }
					"field" {
						attributes["var"] = "state"
						attributes["label"] = "Run State"
						attributes["type"] = "list-single"
						"value" { +"off" }
						"option" { attributes["label"] = "Active"; "value" { +"off" } }
						"option" { attributes["label"] = "Inactive"; "value" { +"on" } }
					}
				}
			}
		})

		assertNotNull(result).let { resp ->
			assertEquals(Status.Executing, resp.status)
			assertEquals("config", resp.node)
			assertEquals(Action.Complete, resp.defaultAction)
			assertEquals("config:20020923T213616Z-700", resp.sessionId)
			assertEquals("responder@domain", resp.jid.toString())
			assertEquals(2, resp.actions.size)
			assertEquals(Action.Prev, resp.actions[0])
			assertEquals(Action.Complete, resp.actions[1])
		}

		reqId = module.executeCommand(result!!.jid, result!!.node, element("x") {
			xmlns = "jabber:x:data"
			attributes["type"] = "submit"
			"field" {
				attributes["var"] = "state"
				"value" { +"on" }
			}
		}, null, "config:20020923T213616Z-700")
			.response {
				it.onSuccess { result = it }
			}
			.send().id

		takina.addReceived(iq {
			attributes["id"] = reqId
			type = IQType.Result
			from = "responder@domain".toJID()
			to = "requester@domain".toJID()
			"command" {
				xmlns = "http://jabber.org/protocol/commands"
				attributes["sessionid"] = "config:20020923T213616Z-700"
				attributes["node"] = "config"
				attributes["status"] = "completed"
				"note" {
					attributes["type"] = "info"
					+"Service 'httpd' has been configured."
				}
			}
		})

		assertNotNull(result).let { resp ->
			assertEquals(Status.Completed, resp.status)
			assertEquals(1, resp.notes.size)
			assertTrue(resp.notes[0] is Note.Info)
			assertEquals("Service 'httpd' has been configured.", resp.notes[0].message)
		}
	}

	@Test
	fun cancelingTest() {
		val module = takina.getModule<CommandsModule>(CommandsModule.TYPE)

		var result: AdHocResult? = null
		var reqId = module.executeCommand("responder@domain".toJID(), "config")
			.response {
				it.onSuccess { result = it }
			}
			.send().id

		takina.addReceived(iq {
			attributes["id"] = reqId
			type = IQType.Result
			from = "responder@domain".toJID()
			to = "requester@domain".toJID()
			"command" {
				xmlns = "http://jabber.org/protocol/commands"
				attributes["sessionid"] = "config:20020923T213616Z-700"
				attributes["node"] = "config"
				attributes["status"] = "executing"
				"actions" {
					attributes["execute"] = "next"
					"next" {}
				}
				"x" {
					xmlns = "jabber:x:data"
					attributes["type"] = "form"
					"title" + { "Configure Service" }
					"instructions" + { "Please select the service to configure." }
					"field" {
						attributes["var"] = "service"
						attributes["label"] = "Service"
						attributes["type"] = "list-single"
						"option" { "value" { +"httpd" } }
						"option" { "value" { +"jabberd" } }
						"option" { "value" { +"postgresql" } }
					}
				}
			}
		})

		reqId = module.executeCommand(result!!.jid, result!!.node, null, Action.Cancel, "config:20020923T213616Z-700")
			.response {
				it.onSuccess { result = it }
			}
			.send().id

		assertContains(iq {
			type = IQType.Set
			to = "responder@domain".toJID()
			"command" {
				xmlns = "http://jabber.org/protocol/commands"
				attributes["sessionid"] = "config:20020923T213616Z-700"
				attributes["node"] = "config"
				attributes["action"] = "cancel"
			}
		}, takina.peekLastSend(), "Invalid output stanza,")


		takina.addReceived(iq {
			attributes["id"] = reqId
			type = IQType.Result
			from = "responder@domain".toJID()
			to = "requester@domain".toJID()
			"command" {
				xmlns = "http://jabber.org/protocol/commands"
				attributes["sessionid"] = "config:20020923T213616Z-700"
				attributes["node"] = "config"
				attributes["status"] = "canceled"
			}
		})

		assertEquals(Status.Canceled, assertNotNull(result).status)

	}

	@Test
	fun testCustomAdHocDisco() {
		val module = takina.getModule<CommandsModule>(CommandsModule.TYPE)
		module.registerAdHocCommand(object : AdHocCommand {
			override fun isAllowed(jid: BareJID): Boolean = jid == "responder@domain".toBareJID()
			override val name = "Testowy"
			override val node = "test"
			override fun process(request: AdHocRequest, response: AdHocResponse) = TODO("Not yet implemented")
		})

		takina.addReceived(iq {
			to = "requester@domain".toJID()
			from = "responder@domain".toJID()
			type = IQType.Get
			"query" {
				xmlns = "http://jabber.org/protocol/disco#items"
				attributes["node"] = "http://jabber.org/protocol/commands"
			}
		})
		assertContains(iq {
			type = IQType.Result
			to = "responder@domain".toJID()
			"query" {
				xmlns = "http://jabber.org/protocol/disco#items"
				attributes["node"] = "http://jabber.org/protocol/commands"
				"item" {
					attributes["node"] = "test"
					attributes["name"] = "Testowy"
				}
			}
		}, takina.peekLastSend(), "Invalid output stanza,")

		takina.addReceived(iq {
			to = "requester@domain".toJID()
			from = "mallet@domain".toJID()
			type = IQType.Get
			"query" {
				xmlns = "http://jabber.org/protocol/disco#items"
				attributes["node"] = "http://jabber.org/protocol/commands"
			}
		})
		assertEquals(0, assertNotNull(takina.peekLastSend()).getFirstChild("query")!!.children.size)

		takina.addReceived(iq {
			to = "requester@domain".toJID()
			from = "responder@domain".toJID()
			type = IQType.Get
			"query" {
				xmlns = "http://jabber.org/protocol/disco#info"
				attributes["node"] = "test"
			}
		})
		assertContains(iq {
			type = IQType.Result
			to = "responder@domain".toJID()
			"query" {
				xmlns = "http://jabber.org/protocol/disco#info"
				attributes["node"] = "test"
				"identity" {
					attributes["name"] = "Testowy"
					attributes["category"] = "automation"
					attributes["type"] = "command-node"
				}
				"feature" { attributes["var"] = "http://jabber.org/protocol/commands" }
				"feature" { attributes["var"] = "jabber:x:data" }
			}
		}, takina.peekLastSend(), "Invalid output stanza,")

		takina.addReceived(iq {
			to = "requester@domain".toJID()
			from = "mallet@domain".toJID()
			type = IQType.Get
			"query" {
				xmlns = "http://jabber.org/protocol/disco#info"
				attributes["node"] = "test"
			}
		})
		assertContains(iq {
			type = IQType.Error
			to = "mallet@domain".toJID()
			"error" {
				attributes["type"] = "auth"
				"forbidden" {
					xmlns = "urn:ietf:params:xml:ns:xmpp-stanzas"
				}
			}
		}, takina.peekLastSend(), "Invalid output stanza,")

	}

	@Test
	fun testCustomAdHocMultipleStagesExecution() {
		val module = takina.getModule<CommandsModule>(CommandsModule.TYPE)
		module.registerAdHocCommand(object : AdHocCommand {
			override fun isAllowed(jid: BareJID): Boolean = jid == "responder@domain".toBareJID()
			override val name = "Testowy"
			override val node = "test"
			override fun process(request: AdHocRequest, response: AdHocResponse) {
				if (request.getSession().values["stage"] == "requestSent" && (request.action == Action.Next || request.action == null)) {
					assertEquals("8756334", request.form?.getFieldByVar("otp")?.fieldValue)
					response.notes = arrayOf(Note.Info("Done"))
					response.status = Status.Completed
				} else {
					val form = JabberDataForm.create(FormType.Form)
						.apply {
							title = "Auth"
							addField("otp", FieldType.TextPrivate).apply {
								fieldLabel = "Enter OneTimePassword number"
							}
						}
					request.getSession().values["stage"] = "requestSent"
					response.form = form
					response.actions = arrayOf(Action.Next)
					response.defaultAction = Action.Next
					response.status = Status.Executing
				}
			}
		})

		takina.addReceived(iq {
			to = "requester@domain".toJID()
			from = "responder@domain".toJID()
			type = IQType.Set
			"command" {
				xmlns = "http://jabber.org/protocol/commands"
				attributes["node"] = "test"
				attributes["action"] = "execute"
			}
		})
		val respStanza = assertNotNull(takina.peekLastSend())
		val sessionId = respStanza.getFirstChild("command")?.attributes?.get("sessionid") ?: fail("Missing sessionid")
		assertContains(iq {
			type = IQType.Result
			"command" {
				xmlns = "http://jabber.org/protocol/commands"
				attributes["node"] = "test"
				attributes["status"] = "executing"
				"actions" {
					attributes["execute"] = "next"
					"next" {}
				}
				"x" {
					xmlns = "jabber:x:data"
					attributes["type"] = "form"
					"title" { +"Auth" }
					"field" {
						attributes["type"] = "text-private"
						attributes["var"] = "otp"
						attributes["label"] = "Enter OneTimePassword number"
					}
				}
			}
		}, respStanza, "Invalid output stanza,")

		takina.addReceived(iq {
			to = "requester@domain".toJID()
			from = "responder@domain".toJID()
			type = IQType.Set
			"command" {
				xmlns = "http://jabber.org/protocol/commands"
				attributes["node"] = "test"
				attributes["sessionid"] = sessionId
				"x" {
					xmlns = "jabber:x:data"
					attributes["type"] = "submit"
					"field" {
						attributes["var"] = "otp"
						"value" { +"8756334" }
					}
				}
			}
		})

		assertContains(iq {
			type = IQType.Result
			"command" {
				xmlns = "http://jabber.org/protocol/commands"
				attributes["node"] = "test"
				attributes["sessionid"] = sessionId
				attributes["status"] = "completed"
				"note" {
					attributes["type"] = "info"
					+"Done"
				}
			}
		}, takina.peekLastSend(), "Invalid output stanza,")

	}

	@Test
	fun testCustomAdHocExecution() {
		val module = takina.getModule<CommandsModule>(CommandsModule.TYPE)
		module.registerAdHocCommand(object : AdHocCommand {
			override fun isAllowed(jid: BareJID): Boolean = jid == "responder@domain".toBareJID()
			override val name = "Testowy"
			override val node = "test"
			override fun process(request: AdHocRequest, response: AdHocResponse) {
				val form = JabberDataForm.create(FormType.Result)
					.apply {
						title = "Available Services"
						setReportedColumns(
							listOf(Field.create("service")
									   .apply { fieldLabel = "Service" },
								   Field.create("status")
									   .apply { fieldLabel = "Status" })
						)
						addItem(
							listOf(Field.create("service")
									   .apply { fieldValue = "httpd" },
								   Field.create("status")
									   .apply { fieldValue = "on" })
						)
						addItem(
							listOf(Field.create("service")
									   .apply { fieldValue = "postgresql" },
								   Field.create("status")
									   .apply { fieldValue = "off" })
						)
					}
				response.form = form
				response.status = Status.Completed
			}
		})

		takina.addReceived(iq {
			to = "requester@domain".toJID()
			from = "responder@domain".toJID()
			type = IQType.Set
			"command" {
				xmlns = "http://jabber.org/protocol/commands"
				attributes["node"] = "test"
				attributes["action"] = "execute"
			}
		})
		assertContains(iq {
			type = IQType.Result
			"command" {
				xmlns = "http://jabber.org/protocol/commands"
				attributes["node"] = "test"
				attributes["status"] = "completed"
				"x" {
					xmlns = "jabber:x:data"
					attributes["type"] = "result"
					"title" { +"Available Services" }
					"reported" {
						"field" {
							attributes["var"] = "service"
							attributes["label"] = "Service"
						}
						"field" {
							attributes["var"] = "status"
							attributes["label"] = "Status"
						}
					}
					"item" {
						"field" {
							attributes["var"] = "service"
							"value" { +"httpd" }
						}
						"field" {
							attributes["var"] = "status"
							"value" { +"on" }
						}
					}
					"item" {
						"field" {
							attributes["var"] = "service"
							"value" { +"postgresql" }
						}
						"field" {
							attributes["var"] = "status"
							"value" { +"off" }
						}
					}
				}
			}
		}, takina.peekLastSend(), "Invalid output stanza,")

	}

}