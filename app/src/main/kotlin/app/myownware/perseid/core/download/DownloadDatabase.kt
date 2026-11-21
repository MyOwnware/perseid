package app.myownware.perseid.core.download

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import app.myownware.perseid.core.engine.MediaType
import app.myownware.perseid.core.engine.TrackMerge
import kotlinx.coroutines.flow.Flow

internal enum class JobState { Queued, Downloading, Publishing, Cancelling, Saved, Failed, Cancelled, Paused, Waiting }

internal enum class DownloadError { NoSpace, Offline, Refused, Merge, Save, Unknown }

@Entity(tableName = "downloads")
internal data class DownloadJob(
    @PrimaryKey val id: String,
    val url: String,
    val type: MediaType,
    val formatIds: String,
    val title: String,
    val thumbnailPath: String?,
    val stagingPath: String,
    val merge: TrackMerge?,
    val createdAt: Long,
    val state: JobState = JobState.Queued,
    val progress: Int = 0,
    val speed: Long? = null,
    val eta: Long? = null,
    val contentUri: String? = null,
    val mimeType: String? = null,
    val finishedAt: Long? = null,
    val error: DownloadError? = null,
) {
    val canCancel get() = state == JobState.Queued || state == JobState.Downloading
    val active get() = state in setOf(JobState.Queued, JobState.Downloading, JobState.Publishing, JobState.Cancelling)
}

@Dao
internal abstract class DownloadDao {
    @Query(
        """SELECT * FROM downloads WHERE state NOT IN ('Saved', 'Failed', 'Cancelled')
            OR rowid = (SELECT rowid FROM downloads WHERE state IN ('Saved', 'Failed') ORDER BY rowid DESC LIMIT 1)
            ORDER BY rowid""",
    )
    abstract fun queue(): Flow<List<DownloadJob>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    abstract fun observe(id: String): Flow<DownloadJob?>

    @Insert
    abstract suspend fun insert(job: DownloadJob)

    @Query("UPDATE downloads SET state = :next WHERE id = :id AND state = :expected")
    abstract suspend fun transition(
        id: String,
        expected: JobState,
        next: JobState,
    ): Int

    @Query("UPDATE downloads SET state = 'Cancelling' WHERE id = :id AND state IN ('Queued', 'Downloading')")
    abstract suspend fun cancel(id: String): Int

    @Query(
        "UPDATE downloads SET progress = :progress, speed = :speed, eta = :eta " +
            "WHERE id = :id AND state = 'Downloading'",
    )
    abstract suspend fun progress(
        id: String,
        progress: Int,
        speed: Long?,
        eta: Long?,
    )

    @Query(
        "UPDATE downloads SET state = 'Saved', progress = 100, contentUri = :uri, mimeType = :mime, finishedAt = :at " +
            "WHERE id = :id AND state = 'Publishing'",
    )
    abstract suspend fun saved(
        id: String,
        uri: String,
        mime: String,
        at: Long,
    )

    @Query(
        "UPDATE downloads SET state = :state, error = :error, finishedAt = :at " +
            "WHERE id = :id AND (state IN ('Queued', 'Downloading', 'Publishing') " +
            "OR (state = 'Cancelling' AND :state = 'Cancelled'))",
    )
    abstract suspend fun finish(
        id: String,
        state: JobState,
        error: DownloadError?,
        at: Long?,
    )
}

@Database(entities = [DownloadJob::class], version = 1)
internal abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloads(): DownloadDao
}
