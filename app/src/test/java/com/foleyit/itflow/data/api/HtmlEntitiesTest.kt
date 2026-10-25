package com.foleyit.itflow.data.api

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HtmlEntitiesTest {
    @Test fun decodesCommonEntities() {
        assertEquals("Smith & Sons \"Q\" <b> it's", decodeHtmlEntities("Smith &amp; Sons &quot;Q&quot; &lt;b&gt; it&#039;s"))
    }

    @Test fun decodesOnlyOnePass() {
        assertEquals("&lt;script&gt;", decodeHtmlEntities("&amp;lt;script&amp;gt;"))
    }

    @Test fun leavesPlainTextAlone() {
        assertEquals("A & B", decodeHtmlEntities("A & B"))
        assertEquals("", decodeHtmlEntities(""))
    }

    private data class Row(
        @com.google.gson.annotations.JsonAdapter(HtmlEntityStringAdapter::class) val name: String?,
        val body: String?,
    )

    @Test fun fieldAdapterDecodesNamesButNotOtherFields() {
        val row = Gson().fromJson("""{"name":"A &amp; B","body":"x &amp; y"}""", Row::class.java)
        assertEquals("A & B", row.name)
        assertEquals("x &amp; y", row.body)
        assertNull(Gson().fromJson("""{"name":null}""", Row::class.java).name)
    }
}
