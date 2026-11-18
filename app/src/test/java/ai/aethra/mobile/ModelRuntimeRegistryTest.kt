package ai.aethra.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRuntimeRegistryTest {
    @Test
    fun pcmWavHeaderHasCorrectMono16KhzPcmFields() {
        val header = PcmWavFormat.header(32000)
        assertEquals(44, header.size)
        assertEquals("RIFF", String(header.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("WAVE", String(header.copyOfRange(8, 12), Charsets.US_ASCII))
        assertEquals("fmt ", String(header.copyOfRange(12, 16), Charsets.US_ASCII))
        assertEquals(16_000, littleEndianInt(header, 24))
        assertEquals(64_000, littleEndianInt(header, 28))
        assertEquals(1, littleEndianShort(header, 22))
        assertEquals(3, littleEndianShort(header, 20))
        assertEquals(4, littleEndianShort(header, 32))
        assertEquals(32, littleEndianShort(header, 34))
        assertEquals(32000, littleEndianInt(header, 40))
    }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun littleEndianShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    @Test
    fun onlyIntegratedAndroidRuntimeIsRunnable() {
        assertEquals(setOf("gemma-4-e2b"), ModelRuntimeRegistry.runnableModelIds())
    }

    @Test
    fun lfm3OmniAdvertisesItsMultimodalContract() {
        val capabilities = ModelRuntimeRegistry.capabilitiesFor("lfm3-omni-2b")
        assertTrue(ModelModality.IMAGE in capabilities.inputs)
        assertTrue(ModelModality.AUDIO in capabilities.inputs)
        assertTrue(ModelModality.VIDEO in capabilities.inputs)
        assertTrue(ModelModality.AUDIO in capabilities.outputs)
        assertFalse(ModelRuntimeRegistry.isRunnableOnAndroid("lfm3-omni-2b"))
    }

    @Test
    fun catalogOnlyModelsAreBlockedByAndroidMediaRouter() {
        val check = MediaInputRouter.check("lfm3-omni-2b", "video/mp4")
        assertTrue(check.acceptedByModel)
        assertFalse(check.acceptedByAndroidRuntime)
    }

    @Test
    fun gemmaVideoInputUsesTheRegisteredFrameAdapter() {
        val check = MediaInputRouter.check("gemma-4-e2b", "video/mp4")
        assertTrue(check.acceptedByModel)
        assertTrue(check.acceptedByAndroidRuntime)
    }

    @Test
    fun mediaMimeTypesRouteToExpectedModalities() {
        assertEquals(ModelModality.IMAGE, MediaInputRouter.modalityForMime("image/jpeg"))
        assertEquals(ModelModality.AUDIO, MediaInputRouter.modalityForMime("audio/mp4"))
        assertEquals(ModelModality.VIDEO, MediaInputRouter.modalityForMime("video/mp4"))
        assertEquals(ModelModality.DOCUMENT, MediaInputRouter.modalityForMime("application/pdf"))
        assertEquals(ModelModality.DOCUMENT, MediaInputRouter.modalityForMime("text/plain"))
        assertEquals(null, MediaInputRouter.modalityForMime("application/zip"))
        assertEquals(null, MediaInputRouter.modalityForMime(null))
    }

    @Test
    fun everyCatalogEntryHasExplicitCapabilityMetadata() {
        val catalogueIds = ModelCatalogue.candidates.map { it.id }.toSet()
        assertEquals(catalogueIds, ModelRuntimeRegistry.allCapabilityModelIds())
    }

    @Test
    fun lfm3AndMiniCpmHaveBothModelAndProjectorDownloadAssets() {
        val lfmAssets = ModelAssets.byModel["lfm3-omni-2b"].orEmpty()
        assertEquals(2, lfmAssets.size)
        assertTrue(lfmAssets.any { it.fileName == "LFM3-Omni-2B-Q4_K_M.gguf" })
        assertTrue(lfmAssets.any { it.fileName == "mmproj-LFM3-Omni-2B-Q4_K_M.gguf" })

        val miniAssets = ModelAssets.byModel["minicpm-v-4-6"].orEmpty()
        assertEquals(2, miniAssets.size)
        assertTrue(miniAssets.any { it.fileName == "MiniCPM-V-4_6-Q4_K_M.gguf" })
        assertTrue(miniAssets.any { it.fileName == "mmproj-model-f16.gguf" })
    }

    @Test
    fun onlyCatalogModelsWithManifestsCanBeDownloaded() {
        assertTrue(ModelAssets.byModel.keys.all { modelId ->
            ModelCatalogue.candidates.any { it.id == modelId }
        })
        assertTrue(ModelAssets.byModel.values.all { assets -> assets.isNotEmpty() && assets.all { it.url.startsWith("https://huggingface.co/") } })
    }

    @Test
    fun partialFilesCannotEnableModelRunWhileDownloadIsActive() {
        val active = DownloadSnapshot(1L, android.app.DownloadManager.STATUS_RUNNING, 100L, 1000L, null, 0)
        assertFalse(ModelDownloadReadiness.isReady(true, 1, listOf(active)))
    }

    @Test
    fun modelRunRequiresAllSavedDownloadsToFinishSuccessfully() {
        val complete = DownloadSnapshot(1L, android.app.DownloadManager.STATUS_SUCCESSFUL, 1000L, 1000L, null, 0)
        val failed = DownloadSnapshot(2L, android.app.DownloadManager.STATUS_FAILED, 0L, 1000L, null, 1001)
        assertTrue(ModelDownloadReadiness.isReady(true, 1, listOf(complete)))
        assertFalse(ModelDownloadReadiness.isReady(true, 2, listOf(complete, failed)))
        assertFalse(ModelDownloadReadiness.isReady(false, 1, listOf(complete)))
    }
}
