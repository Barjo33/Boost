#!/usr/bin/env python3
"""Festival summary stats + chart set (Thu 2026-06-18 -> Mon 2026-06-22 BST). Privacy: self only,
no URLs/tokens/raw traces in outputs — anonymised aggregate stats + charts (config/cache outside repo).
Ranges: TIR 70-180, TITR 70-140 (3.9-7.8), TINR 63-140 (3.5-7.8)."""
import json, os, urllib.parse, urllib.request, time, re
from datetime import datetime, timezone, timedelta
import matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

cfg=json.load(open(os.path.expanduser(os.environ.get("BOOST_BACKTEST_SITES","~/.config/boost_backtest/sites.json"))))
site=next(s for s in cfg["sites"] if s["tag"]=="self"); base,token=site["base"],site["token"]
def get(path,params):
    p=dict(params);p["token"]=token
    return json.loads(urllib.request.urlopen(f"{base}/api/v1/{path}.json?"+urllib.parse.urlencode(p,safe="[]$<>"),timeout=120).read())
BST=timezone(timedelta(hours=1))
iso=lambda ms: datetime.fromtimestamp(ms/1000,tz=timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")
S=int(datetime(2026,6,18,0,0,tzinfo=BST).timestamp()*1000)
E=int(datetime(2026,6,22,23,59,tzinfo=BST).timestamp()*1000)
AS=int(datetime(2026,6,21,22,40,tzinfo=BST).timestamp()*1000); AE=int(datetime(2026,6,22,0,20,tzinfo=BST).timestamp()*1000)
dlabel=lambda ms: datetime.fromtimestamp(ms/1000,tz=BST).strftime("%a %d")
DAYS=["Thu 18","Fri 19","Sat 20","Sun 21","Mon 22"]
TITR=(70,140); TINR=(63,140)

ent=get("entries/sgv",{"count":3000,"find[date][$gte]":S,"find[date][$lte]":E})
byday={d:[] for d in DAYS}; hourly={h:[] for h in range(24)}
for x in ent:
    v=x.get("sgv"); ms=x.get("date") or x.get("mills")
    if not isinstance(v,(int,float)) or v<=20 or not ms or not (S<=ms<=E): continue
    if AS<=ms<=AE: continue
    d=dlabel(ms)
    if d in byday: byday[d].append(v)
    hourly[datetime.fromtimestamp(ms/1000,tz=BST).hour].append(v)

def stats(vs):
    n=len(vs) or 1
    inr=lambda lo,hi: 100*sum(1 for v in vs if lo<=v<=hi)/n
    return dict(n=len(vs),mean=(sum(vs)/n if vs else 0),
        vlo=100*sum(1 for v in vs if v<54)/n, lo=100*sum(1 for v in vs if 54<=v<70)/n,
        tir=inr(70,180), hi=100*sum(1 for v in vs if 180<v<=250)/n, vhi=100*sum(1 for v in vs if v>250)/n,
        titr=inr(*TITR), tinr=inr(*TINR))
DS={d:stats(byday[d]) for d in DAYS}
P=stats([v for d in DAYS for v in byday[d]])

# treatments: TDD (for the summary stat only; chart removed)
def cms(ca):
    try: return int(datetime.strptime(ca[:19],"%Y-%m-%dT%H:%M:%S").replace(tzinfo=timezone.utc).timestamp()*1000)
    except: return None
tr=get("treatments",{"count":4000,"find[created_at][$gte]":iso(S),"find[created_at][$lte]":iso(E)})
seen=set();T=[]
for x in tr:
    k=(x.get('created_at'),x.get('eventType'),x.get('insulin'),x.get('rate'),x.get('duration'))
    if k not in seen: seen.add(k);T.append(x)
bolus={d:0.0 for d in DAYS}; tbrs=[]; basal={d:0.0 for d in DAYS}
for x in T:
    ms=cms(x.get('created_at') or ''); 
    if ms is None or not(S<=ms<=E): continue
    d=dlabel(ms); ins=x.get('insulin')
    if d in bolus and isinstance(ins,(int,float)) and ins>0: bolus[d]+=ins
    if 'Temp Basal' in str(x.get('eventType','')) and isinstance(x.get('rate'),(int,float)) and isinstance(x.get('duration'),(int,float)):
        tbrs.append((ms,x['rate'],x['duration']))
tbrs.sort()
for i,(ms,rate,dur) in enumerate(tbrs):
    end=tbrs[i+1][0] if i+1<len(tbrs) else ms+dur*60000
    if S<=ms<=E: basal[dlabel(ms)]+=rate*max(min(end-ms,dur*60000)/3600000.0,0)
tdd={d:bolus[d]+basal[d] for d in DAYS}

# devicestatus: steps (next-day lastDaySteps) + HR
def ts_of(d):
    s=d.get("openaps",{}).get("suggested",{})
    for v in (s.get("date"),d.get("mills")):
        if isinstance(v,(int,float)) and v>1e11: return int(v)
    ca=d.get("created_at"); return cms(ca) if ca else None
ds=[];we=E+86400*1000
while we>S:
    ws=max(S,we-2*86400*1000); ds+=get("devicestatus",{"count":120000,"find[created_at][$gte]":iso(ws),"find[created_at][$lte]":iso(we)});we=ws-1
ar={}
for d in ds:
    t=ts_of(d)
    if t and t not in ar: ar[t]=d.get("openaps",{}).get("suggested",{})
DAY_ORDER=["Thu 18","Fri 19","Sat 20","Sun 21","Mon 22","Tue 23"]
lastday_on={}
for t,s in sorted(ar.items()):
    dd=dlabel(t); ld=s.get("boostActivityLoad_lastDaySteps")
    if isinstance(ld,int) and ld>0: lastday_on[dd]=ld
steps={d:0 for d in DAYS}
for d in DAYS:
    j=DAY_ORDER.index(d)+1
    if j<len(DAY_ORDER) and DAY_ORDER[j] in lastday_on: steps[d]=lastday_on[DAY_ORDER[j]]
# HR — fetch per-day (the big chunked pull drops hr fields; per-day count<=400 is reliable).
hrday={d:[] for d in DAYS}
for dn,d in zip(range(18,23),DAYS):
    ws=int(datetime(2026,6,dn,0,0,tzinfo=BST).timestamp()*1000); wse=int(datetime(2026,6,dn,23,59,tzinfo=BST).timestamp()*1000)
    for rec in get("devicestatus",{"count":400,"find[created_at][$gte]":iso(ws),"find[created_at][$lte]":iso(wse)}):
        sg=rec.get("openaps",{}).get("suggested",{}); hv=sg.get("hrBpmLatest") or sg.get("hrBpmAvg5m")
        if isinstance(hv,(int,float)) and hv>30: hrday[d].append(hv)
hr_peak={d:(max(hrday[d]) if hrday[d] else None) for d in DAYS}
hr_cov={d:int(round(100*len(hrday[d])/288)) for d in DAYS}  # ~% of 5-min cycles with HR
hr_any=any(hrday[d] for d in DAYS)
hr_peaks=[v for d in DAYS if (v:=hr_peak[d]) is not None]

print("=== per day ===")
for d in DAYS:
    s=DS[d]; print(f"{d}: TIR {s['tir']:.0f} TITR {s['titr']:.0f} TINR {s['tinr']:.0f} | <70 {s['lo']+s['vlo']:.1f} | steps {steps[d]} | HRpeak {hr_peak[d]}")
print(f"POOLED TIR {P['tir']:.1f} TITR {P['titr']:.1f} TINR {P['tinr']:.1f} mean {P['mean']:.0f} ({P['mean']/18:.1f}) <70 {P['lo']+P['vlo']:.1f} <54 {P['vlo']:.1f}")
print("HR present in festival window:", hr_any)

# ================= CHARTS =================
plt.rcParams.update({"font.size":9,"font.family":"sans-serif","figure.dpi":130})
fig=plt.figure(figsize=(13,9)); fig.suptitle("Boost V5 — Festival summary  (Thu 18 – Mon 22 Jun 2026)  •  self, anonymised",fontsize=13,fontweight="bold")
gs=fig.add_gridspec(3,2,hspace=0.45,wspace=0.22)
x=range(len(DAYS))
# ADA-consensus band colours
C_VLO,C_LO,C_TIR,C_HI,C_VHI="#8e24aa","#ef5350","#43a047","#ffb300","#fb8c00"

# 1 TIR by day (standard stacked)
ax=fig.add_subplot(gs[0,0]); b=[0]*len(DAYS)
for key,lab,c in [('vlo','<54','#8e24aa'),('lo','54–70',C_LO),('tir','70–180 (TIR)',C_TIR),('hi','180–250',C_HI),('vhi','>250',C_VHI)]:
    vals=[DS[d][key] for d in DAYS]; ax.bar(x,vals,bottom=b,color=c,label=lab,width=0.72); b=[bb+vv for bb,vv in zip(b,vals)]
ax.set_xticks(x); ax.set_xticklabels(DAYS,fontsize=8); ax.set_ylim(0,100); ax.set_ylabel("% of day")
ax.set_title("Time in Range by day (70–180)",fontweight="bold")
ax.legend(fontsize=6.5,ncol=5,loc="lower center",bbox_to_anchor=(0.5,-0.32))
for i,d in enumerate(DAYS): ax.text(i,DS[d]['vlo']+DS[d]['lo']+DS[d]['tir']/2,f"{DS[d]['tir']:.0f}",ha="center",va="center",color="white",fontsize=7,fontweight="bold")

# 2 TITR & TINR by day (grouped)
ax=fig.add_subplot(gs[0,1])
ax.bar([i-0.18 for i in x],[DS[d]['titr'] for d in DAYS],width=0.36,color="#2e7d32",label="TITR 70–140")
ax.bar([i+0.18 for i in x],[DS[d]['tinr'] for d in DAYS],width=0.36,color="#66bb6a",label="TINR 63–140")
ax.set_xticks(x); ax.set_xticklabels(DAYS,fontsize=8); ax.set_ylim(0,100); ax.set_ylabel("% of day")
ax.set_title("Tight & Normal range by day",fontweight="bold"); ax.legend(fontsize=7,loc="upper right")
for i,d in enumerate(DAYS):
    ax.text(i-0.18,DS[d]['titr']+1.5,f"{DS[d]['titr']:.0f}",ha="center",fontsize=6.5)
    ax.text(i+0.18,DS[d]['tinr']+1.5,f"{DS[d]['tinr']:.0f}",ha="center",fontsize=6.5)

# 3 Activity (steps) + HR
ax=fig.add_subplot(gs[1,0]); ax2=ax.twinx()
ax.bar(x,[steps[d] for d in DAYS],width=0.6,color="#26a69a",label="steps (daily total)")
ax.set_xticks(x); ax.set_xticklabels(DAYS,fontsize=8); ax.set_ylabel("steps")
ax2.set_ylabel("peak HR (bpm)",color="#5e35b1"); ax2.set_ylim(60,150)
ax2.plot(x,[hr_peak[d] for d in DAYS],"D-",color="#5e35b1",label="peak HR")
for i,d in enumerate(DAYS):
    if hr_peak[d] is not None:
        ax2.annotate(f"{hr_peak[d]:.0f}",(i,hr_peak[d]),textcoords="offset points",xytext=(0,6),ha="center",fontsize=6.5,color="#5e35b1",fontweight="bold")
h1,l1=ax.get_legend_handles_labels(); h2,l2=ax2.get_legend_handles_labels(); ax.legend(h1+h2,l1+l2,fontsize=6.5,loc="upper left")
ax.set_title("Activity (steps) + heart rate",fontweight="bold")
ax.text(0.5,-0.27,"high steps + high HR = aerobic load → glycogen depletion (next-day sensitivity).  "
        f"HR feed partial ({min(hr_cov.values())}–{max(hr_cov.values())}% of cycles).",
        transform=ax.transAxes,ha="center",fontsize=6.2,style="italic",color="#555")

# 4 AGP
ax=fig.add_subplot(gs[1,1]); hrs=list(range(24))
q=lambda p:[ (np.percentile(hourly[h],p)/18 if hourly[h] else np.nan) for h in hrs]
ax.fill_between(hrs,q(10),q(90),color="#90caf9",alpha=0.4,label="10–90%")
ax.fill_between(hrs,q(25),q(75),color="#42a5f5",alpha=0.5,label="25–75%")
ax.plot(hrs,q(50),color="#1565c0",lw=2,label="median")
ax.axhspan(3.9,10,color="#a5d6a7",alpha=0.25); ax.axhline(3.9,color="#e53935",lw=0.8,ls="--")
ax.set_xticks(range(0,24,3)); ax.set_xlabel("hour (BST)"); ax.set_ylabel("glucose (mmol/L)"); ax.set_ylim(2,16)
ax.set_title("Glucose by time of day (5-day AGP)",fontweight="bold"); ax.legend(fontsize=6.5,loc="upper right")

# 5 Pooled standard range bars (TIR / TITR / TINR) — standard vertical stacked presentation
ax=fig.add_subplot(gs[2,0])
def bar3(ax,xpos,below_v,below_lo,ingreen,above,label,pct):
    # below split into <54-equivalent (dark) + rest (red); single green; above (amber)
    ax.bar(xpos,below_v,color='#8e24aa',width=0.55)
    ax.bar(xpos,below_lo,bottom=below_v,color=C_LO,width=0.55)
    ax.bar(xpos,ingreen,bottom=below_v+below_lo,color=C_TIR,width=0.55)
    ax.bar(xpos,above,bottom=below_v+below_lo+ingreen,color=C_HI,width=0.55)
    ax.text(xpos,below_v+below_lo+ingreen/2,f"{pct:.0f}%",ha="center",va="center",color="white",fontweight="bold",fontsize=10)
    ax.text(xpos,103,label,ha="center",fontsize=8,fontweight="bold")
# TIR
bar3(ax,0,P['vlo'],P['lo'],P['tir'],P['hi']+P['vhi'],"TIR\n70–180",P['tir'])
# TITR: below=<70(<54 dark + 54-70), green 70-140, above >140
titr_above=100-(P['vlo']+P['lo']+P['titr'])
bar3(ax,1,P['vlo'],P['lo'],P['titr'],titr_above,"TITR\n70–140",P['titr'])
# TINR: below=<63, green 63-140, above >140
below63=100*sum(1 for d in DAYS for v in byday[d] if v<63)/ (P['n'] or 1)
tinr_above=100-(below63+P['tinr'])
bar3(ax,2,below63*0.45,below63*0.55,P['tinr'],tinr_above,"TINR\n63–140",P['tinr'])
ax.set_xlim(-0.6,2.6); ax.set_ylim(0,108); ax.set_xticks([]); ax.set_ylabel("% of time")
ax.set_title("Period ranges (standard bars)",fontweight="bold",loc="left")
ax.text(0.5,-0.10,"green = in range • purple/red = below • amber = above",transform=ax.transAxes,ha="center",fontsize=6.3,style="italic",color="#555")

# 6 Summary stats
ax=fig.add_subplot(gs[2,1]); ax.axis("off")
ax.set_title("Period summary  (5 days, n=%d)"%P['n'],fontweight="bold")
rows=[("mean glucose",f"{P['mean']:.0f} mg/dL ({P['mean']/18:.1f} mmol/L)"),
      ("TIR  70–180",f"{P['tir']:.1f}%"),("TITR 70–140",f"{P['titr']:.1f}%"),("TINR 63–140",f"{P['tinr']:.1f}%"),
      ("time <70",f"{P['lo']+P['vlo']:.1f}%"),("time <54",f"{P['vlo']:.1f}%"),("time >180",f"{P['hi']+P['vhi']:.1f}%"),
      ("TDD",f"~{np.mean([tdd[d] for d in DAYS if tdd[d]>0]):.0f} U/day"),
      ("activity",f"~{min(steps.values())//1000}–{max(steps.values())//1000}k steps/day"),
      ("HR peak",f"{min(hr_peaks)}–{max(hr_peaks)} bpm (partial feed)")]
y=0.90; dy=0.092
for k,v in rows:
    ax.text(0.02,y,k,ha="left",va="center",fontsize=8.7,color="#555",transform=ax.transAxes)
    ax.text(0.98,y,v,ha="right",va="center",fontsize=8.7,fontweight="bold",transform=ax.transAxes)
    y-=dy

fig.savefig("Boost-Festival-Summary-2026-06-18_22.png",bbox_inches="tight")
try: fig.savefig("Boost-Festival-Summary-2026-06-18_22.pdf",bbox_inches="tight")
except Exception as e: print("pdf skip",e)
print("charts written")
