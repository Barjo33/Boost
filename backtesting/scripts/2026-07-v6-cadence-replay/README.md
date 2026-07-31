# V6 engine cadence replay

Drives the real `DetermineBasalBoostV5.decide()` on the JVM over the same glucose record at
two cadences, and compares what the engine asks for.

The engine is pure Kotlin, importing only `kotlin.math` and the Dagger annotations, so it
compiles and runs off Android with two annotation stubs. No behaviour is reimplemented. The
delta windows in the harness mirror `DeltaCalculator.kt`.

## Running

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
kotlinc src/stub/inject.kt src/engine/*.kt src/Harness.kt -include-runtime -d replay.jar
python3 01_extract.py
java -jar replay.jar replay_input.csv out_1min.csv 1
java -jar replay.jar replay_input.csv out_5min.csv 5
python3 02_compare.py
```

`src/engine/` is a copy of the shipped V5 package taken from this branch. Re-copy it after any
engine change, or the replay will silently test stale code.

## What this is and is not

It is an open-loop replay. IOB comes from the recorded trajectory rather than from the doses
the harness recommends, because without a glucodynamic model glucose cannot respond to a
changed dose. It therefore answers what the engine RECOMMENDS given identical state, not what
would have happened to the person.

`baseInsulinReq` is a simplified projection rather than the full Boost oref sensitivity chain.
Both arms compute it identically, so the contrast is fair, but the absolute units per day are
not this user's real dosing.
