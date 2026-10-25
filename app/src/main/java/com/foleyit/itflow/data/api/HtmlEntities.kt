package com.foleyit.itflow.data.api

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

private val ENTITY = Regex("&(amp|lt|gt|quot|apos|#0*39|#x0*27);")

/**
 * The server stores display names HTML-escaped ("Smith &amp; Sons"). Plain-text views must show the
 * characters, not the entities. Single pass, so "&amp;lt;" becomes "&lt;" and not "<".
 */
fun decodeHtmlEntities(s: String): String =
    if ('&' !in s) s else ENTITY.replace(s) {
        when (it.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            else -> "'"
        }
    }

/** Use on display-name fields only (`@JsonAdapter`); HTML bodies must keep their entities. */
class HtmlEntityStringAdapter : TypeAdapter<String?>() {
    override fun write(out: JsonWriter, value: String?) {
        if (value == null) out.nullValue() else out.value(value)
    }

    override fun read(reader: JsonReader): String? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        return decodeHtmlEntities(reader.nextString())
    }
}
