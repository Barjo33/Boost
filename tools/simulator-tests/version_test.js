// Each Boost 6.3.0 rule the simulator models, checked on a hand-built input: in 6.3.0 mode the
// result moves in the direction the Kotlin at c8ce6355d0 says it should, and in 6.2.0 mode it does
// not. The V1 low-suspend tag and the lowered low-glucose threshold are not here, because the
// simulator does not model V1's low-glucose threshold at all.
const fs = require('fs');
const { JSDOM } = require('jsdom');

const stub = w => { w.HTMLCanvasElement.prototype.getContext = () => new Proxy({}, {
  get: (t, k) => k === 'measureText' ? () => ({ width: 10 })
    : (k === 'createLinearGradient' || k === 'createRadialGradient') ? () => ({ addColorStop() {} })
    : k === 'canvas' ? { width: 600, height: 300, style: {} } : typeof k === 'string' ? () => {} : undefined,
  set: () => true }); };
const w = new JSDOM(fs.readFileSync(process.argv[2], 'utf8'), {
  runScripts: 'dangerously', pretendToBeVisual: true, url: 'https://example.invalid/', beforeParse: stub,
}).window;

let fails = 0;
const check = (ok, msg) => { if (!ok) { fails++; console.log('  FAIL ' + msg); } };
const doc = w.document;
const setVersion = v => { doc.getElementById('s-simVersion').value = v; };
// Runs fn once per version and returns { v63, v62 }.
const both = fn => ({ v63: fn(true), v62: fn(false) });

// 1a. COMMITTED stays COMMITTED while the rise is still 3 mg/dL per 5 min or more.
let r = both(v63 => w.v6MealStep({ state: 'COMMITTED', age: 3, committed: true },
  { v63, score: 0.5, eventualBgOffset: 40, delta: 5, deltaAccl: -8, deltaDeclining: true }).state);
check(r.v63 === 'COMMITTED' && r.v62 === 'RECOVERING', `COMMITTED back-off on delta 5: ${JSON.stringify(r)}`);

// 1b. RECOVERING re-engages on a fast rise without acceleration.
r = both(v63 => w.v6MealStep({ state: 'RECOVERING', age: 1, committed: true },
  { v63, score: 0.5, eventualBgOffset: 40, delta: 9, deltaAccl: 0, deltaDeclining: false }).state);
check(r.v63 === 'COMMITTED' && r.v62 === 'RECOVERING', `RECOVERING re-engage on delta 9: ${JSON.stringify(r)}`);

// 2. Primer. A hand-built OBSERVING cycle with no IOB, BG 140, no recent low.
const primer = (v63, over, prior) => {
  setVersion(v63 ? '6.3.2' : '6.2.0');
  const inp = Object.assign(w.getInputs(), { bg: 140, iob: 0, maxIob: 3, primerCapU: 0.5,
    fastCarbConfirm: false, recentLowBg: 120, mlHypoRisk: 0 });
  const res = w.calcAll(inp);
  const m = w.v6Measurement(inp, res, over);
  const st = Object.assign(w.v6FreshState(), { state: 'OBSERVING', age: 0 }, prior);
  return w.v6Decide(res.insulinReqV1, m, st, w.v6Knobs(inp));
};
// 2a. A sustained rise with no acceleration opens the 6.3.0 primer only.
let p = both(v63 => primer(v63, { delta: 5, shortAvgDelta: 4, deltaAccl: 0 }, {}));
check(p.v63.state === 'OBSERVING' && p.v62.state === 'OBSERVING', `primer case left OBSERVING: ${p.v63.state}/${p.v62.state}`);
check(p.v63.primerBolusU > 0 && p.v62.primerBolusU === 0,
  `primer on short avg 4, accel 0: 6.3.0 ${p.v63.primerBolusU} (${p.v63.primerReason}), 6.2.0 ${p.v62.primerBolusU} (${p.v62.primerReason})`);
// 2b. Once a meal was committed this session, 6.3.0 does not prime a second rise.
p = both(v63 => primer(v63, { delta: 5, shortAvgDelta: 4, deltaAccl: 25 }, { committed: true }));
check(p.v63.primerBolusU === 0 && p.v62.primerBolusU > 0,
  `primer after commit: 6.3.0 ${p.v63.primerBolusU} (${p.v63.primerReason}), 6.2.0 ${p.v62.primerBolusU} (${p.v62.primerReason})`);

// 3. RECOVERING is held to the committed cap.
r = both(v63 => w.v6StateCap('RECOVERING', 2.0, 2.5, 0.5, v63));
check(r.v63 === 0.5 && r.v62 === 2.0, `RECOVERING cap: ${JSON.stringify(r)}`);

// 4. The budget floor falls with Hypo Caution. At full hypo risk and Hypo Caution 2 the ML damper
// is at its floor of 0.25, under the 6.2.0 budget floor of 0.30 but over the 6.3.0 one of 0.15.
r = both(v63 => w.v6Budget(1.0, 1.0, false, { hypoCaution: 2.0, sensitivity: 1.0, v63 }).budget);
check(Math.abs(r.v63 - 0.25) < 1e-9 && Math.abs(r.v62 - 0.30) < 1e-9, `budget floor: ${JSON.stringify(r)}`);

// 5. The rapid-rise gate takes the largest of delta, short and long average in 6.3.0.
r = both(v63 => {
  setVersion(v63 ? '6.3.2' : '6.2.0');
  const inp = Object.assign(w.getInputs(), { bg: 100, delta: 2, shortAvgDelta: 2, longAvgDelta: 35, iob: 0 });
  const res = w.calcAll(inp);
  return w.v6Gates(1.0, w.v6Measurement(inp, res)).hard;
});
check(/maxDelta/.test(r.v63 || '') && r.v62 === null, `maxDelta gate on long avg 35: ${JSON.stringify(r)}`);

// 8. Percent scale is on at 200% in 6.3.0 whatever the toggle says.
doc.getElementById('t-percentScale').checked = false;
doc.getElementById('s-percentScale').value = '100';
r = both(v63 => { setVersion(v63 ? '6.3.2' : '6.2.0'); const i = w.getInputs(); return [i.percentScale, i.percentScaleVal]; });
check(r.v63[0] === true && r.v63[1] === 200 && r.v62[0] === false && r.v62[1] === 100, `percent scale: ${JSON.stringify(r)}`);

// 9. The SMB basal minutes are used as set: 6.3.2 sets 15 and 20 only at first start, and no longer lowers them.
doc.getElementById('s-smbMin').value = '60';
doc.getElementById('s-uamSmbMin').value = '60';
r = both(v63 => { setVersion(v63 ? '6.3.2' : '6.2.0'); const i = w.getInputs(); return [i.smbMin, i.uamSmbMin]; });
check(r.v63.join() === '60,60' && r.v62.join() === '60,60', `SMB basal minutes: ${JSON.stringify(r)}`);

// The explanatory text follows the version.
doc.getElementById('s-v6PriorState').value = 'RECOVERING';
const why = v => { setVersion(v); w.update(); return doc.getElementById('out-v6-state-why').textContent; };
const why63 = why('6.3.2'), why62 = why('6.2.0');
check(/^RECOVERING/.test(doc.getElementById('out-v6-state-name').textContent), 'default inputs left RECOVERING');
check(/Committed cap/.test(why63) && !/Committed cap/.test(why62), `RECOVERING text: "${why63}" / "${why62}"`);
check(doc.getElementById('note-percentScale').style.display === 'none', 'percent scale note shown in 6.2.0 mode');
setVersion('6.3.2'); w.update();
check(doc.getElementById('note-percentScale').style.display !== 'none', 'percent scale note hidden in 6.3.0 mode');
check(doc.getElementById('s-percentScale').disabled, 'percent scale slider still enabled in 6.3.0 mode');

console.log(fails === 0 ? 'PASS — all 6.3.0 rule checks' : `${fails} check(s) FAILED`);
process.exit(fails === 0 ? 0 : 1);
