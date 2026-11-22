# Privacy

Perseid has no accounts, ads, analytics, telemetry, crash reporting, tracking SDKs or Google Play Services. It has no server of its own.

Tap Paste to let Perseid read the current clipboard text. You can also enter a link yourself or send one through Android's share sheet. The app requests a preview when you tap Preview or Paste, submit the field with the keyboard, or share a link to Perseid. It contacts the source site and the services that supply metadata and the thumbnail. Those services can see your IP address and the requests they receive.

Preview links and images are held in app memory. The app does not write links or titles to release logs. It prepares the bundled engine in private on-device storage the first time you request a preview. Private app data is excluded from Android backup and device transfer.

When you tap Download, Perseid reconnects to the source and downloads the selected tracks. It stores the link you entered, format IDs, title, thumbnail, progress and file locations in a local database. Temporary files stay in private app storage. Perseid does not keep the temporary media URLs returned by the source. Finished files are saved in Download/Perseid/ and can be opened or shared at your request. Android may retain those files after you uninstall Perseid.

Android's foreground-service and data-sync permissions let the download run while you leave the app. WorkManager uses network-state checks and a wake lock to run the transfer and registers app components for scheduling, including after device restarts. Perseid asks for notification permission on Android 13 and later so you can see progress, cancel a transfer or open a saved file from the notification. If you decline, progress still appears in the app. Perseid needs no broad storage permission.

The engine update is the only network activity outside media previews and downloads. When you tap **Update download engine**, Perseid asks the GitHub API for the latest stable yt-dlp release. If you confirm an update, it retrieves the engine, checksums and signature from GitHub and its release asset hosts. GitHub can see your IP address and these requests. Perseid sends no media links or download history with them. Restoring the previous engine happens on the device. Update requests stop when Perseid leaves the foreground.
