"""Configurable CGM sensor chain, so cadence can be switched cleanly.

True glucose from the simulator is passed through the stages a real device applies before a
number reaches the loop:

  1. blood to interstitial transport, a first-order lag. Vettoretti et al. (Sensors 2019) put
     the median time constant at 3.8 min.
  2. the manufacturer's internal filter. Measurement on real records showed the REPORTED
     series carries almost no independent per-sample noise, so this stage is what removes it.
  3. additive noise with AR(2) structure, at the level that survives filtering.
  4. quantisation to whole mg/dL, which real devices apply.
  5. reporting at the configured interval.

Stage 3 and 4 are what a cadence comparison turns on, so they are calibrated against the real
record rather than assumed.
"""
import numpy as np

class Sensor:
    def __init__(self, interval_min, tau_min=3.8, filt_min=0.0,
                 noise_sd=1.0, ar1=1.53, ar2=-0.69, quantise=True, seed=0):
        self.interval = int(interval_min)
        self.tau = float(tau_min)
        self.filt = float(filt_min)
        self.noise_sd = float(noise_sd)
        self.ar1, self.ar2 = float(ar1), float(ar2)
        self.quantise = quantise
        self.rng = np.random.default_rng(seed)
        self._ig = None
        self._f = None
        self._e1 = 0.0
        self._e2 = 0.0
        self._k = 0
        self._last = None

    def step(self, true_bg, dt_min=1.0):
        """Advance one simulator minute. Returns a reading, or None if not a reporting minute."""
        a = np.exp(-dt_min/max(self.tau, 1e-9))
        self._ig = true_bg if self._ig is None else a*self._ig + (1-a)*true_bg
        if self.filt > 0:
            b = np.exp(-dt_min/self.filt)
            self._f = self._ig if self._f is None else b*self._f + (1-b)*self._ig
            base = self._f
        else:
            base = self._ig
        # AR(2) noise, scaled so the stationary SD is noise_sd
        w = self.rng.normal(0.0, 1.0)
        e = self.ar1*self._e1 + self.ar2*self._e2 + w
        self._e2, self._e1 = self._e1, e
        var = 1.0/max(1.0 - self.ar1**2 - self.ar2**2 - 2*self.ar1**2*self.ar2/(1-self.ar2), 1e-6)
        v = base + self.noise_sd*e/np.sqrt(abs(var))
        self._k += 1
        if (self._k - 1) % self.interval != 0:
            return None
        if self.quantise:
            v = float(np.round(v))
        self._last = v
        return v

def variogram(ts_min, v, lags):
    """D(tau) on an irregular series, ts in minutes."""
    ts = np.asarray(ts_min, float); v = np.asarray(v, float)
    out = {}
    for L in lags:
        j = np.searchsorted(ts, ts + L)
        ok = j < len(ts)
        i_ = np.nonzero(ok)[0]; j_ = j[ok]
        keep = np.abs(ts[j_]-ts[i_] - L) <= max(0.6, 0.12*L)
        if keep.sum() < 100: continue
        out[L] = float(np.mean((v[j_[keep]]-v[i_[keep]])**2))
    return out

def loglog_slope(vg, lo, hi):
    ls = [(L, D) for L, D in sorted(vg.items()) if lo <= L <= hi and D > 0]
    if len(ls) < 3: return None
    x = np.log([l for l, _ in ls]); y = np.log([d for _, d in ls])
    return float(np.polyfit(x, y, 1)[0])
