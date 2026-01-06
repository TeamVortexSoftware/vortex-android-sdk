package com.vortexsoftware.android.sdk.prefetch

import com.vortexsoftware.android.sdk.api.VortexClient
import com.vortexsoftware.android.sdk.cache.VortexConfigurationCache
import com.vortexsoftware.android.sdk.models.WidgetConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Prefetches widget configuration for instant rendering.
 * Use this class to fetch the configuration early (e.g., when JWT becomes available)
 * so the VortexInviteView can render immediately without showing a loading spinner.
 *
 * The prefetcher stores the configuration in the shared cache, which is automatically
 * used by VortexInviteView. The view will still refresh in the background when it mounts
 * to ensure the configuration is up-to-date (stale-while-revalidate pattern).
 *
 * ## Example Usage
 *
 * ```kotlin
 * // In your Activity/Fragment/ViewModel where JWT becomes available
 * val prefetcher = VortexConfigurationPrefetcher(
 *     componentId = "your-component-id"
 * )
 *
 * // Start prefetching when JWT is ready
 * lifecycleScope.launch {
 *     prefetcher.prefetch(jwt)
 * }
 *
 * // Later, when showing VortexInviteView, it will use the cached configuration
 * VortexInviteView(
 *     componentId = "your-component-id",
 *     jwt = jwt,
 *     // ... other parameters
 * )
 * ```
 */
class VortexConfigurationPrefetcher(
    val componentId: String,
    private val apiBaseUrl: String = VortexClient.DEFAULT_BASE_URL,
    private val enableLogging: Boolean = false
) {
    private val client = VortexClient(
        baseUrl = apiBaseUrl,
        jwt = null,
        enableLogging = enableLogging
    )
    
    private val _isLoading = MutableStateFlow(false)
    /** Current prefetch loading state */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<Throwable?>(null)
    /** Error that occurred during prefetch, if any */
    val error: StateFlow<Throwable?> = _error.asStateFlow()
    
    private val _isPrefetched = MutableStateFlow(false)
    /** Whether configuration has been successfully prefetched */
    val isPrefetched: StateFlow<Boolean> = _isPrefetched.asStateFlow()
    
    /**
     * The prefetched widget configuration (read from cache)
     */
    val widgetConfiguration: WidgetConfiguration?
        get() = VortexConfigurationCache.get(componentId)?.configuration
    
    /**
     * The prefetched deployment ID (read from cache)
     */
    val deploymentId: String?
        get() = VortexConfigurationCache.get(componentId)?.deploymentId
    
    /**
     * Prefetch the widget configuration.
     * @param jwt JWT authentication token
     * @return The prefetched configuration, or null if prefetch failed
     */
    suspend fun prefetch(jwt: String): WidgetConfiguration? {
        // Check if already cached
        VortexConfigurationCache.get(componentId)?.let { cached ->
            _isPrefetched.value = true
            return cached.configuration
        }
        
        _isLoading.value = true
        _error.value = null
        
        return try {
            val clientWithJwt = VortexClient(
                baseUrl = apiBaseUrl,
                jwt = jwt,
                enableLogging = enableLogging
            )
            
            val result = clientWithJwt.getWidgetConfiguration(componentId)
            
            result.fold(
                onSuccess = { response ->
                    val config = WidgetConfiguration.fromDTO(response.data)
                    val deploymentId = response.data.deploymentId
                    
                    // Store in shared cache
                    VortexConfigurationCache.set(
                        componentId = componentId,
                        configuration = config,
                        deploymentId = deploymentId
                    )
                    
                    _isPrefetched.value = true
                    _isLoading.value = false
                    config
                },
                onFailure = { e ->
                    _error.value = e
                    _isLoading.value = false
                    null
                }
            )
        } catch (e: Exception) {
            _error.value = e
            _isLoading.value = false
            null
        }
    }
    
    /**
     * Clear the prefetched configuration from cache
     */
    fun clearCache() {
        VortexConfigurationCache.clear(componentId)
        _isPrefetched.value = false
    }
}
