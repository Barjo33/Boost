---
name: boost-wff-watchface
description: Standalone WFF (Watch Face Format) replica of the AAPS DigitalStyle face for Wear OS 6 watches
metadata: 
  node_type: memory
  type: project
  originSessionId: 2cafdbf8-0860-4435-96ee-1c01c6a6b5ae
---

`~/StudioProjects/BoostWFF` — a standalone Watch Face Format (WFF) project that replicates the
AndroidAPS `DigitalStyle` watch face, built for Tim's Oppo Watch X3 (Wear OS 6 / SDK 36), which
rejects AAPS's legacy `androidx.wear.watchface` runtime faces.

Key facts:
- Feasibility gate that made this possible: AAPS complications extend the **modern**
  `androidx.wear.watchface.complications.datasource.ComplicationDataSourceService` (`onComplicationRequest`),
  so a WFF face can host them. (The legacy runtime *faces* are still blocked by Wear OS 6.)
- WFF is declarative XML, no code → **cannot** draw the AAPS BG history graph. Data shows only via
  complication slots the user assigns in the watch face editor.
- Project = copy of Google's `wear-os-samples/WatchFaceFormat/SimpleDigital`, rebranded
  (`com.boost.aaps.wff`, label "Boost AAPS"), with `watchface.xml` rewritten. WFF format.version=2, compileSdk 35.
- Layout (Tim-tuned): slot0 BG hero shows BG (complication TEXT) + delta/age (its TITLE) stacked;
  slot1 IOB lower-left; slot2 **DynISF** lower-right; slot3 status/loop; slot4 top (basal/batt/date)
  + native TimeText. COB removed (not used by Boost). No standalone delta complication exists in AAPS
  — delta rides in the BG complication's title.
- **DynISF complication is a NEW AAPS-side feature** (Boost-V6 branch `Boost-V6-wear-dynisf`, commit aba8a801b1),
  display-only: `EventData.Status` gained `variableSens: String=""` (wire-compat default); DataHandlerMobile
  formats `loop.lastRun.constraintsProcessed.variableSens` (or follower APS result) in profile units;
  new `wear/.../complications/DynIsfComplication.kt` (SHORT_TEXT, title "ISF") + manifest + WearServicesModule
  Dagger binding. Needs rebuilt phone+wear APKs to work. Never touches dosing.
- Validate with `wff-validator.jar` (github.com/google/watchface releases): `java -jar wff-validator.jar 2 watchface.xml`.
  Gotcha found: every `ComplicationSlot` REQUIRES a `<BoundingShape>` child (used `<BoundingBox>`).
- **CRITICAL gotcha:** `res/xml/watch_face_info.xml` MUST contain `<Editable value="true" />` or the watch
  shows NO edit/customise button → complication slots can't be assigned → face renders time+labels only,
  all slots blank. The SimpleDigital sample omits it (no complications); the Complications sample has it.
  Validator does NOT catch this. Also: XML comments can't contain `--` (validator catches that one).
- Diagnosing on-watch: `adb shell screencap -p /sdcard/x.png && adb pull` to SEE the face. Confirmed the
  face renders (time + static IOB/ISF labels) with empty slots when complications unassigned.
- Build: `./gradlew :watchface:assembleDebug` (JAVA_HOME = Android Studio JBR). Debug-signed APK is fine
  for personal sideload. Dated APK on Drive `Boost-v2-Analysis/Boost-AAPS-WFF-watchface-<date>.apk`.
- Install: `adb install -r`, then long-press face → edit → assign AAPS complications to each slot.

## Colour/ring variants (2026-06-25/26)
- `~/StudioProjects/BoostWFFVariants` — separate project, 3 product flavors (applicationId
  `com.boost.aaps.wff.{tir,bgring,hrring}`, install alongside the clean `com.boost.aaps.wff`):
  - **Boost TIR**: BG number recoloured by Time-In-Range band (no ring, by design).
  - **Boost BG Ring**: TIR colour + edge ring that fills with BG. Ring sweep maps **40–250 mg/dl**
    (clamp in the face XML, NOT the complication) so everyday BG moves it; colour bands still use the
    true value (complication 40–350) so >250 very-high still fires.
  - **Boost HR Ring**: TIR colour + edge ring whose sweep+colour track `[HEART_RATE]` (green<100,
    amber100-140, red>140). `[HEART_RATE]`/`[ACCELEROMETER_ANGLE_X]` are global scene sources (no slot).
- TIR bands (mg/dl): >250 red `#ff5252` · >180 amber `#ffffc233` · 70–180 green `#ff41c97b` ·
  54–69 orange `#ffff9f45` · <54 dark-red `#ffc62828` (matches BgBobbleView scheme).
- **Needs a numeric BG**: SHORT_TEXT complications give WFF only a string. Added wear
  `SgvRangedComplication` (RANGED_VALUE, value=sgv mgdl 40–350, +text +delta title) — commit on
  `Boost-V6-wear-dynisf`. Faces point BG slot at "Blood Glucose (ranged)".
- WFF data-driven arc pattern: `<Arc startAngle endAngle><Stroke/><Transform target="endAngle"
  value="-150 + (clamp(val,lo,hi)-lo)/(hi-lo)*300"/></Arc>`. Full-face background ComplicationSlot
  (x0 y0 450x450) lets the ring read `[COMPLICATION.RANGED_VALUE_VALUE]`; other slots overlay on top.
- All three validate (wff-validator v2), debug-signed, on street.tj Drive (Boost-Face-{TIR,BGRing,HRRing}).

## TIR pie ring on the TIR face (2026-06-26)
- TIR face now also has an **edge donut ring = the last-24h Time-In-Range distribution** (a pie).
  BG number still colours by **current BG** (independent of the pie) — two separate complications.
- New wear `TirWeightedComplication` (WEIGHTED_ELEMENTS) parses `EventData.Status.tirWeights`
  ("vlow,low,inrange,high,vhigh" %) into 5 weighted+coloured elements (same TIR band colours;
  grey single-element when no data). DataHandlerMobile buckets last-24h BG via
  `persistenceLayer.getBgReadingsDataFromTimeToTime(now-24h,now,true)` into fixed AGP bands
  (54/70/180/250 mgdl) → percentages. `tirWeights` field added to EventData.Status (default "").
- WFF pie syntax: `<Arc startAngle="-90" endAngle="270"><WeightedStroke colors="[COMPLICATION.WEIGHTED_ELEMENTS_COLORS]"
  weights="[COMPLICATION.WEIGHTED_ELEMENTS_WEIGHTS]" thickness=.../></Arc>` inside a full-face
  WEIGHTED_ELEMENTS slot (colours+weights both come FROM the complication). WeightedElementsComplicationData
  .Element(weight: Float, color: Int); Builder(List<Element>, ComplicationText).
- Wear commit on `Boost-V6-wear-dynisf`: 56609e5853 (TIR), plus earlier DynISF/ranged commits.

## CURRENT STATE (2026-06-26) — what's where
- **AAPS branch `Boost-V6-wear-dynisf`** (NOT pushed): 3 display-only complications (DynIsf, SgvRanged,
  TirWeighted) + EventData.Status fields `variableSens`+`tirWeights` computed in DataHandlerMobile.
  Dosing path untouched. Compiles + release-builds clean (KSP `:app:kspFullReleaseKotlin` flakes with
  PROCESSING_ERROR ~1st try — just re-run assemble).
- **Installed on the Oppo Watch X3**: wear app (TIRpie build), faces TIR (with pie ring) / BGRing / HRRing,
  + the original clean `com.boost.aaps.wff` (untouched). All confirmed working on-wrist: TIR colour ✓,
  BG ring ✓ (40-250), HR ring ✓.
- **PENDING — phone**: `Boost-V6-PHONE-TIRpie-2026-06-26.apk` on street.tj Drive must be installed
  **on the PHONE** (NOT the watch — same package `info.nightscout.androidaps`; pushing the phone APK to
  the watch is WRONG, was caught/stopped once). The phone APK is what actually sends variableSens +
  tirWeights; until it's on the phone, DynISF shows `--` and the TIR pie shows solid grey.
- Reinstalling a face APK **resets its complication slot assignments** → must re-Customise.
- adb-over-wifi to the Oppo X3 is VERY flaky: connect port rotates constantly, drops mid-query, and the
  number under "Pair new device" is NOT the connect port (use the IP:port at the TOP of the Wireless
  debugging screen). Ask Tim for a fresh port each time. To screenshot: WAKEUP, then it may show the app
  launcher — use a short POWER press to get back to the face.

Related: [[boost_v4_port_complete]], [[drive-apk-destination]].
