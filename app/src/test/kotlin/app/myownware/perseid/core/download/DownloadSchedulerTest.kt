package app.myownware.perseid.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSchedulerTest {
    @Test
    fun fallsBackImmediatelyWhenTransferSchedulingFails() {
        val transfer = Scheduler(accepts = false)
        val work = Scheduler(accepts = true)

        assertTrue(FallbackDownloadScheduler(transfer, work).schedule())
        assertEquals(1, transfer.attempts)
        assertEquals(1, work.attempts)
    }

    @Test
    fun acceptedTransferDoesNotScheduleWork() {
        val transfer = Scheduler(accepts = true)
        val work = Scheduler(accepts = true)

        assertTrue(FallbackDownloadScheduler(transfer, work).schedule())
        assertEquals(0, work.attempts)
    }

    private class Scheduler(
        private val accepts: Boolean,
    ) : DownloadScheduler {
        var attempts = 0

        override fun pending(): Boolean = false

        override fun schedule(): Boolean {
            attempts++
            return accepts
        }
    }
}
