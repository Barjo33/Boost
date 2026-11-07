// Parsing checks for boost_analyser.html on Boost 6.2.0 (AAPS 3.4) and 6.3.0 (AAPS 4.0) records.
// The page's pure-function block is lifted out and run in a vm context, so no DOM is needed.
// The reason strings are synthetic, written to the formats in OpenAPSBoostPlugin.kt.
// Usage: node tools/analyser-tests/parse_test.js [path/to/boost_analyser.html]
const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");

const file = process.argv[2] || path.join(__dirname, "../../boost_analyser.html");
const html = fs.readFileSync(file, "utf8");
const start = html.indexOf("/* ================================ CORE");
const end = html.indexOf("/* ------------------------------ rendering");
assert(start > 0 && end > start, "could not find the CORE block");
const ctx = vm.createContext({});
vm.runInContext(html.slice(start, end), ctx);
const A = ctx;

const T0 = Date.parse("2026-01-01T12:00:00Z");
const ds = (i, sug, extra) => Object.assign({
  device: "openaps://phone",
  openaps: { suggested: Object.assign({
    timestamp: new Date(T0 + i * 300000).toISOString(), bg: 150, variable_sens: 50,
    boostV5_state: "IDLE", boostV5_age: 0, boostV5_score: 0.1, boostV5_budget: 1,
    boostV5_finalDose: 0.5, boostV5_committedCap: 2, boostV5_confirmedCap: 3, boostV5_gateReduction: "none",
  }, sug) },
}, extra);

let n = 0;
const check = (name, fn) => { fn(); n++; console.log("ok  " + name); };

// ---- 6.2.0 on AAPS 3.4: shadow and V6-active cycles, version in configuration.apsConfiguration ----
const v62 = A.parseCycles([
  ds(0, { boostV5_active: false, units: 0.3 },
    { configuration: { version: "3.4.2.6", apsConfiguration: { boostVersion: "6.2.0+dev.abc" } } }),
  ds(1, { boostV5_active: true, units: 0.8,
    reason: "COB: 0; V6-ACTIVE drove SMB 0.8U (base would=0.35U, state=OBSERVING); primer=bolus,0.15U; " }),
  ds(2, { boostV5_active: true, units: 0.2,
    reason: "V6 suppressed (SLEEPING) — base SMB 0.2U; primer=tbr,0.1U→1.2U/h×30m; " }),
  ds(3, { boostV5_active: true, reason: "tranche=1.0,0.6,0.4,0.7,CONFIRMED; V6-ACTIVE drove SMB 0.6U (base would=0.4U, state=CONFIRMED); " }),
]);
check("6.2.0 cycle count and source", () => {
  assert.strictEqual(v62.length, 4);
  assert(v62.every(c => c.src === "aaps"));
});
check("6.2.0 shadow pairs on delivered units", () =>
  assert.deepStrictEqual({ ...A.pairDoses(v62[0]) }, { v1: 0.3, v6: 0.5 }));
check("6.2.0 base would= is the V1 counterfactual", () =>
  assert.deepStrictEqual({ ...A.pairDoses(v62[1]) }, { v1: 0.35, v6: 0.5 }));
check("6.2.0 suppression pairs on delivered units", () =>
  assert.deepStrictEqual({ ...A.pairDoses(v62[2]) }, { v1: 0.2, v6: 0.5 }));
check("tranche crumb does not disturb base would=", () =>
  assert.strictEqual(A.pairDoses(v62[3]).v1, 0.4));
check("primer crumbs", () => {
  assert.deepStrictEqual({ ...v62[1].primer }, { mode: "bolus", units: 0.15, delivered: true });
  assert.deepStrictEqual({ ...v62[2].primer }, { mode: "tbr", units: 0.1, delivered: true });
  assert.strictEqual(A.parsePrimer("primer=tbr-skipped(base-temp 0<basal 1); ").mode, "skipped");
  assert.strictEqual(A.parsePrimer("primer=tbr-subsumed(base 2≥primer 1.2U/h); ").mode, "subsumed");
});
check("6.2.0 active detection from reason text without the flag", () => {
  const c = A.parseCycles([ds(0, { boostV5_active: undefined, reason: "V6 override skipped (Boost inactive) — base SMB 0U; " })]);
  assert.strictEqual(c[0].active, true);
});
check("6.2.0 version from configuration", () => {
  const v = A.boostVersionOf(v62);
  assert.strictEqual(v.ver, "6.2.0+dev.abc");
  assert.strictEqual(v.at63, false);
});

// ---- 6.3.0 on AAPS 4.0: one plugin, always active, version on suggested, lowSuspend tag ----
const v63 = A.parseCycles([
  ds(0, { boostV5_active: true, units: 1.1, boostVersion: "6.3.0+dev.c8ce6355d0",
    reason: "hypo4=0.01,0.02; V6-ACTIVE drove SMB 1.1U (base would=0.7U, state=COMMITTED); " }),
  ds(1, { boostV5_active: true, boostVersion: "6.3.0+dev.c8ce6355d0", boostV5_finalDose: 0,
    reason: "minGuardBG 60<70; lowSuspend=1; " }),
  ds(2, { boostV5_active: true, boostVersion: "6.3.0+dev.c8ce6355d0",
    reason: "lowSuspend=1; V6 suppressed (cumulative SMB cap 3U/3U reached); " }),
]);
check("6.3.0 base would=", () =>
  assert.deepStrictEqual({ ...A.pairDoses(v63[0]) }, { v1: 0.7, v6: 0.5 }));
check("6.3.0 V1 low suspend pairs both engines at V1's dose", () => {
  assert.deepStrictEqual({ ...A.pairDoses(v63[1]) }, { v1: 0, v6: 0 });
  assert.deepStrictEqual({ ...A.pairDoses(v63[2]) }, { v1: 0, v6: 0 });
});
check("6.3.0 version from suggested.boostVersion", () => {
  const v = A.boostVersionOf(v63);
  assert.strictEqual(v.ver, "6.3.0+dev.c8ce6355d0");
  assert.strictEqual(v.at63, true);
  assert.strictEqual(A.boostVersionOf([]).at63, null);
});

// ---- committed cap hint: RECOVERING counts only on 6.3.0+ ----
const pinned = s => ({ state: s, fd: 2, ccap: 2, fcap: 3, bg: 200, gates: "none",
  floorWouldAdd: null, confirmGate: null });
const cyc = [pinned("RECOVERING"), pinned("RECOVERING"), pinned("RECOVERING"), pinned("COMMITTED")];
const hint = at63 => A.leverHints({ episodes: [], nearMiss: [], gly: null, night: { n: 0 }, at63 }, cyc, true)
  .find(h => h.lever === "Committed cap (U)");
check("committed cap hint counts RECOVERING on 6.3.0", () => assert(hint(true) && /4 COMMITTED or RECOVERING/.test(hint(true).why)));
check("committed cap hint ignores RECOVERING on 6.2.0 and unknown", () => {
  assert.strictEqual(hint(false), undefined);
  assert.strictEqual(hint(null), undefined);
});

console.log(`${n} checks passed`);
