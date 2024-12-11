package lib.takina.core.utils

expect class Lock() {
    
    fun <T>withLock(fn: ()->T): T;

}