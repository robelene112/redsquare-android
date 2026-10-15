package nl.davidengelen.redsquare.chat

import nl.davidengelen.redsquare.saito.RedSquareImages

// Chat's upload callback sends one img.img-prev, never an arbitrary HTML document.
private val imageMessage = Regex("""\A<img class="img-prev" src="([^"<>\r\n]+)">\z""")

internal fun chatImageSource(message: String): String? = imageMessage.matchEntire(message)
    ?.groupValues?.get(1)?.let(RedSquareImages::safeSource)
