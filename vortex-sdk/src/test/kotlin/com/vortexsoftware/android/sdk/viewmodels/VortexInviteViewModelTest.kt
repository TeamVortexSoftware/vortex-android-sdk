package com.vortexsoftware.android.sdk.viewmodels

import app.cash.turbine.test
import com.vortexsoftware.android.sdk.api.VortexClient
import com.vortexsoftware.android.sdk.api.dto.InvitationTarget
import com.vortexsoftware.android.sdk.api.dto.OutgoingInvitation
import com.vortexsoftware.android.sdk.cache.VortexConfigurationCache
import com.vortexsoftware.android.sdk.models.OutgoingInvitationItem
import com.vortexsoftware.android.sdk.models.OutgoingInvitationsConfig
import io.mockk.coEvery
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VortexInviteViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Intercept every VortexClient instance the ViewModel constructs so tests
        // don't touch the network.
        mockkConstructor(VortexClient::class)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
        // The ViewModel writes into VortexConfigurationCache on successful fetches;
        // clear both halves so state doesn't leak between tests.
        VortexConfigurationCache.clear()
        VortexConfigurationCache.clearOutgoingInvitations()
    }

    // region Helpers

    private fun buildViewModel(
        jwt: String? = "test-jwt",
        outgoingInvitationsConfig: OutgoingInvitationsConfig? = null,
    ): VortexInviteViewModel = VortexInviteViewModel(
        componentId = "test-component",
        jwt = jwt,
        apiBaseUrl = "https://example.invalid",
        analyticsBaseUrl = null,
        group = null,
        segmentation = null,
        googleClientId = null,
        onDismiss = null,
        onEvent = null,
        outgoingInvitationsConfig = outgoingInvitationsConfig,
    )

    private fun stubOutgoingInvitations(invitations: List<OutgoingInvitation>) {
        coEvery {
            anyConstructed<VortexClient>().getOutgoingInvitations()
        } returns Result.success(invitations)
    }

    private fun stubOutgoingInvitationsFailure(error: Throwable = RuntimeException("boom")) {
        coEvery {
            anyConstructed<VortexClient>().getOutgoingInvitations()
        } returns Result.failure(error)
    }

    private fun invitation(
        id: String,
        vararg targets: InvitationTarget,
    ): OutgoingInvitation = OutgoingInvitation(id = id, targets = targets.toList())

    private fun internalTarget(value: String) =
        InvitationTarget(targetType = "internal", targetValue = value)

    private fun internalIdTarget(value: String) =
        InvitationTarget(targetType = "internalId", targetValue = value)

    private fun emailTarget(value: String) =
        InvitationTarget(targetType = "email", targetValue = value)

    private fun smsTarget(value: String) =
        InvitationTarget(targetType = "sms", targetValue = value)

    private fun shareTarget(value: String) =
        InvitationTarget(targetType = "share", targetValue = value)

    // endregion

    // region updateOutgoingInvitationUserIds

    // DEV-2417 regression: when the backend returns `[email, internal]`, the previous
    // `firstOrNull()` implementation would cache the email string instead of the
    // bffUUID, causing the contact to reappear in FindFriendsView.
    @Test
    fun `extracts internal target value when email target is first (DEV-2417)`() = runTest {
        stubOutgoingInvitations(
            listOf(
                invitation(
                    id = "inv-1",
                    emailTarget("alice@example.com"),
                    internalTarget("bff-uuid-alice"),
                ),
            ),
        )

        val vm = buildViewModel()
        vm.fetchOutgoingInvitations()

        assertEquals(setOf("bff-uuid-alice"), vm.outgoingInvitationUserIds.value)
    }

    @Test
    fun `extracts internal target value when internal target is first`() = runTest {
        stubOutgoingInvitations(
            listOf(
                invitation(
                    id = "inv-1",
                    internalTarget("bff-uuid-alice"),
                    emailTarget("alice@example.com"),
                ),
            ),
        )

        val vm = buildViewModel()
        vm.fetchOutgoingInvitations()

        assertEquals(setOf("bff-uuid-alice"), vm.outgoingInvitationUserIds.value)
    }

    @Test
    fun `extracts internalId target value (alternative target type naming)`() = runTest {
        stubOutgoingInvitations(
            listOf(
                invitation(
                    id = "inv-1",
                    emailTarget("alice@example.com"),
                    internalIdTarget("bff-uuid-alice"),
                ),
            ),
        )

        val vm = buildViewModel()
        vm.fetchOutgoingInvitations()

        assertEquals(setOf("bff-uuid-alice"), vm.outgoingInvitationUserIds.value)
    }

    @Test
    fun `email-only invitation contributes no id (cannot match FindFriendsContact)`() = runTest {
        stubOutgoingInvitations(
            listOf(invitation(id = "inv-1", emailTarget("alice@example.com"))),
        )

        val vm = buildViewModel()
        vm.fetchOutgoingInvitations()

        assertTrue(vm.outgoingInvitationUserIds.value.isEmpty())
    }

    @Test
    fun `sms and share targets are ignored`() = runTest {
        stubOutgoingInvitations(
            listOf(
                invitation(id = "inv-sms", smsTarget("+1555")),
                invitation(id = "inv-share", shareTarget("https://vortex/share/x")),
            ),
        )

        val vm = buildViewModel()
        vm.fetchOutgoingInvitations()

        assertTrue(vm.outgoingInvitationUserIds.value.isEmpty())
    }

    @Test
    fun `null targets list does not crash and contributes nothing`() = runTest {
        stubOutgoingInvitations(
            listOf(OutgoingInvitation(id = "inv-1", targets = null)),
        )

        val vm = buildViewModel()
        vm.fetchOutgoingInvitations()

        assertTrue(vm.outgoingInvitationUserIds.value.isEmpty())
    }

    @Test
    fun `multiple invitations with mixed target orders all resolve to internal ids`() = runTest {
        stubOutgoingInvitations(
            listOf(
                invitation("inv-1", emailTarget("a@x.com"), internalTarget("bff-a")),
                invitation("inv-2", internalTarget("bff-b"), emailTarget("b@x.com")),
                invitation("inv-3", emailTarget("c@x.com"), internalIdTarget("bff-c")),
            ),
        )

        val vm = buildViewModel()
        vm.fetchOutgoingInvitations()

        assertEquals(
            setOf("bff-a", "bff-b", "bff-c"),
            vm.outgoingInvitationUserIds.value,
        )
    }

    @Test
    fun `merges internal config invitations with fetched invitations`() = runTest {
        stubOutgoingInvitations(
            listOf(
                invitation("inv-api", emailTarget("api@x.com"), internalTarget("bff-api")),
            ),
        )
        val config = OutgoingInvitationsConfig(
            internalInvitations = listOf(
                OutgoingInvitationItem(id = "bff-local", name = "Local"),
            ),
        )

        val vm = buildViewModel(outgoingInvitationsConfig = config)
        vm.fetchOutgoingInvitations()

        assertEquals(
            setOf("bff-local", "bff-api"),
            vm.outgoingInvitationUserIds.value,
        )
    }

    @Test
    fun `fetch failure still flips loaded flag and leaves ids empty`() = runTest {
        stubOutgoingInvitationsFailure()

        val vm = buildViewModel()
        vm.outgoingInvitationUserIds.test {
            assertTrue(awaitItem().isEmpty())

            vm.fetchOutgoingInvitations()

            // No new emission is expected on failure — the failure branch doesn't
            // touch the ids set. Assert we still see an empty set and the loaded
            // flag has flipped so the UI shimmer can clear.
            expectNoEvents()
            assertTrue(vm.isOutgoingInvitationsLoaded.value)
        }
    }

    // endregion
}
