package dev.penlab.sdk

/**
 * Who may call a plugin's service. The rule is kept apart from the Android calls that feed it so it
 * can be unit-tested.
 */
object CallerPolicy {
  /**
   * True if one of [callerPackages] (the packages of the calling UID) is [hostPackage], and, when
   * [requireSameSignature] is set, the caller is signed with the plugin's own certificate
   * ([signaturesMatch] is only asked then).
   */
  fun isAllowed(
    callerPackages: List<String>,
    requireSameSignature: Boolean,
    signaturesMatch: () -> Boolean,
    hostPackage: String = PenLabBridge.HOST_PACKAGE,
  ): Boolean = hostPackage in callerPackages && (!requireSameSignature || signaturesMatch())
}
