package nl.davidengelen.redsquare

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.text.AnnotatedString

/** Compare every input character exactly, independently of a field's visible accessibility label. */
internal fun SemanticsNodeInteraction.assertEditableTextEquals(expected: String): SemanticsNodeInteraction =
    assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(expected)))
