
package lib.takina.core.eventbus

interface EventHandler<in T : Event> {

	fun onEvent(event: T)

}