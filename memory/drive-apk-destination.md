---
name: drive-apk-destination
description: Which Google Drive mount APK deliverables must go to
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 2cafdbf8-0860-4435-96ee-1c01c6a6b5ae
---

APK deliverables go to the **[user-Drive-account]** Drive, path:
`~/Library/CloudStorage/GoogleDrive-[user-Drive-account]/My Drive/Boost-v2-Analysis/`

**Why:** Two Drive mounts exist on this Mac — `GoogleDrive-[user-Drive-account]` AND
`GoogleDrive-[user-Drive-account]`, and BOTH contain a `My Drive/Boost-v2-Analysis`
folder. Tim watches the **[user-Drive-account]** one. On 2026-06-25 I copied APKs to the
[user-domain] mount and Tim couldn't see them.

**How to apply:** When `find` locates a `Boost-v2-Analysis`, do NOT just take the first hit —
explicitly target the `[user-Drive-account]` mount. Large APKs (~96 MB phone build) take a minute
to sync to the cloud after copying. Related: [[boost-wff-watchface]].
