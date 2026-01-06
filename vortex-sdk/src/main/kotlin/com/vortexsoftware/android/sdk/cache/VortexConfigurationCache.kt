package com.vortexsoftware.android.sdk.cache

import com.vortexsoftware.android.sdk.models.WidgetConfiguration

/**
 * Thread-safe singleton cache for widget configurations.
 * This cache is shared across all views and prefetchers to ensure
 * configuration updates persist between component mount/unmount cycles.
 *
 * Benefits:
 * - Single source of truth for widget configurations
 * - Persists between modal open/close cycles
 * - Automatic synchronization between prefetch and main views
 * - Thread-safe via synchronized blocks
 */
object VortexConfigurationCache {
    
    private val cache = mutableMapOf<String, CachedConfiguration>()
    
    /**
     * Cached configuration with metadata
     */
    data class CachedConfiguration(
        val configuration: WidgetConfiguration,
        val deploymentId: String?,
        val cachedAt: Long = System.currentTimeMillis()
    )
    
    /**
     * Get a cached widget configuration by component ID.
     * @param componentId The widget component ID
     * @return The cached configuration data, or null if not found
     */
    @Synchronized
    fun get(componentId: String): CachedConfiguration? {
        return cache[componentId]
    }
    
    /**
     * Store a widget configuration in the cache.
     * @param componentId The widget component ID
     * @param configuration The widget configuration to cache
     * @param deploymentId Optional deployment ID from the API response
     */
    @Synchronized
    fun set(
        componentId: String,
        configuration: WidgetConfiguration,
        deploymentId: String? = null
    ) {
        cache[componentId] = CachedConfiguration(
            configuration = configuration,
            deploymentId = deploymentId,
            cachedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Clear cached configuration(s).
     * @param componentId Optional component ID to clear specific config. If null, clears all.
     */
    @Synchronized
    fun clear(componentId: String? = null) {
        if (componentId != null) {
            cache.remove(componentId)
        } else {
            cache.clear()
        }
    }
    
    /**
     * Check if a configuration exists in the cache.
     * @param componentId The widget component ID
     * @return True if configuration is cached, false otherwise
     */
    @Synchronized
    fun has(componentId: String): Boolean {
        return cache.containsKey(componentId)
    }
    
    /**
     * Get cache statistics for debugging.
     * @return Map with cache size and cached component IDs
     */
    @Synchronized
    fun stats(): Map<String, Any> {
        return mapOf(
            "size" to cache.size,
            "keys" to cache.keys.toList()
        )
    }
}
