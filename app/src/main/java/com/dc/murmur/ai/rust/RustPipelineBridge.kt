package com.dc.murmur.ai.rust

import android.util.Log

/**
 * JNI bridge to the Murmur-rs native library.
 *
 * Handles library loading and declares external native methods.
 * All methods are static — the native Pipeline is tracked by an opaque pointer (Long).
 */
object RustPipelineBridge {

    private const val TAG = "RustPipelineBridge"

    /** Whether the native library was successfully loaded. */
    var isLoaded: Boolean = false
        private set

    /** Error message if the library failed to load. */
    var loadError: String? = null
        private set

    fun load() {
        if (isLoaded) return
        try {
            System.loadLibrary("murmur_rs")
            isLoaded = true
            Log.i(TAG, "libmurmur_rs.so loaded")
        } catch (e: UnsatisfiedLinkError) {
            loadError = e.message
            Log.w(TAG, "Failed to load libmurmur_rs.so: ${e.message}")
        }
    }

    // --- Native methods (JNI) ---

    /** Create a new pipeline from JSON config. Returns opaque pointer (0 on error). */
    @JvmStatic
    external fun nativeCreate(configJson: String): Long

    /** Destroy a pipeline and free its memory. */
    @JvmStatic
    external fun nativeDestroy(ptr: Long)

    /** Initialize the pipeline (load models, detect Claude). Returns 0 on success. */
    @JvmStatic
    external fun nativeInit(ptr: Long): Int

    /** Process an audio file through the full pipeline. Returns JSON or null. */
    @JvmStatic
    external fun nativeProcess(ptr: Long, chunkId: String, filePath: String): String?

    /** Set speaker profiles for matching. JSON array of SpeakerProfile. Returns 0 on success. */
    @JvmStatic
    external fun nativeSetProfiles(ptr: Long, json: String): Int

    /** Extract a speaker embedding from PCM data. Returns base64 string or null. */
    @JvmStatic
    external fun nativeExtractEmbedding(ptr: Long, pcmData: FloatArray): String?

    /** Check if the pipeline is initialized and ready. Returns 1 if ready, 0 if not. */
    @JvmStatic
    external fun nativeIsReady(ptr: Long): Int
}
