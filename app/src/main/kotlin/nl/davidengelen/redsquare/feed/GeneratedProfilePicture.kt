package nl.davidengelen.redsquare.feed

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.SaitoIdenticon

private const val IdenticonGridSize = 5
private val identicons = object : LruCache<String, ImageBitmap>(128) {
    override fun create(key: String): ImageBitmap {
        val identity = SaitoIdenticon.forAddress(key)
        val colors = IntArray(identity.cells.size) { index ->
            if (identity.cells[index]) identity.foreground else SaitoIdenticon.BACKGROUND
        }
        return Bitmap.createBitmap(colors, IdenticonGridSize, IdenticonGridSize, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

@Composable
internal fun GeneratedProfilePicture(author: String, modifier: Modifier) {
    val picture = remember(author) { requireNotNull(identicons.get(author)) }
    Image(picture, stringResource(R.string.profile_generated_picture), modifier,
        contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None)
}
