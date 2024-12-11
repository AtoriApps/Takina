package lib.takina.core.utils

actual class Lock {
    actual fun <T> withLock(fn: () -> T): T {
        return fn();
    }

}