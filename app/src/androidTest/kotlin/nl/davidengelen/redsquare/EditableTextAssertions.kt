package nl.davidengelen.redsquare

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert

/** Compare every UTF-16 character exactly, independently of labels and composing style spans. */
internal fun SemanticsNodeInteraction.assertEditableTextEquals(expected: String): SemanticsNodeInteraction {
    var observed: String? = null
    val matcher = SemanticsMatcher("EditableText text equals the complete expected string") { node ->
        observed = node.config.getOrNull(SemanticsProperties.EditableText)?.text
        observed == expected
    }
    return assert(matcher, messagePrefixOnError = {
        val actual = observed
        val mismatch = if (actual == null) 0 else
            (0 until minOf(expected.length, actual.length)).firstOrNull { expected[it] != actual[it] }
                ?: minOf(expected.length, actual.length)
        fun units(value: String?): String = value?.let {
            val start = maxOf(0, mismatch - 4)
            val end = minOf(it.length, mismatch + 5)
            it.substring(minOf(start, end), end).map { character -> character.code.toString(16).padStart(4, '0') }.joinToString(" ")
        } ?: "missing EditableText"
        "EditableText mismatch at UTF-16 offset $mismatch, expectedLength=${expected.length}, actualLength=${actual?.length}. " +
            "Expected units=${units(expected)}; actual units=${units(actual)}"
    })
}
