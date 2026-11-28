package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
        val foreground = Color(identicon.foreground)
        // Each cell and its outline are opaque and share a color. Fill their
        // joined bounds directly so the first avatar needs no stroke shader.
        val edge = size.width * 0.0025f
        val filledCell = Size(cell.width + 2 * edge, cell.height + 2 * edge)
        identicon.cells.forEachIndexed { index, filled ->
            if (filled) {
                val offset = Offset(index % 5 * cell.width - edge, index / 5 * cell.height - edge)
                drawRect(foreground, offset, filledCell)
            }
        }
    }
}
