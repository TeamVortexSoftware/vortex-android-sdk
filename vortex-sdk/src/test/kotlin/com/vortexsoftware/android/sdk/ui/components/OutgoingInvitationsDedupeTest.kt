package com.vortexsoftware.android.sdk.ui.components

import com.vortexsoftware.android.sdk.api.dto.InvitationTarget
import com.vortexsoftware.android.sdk.api.dto.OutgoingInvitation
import com.vortexsoftware.android.sdk.models.OutgoingInvitationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutgoingInvitationsDedupeTest {

    // region Helpers

    private fun apiItem(
        invitationId: String,
        name: String,
        vararg targets: InvitationTarget,
    ): OutgoingInvitationItem {
        val invitation = OutgoingInvitation(id = invitationId, targets = targets.toList())
        return OutgoingInvitationItem(
            id = invitationId,
            name = name,
            isVortexInvitation = true,
            invitation = invitation,
        )
    }

    private fun internalItem(userId: String, name: String): OutgoingInvitationItem =
        OutgoingInvitationItem(
            id = userId,
            name = name,
            isVortexInvitation = false,
            invitation = null,
        )

    private fun internalTarget(value: String) =
        InvitationTarget(targetType = "internal", targetValue = value)

    private fun internalIdTarget(value: String) =
        InvitationTarget(targetType = "internalId", targetValue = value)

    private fun emailTarget(value: String) =
        InvitationTarget(targetType = "email", targetValue = value)

    // endregion

    // DEV-2418 regression: apiItems.id is the invitation UUID, internalItems.id is the
    // invitee bffUUID. The previous `apiIds.contains(internal.id)` check never matched,
    // so both rows for the same invitee got shown.
    @Test
    fun `removes internal row when matching api invitation exists for same invitee`() {
        val api = apiItem(
            invitationId = "inv-uuid-1",
            name = "Alice (API)",
            internalTarget("bff-alice"),
            emailTarget("alice@example.com"),
        )
        val internal = internalItem(userId = "bff-alice", name = "Alice (local)")

        val result = dedupeOutgoingInvitations(listOf(api), listOf(internal))

        assertEquals(listOf(api), result)
    }

    @Test
    fun `matches internalId target type as well`() {
        val api = apiItem(
            invitationId = "inv-uuid-1",
            name = "Alice (API)",
            emailTarget("alice@example.com"),
            internalIdTarget("bff-alice"),
        )
        val internal = internalItem(userId = "bff-alice", name = "Alice (local)")

        val result = dedupeOutgoingInvitations(listOf(api), listOf(internal))

        assertEquals(listOf(api), result)
    }

    // DEV-2417 overlap: even if the email target comes first, the internal
    // target is found and the dedupe still matches. Guards against the same
    // non-deterministic target-order bug biting here.
    @Test
    fun `dedupes correctly when backend puts email target before internal target`() {
        val api = apiItem(
            invitationId = "inv-uuid-1",
            name = "Alice (API)",
            emailTarget("alice@example.com"),
            internalTarget("bff-alice"),
        )
        val internal = internalItem(userId = "bff-alice", name = "Alice (local)")

        val result = dedupeOutgoingInvitations(listOf(api), listOf(internal))

        assertEquals(listOf(api), result)
    }

    @Test
    fun `keeps internal row when no api invitation targets the same user`() {
        val api = apiItem(
            invitationId = "inv-uuid-1",
            name = "Alice (API)",
            internalTarget("bff-alice"),
        )
        val internal = internalItem(userId = "bff-bob", name = "Bob (local)")

        val result = dedupeOutgoingInvitations(listOf(api), listOf(internal))

        assertEquals(listOf(api, internal), result)
    }

    @Test
    fun `keeps internal row when api invitation has no internal target`() {
        val api = apiItem(
            invitationId = "inv-uuid-1",
            name = "someone@example.com",
            emailTarget("someone@example.com"),
        )
        val internal = internalItem(userId = "bff-alice", name = "Alice (local)")

        val result = dedupeOutgoingInvitations(listOf(api), listOf(internal))

        assertEquals(listOf(api, internal), result)
    }

    @Test
    fun `api items always come first and internal items are appended after dedupe`() {
        val apiA = apiItem("inv-1", "Alice (API)", internalTarget("bff-alice"))
        val apiB = apiItem("inv-2", "Bob (API)", internalTarget("bff-bob"))
        val internalC = internalItem("bff-carol", "Carol (local)")
        val internalA = internalItem("bff-alice", "Alice (local)") // duplicate — drops

        val result = dedupeOutgoingInvitations(
            listOf(apiA, apiB),
            listOf(internalC, internalA),
        )

        assertEquals(listOf(apiA, apiB, internalC), result)
    }

    @Test
    fun `empty api list returns all internal items`() {
        val internalA = internalItem("bff-alice", "Alice (local)")
        val internalB = internalItem("bff-bob", "Bob (local)")

        val result = dedupeOutgoingInvitations(emptyList(), listOf(internalA, internalB))

        assertEquals(listOf(internalA, internalB), result)
    }

    @Test
    fun `empty internal list returns api items unchanged`() {
        val api = apiItem("inv-1", "Alice (API)", internalTarget("bff-alice"))

        val result = dedupeOutgoingInvitations(listOf(api), emptyList())

        assertEquals(listOf(api), result)
    }

    @Test
    fun `both empty returns empty`() {
        assertTrue(dedupeOutgoingInvitations(emptyList(), emptyList()).isEmpty())
    }
}
