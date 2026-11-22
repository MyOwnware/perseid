package app.myownware.perseid.core.download

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
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

internal enum class JobState {
    Queued,
    Downloading,
    Merging,
    Publishing,
    Cancelling,
    Saved,
    Failed,
    Cancelled,
    Pausing,
    Paused,
    AndroidPaused,
    Waiting,
    Retrying,
}

internal enum class DownloadError { NoSpace, Offline, Refused, Merge, Save, Unknown, Transient, Expired, CannotResume }

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
    @ColumnInfo(defaultValue = "0") val retries: Int = 0,
    val author: String? = null,
    val durationSeconds: Long? = null,
    val sizeBytes: Long? = null,
) {
    val canCancel get() = state !in setOf(JobState.Publishing, JobState.Cancelling, JobState.Saved, JobState.Cancelled)
    val active
        get() = state in ActiveStates
}

private val ActiveStates =
    setOf(
        JobState.Queued,
        JobState.Downloading,
        JobState.Merging,
        JobState.Publishing,
        JobState.Cancelling,
    )

@Dao
internal abstract class DownloadDao {
    @Query(
        """SELECT * FROM downloads WHERE state NOT IN ('Saved', 'Failed', 'Cancelled')
            OR rowid = (SELECT rowid FROM downloads WHERE state = 'Saved' ORDER BY rowid DESC LIMIT 1)
            OR state = 'Failed'
            ORDER BY state IN ('Saved', 'Failed'), rowid""",
    )
    abstract fun queue(): Flow<List<DownloadJob>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    abstract fun observe(id: String): Flow<DownloadJob?>

    @Query("SELECT * FROM downloads WHERE state = 'Saved' ORDER BY finishedAt DESC, rowid DESC")
    abstract fun library(): Flow<List<DownloadJob>>

    @Query("DELETE FROM downloads WHERE id = :id AND state = 'Saved'")
    abstract suspend fun removeSaved(id: String)

    @Query("UPDATE downloads SET sizeBytes = :bytes WHERE id = :id AND state = 'Saved' AND sizeBytes IS NULL")
    abstract suspend fun recordSavedSize(id: String, bytes: Long)

    @Insert
    abstract suspend fun insert(job: DownloadJob)

    @Query(
        "UPDATE downloads SET state = :next, speed = NULL, eta = NULL " +
            "WHERE state IN (:states) AND (:id IS NULL OR id = :id)",
    )
    abstract suspend fun transitionQueue(
        states: Set<JobState>,
        next: JobState,
        id: String? = null,
    ): Int

    @Query(
        "UPDATE downloads SET state = 'Queued', retries = 0, error = NULL, finishedAt = NULL " +
            "WHERE id = :id AND state IN ('Paused', 'Failed')",
    )
    abstract suspend fun requeue(id: String): Int

    @Query(
        "UPDATE downloads SET state = :state, error = :error, retries = :retries, " +
            "speed = NULL, eta = NULL, finishedAt = CASE WHEN :state = 'Failed' " +
            "THEN CAST(strftime('%s', 'now') AS INTEGER) * 1000 ELSE NULL END " +
            "WHERE id = :id AND state = :expected",
    )
    abstract suspend fun record(
        id: String,
        expected: JobState,
        state: JobState,
        error: DownloadError?,
        retries: Int,
    )

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

    @Query("UPDATE downloads SET state = 'Publishing' WHERE id = :id AND state IN ('Downloading', 'Merging')")
    abstract suspend fun beginPublishing(id: String): Int

    @Query("UPDATE downloads SET contentUri = :uri, mimeType = :mime WHERE id = :id AND state = 'Publishing'")
    abstract suspend fun attachExport(
        id: String,
        uri: String,
        mime: String,
    )

    @Query(
        "UPDATE downloads SET state = 'Saved', progress = 100, contentUri = :uri, mimeType = :mime, " +
            "finishedAt = :at, sizeBytes = :bytes " +
            "WHERE id = :id AND state = 'Publishing'",
    )
    abstract suspend fun saved(
        id: String,
        uri: String,
        mime: String,
        at: Long,
        bytes: Long,
    )
}

internal suspend fun DownloadDao.transition(
    id: String,
    expected: JobState,
    next: JobState,
): Int = transitionQueue(setOf(expected), next, id)

@Database(
    entities = [DownloadJob::class],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
internal abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloads(): DownloadDao
}
