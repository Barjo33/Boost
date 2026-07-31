# Closed-loop cadence simulation

Goal: run the real Boost engine against a simulator whose CGM cadence can be switched between
one and five minutes, so that the dose feeds back into glucose and the open-loop caveat of the
replay study is removed.

## What is built and working

`src/EngineServer.kt` wraps the shipped `DetermineBasalBoostV5.decide()` as a per-cycle
server: one JSON object in on stdin, one out on stdout, persisted state carried between calls.
The V5 package is pure Kotlin, so this is the genuine engine rather than a port. That removes
the blocker recorded in `2026-07-insilico/README.md`, which said a Python port would first have
to pass a fidelity gate against logged doses. There is no port to validate.

```
kotlinc src/stub/inject.kt src/engine/*.kt src/EngineServer.kt -include-runtime -d engine_server.jar
echo '{"bg":150,"delta":8,"shortAvgDelta":5,"deltaAccl":60,"eventualBg":190,"iob":0.5,"baseInsulinReq":0.9,"hour":12}' | java -jar engine_server.jar
```

`sensor.py` is a configurable sensor chain: interstitial lag, optional internal filter, AR(2)
noise, quantisation to whole mg/dl, and a switchable reporting interval.

## What the fidelity gate found, and why the plan changed

`01_sensor_fidelity.py` compares the variogram of the simulated feed against the real record.
The simulator is far smoother than reality at every timescale:

| | D(1 min) | D(5 min) | D(30 min) | slope 5 to 20 | slope 20 to 60 |
|---|---|---|---|---|---|
| Simulated, calibrated noise | 0.72 | 10.9 | 181 | 1.64 | 1.19 |
| Real record | 4.44 | 47.5 | 556 | 1.35 | 1.29 |

Short-lag variance is six times too small and thirty-minute variance three times too small.
The simulated patient also never fell below 139 mg/dl across five days, so it produces no
hypoglycaemia at all.

This matters more for a cadence study than for most. The entire question is what happens at
short lags, so a simulator whose short-lag variance is six times too small will understate what
a one-minute feed reacts to. The bias has a known direction, since a smoother signal triggers
fewer dose events, so a study run on this simulator would understate the rate effect measured
in the open-loop replay rather than overstate it. It would still be the wrong number.

Inflating the sensor noise does not fix this. The deficit at 30 minutes is a shortfall in the
physiology, not in the sensor, and raising sensor noise to cover it would misattribute
metabolic variability to the transducer.

## Recommended architecture instead

Drive the counterfactual from the real trace rather than from a synthetic patient. Take the
real one-minute record as the observed trajectory, run the engine at each cadence, and apply
only the DIFFERENCE in insulin against the observed course through an insulin action curve.
All the real variability is retained, and only the perturbation is modelled.

The limitation to state plainly is that this is a linear perturbation argument, and it is
sound for small dose differences. The difference measured in the replay was not small, so the
counterfactual should be reported with the perturbation size alongside it, and treated as
indicative of direction rather than of magnitude.
