package app.myownware.perseid.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueOutcomeTest {
    @Test
    fun offlineTimeDoesNotUseARetry() {
        val outcome = recoveryOutcome(DownloadError.Offline, connected = false, retries = 2)
        assertEquals(QueueOutcome(JobState.Waiting, 2), outcome)
        assertTrue(outcome.retry)
    }

    @Test
    fun repeatedTransientFailuresEventuallyNeedUserAction() {
        assertEquals(QueueOutcome(JobState.Retrying, 3), recoveryOutcome(DownloadError.Transient, true, 2))
        val exhausted = recoveryOutcome(DownloadError.Transient, true, 3)
        assertEquals(QueueOutcome(JobState.Failed, 3, DownloadError.CannotResume), exhausted)
        assertFalse(exhausted.retry)
    }

    @Test
    fun anExpiredStreamStopsRetryingAtTheLimit() {
        assertEquals(QueueOutcome(JobState.Retrying, 3), recoveryOutcome(DownloadError.Expired, true, 2))
        assertEquals(
            QueueOutcome(JobState.Failed, 3, DownloadError.CannotResume),
            recoveryOutcome(DownloadError.Expired, true, 3),
        )
    }

    @Test
    fun pausedAndUnsafeJobsRequireAUserDecision() {
        assertFalse(JobState.Paused in RecoverableStates)
        assertTrue(JobState.Publishing in RecoverableStates)
        val unsafe = recoveryOutcome(DownloadError.CannotResume, true, 0)
        assertEquals(QueueOutcome(JobState.Failed, 0, DownloadError.CannotResume), unsafe)
    }
}
