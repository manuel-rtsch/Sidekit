package hrln.interactive.sidekit.apps.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MediaSessionRegistryTest {
    private class FakeOwner(override val sessionId: String) : MediaSessionOwner {
        override val notificationChannelId = "channel-$sessionId"
        override val notificationChannelName = "Channel $sessionId"
        override val notificationId = sessionId.hashCode()
        override var isActive = false
        var pauses = 0

        override fun pause() {
            pauses++
            isActive = false
        }
    }

    @Test
    fun `activating an owner pauses every other active owner`() {
        val registry = MediaSessionRegistry()
        val music = FakeOwner("music").apply { isActive = true }
        val remote = FakeOwner("remote")
        registry.register(music)
        registry.register(remote)

        registry.activate(remote)

        assertEquals(1, music.pauses)
        assertEquals(0, remote.pauses)
        assertSame(remote, registry.active)
    }

    @Test
    fun `activating twice does not pause the owner again`() {
        val registry = MediaSessionRegistry()
        val music = FakeOwner("music").apply { isActive = true }
        registry.register(music)

        registry.activate(music)
        registry.activate(music)

        assertEquals(0, music.pauses)
        assertSame(music, registry.active)
    }

    @Test
    fun `owners that are already paused are left alone`() {
        val registry = MediaSessionRegistry()
        val music = FakeOwner("music")
        registry.register(music)

        val remote = FakeOwner("remote")
        registry.register(remote)

        registry.activate(remote)

        assertEquals(0, music.pauses)
        assertNotSame(music, registry.active)
    }

    @Test
    fun `deactivating a non-active owner keeps the active session`() {
        val registry = MediaSessionRegistry()
        val music = FakeOwner("music").apply { isActive = true }
        val remote = FakeOwner("remote").apply { isActive = true }
        registry.register(music)
        registry.register(remote)
        registry.activate(remote)

        registry.deactivate(music)

        assertSame(remote, registry.active)
    }

    @Test
    fun `deactivating the active owner clears it`() {
        val registry = MediaSessionRegistry()
        val music = FakeOwner("music").apply { isActive = true }
        registry.register(music)
        registry.activate(music)

        registry.deactivate(music)

        assertNull(registry.active)
    }

    @Test
    fun `unregistering the active owner clears it`() {
        val registry = MediaSessionRegistry()
        val music = FakeOwner("music")
        registry.register(music)
        registry.activate(music)

        registry.unregister(music)

        assertNull(registry.active)
        assertEquals(emptyList<MediaSessionOwner>(), registry.registered)
    }

    @Test
    fun `release order is irrelevant - whoever activates last wins`() {
        val registry = MediaSessionRegistry()
        val a = FakeOwner("a").apply { isActive = true }
        val b = FakeOwner("b").apply { isActive = true }
        registry.register(a)
        registry.register(b)

        registry.activate(a)
        registry.activate(b)

        assertEquals(1, a.pauses)
        assertSame(b, registry.active)
    }
}
