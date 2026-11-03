package nl.davidengelen.redsquare.shell

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.net.HttpURLConnection
import java.net.URL

private val covers = android.util.LruCache<String, ImageBitmap>(8)
@Composable
internal fun RemoteCover(url: String, modifier: Modifier, description: String) {
    val bitmap by produceState(covers.get(url), url) {
        if (value == null) value = withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000; connection.readTimeout = 5000
                try {
                    require(connection.contentLengthLong <= 2_000_000)
                    val bytes = connection.inputStream.use { stream ->
                        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) { val n = stream.read(buffer); if (n < 0) break; require(out.size()+n <= 2_000_000); out.write(buffer,0,n) }
                        out.toByteArray()
                    }
                    val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes,0,bytes.size,size)
                    require(size.outWidth > 0 && size.outHeight > 0)
                    var sample = 1
                    while (size.outWidth / sample > 1200 || size.outHeight / sample > 1200) sample *= 2
                    BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()?.also { covers.put(url,it) }
                } finally { connection.disconnect() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!, description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else ShellIcon("image", description, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun DemoQr(value: String) {
    val bitmap = remember(value) {
        val matrix = MultiFormatWriter().encode(value,BarcodeFormat.QR_CODE,256,256)
        Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888).also { bmp ->
            for (y in 0 until 256) for (x in 0 until 256) bmp.setPixel(x,y,if(matrix[x,y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }.asImageBitmap()
    }
    Box(Modifier.fillMaxWidth().padding(16.dp),contentAlignment = Alignment.Center) { Image(bitmap,"QR code for the example address",Modifier.size(220.dp).background(Color.White)) }
}
