// The mg/dL view must be unchanged by the port. Both files are driven over the same grid of inputs
// and their outputs compared exactly; any difference is a regression for the readers who never
// touch the toggle.
//
// The working copy is pinned to Boost 6.2.0 (AAPS 3.4.2.6), so this also shows that adding the
// 6.3.0 rules left the 6.2.0 results alone. HEAD may predate the selector, in which case pinning
// it is a no-op. The V6 decision is compared as well as calcAll, since calcAll is V1 only.
const fs = require('fs'); const { JSDOM } = require('jsdom');
const stub = w => { w.HTMLCanvasElement.prototype.getContext = () => new Proxy({}, {
  get: (t,k)=> k==='measureText'?()=>({width:10}):(k==='createLinearGradient'||k==='createRadialGradient')?()=>({addColorStop(){}}):k==='canvas'?{width:600,height:300,style:{}}:typeof k==='string'?()=>{}:undefined,
  set: ()=>true }); };
const load = f => new JSDOM(fs.readFileSync(f,'utf8'),
  {runScripts:'dangerously', pretendToBeVisual:true, url:'https://example.invalid/', beforeParse:stub}).window;
const A = load(process.argv[2]), B = load(process.argv[3]);
for (const w of [A, B]) { const v = w.document.getElementById('s-simVersion'); if (v) v.value = '6.2.0'; }
// The V6 fields both versions of the page compute. Text fields are left out on purpose.
const V6_KEYS = ['state', 'score', 'budget', 'actionMult', 'rawDose', 'velScaled', 'capped',
  'prospectiveShot', 'confirmDoseAdequate', 'primerBolusU', 'finalDose'];
const v6Run = (w, inp, r) => {
  const prior = w.v6FreshState(); prior.state = inp.v6PriorState;
  if (prior.state === 'OBSERVING') prior.age = 2;
  if (prior.state !== 'IDLE' && prior.state !== 'OBSERVING') { prior.committed = true; prior.age = 1; }
  const d = w.v6Decide(r.insulinReqV1, w.v6Measurement(inp, r), prior, w.v6Knobs(inp));
  const ov = w.v6Override(d, r.tier === 'BLOCKED' ? 0 : r.microBolus, { recentLowBg: inp.recentLowBg, bg: inp.bg, cumSmbCap: 0 });
  return { ...Object.fromEntries(V6_KEYS.map(k => [k, d[k]])), overrideDose: ov.dose };
};

const GRID = [
  {bg:60,delta:-6,iob:2}, {bg:90,delta:0,iob:0}, {bg:140,delta:2,iob:0.5},
  {bg:180,delta:8,iob:1}, {bg:250,delta:12,iob:3}, {bg:340,delta:-2,iob:0.2},
];
let diffs = 0, n = 0;
for (const g of GRID) {
  for (const state of ['IDLE','OBSERVING','CONFIRMED','COMMITTED','RECOVERING']) {
    for (const tdd of [25, 40, 70]) {
      const run = w => {
        const inp = w.getInputs();
        Object.assign(inp, g, {tdd, v6PriorState: state});
        const r = w.calcAll(inp);
        return JSON.stringify(r) + JSON.stringify(v6Run(w, inp, r));
      };
      const a = run(A), b = run(B);
      n++;
      if (a !== b) { diffs++; if (diffs <= 3) console.log(`  DIFF bg=${g.bg} ${state} tdd=${tdd}\n    was ${a}\n    now ${b}`); }
    }
  }
}
console.log(diffs === 0 ? `PASS — ${n} input combinations identical in mg/dL (6.2.0 mode, V1 and V6)` : `${diffs}/${n} DIFFER`);
process.exit(diffs === 0 ? 0 : 1);
