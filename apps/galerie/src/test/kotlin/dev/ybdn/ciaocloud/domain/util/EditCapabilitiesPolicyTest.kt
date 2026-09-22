package dev.ybdn.ciaocloud.domain.util

import dev.ybdn.ciaocloud.domain.model.EditAvailability
import dev.ybdn.ciaocloud.domain.model.EditCapabilities
import dev.ybdn.ciaocloud.domain.model.EditUnavailableReason
import dev.ybdn.ciaocloud.domain.model.GalleryLocation
import dev.ybdn.ciaocloud.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditCapabilitiesPolicyTest {

    private val ok = EditAvailability.Available
    private fun no(reason: EditUnavailableReason) = EditAvailability.Unavailable(reason)

    private fun caps(
        mime: String = "image/jpeg",
        location: GalleryLocation = GalleryLocation.PHONE,
        type: MediaType = MediaType.PHOTO,
        ssd: Boolean = true,
        transfer: Boolean = false,
        external: Boolean = false,
        locked: Boolean = false,
        width: Int? = 4000,
        height: Int? = 3000,
    ) = EditCapabilitiesPolicy.capabilities(EditContext(location, type, mime, width, height, ssd, transfer, external, locked))

    @Test
    fun `jpeg allows everything`() {
        assertEquals(EditCapabilities(ok, ok, ok, ok), caps())
    }

    @Test
    fun `png and webp are reencoded without lossless rotation`() {
        val expected = EditCapabilities(ok, ok, no(EditUnavailableReason.UNSUPPORTED_FORMAT), ok)
        assertEquals(expected, caps("image/png"))
        assertEquals(expected, caps("image/webp"))
    }

    @Test
    fun `heic and avif are copied as jpeg with read only metadata`() {
        val expected = EditCapabilities(
            ok,
            no(EditUnavailableReason.FORMAT_CHANGES_ON_SAVE),
            no(EditUnavailableReason.UNSUPPORTED_FORMAT),
            no(EditUnavailableReason.METADATA_READ_ONLY),
        )
        assertEquals(expected, caps("image/heic"))
        assertEquals(expected, caps("IMAGE/AVIF"))
    }

    @Test
    fun `dng is preview only`() {
        val unsupported = no(EditUnavailableReason.UNSUPPORTED_FORMAT)
        assertEquals(EditCapabilities(unsupported, unsupported, unsupported, no(EditUnavailableReason.METADATA_READ_ONLY)), caps("image/x-adobe-dng"))
    }

    @Test
    fun `gif and photos over 200 megapixels are not editable`() {
        val unsupported = no(EditUnavailableReason.UNSUPPORTED_FORMAT)
        assertEquals(EditCapabilities(unsupported, unsupported, unsupported, unsupported), caps("image/gif"))
        val huge = caps(width = 20_000, height = 10_001)
        assertEquals(no(EditUnavailableReason.TOO_LARGE), huge.editCopy)
        assertEquals(no(EditUnavailableReason.TOO_LARGE), huge.replace)
        assertEquals(no(EditUnavailableReason.UNSUPPORTED_FORMAT), huge.metadata)
        assertTrue(caps(width = null, height = null).editCopy.isAvailable)
    }

    @Test
    fun `videos, external media and locked viewer hide every action`() {
        listOf(
            caps(type = MediaType.VIDEO, mime = "video/mp4") to EditUnavailableReason.VIDEO,
            caps(external = true) to EditUnavailableReason.EXTERNAL_MEDIA,
            caps(locked = true) to EditUnavailableReason.LOCKED,
        ).forEach { (capabilities, reason) ->
            assertEquals(EditCapabilities(no(reason), no(reason), no(reason), no(reason)), capabilities)
            assertTrue(reason.hidesAction)
        }
    }

    @Test
    fun `phone only media does not need the ssd`() {
        assertEquals(EditCapabilities(ok, ok, ok, ok), caps(location = GalleryLocation.PHONE, ssd = false))
    }

    @Test
    fun `ssd only media needs the ssd for every write`() {
        val required = no(EditUnavailableReason.SSD_REQUIRED)
        assertEquals(EditCapabilities(required, required, ok, required), caps(location = GalleryLocation.SSD, ssd = false))
        assertEquals(EditCapabilities(ok, ok, ok, ok), caps(location = GalleryLocation.SSD))
        assertFalse(EditUnavailableReason.SSD_REQUIRED.hidesAction)
    }

    @Test
    fun `media on both copies to the phone but needs the ssd to change originals`() {
        val required = no(EditUnavailableReason.SSD_REQUIRED)
        assertEquals(EditCapabilities(ok, required, ok, required), caps(location = GalleryLocation.BOTH, ssd = false))
    }

    @Test
    fun `a running transfer disables edits`() {
        val running = no(EditUnavailableReason.TRANSFER_RUNNING)
        assertEquals(EditCapabilities(running, running, running, running), caps(transfer = true))
        // Un format sans objet reste masqué plutôt que désactivé.
        assertEquals(no(EditUnavailableReason.FORMAT_CHANGES_ON_SAVE), caps("image/heic", transfer = true).replace)
    }
}
