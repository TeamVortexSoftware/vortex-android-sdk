package com.vortexsoftware.android.sdk.cache

import com.vortexsoftware.android.sdk.api.dto.OutgoingInvitation
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
    
    /// Cached outgoing invitations keyed by JWT hash
    private val outgoingInvitationsCache = mutableMapOf<String, List<OutgoingInvitation>>()
    
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
    
    // ========================================================================
    // Outgoing Invitations Cache
    // ========================================================================
    
    /**
     * Get cached outgoing invitations.
     * @param jwt JWT token (used as cache key via hash)
     * @return Cached outgoing invitations, or null if not cached
     */
    @Synchronized
    fun getOutgoingInvitations(jwt: String): List<OutgoingInvitation>? {
        return outgoingInvitationsCache[jwt.hashCode().toString()]
    }
    
    /**
     * Store outgoing invitations in the cache.
     * @param jwt JWT token (used as cache key via hash)
     * @param invitations The outgoing invitations to cache
     */
    @Synchronized
    fun setOutgoingInvitations(jwt: String, invitations: List<OutgoingInvitation>) {
        outgoingInvitationsCache[jwt.hashCode().toString()] = invitations
    }
    
    /**
     * Clear cached outgoing invitations.
     * @param jwt Optional JWT to clear specific cache. If null, clears all.
     */
    @Synchronized
    fun clearOutgoingInvitations(jwt: String? = null) {
        if (jwt != null) {
            outgoingInvitationsCache.remove(jwt.hashCode().toString())
        } else {
            outgoingInvitationsCache.clear()
        }
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
