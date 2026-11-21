package nl.davidengelen.redsquare.spaces

/** Channel preferences are local to the receiving account/device. They never
 * grant an author permission to request an everyone alert. */
enum class SpaceNotificationMode(val wire: String) { PERSONAL("personal"), ALL("all"), SILENT("silent") }

data class SpaceNotificationPolicy(val mode: SpaceNotificationMode = SpaceNotificationMode.PERSONAL,
    val allowEveryone: Boolean = true) {
    /** Inputs refer to independently authenticated, received activity. */
    fun shouldNotify(ownMessage: Boolean, mentioned: Boolean = false, repliedToMe: Boolean = false, everyone: Boolean = false): Boolean =
        !ownMessage && when (mode) {
            SpaceNotificationMode.SILENT -> false
            SpaceNotificationMode.ALL -> true
            SpaceNotificationMode.PERSONAL -> mentioned || repliedToMe || (everyone && allowEveryone)
        }
}
