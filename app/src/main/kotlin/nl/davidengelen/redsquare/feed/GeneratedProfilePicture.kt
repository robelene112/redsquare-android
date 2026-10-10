package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.SaitoIdenticon

@Composable
internal fun GeneratedProfilePicture(author: String, modifier: Modifier) {
    val identicon = remember(author) { SaitoIdenticon.forAddress(author) }
    val label = stringResource(R.string.profile_generated_picture)
    Canvas(modifier.semantics { contentDescription = label }) {
        drawRect(Color(SaitoIdenticon.BACKGROUND))
        val cell = Size(size.width / 5, size.height / 5)
        identicon.cells.forEachIndexed { index, filled ->
            if (filled) {
                val offset = Offset(index % 5 * cell.width, index / 5 * cell.height)
                drawRect(Color(identicon.foreground), offset, cell)
                drawRect(Color(identicon.foreground), offset, cell, style = Stroke(size.width * 0.005f))
            }
        }
    }
}
