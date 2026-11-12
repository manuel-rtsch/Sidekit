package hrln.interactive.sidekit.sdk.crypto

/** Symmetric byte encryption for files the app keeps in private storage. */
interface ByteCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(sealed: ByteArray): ByteArray
}
