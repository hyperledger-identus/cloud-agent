package interactions

import net.serenitybdd.screenplay.rest.interactions.RestInteraction

fun Post.body(obj: Any): RestInteraction {
    return this.with {
        it.header("Content-Type", "application/json").body(obj)
    }
}

fun Post.rawBytesBody(bytes: ByteArray): RestInteraction {
    return this.with {
        it.header("Content-Type", "application/octet-stream").body(bytes)
    }
}

fun Put.rawBytesBody(bytes: ByteArray): RestInteraction {
    return this.with {
        it.header("Content-Type", "application/octet-stream").body(bytes)
    }
}

fun Patch.body(obj: Any): RestInteraction {
    return this.with {
        it.header("Content-Type", "application/json").body(obj)
    }
}
