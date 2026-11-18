package hrln.interactive.sidekit.system

/**
 * Keeps the keypad Back service registered under the component name Sidekit has always used.
 *
 * The behaviour lives in the SDK (third-party apps enable their own copy there), but the component
 * name is what Android stores when a user enables an accessibility service. Renaming it would leave
 * the old entry enabled and the new one off, so the keypad's top-left key would silently stop
 * going Back after an app update. This subclass keeps the name stable while the implementation
 * moves.
 */
class KeypadAccessibilityService : hrln.interactive.sidekit.sdk.system.KeypadAccessibilityService()
