package app.myownware.perseid

import android.app.Application
import androidx.work.Configuration
import app.myownware.perseid.core.AppContainer
import app.myownware.perseid.core.download.FIRST_TRANSFER_JOB_ID

class PerseidApp :
    Application(),
    Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setJobSchedulerJobIdRange(0, FIRST_TRANSFER_JOB_ID - 1).build()

    val container by lazy { AppContainer(this) }
}
