package hrln.interactive.sidekit.sdk.system

/**
 * Lets a host handle the keypad's Back key before it becomes a global Back.
 *
 * A Sidekit host with its own navigation layers (Sideport's projection, for example) registers a
 * delegate that returns true when it consumed the press. Without a delegate the accessibility
 * service performs the system Back action.
 *
 * The delegate is process-wide and main-thread only, like the accessibility service itself.
 */
object KeypadSystemBack {
    @Volatile
    var delegate: (() -> Boolean)? = null
}
