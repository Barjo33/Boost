# Simulator unit-display checks

Checks for the mg/dL and mmol/L toggle in `boost_simulator.html`.

```
cd tools/simulator-tests
npm install
npm test
```

## What is being checked

The simulator holds every value internally in mg/dL and converts only for display, so the setting
cannot change a result. That claim is checked directly: `calcAll` is run over one fixed set of
inputs under both settings and the outputs must be identical.

Everything else follows from a range input showing a coarser grid in mmol than in mg/dL. A value
written in one unit and read back in the other lands on the nearest step the control offers, so the
checks bound that error at half a step and require it to settle rather than accumulate over repeated
switches.

## Why three harnesses

`units_test.js` runs in jsdom, which is quick and can call the page's own functions. It is not
sufficient on its own: jsdom does not implement the value sanitisation a range input performs, so it
keeps a value that is not on a step where a browser would snap it to the nearest one.

`chrome_check.js` repeats the invariants that depend on that inside headless Chrome. It is what
found the display grid sitting off-centre, offering 7.82 mmol/L where a reader expects 7.8, because
a converted bound is not a round number and the grid is counted from the minimum.

`regress.js` drives the working copy and `HEAD` over the same inputs and requires identical results
in mg/dL. Most readers never touch the toggle and should see no change at all.

`version_test.js` checks the Boost 6.3.0 (AAPS 4.0.0) rules. Each is run on a hand-built input in
both versions, and must change the result in the direction the 6.3.0 Kotlin gives in 6.3.0 mode
only. `regress.js` pins the working copy to 6.2.0 (AAPS 3.4.2.6) and compares the V6 decision as
well as `calcAll`, so it also shows that the 6.2.0 results did not move.

Chrome is skipped with a warning if it is not installed. The others always run.

## What is deliberately not converted

The ISF derivation panel is shown in mg/dL whatever the setting, and says so. It transcribes the
algorithm's own arithmetic, and the constants in it — 1800, 2300, the insulin divisor — are mg/dL
quantities. Converting the operands would leave a sum on screen that no longer adds up.
