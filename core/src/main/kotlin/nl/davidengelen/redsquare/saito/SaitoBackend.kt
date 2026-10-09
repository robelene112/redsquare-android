package nl.davidengelen.redsquare.saito

/** The only approved app backends. Imported wallet peer options are never consulted. */
enum class SaitoBackend(val id: String, val webUrl: String, val peerUrl: String) {
    OWNER("owner", "https://saito.davidengelen.nl", "wss://saito.davidengelen.nl/wsopen"),
    OFFICIAL("official", "https://saito.io", "wss://saito.io/wsopen");

    val domain: String get() = webUrl.removePrefix("https://")
}
