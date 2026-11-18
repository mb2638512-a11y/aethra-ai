package ai.aethra.mobile

/** Receives one decoded token piece from the native GGUF generation loop. */
fun interface NativeTokenCallback {
    fun onToken(token: String)
}
