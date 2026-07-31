"""AAPS exponential insulin model. Used to feed a dose back into glucose and into IOB."""
import numpy as np

class Insulin:
    def __init__(self, peak_min=75.0, dia_min=360.0):
        td, tp = float(dia_min), float(peak_min)
        self.td, self.tp = td, tp
        self.tau = tp*(1-tp/td)/(1-2*tp/td)
        self.a = 2*self.tau/td
        self.S = 1/(1-self.a+(1+self.a)*np.exp(-td/self.tau))

    def iob_fraction(self, t):
        """Fraction of a unit still to act, t minutes after delivery."""
        t = np.asarray(t, float)
        tau, a, S, td = self.tau, self.a, self.S, self.td
        f = 1 - S*(1-a)*((t**2/(tau*td*(1-a)) - t/tau - 1)*np.exp(-t/tau) + 1)
        return np.clip(np.where(t < 0, 1.0, np.where(t > td, 0.0, f)), 0.0, 1.0)

    def action_fraction(self, t):
        """Fraction of a unit's total glucose-lowering effect already delivered by time t."""
        return 1.0 - self.iob_fraction(t)
