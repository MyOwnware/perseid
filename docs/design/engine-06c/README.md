# Engine update verification

These screenshots were taken on Android 14 with the minified release APK signed for local testing.

| State | French | Arabic |
| --- | --- | --- |
| Update available | [French](available-fr.png) | [Arabic](available-ar.png) |
| Downloading and verifying | [French](updating-fr.png) | [Arabic](updating-ar.png) |
| Updated | [French](updated-fr.png) | [Arabic](updated-ar.png) |
| Up to date | [French](up-to-date-fr.png) | [Arabic](up-to-date-ar.png) |
| Previous version restored | [French](restored-fr.png) | [Arabic](restored-ar.png) |
| No internet | [French](offline-fr.png) | [Arabic](offline-ar.png) |

The device test restored 2025.11.12, checked GitHub, and confirmed the signed update to 2026.10.26. It restored 2025.11.12 again and retained that selection after restarting Perseid. About showed the installed engine version on Android 16. The open-source licences screen displayed the Bouncy Castle notice on Android 14.

On a fresh Android 16 profile, Perseid used the engine bundled in the APK without a manual update. The emulator stopped during the first transfer. Perseid preserved the partial, declined an unsafe resume after reboot, and waited for **Start again**. The next run downloaded from byte zero, saved a 288,611,885-byte 1920×1080 H.264/AAC MP4, and played it in Android Gallery. The file lasted 634.624 seconds, and both tracks decoded near the end. [Test video](https://www.youtube.com/watch?v=aqz-KE-bpKQ)

The minified unsigned arm64 APK measured 24,535,941 bytes, up 480,163 bytes from the bundled-engine build. APK alignment passed. The R8 usage report showed removal of the unused OpenPGP key, signature and encrypted-data generators.
