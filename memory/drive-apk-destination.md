---
name: drive-apk-destination
description: Which Google Drive mount APK deliverables must go to
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 2cafdbf8-0860-4435-96ee-1c01c6a6b5ae
---

APK deliverables go to the **street.tj@gmail.com** Drive, path:
`~/Library/CloudStorage/GoogleDrive-street.tj@gmail.com/My Drive/Boost-v2-Analysis/`

**Why:** Two Drive mounts exist on this Mac — `GoogleDrive-street.tj@gmail.com` AND
`GoogleDrive-tim.street@liveintheirshoes.com`, and BOTH contain a `My Drive/Boost-v2-Analysis`
folder. Tim watches the **street.tj@gmail.com** one. On 2026-06-25 I copied APKs to the
liveintheirshoes mount and Tim couldn't see them.

**How to apply:** When `find` locates a `Boost-v2-Analysis`, do NOT just take the first hit —
explicitly target the `street.tj@gmail.com` mount. Large APKs (~96 MB phone build) take a minute
to sync to the cloud after copying. Related: [[boost-wff-watchface]].
