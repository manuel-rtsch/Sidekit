package hrln.interactive.sidekit.config

import android.os.Process
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class TrustedCallersTest {
    private val own = "AA".repeat(32)
    private val partner = "BB".repeat(32)
    private val stranger = "CC".repeat(32)

    private fun policy(
        ownDigests: Set<String> = setOf(own),
        allowed: Set<String> = setOf(partner),
        packages: Map<String, Set<String>> = emptyMap(),
        packagesByUid: Map<Int, Set<String>> = emptyMap()
    ) = TrustPolicy(
        ownDigests = ownDigests,
        allowedDigests = allowed,
        digestsOf = { packageName -> packages[packageName].orEmpty() },
        packagesOf = { uid -> packagesByUid[uid].orEmpty() }
    )

    @Test
    fun `an app signed with the host certificate is trusted`() {
        val policy = policy(packages = mapOf("com.example.first.party" to setOf(own)))

        assertTrue(policy.isTrustedPackage("com.example.first.party"))
    }

    @Test
    fun `an allowlisted partner is trusted and anything else is not`() {
        val policy = policy(
            packages = mapOf(
                "com.example.partner" to setOf(partner),
                "com.example.stranger" to setOf(stranger)
            )
        )

        assertTrue(policy.isTrustedPackage("com.example.partner"))
        assertFalse(policy.isTrustedPackage("com.example.stranger"))
    }

    @Test
    fun `a package without readable certificates is not trusted`() {
        assertFalse(policy().isTrustedPackage("com.example.unknown"))
    }

    @Test
    fun `an empty allowlist leaves only the host certificate trusted`() {
        val policy = policy(allowed = emptySet(), packages = mapOf("com.example.partner" to setOf(partner)))

        assertFalse(policy.isTrustedPackage("com.example.partner"))
    }

    @Test
    fun `every package of a uid must be trusted`() {
        val policy = policy(
            packages = mapOf(
                "com.example.host.signed" to setOf(own),
                "com.example.other" to setOf(stranger)
            ),
            packagesByUid = mapOf(42 to setOf("com.example.host.signed", "com.example.other"))
        )

        assertFalse(policy.isTrustedUid(42))

        val onlyTrusted = policy(
            packages = mapOf("com.example.host.signed" to setOf(own)),
            packagesByUid = mapOf(42 to setOf("com.example.host.signed"))
        )
        assertTrue(onlyTrusted.isTrustedUid(42))
    }

    @Test
    fun `an app signed with several certificates must trust all of them`() {
        val both = policy(packages = mapOf("com.example.two" to setOf(own, partner)))
        assertTrue(both.isTrustedPackage("com.example.two"))

        val oneStranger = policy(packages = mapOf("com.example.two" to setOf(own, stranger)))
        assertFalse(oneStranger.isTrustedPackage("com.example.two"))
    }

    @Test
    fun `this process is trusted without a certificate lookup`() {
        val policy = policy()

        assertTrue(policy.isTrustedUid(Process.myUid()))
        assertFalse(policy.isTrustedUid(Process.myUid() + 1))
    }

    @Test
    fun `digest parsing accepts the formats tools print`() {
        val spaced = own.chunked(2).joinToString(":")
        assertEquals(own, TrustedCallers.normalizeDigest(spaced))
        assertEquals(own, TrustedCallers.normalizeDigest(own.lowercase()))
        assertEquals(own, TrustedCallers.normalizeDigest(" $spaced "))
        assertNull(TrustedCallers.normalizeDigest(""))
        assertNull(TrustedCallers.normalizeDigest("not-a-digest"))
        assertNull(TrustedCallers.normalizeDigest("ZZ".repeat(32)))
    }
}
