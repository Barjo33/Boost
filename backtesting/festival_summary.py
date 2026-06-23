#!/usr/bin/env python3
"""Festival summary stats + chart set (Wed 2026-06-17 -> Mon 2026-06-22 BST). Privacy: self only,
no URLs/tokens/raw traces in outputs — anonymised aggregate stats + charts. Mirrors the privacy
design of the other backtesting scripts (config/cache outside repo)."""
import json, os, urllib.parse, urllib.request, time, re
from datetime import datetime, timezone, timedelta
import matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
import numpy as np

cfg=json.load(open(os.path.expanduser(os.environ.get("BOOST_BACKTEST_SITES","~/.config/boost_backtest/sites.json"))))
site=next(s for s in cfg["sites"] if s["tag"]=="self"); base,token=site["base"],site["token"]
def get(path,params):
    p=dict(params);p["token"]=token
    return json.loads(urllib.request.urlopen(f"{base}/api/v1/{path}.json?"+urllib.parse.urlencode(p,safe="[]$<>"),timeout=120).read())
BST=timezone(timedelta(hours=1))
iso=lambda ms: datetime.fromtimestamp(ms/1000,tz=timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")
S=int(datetime(2026,6,17,0,0,tzinfo=BST).timestamp()*1000)
E=int(datetime(2026,6,22,23,59,tzinfo=BST).timestamp()*1000)
# sensor-artifact discard window (dying G6 + G7 warmup)
AS=int(datetime(2026,6,21,22,40,tzinfo=BST).timestamp()*1000); AE=int(datetime(2026,6,22,0,20,tzinfo=BST).timestamp()*1000)
dlabel=lambda ms: datetime.fromtimestamp(ms/1000,tz=BST).strftime("%a %d")
DAYS=["Wed 17","Thu 18","Fri 19","Sat 20","Sun 21","Mon 22"]

# ---- CGM ----
ent=get("entries/sgv",{"count":3000,"find[date][$gte]":S,"find[date][$lte]":E})
byday={d:[] for d in DAYS}; hourly={h:[] for h in range(24)}; allpts=[]
for x in ent:
    v=x.get("sgv"); ms=x.get("date") or x.get("mills")
    if not isinstance(v,(int,float)) or v<=20 or not ms or not (S<=ms<=E): continue
    if AS<=ms<=AE: continue
    d=dlabel(ms)
    if d in byday: byday[d].append(v); allpts.append((ms,v))
    hourly[datetime.fromtimestamp(ms/1000,tz=BST).hour].append(v)

def bands(vs):
    n=len(vs) or 1
    return dict(n=len(vs),mean=sum(vs)/n if vs else 0,
        vlo=100*sum(1 for v in vs if v<54)/n, lo=100*sum(1 for v in vs if 54<=v<70)/n,
        tir=100*sum(1 for v in vs if 70<=v<=180)/n, hi=100*sum(1 for v in vs if 180<v<=250)/n,
        vhi=100*sum(1 for v in vs if v>250)/n)
daystats={d:bands(byday[d]) for d in DAYS}
pooled=bands([v for d in DAYS for v in byday[d]])

# ---- treatments: TDD (bolus + integrated basal) ----
def cms(ca):
    try: return int(datetime.strptime(ca[:19],"%Y-%m-%dT%H:%M:%S").replace(tzinfo=timezone.utc).timestamp()*1000)
    except: return None
tr=get("treatments",{"count":4000,"find[created_at][$gte]":iso(S),"find[created_at][$lte]":iso(E)})
seen=set();T=[]
for x in tr:
    k=(x.get('created_at'),x.get('eventType'),x.get('insulin'),x.get('rate'),x.get('duration'))
    if k in seen: continue
    seen.add(k);T.append(x)
bolus={d:0.0 for d in DAYS}; basal={d:0.0 for d in DAYS}; tbrs=[]
for x in T:
    ms=cms(x.get('created_at') or ''); 
    if ms is None or not(S<=ms<=E): continue
    d=dlabel(ms); et=str(x.get('eventType','')); ins=x.get('insulin')
    if d in bolus and isinstance(ins,(int,float)) and ins>0: bolus[d]+=ins
    if 'Temp Basal' in et and isinstance(x.get('rate'),(int,float)) and isinstance(x.get('duration'),(int,float)):
        tbrs.append((ms,x['rate'],x['duration']))
tbrs.sort()
for i,(ms,rate,dur) in enumerate(tbrs):
    end=tbrs[i+1][0] if i+1<len(tbrs) else ms+dur*60000
    run=min(end-ms,dur*60000)/3600000.0
    if S<=ms<=E: basal[dlabel(ms)]+=rate*max(run,0)
tdd={d:bolus[d]+basal[d] for d in DAYS}

# ---- devicestatus: V5 vs V1-would SMB, steps ----
RE=re.compile(r"V1 would=([0-9.]+)U")
def ts_of(d):
    s=d.get("openaps",{}).get("suggested",{})
    for v in (s.get("date"),d.get("mills")):
        if isinstance(v,(int,float)) and v>1e11: return int(v)
    ca=d.get("created_at"); return cms(ca) if ca else None
ds=[];we=E+86400*1000
while we>S:
    ws=max(S,we-2*86400*1000)
    ds+=get("devicestatus",{"count":120000,"find[created_at][$gte]":iso(ws),"find[created_at][$lte]":iso(we)});we=ws-1
ar={};
for d in ds:
    t=ts_of(d)
    if t and t not in ar: ar[t]=d.get("openaps",{}).get("suggested",{})
v5d={d:0.0 for d in DAYS}; v1d={d:0.0 for d in DAYS}; steps={d:0 for d in DAYS}
for t,s in sorted(ar.items()):
    if not(S<=t<=E): continue
    d=dlabel(t)
    if d not in v5d: continue
    r=str(s.get("reason","")); m=RE.search(r)
    if "V5-ACTIVE drove" in r and m and isinstance(s.get("units"),(int,float)):
        v5d[d]+=s["units"]; v1d[d]+=float(m.group(1))
    st=s.get("boostActivityLoad_stepsToday")
    if isinstance(st,int): steps[d]=max(steps[d],st)
# fallback steps from lastDay if stepsToday sparse
for t,s in sorted(ar.items()):
    if not(S<=t<=E): continue
    d=dlabel(t); ld=s.get("boostActivityLoad_lastDaySteps")
    # lastDay is *yesterday's* total; use as proxy only if stepsToday never populated
    if steps[d]==0 and isinstance(ld,int): pass


# steps per day = the COMPLETED HC daily total, reported as next-day's lastDaySteps (authoritative;
# stepsToday telemetry only exists from 06-19, so this backfills Wed/Thu too).
DAY_ORDER=["Wed 17","Thu 18","Fri 19","Sat 20","Sun 21","Mon 22","Tue 23"]
lastday_on={}
for t,s2 in sorted(ar.items()):
    dd=dlabel(t); ld=s2.get("boostActivityLoad_lastDaySteps")
    if isinstance(ld,int) and ld>0: lastday_on[dd]=ld
for i,d in enumerate(DAYS):
    nxt=DAY_ORDER[DAY_ORDER.index(d)+1] if d in DAY_ORDER and DAY_ORDER.index(d)+1<len(DAY_ORDER) else None
    if nxt and nxt in lastday_on: steps[d]=lastday_on[nxt]

print("=== per-day ===")
for d in DAYS:
    s=daystats[d]
    print(f"{d}: n{s['n']:>3} mean {s['mean']:.0f} TIR {s['tir']:.0f}% <70 {s['lo']+s['vlo']:.1f}% >180 {s['hi']+s['vhi']:.0f}% | TDD {tdd[d]:.1f} (bol {bolus[d]:.1f}/bas {basal[d]:.1f}) | steps {steps[d]} | V5 {v5d[d]:.1f} v V1w {v1d[d]:.1f}")
print(f"POOLED: n{pooled['n']} mean {pooled['mean']:.0f} ({pooled['mean']/18:.1f}) TIR {pooled['tir']:.1f}% <70 {pooled['lo']+pooled['vlo']:.1f}% <54 {pooled['vlo']:.1f}% >180 {pooled['hi']+pooled['vhi']:.1f}%")

# ================= CHARTS =================
plt.rcParams.update({"font.size":9,"font.family":"sans-serif","figure.dpi":130})
fig=plt.figure(figsize=(13,9)); fig.suptitle("Boost V5 — Festival summary  (Wed 17 – Mon 22 Jun 2026)  •  self, anonymised",fontsize=13,fontweight="bold")
gs=fig.add_gridspec(3,2,hspace=0.42,wspace=0.22)
COL={'vlo':'#7b1fa2','lo':'#e53935','tir':'#43a047','hi':'#fb8c00','vhi':'#c62828'}

# 1 TIR stacked
ax=fig.add_subplot(gs[0,0]); x=range(len(DAYS))
b=[0]*len(DAYS)
for key,lab,c in [('vlo','<54','#6a1b9a'),('lo','54–70','#ef5350'),('tir','70–180 (TIR)','#66bb6a'),('hi','180–250','#ffa726'),('vhi','>250','#c62828')]:
    vals=[daystats[d][key] for d in DAYS]; ax.bar(x,vals,bottom=b,color=c,label=lab,width=0.7); b=[bb+vv for bb,vv in zip(b,vals)]
ax.set_xticks(x); ax.set_xticklabels(DAYS,rotation=0,fontsize=8); ax.set_ylabel("% of day"); ax.set_ylim(0,100)
ax.set_title("Time-in-range by day",fontweight="bold"); ax.legend(fontsize=6.5,loc="lower center",ncol=5,bbox_to_anchor=(0.5,-0.30))
for i,d in enumerate(DAYS): ax.text(i,daystats[d]['vlo']+daystats[d]['lo']+daystats[d]['tir']/2,f"{daystats[d]['tir']:.0f}",ha="center",va="center",fontsize=7,color="white",fontweight="bold")

# 2 mean + TDD
ax=fig.add_subplot(gs[0,1]); ax2=ax.twinx()
ax.bar([i-0.18 for i in x],[tdd[d] for d in DAYS],width=0.36,color="#5c6bc0",label="TDD (U)")
ax.bar([i+0.18 for i in x],[basal[d] for d in DAYS],width=0.36,color="#b0bec5",label="basal (U)")
ax2.plot(x,[daystats[d]['mean']/18 for d in DAYS],"o-",color="#d81b60",label="mean (mmol/L)")
ax.set_xticks(x); ax.set_xticklabels(DAYS,fontsize=8); ax.set_ylabel("insulin (U/day)"); ax2.set_ylabel("mean glucose (mmol/L)",color="#d81b60")
ax.set_title("Daily insulin (TDD / basal) & mean glucose",fontweight="bold")
h1,l1=ax.get_legend_handles_labels(); h2,l2=ax2.get_legend_handles_labels(); ax.legend(h1+h2,l1+l2,fontsize=6.5,loc="upper left")

# 3 steps vs low-time
ax=fig.add_subplot(gs[1,0]); ax2=ax.twinx()
ax.bar(x,[steps[d] for d in DAYS],width=0.6,color="#26a69a",label="steps (max/day)")
ax2.plot(x,[daystats[d]['lo']+daystats[d]['vlo'] for d in DAYS],"s-",color="#e53935",label="% time <70")
ax.set_xticks(x); ax.set_xticklabels(DAYS,fontsize=8); ax.set_ylabel("steps"); ax2.set_ylabel("% time <70",color="#e53935")
ax.set_title("Activity (steps) vs time-low",fontweight="bold")
h1,l1=ax.get_legend_handles_labels(); h2,l2=ax2.get_legend_handles_labels(); ax.legend(h1+h2,l1+l2,fontsize=6.5,loc="upper left")

# 4 V5 vs V1-would SMB
ax=fig.add_subplot(gs[1,1])
ax.bar([i-0.18 for i in x],[v5d[d] for d in DAYS],width=0.36,color="#43a047",label="V5 delivered SMB")
ax.bar([i+0.18 for i in x],[v1d[d] for d in DAYS],width=0.36,color="#ef6c00",label="V1-would SMB (counterfactual)")
ax.set_xticks(x); ax.set_xticklabels(DAYS,fontsize=8); ax.set_ylabel("SMB total (U, paired cycles)")
ax.set_title("V5 vs V1-would correction SMB",fontweight="bold"); ax.legend(fontsize=6.5)
ax.text(0.5,-0.34,"SMB-only on diverging cycles; not TDD. Basal (~40% of TDD) is shared.",transform=ax.transAxes,ha="center",fontsize=6.3,style="italic",color="#555")

# 5 hourly AGP-style median + IQR
ax=fig.add_subplot(gs[2,0]); hrs=list(range(24))
med=[np.median(hourly[h])/18 if hourly[h] else np.nan for h in hrs]
p25=[np.percentile(hourly[h],25)/18 if hourly[h] else np.nan for h in hrs]
p75=[np.percentile(hourly[h],75)/18 if hourly[h] else np.nan for h in hrs]
p10=[np.percentile(hourly[h],10)/18 if hourly[h] else np.nan for h in hrs]
p90=[np.percentile(hourly[h],90)/18 if hourly[h] else np.nan for h in hrs]
ax.fill_between(hrs,p10,p90,color="#90caf9",alpha=0.4,label="10–90%")
ax.fill_between(hrs,p25,p75,color="#42a5f5",alpha=0.5,label="25–75%")
ax.plot(hrs,med,color="#1565c0",lw=2,label="median")
ax.axhspan(3.9,10,color="#a5d6a7",alpha=0.25); ax.axhline(3.9,color="#e53935",lw=0.8,ls="--")
ax.set_xticks(range(0,24,3)); ax.set_xlabel("hour of day (BST)"); ax.set_ylabel("glucose (mmol/L)"); ax.set_ylim(2,16)
ax.set_title("Glucose by time of day (6-day AGP)",fontweight="bold"); ax.legend(fontsize=6.5,loc="upper right")

# 6 pooled summary text + donut
ax=fig.add_subplot(gs[2,1]); ax.axis("off")
sizes=[pooled['vlo'],pooled['lo'],pooled['tir'],pooled['hi'],pooled['vhi']]
cols=['#6a1b9a','#ef5350','#66bb6a','#ffa726','#c62828']
w,_=ax.pie(sizes,colors=cols,startangle=90,counterclock=False,radius=0.85,center=(0.25,0.5),wedgeprops=dict(width=0.38))
ax.text(0.25,0.5,f"{pooled['tir']:.0f}%\nTIR",ha="center",va="center",fontsize=12,fontweight="bold")
txt=(f"6-day pooled (n={pooled['n']})\n"
     f"mean  {pooled['mean']:.0f} mg/dL ({pooled['mean']/18:.1f} mmol/L)\n"
     f"TIR 70–180   {pooled['tir']:.1f}%\n"
     f"time <70     {pooled['lo']+pooled['vlo']:.1f}%\n"
     f"time <54     {pooled['vlo']:.1f}%\n"
     f"time >180    {pooled['hi']+pooled['vhi']:.1f}%\n"
     f"TDD          ~{np.mean([tdd[d] for d in DAYS if tdd[d]>0]):.0f} U/day\n"
     f"activity     ~18–26k steps/day")
ax.text(0.62,0.5,txt,ha="left",va="center",fontsize=9,family="monospace")
ax.set_title("Period summary",fontweight="bold",loc="left")

fig.savefig("Boost-Festival-Summary-2026-06-17_22.png",bbox_inches="tight")
try:
    fig.savefig("Boost-Festival-Summary-2026-06-17_22.pdf",bbox_inches="tight")
except Exception as e: print("pdf skip",e)
print("charts written")
