package nl.davidengelen.redsquare.spaces

/** Invoked by the background Spaces coordinator, never during composition. */
internal object NativeSpaceMls {
    init { System.loadLibrary("saito_spaces_mls") }
    @JvmStatic external fun exchange(request: String): String
}
