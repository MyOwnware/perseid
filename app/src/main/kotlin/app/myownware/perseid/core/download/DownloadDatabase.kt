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
    Publishing,
    Cancelling,
    Saved,
    Failed,
    Cancelled,
    Pausing,
    Paused,
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
) {
    val canCancel get() = state !in setOf(JobState.Publishing, JobState.Cancelling, JobState.Saved, JobState.Cancelled)
    val active get() = state in setOf(JobState.Queued, JobState.Downloading, JobState.Publishing, JobState.Cancelling)
}

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

    @Insert
    abstract suspend fun insert(job: DownloadJob)

    @Query("UPDATE downloads SET state = :next WHERE id = :id AND state = :expected")
    abstract suspend fun transition(
        id: String,
        expected: JobState,
        next: JobState,
    ): Int

    @Query(
        "UPDATE downloads SET state = 'Queued', retries = 0, error = NULL, finishedAt = NULL " +
            "WHERE id = :id AND state IN ('Paused', 'Failed') " +
            "AND (:restart = (error IS 'CannotResume'))",
    )
    abstract suspend fun requeue(
        id: String,
        restart: Boolean,
    ): Int

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
}

@Database(
    entities = [DownloadJob::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
internal abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloads(): DownloadDao
}
