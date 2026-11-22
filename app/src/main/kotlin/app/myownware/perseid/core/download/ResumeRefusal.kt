package app.myownware.perseid.core.download

import app.myownware.perseid.BuildConfig
import java.util.logging.Logger

internal enum class ResumeRefusal {
    MissingCheckpoint,
    SourceIdentityChanged,
    PartialExceedsCheckpoint,
    SourceLengthChanged,
    PrefixRangeTruncated,
    ValidatorChanged,
    UnexpectedEncoding,
    InvalidContentRange,
    UnexpectedRangeBounds,
    PrefixMismatch,
    RangeIgnored,
    RangeUnsatisfiable,
    FragmentedTrack,
    FormatUnavailable,
    PartialFileMissing,
}

internal fun cannotResume(
    reason: ResumeRefusal,
    cause: Throwable? = null,
): Nothing {
    if (BuildConfig.DEBUG) Logger.getLogger("PerseidResume").warning(reason.name)
    throw DownloadFailure(DownloadError.CannotResume, cause)
}
