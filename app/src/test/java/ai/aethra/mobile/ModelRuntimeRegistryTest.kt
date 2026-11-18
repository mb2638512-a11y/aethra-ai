package ai.aethra.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRuntimeRegistryTest {
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
}
