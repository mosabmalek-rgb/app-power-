package com.example

import com.example.data.validatePage
import com.example.data.api.MobileReadingCycleBoundsDto
import com.example.data.api.MobileSubscriberDeltaResponse
import com.example.data.api.MobileSubscriberDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SubscriberDeltaContractTest {
    private fun page(
        mode: String = "bootstrap",
        branch: Long = 7,
        nextCursor: String = "signed-cursor",
        hasMore: Boolean = false,
        upserts: List<MobileSubscriberDto> = emptyList(),
        deletes: List<Long> = emptyList()
    ) = MobileSubscriberDeltaResponse(
        tenantId=1,userId=2,branchId=branch,mode=mode,scopeVersion="a".repeat(64),
        currentCycle=MobileReadingCycleBoundsDto("cycle-1","2026-11-01T00:00:00Z",
          "2026-12-01T00:00:00Z","November"),
        upserts=upserts,deletes=deletes,nextCursor=nextCursor,hasMore=hasMore,
        requiresBootstrap=false,serverTime="2026-11-23T00:00:00Z",
        transferredCount=upserts.size+deletes.size
    )
    @Test fun validInitialBootstrap() {
        validatePage(page(),1,2,7,null,true,null,null)
    }
    @Test fun rejectsForeignBranch() {
        assertThrows(IllegalArgumentException::class.java) {
            validatePage(page(branch=8),1,2,7,null,true,null,null)
        }
    }
    @Test fun rejectsForeignSubscriber() {
        val sub=MobileSubscriberDto(5,"S-5","A","123",null,9,null,null)
        assertThrows(IllegalArgumentException::class.java) {
            validatePage(page(upserts=listOf(sub)),1,2,7,null,true,null,null)
        }
    }
    @Test fun rejectsConflictingDelete() {
        val sub=MobileSubscriberDto(5,"S-5","A","123",null,7,null,null)
        assertThrows(IllegalArgumentException::class.java) {
            validatePage(page(upserts=listOf(sub),deletes=listOf(5)),1,2,7,null,true,null,null)
        }
    }
    @Test fun rejectsStalledCursor() {
        assertThrows(IllegalArgumentException::class.java) {
            validatePage(page(mode="delta",nextCursor="same",hasMore=true),
                1,2,7,"same",false,"a".repeat(64),"cycle-1")
        }
    }
    @Test fun acceptsDeltaWithStableScope() {
        validatePage(page(mode="delta"),1,2,7,"old",false,"a".repeat(64),"cycle-1")
        assertEquals(0,page().transferredCount)
    }
    @Test fun rejectsTenantAndUserMismatch() {
        assertThrows(IllegalArgumentException::class.java) {
            validatePage(page(), 99, 2, 7, null, true, null, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validatePage(page(), 1, 99, 7, null, true, null, null)
        }
    }

    @Test fun rejectsDuplicateUpsertsAndDeletes() {
        val subscriber = MobileSubscriberDto(5, "S5", "reader", "123", null, 7, null, null)
        assertThrows(IllegalArgumentException::class.java) {
            validatePage(page(upserts=listOf(subscriber, subscriber)),
                1, 2, 7, null, true, null, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validatePage(page(deletes=listOf(5, 5)),
                1, 2, 7, null, true, null, null)
        }
    }

    @Test fun rejectsScopeOrCycleChangeAcrossPages() {
        assertThrows(IllegalArgumentException::class.java) {
            validatePage(page(mode="delta"), 1, 2, 7,
                "previous", false, "b".repeat(64), "cycle-1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            validatePage(page(mode="delta"), 1, 2, 7,
                "previous", false, "a".repeat(64), "older-cycle")
        }
    }

    @Test fun rejectsWrongPageModeForInitialBootstrap() {
        assertThrows(IllegalArgumentException::class.java) {
            validatePage(page(mode="delta"), 1, 2, 7,
                null, true, null, null)
        }
    }

}
