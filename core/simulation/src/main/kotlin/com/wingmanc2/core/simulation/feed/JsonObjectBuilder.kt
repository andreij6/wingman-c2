package com.wingmanc2.core.simulation.feed

internal class JsonObjectBuilder {
    private val text = StringBuilder(INITIAL_CAPACITY).append('{')

    fun field(name: String, rawValue: String) {
        if (text.length > 1) text.append(',')
        text.append('"').append(name).append("\":").append(rawValue)
    }

    fun build(): String = text.append('}').toString()

    private companion object {
        const val INITIAL_CAPACITY = 128
    }
}

internal inline fun buildJsonObject(block: JsonObjectBuilder.() -> Unit): String = JsonObjectBuilder().apply(block).build()
