#!/usr/bin/env python3
"""Generate Ketchup Player v5.1 mockup HTML from Palette.kt tokens (no raster posters)."""
from __future__ import annotations

from pathlib import Path

OUT = Path(__file__).resolve().parent / "ketchup-player-makiety-v5.1.html"
LOGO = "../../../branding/source/ketchup-player-logo.jpg"

# Tokeny 1:1 z core/designsystem/Palette.kt + AccentId (v5).
# Własny: hue=195 (DEFAULT_HUE), AccentFit.fromHsl(s, l) jak w Palettes.custom.
TOKENS = """
.theme-morski{
  --bg:#0B1112; --bgimg:radial-gradient(1200px 700px at 18% -10%,#1E2B2F 0%,transparent 58%),linear-gradient(180deg,#0B1112,#060B0C);
  --s1:rgba(22,32,35,.82); --s2:#162023; --s3:#1E2B2F; --ln:#2B3B40; --past:#0B1112;
  --tx:#E8EFF0; --tx2:#A3B6BA; --sc:6,11,12; --osd:6,11,12;
  --rad:20px; --railbg:rgba(11,17,18,.78); --railbd:1px solid rgba(43,59,64,.9); --panbd:none;
  --chlbg:#E6EEEF; --chltx:#0B1112;
  --acc:#35C2B4; --accf:#35C2B4; --onacc:#0B0B0B; --glow:rgba(53,194,180,.38);
  --grn:#3DBE4B; --danger:#D4534A; --warn:#E3A857;
}
.theme-grafit{
  --bg:#121315; --bgimg:radial-gradient(1200px 700px at 18% -10%,#24272B 0%,transparent 58%),linear-gradient(180deg,#121315,#0A0B0C);
  --s1:rgba(26,28,31,.82); --s2:#1A1C1F; --s3:#24272B; --ln:#33373D; --past:#121315;
  --tx:#ECEEF0; --tx2:#A9AFB6; --sc:10,11,12; --osd:10,11,12;
  --rad:18px; --railbg:rgba(18,19,21,.78); --railbd:1px solid rgba(51,55,61,.9); --panbd:none;
  --chlbg:#E9ECEF; --chltx:#121315;
  --acc:#35C2B4; --accf:#35C2B4; --onacc:#0B0B0B; --glow:rgba(53,194,180,.38);
  --grn:#3DBE4B; --danger:#D4534A; --warn:#E3A857;
}
.theme-czern{
  --bg:#000000; --bgimg:none;
  --s1:#111111; --s2:#111111; --s3:#262626; --ln:#262626; --past:#000000;
  --tx:#F2F2F2; --tx2:#9E9E9E; --sc:0,0,0; --osd:0,0,0;
  --rad:6px; --railbg:#111111; --railbd:1px solid #262626; --panbd:1px solid #262626;
  --chlbg:#1C1C1C; --chltx:#F2F2F2;
  --acc:#EDEDED; --accf:#EDEDED; --onacc:#0B0B0B; --glow:rgba(237,237,237,.22);
  --grn:#3DBE4B; --danger:#D4534A; --warn:#E3A857;
}
.theme-wlasny{
  --bg:#0C1113; --bgimg:radial-gradient(1200px 700px at 18% -10%,#212C30 0%,transparent 58%),linear-gradient(180deg,#0C1113,#06090A);
  --s1:rgba(22,31,34,.82); --s2:#161F22; --s3:#212C30; --ln:#334247; --past:#0C1113;
  --tx:#EDF1F2; --tx2:#AFBCC0; --sc:6,9,10; --osd:6,9,10;
  --rad:20px; --railbg:rgba(12,17,19,.78); --railbd:1px solid rgba(51,66,71,.9); --panbd:none;
  --chlbg:#E7EDEF; --chltx:#0C1113;
  --acc:#35C2B4; --accf:#35C2B4; --onacc:#0B0B0B; --glow:rgba(53,194,180,.38);
  --grn:#3DBE4B; --danger:#D4534A; --warn:#E3A857;
}
"""

CSS = r"""
*{box-sizing:border-box}
html,body{margin:0;background:#07080a;color:var(--tx);font-family:Roboto,"Noto Sans",Arial,sans-serif}
header.doc{padding:20px 28px 8px;color:#cfd6df;font-size:15px;display:flex;flex-wrap:wrap;gap:10px 16px;align-items:center}
header.doc a{color:#9ec9d4;text-decoration:none}
header.doc a.on{color:#fff;border-bottom:2px solid var(--acc)}
header.doc span{opacity:.7}
.note{margin:8px 28px 32px;max-width:1200px;color:#b7c2ce;font-size:15px;line-height:1.45}
.stage{background:#050608;display:inline-block;margin:0 28px 8px;border-radius:8px;overflow:hidden}
.frame{position:relative;overflow:hidden;color:var(--tx);background:var(--bg);background-image:var(--bgimg);font-size:24px}
.tv1080{width:1920px;height:1080px}
.tv720{width:1280px;height:720px;font-size:18px}
.phone{width:1080px;height:1920px;font-size:32px}
.rail{position:absolute;left:96px;top:54px;bottom:54px;width:104px;background:var(--railbg);border:var(--railbd);border-radius:var(--rad);padding:16px 12px;display:flex;flex-direction:column;gap:6px;z-index:5}
.tv720 .rail{left:56px;top:32px;bottom:32px;width:72px;padding:10px 8px;gap:4px}
.mark{height:56px;border-radius:50%;background:#111217 center/cover no-repeat;margin:0 auto 8px;width:56px}
.tv720 .mark{height:40px;width:40px}
.ri{height:72px;border-radius:14px;display:flex;flex-direction:column;align-items:center;justify-content:center;color:var(--tx2);position:relative}
.tv720 .ri{height:48px;border-radius:10px}
.ri em{display:none}
.ri.act{background:var(--s3);color:var(--tx)}
.ri .ng{position:absolute;top:10px;right:14px;width:10px;height:10px;background:var(--grn);border-radius:50%}
.rsep{height:1px;background:var(--ln);margin:6px 8px}
.ct{position:absolute;left:240px;right:96px;top:54px;bottom:54px}
.tv720 .ct{left:152px;right:56px;top:32px;bottom:32px}
.topbar{display:flex;align-items:center;gap:32px;height:72px}
.tv720 .topbar{height:52px;gap:16px}
.topbar h1{margin:0;font-size:40px;font-weight:600}
.tv720 .topbar h1{font-size:28px}
.tb-r{margin-left:auto;display:flex;align-items:center;gap:20px;color:var(--tx2)}
.vpn{font-size:22px}.clock{font-size:30px}
.tv720 .vpn{font-size:16px}.tv720 .clock{font-size:20px}
.f{transform:scale(1.08);outline:4px solid var(--acc);outline-offset:4px;box-shadow:0 0 40px var(--glow);z-index:3;position:relative}
.kick{font-size:22px;letter-spacing:.12em;text-transform:uppercase;color:var(--tx2);font-weight:600}
.tv720 .kick{font-size:16px}
h2{margin:0 0 8px;font-size:44px;font-weight:650}
.tv720 h2{font-size:30px}
.meta,.src,.next{color:var(--tx2)}
p{margin:8px 0 16px;font-size:24px;line-height:1.35;max-width:52ch;color:var(--tx)}
.tv720 p{font-size:18px}
.btn{display:inline-flex;align-items:center;gap:10px;padding:16px 28px;border-radius:14px;background:var(--s2);font-size:26px;margin-right:12px}
.tv720 .btn{padding:10px 16px;font-size:18px;border-radius:10px}
.btn.pri{background:var(--accf);color:var(--onacc)}
.btn.sm{font-size:22px;padding:12px 20px}
.row{display:flex;gap:20px;overflow:visible;padding:12px 8px 8px}
.rowh{display:flex;align-items:center;justify-content:space-between;margin:22px 0 12px}
.rowh b{font-size:28px}
.tv720 .rowh{margin:12px 0 8px}.tv720 .rowh b{font-size:20px}
.ph{border-radius:12px;display:flex;align-items:flex-end;padding:14px;color:#fff;position:relative;overflow:hidden;isolation:isolate}
.ph .ptitle{font-weight:700;font-size:22px;text-shadow:0 1px 8px rgba(0,0,0,.55);z-index:1}
.tv720 .ph .ptitle{font-size:16px}
.ph .mono{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font-weight:800;font-size:42px;opacity:.28;letter-spacing:.04em}
.pb{position:absolute;left:12px;right:12px;bottom:10px;height:6px;background:rgba(255,255,255,.25);border-radius:4px;z-index:1}
.pb s{display:block;height:100%;background:var(--acc);border-radius:4px}
.pc{flex:0 0 280px}
.pc .ph{height:158px}
.pc .cap,.po .cap{margin-top:8px}
.pc .cap b,.po .cap b{display:block;font-size:22px;line-height:1.25}
.pc .cap span,.po .cap span{display:block;color:var(--tx2);font-size:18px;line-height:1.3}
.po{flex:0 0 168px}
.po .ph{height:252px}
.tv720 .pc{flex:0 0 180px}.tv720 .pc .ph{height:104px}
.tv720 .po{flex:0 0 112px}.tv720 .po .ph{height:168px}
.tv720 .pc .cap b{font-size:16px}.tv720 .pc .cap span{font-size:15px}
.badge{position:absolute;top:10px;right:10px;background:var(--grn);color:#06210c;font-size:16px;font-weight:700;padding:4px 8px;border-radius:8px;z-index:1}
.hero{position:relative;height:340px;border-radius:20px;overflow:hidden;margin-bottom:8px}
.tv720 .hero{height:200px;border-radius:14px}
.hero .bg{position:absolute;inset:0}
.hero .hg{position:absolute;inset:0;background:linear-gradient(90deg,rgba(var(--sc),.92) 0%,rgba(var(--sc),.55) 48%,rgba(var(--sc),.2) 100%)}
.hero .hi{position:absolute;left:40px;right:360px;bottom:28px}
.tv720 .hero .hi{left:24px;right:220px;bottom:16px}
.hero .hpo{position:absolute;right:40px;top:28px;width:220px}
.hero .hpo .ph{height:230px}
.tv720 .hero .hpo{right:20px;top:16px;width:140px}.tv720 .hero .hpo .ph{height:168px}
/* live */
.cols{display:grid;grid-template-columns:280px 1fr 420px;gap:20px;height:calc(100% - 80px)}
.tv720 .cols{grid-template-columns:190px 1fr 280px;gap:12px;height:calc(100% - 56px)}
.panel{background:var(--s1);border:var(--panbd);border-radius:var(--rad);padding:18px;overflow:visible}
.tv720 .panel{padding:12px}
.panel h3{margin:0 0 12px;font-size:22px;color:var(--tx2);font-weight:600}
.gi,.li{display:flex;align-items:center;gap:12px;padding:12px 14px;border-radius:12px;margin-bottom:6px}
.gi b{flex:1}.gi em{color:var(--tx2);font-style:normal}
.li .num{width:48px;color:var(--tx2)}
.chl{background:var(--chlbg);color:var(--chltx);font-size:16px;font-weight:800;padding:8px 10px;border-radius:8px;min-width:72px;text-align:center}
.li .grow{flex:1}.li .grow b{display:block}.li .grow span{color:var(--tx2);font-size:20px}
.tv720 .li .grow span{font-size:15px}.tv720 .chl{font-size:13px;min-width:52px;padding:5px 6px}
.minibar{height:5px;background:rgba(255,255,255,.15);border-radius:4px;margin-top:6px}
.minibar s{display:block;height:100%;background:var(--acc)}
.liveb{display:inline-block;background:var(--acc);color:var(--onacc);font-size:16px;font-weight:700;padding:4px 8px;border-radius:6px}
/* epg */
.tabs{display:flex;gap:10px}
.tab{padding:10px 18px;border-radius:999px;background:var(--s2);font-size:22px}
.tv720 .tab{padding:6px 12px;font-size:16px}
.tab.on{background:var(--acc);color:var(--onacc)}
.epgd{display:flex;gap:20px;align-items:center;margin:8px 0 14px}
.epgd .ph{width:160px;height:90px;flex:none}
.epgd h2{font-size:36px}
.grid-epg{display:grid;grid-template-columns:280px 1fr;gap:0}
.tv720 .grid-epg{grid-template-columns:190px 1fr}
.etimes{display:grid;grid-template-columns:repeat(6,1fr);color:var(--tx2);font-size:20px;padding:0 0 8px 280px}
.tv720 .etimes{padding-left:190px;font-size:15px}
.erow{display:contents}
.ech{display:flex;align-items:center;gap:10px;padding:8px 10px;border-top:1px solid var(--ln);min-height:72px}
.ecs{position:relative;border-top:1px solid var(--ln);height:72px}
.ec{position:absolute;top:8px;bottom:8px;background:var(--s2);border-radius:10px;padding:8px 10px;overflow:hidden}
.ec b{display:block;font-size:20px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.ec span{color:var(--tx2);font-size:18px}
.tv720 .ecs,.tv720 .ech{min-height:48px;height:48px}
.tv720 .ec b{font-size:15px}.tv720 .ec span{font-size:13px}
.nowline{position:absolute;top:0;bottom:0;width:3px;background:var(--acc);z-index:2}
/* vod */
.vod{display:grid;grid-template-columns:240px 1fr;gap:20px;height:calc(100% - 80px)}
.tv720 .vod{grid-template-columns:160px 1fr;gap:12px;height:calc(100% - 56px)}
.sbox{background:var(--s2);border-radius:14px;padding:16px 20px;margin-bottom:16px}
.chips{display:flex;gap:10px;flex-wrap:wrap;margin-bottom:16px}
.chip{padding:10px 16px;border-radius:999px;background:var(--s2);font-size:20px}
.chip.on{background:var(--acc);color:var(--onacc)}
.g6{display:grid;grid-template-columns:repeat(6,1fr);gap:16px}
.tv720 .g6{gap:10px}
.g6 .ph{height:240px}
.tv720 .g6 .ph{height:150px}
.g6 .cap{margin-top:8px;font-size:20px}
.g6 .cap span{display:block;color:var(--tx2);font-size:18px}
.tv720 .g6 .cap{font-size:15px}.tv720 .g6 .cap span{font-size:14px}
/* details */
.eps{display:flex;gap:16px;overflow:hidden}
.eps .ph{width:260px;height:146px}
.sim .ph{width:160px;height:90px}
/* player */
.player{position:absolute;inset:0}
.player .bg{position:absolute;inset:0}
.ogt{position:absolute;inset:0;background:linear-gradient(180deg,rgba(var(--osd),.82),transparent 36%,transparent 58%,rgba(var(--osd),.88))}
.osdt{position:absolute;left:96px;right:96px;top:54px;display:flex;justify-content:space-between}
.tv720 .osdt{left:56px;right:56px;top:32px}
.toast{position:absolute;right:96px;top:240px;background:rgba(var(--osd),.92);border-left:5px solid var(--acc);padding:18px 24px;border-radius:12px;max-width:640px}
.srcb{background:rgba(var(--osd),.7);padding:8px 14px;border-radius:10px}
.osdb{position:absolute;left:96px;right:96px;bottom:54px}
.tv720 .osdb{left:56px;right:56px;bottom:32px}
.seek{display:flex;align-items:center;gap:20px;margin-bottom:22px}
.seek .track{flex:1;height:8px;background:rgba(255,255,255,.22);border-radius:8px;position:relative}
.seek .track s{display:block;height:100%;width:45%;background:var(--acc);border-radius:8px}
.seek .track u{position:absolute;top:-7px;left:45%;width:22px;height:22px;margin-left:-11px;border-radius:50%;background:#fff}
.ctl{display:flex;align-items:center;gap:12px}
.cb{width:72px;height:72px;border-radius:50%;background:rgba(var(--osd),.7);display:flex;align-items:center;justify-content:center}
.tv720 .cb{width:48px;height:48px}
.cb svg{width:32px;height:32px;stroke:currentColor;fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}
.cb.f{background:var(--accf);color:var(--onacc)}
.sp{flex:1}
/* settings */
.set{display:grid;grid-template-columns:320px 1fr;gap:20px;height:calc(100% - 80px)}
.tv720 .set{grid-template-columns:220px 1fr;height:calc(100% - 56px)}
.opt{display:flex;align-items:center;justify-content:space-between;padding:16px 18px;border-radius:12px;margin-bottom:8px;background:transparent}
.opt b{display:block}.opt span{color:var(--tx2);font-size:20px}
.tcard{width:220px;background:var(--s2);border-radius:16px;padding:12px;display:inline-block;margin-right:14px;vertical-align:top}
.tcard .mini{height:90px;border-radius:10px;margin-bottom:8px}
.swc{width:56px;height:56px;border-radius:50%;display:inline-block;margin-right:16px;vertical-align:middle}
/* onboarding */
.onb{position:absolute;inset:54px 96px;display:grid;grid-template-columns:640px 1fr;gap:72px}
.tv720 .onb{inset:32px 56px;grid-template-columns:380px 1fr;gap:28px}
.onl img{width:100%;border-radius:16px;background:#111217}
.stp{padding:12px 0;color:var(--tx2);font-size:26px}
.stp.act{color:var(--tx);border-left:4px solid var(--acc);padding-left:12px}
.stp.done{color:var(--tx2)}
.tgrid{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;margin:20px 0}
.tc{background:var(--s2);border-radius:16px;padding:22px}
.tc b{display:block;font-size:26px;margin-bottom:6px}
.tc span{color:var(--tx2);font-size:20px}
.inp{background:var(--s2);border-radius:12px;padding:16px 18px;font-size:26px;margin:8px 0 12px}
.warn{color:var(--warn);font-size:20px;margin:0 0 12px}
.qr{display:flex;align-items:center;gap:16px;color:var(--tx2);font-size:22px}
.qrb{width:72px;height:72px;border-radius:8px;background:
  linear-gradient(#EAF2FA 0 0) 0 0/12px 12px no-repeat,
  linear-gradient(#EAF2FA 0 0) 60px 0/12px 12px no-repeat,
  linear-gradient(#EAF2FA 0 0) 0 60px/12px 12px no-repeat,
  repeating-linear-gradient(90deg,#EAF2FA 0 8px,transparent 8px 14px),
  repeating-linear-gradient(#163152 0 8px,transparent 8px 14px);background-color:#0A1A2F}
/* pin */
.pinbox{position:absolute;inset:0;background:rgba(6,18,36,.72);display:flex;align-items:center;justify-content:center}
.pinc{width:640px;background:var(--s2);border-radius:24px;padding:48px;text-align:center}
.dots{display:flex;gap:16px;justify-content:center;margin:28px 0}
.dot{width:22px;height:22px;border-radius:50%;border:2px solid var(--tx2)}
.dot.on{background:var(--acc);border-color:var(--acc)}
.keys{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;max-width:360px;margin:0 auto}
.key{background:var(--s3);border-radius:14px;padding:18px 0;font-size:28px}
/* empty / error / loading */
.center{height:calc(100% - 80px);display:flex;align-items:center;justify-content:center;text-align:center}
.skel{background:linear-gradient(90deg,var(--s2),var(--s3),var(--s2));background-size:200% 100%;border-radius:14px}
/* phone */
.phone .phead{position:absolute;left:48px;right:48px;top:64px;display:flex;align-items:center;gap:20px}
.phone .phead h1{font-size:52px;margin:0}
.phone .pbody{position:absolute;left:48px;right:48px;top:180px;bottom:200px;overflow:hidden}
.phone .pnav{position:absolute;left:32px;right:32px;bottom:40px;height:132px;background:var(--railbg);border:var(--railbd);border-radius:28px;display:flex;justify-content:space-around;align-items:center}
.phone .pn{display:flex;flex-direction:column;align-items:center;gap:6px;color:var(--tx2);font-size:22px}
.phone .pn.act{color:var(--acc)}
.phone .pn .ng{width:10px;height:10px;background:var(--grn);border-radius:50%;position:absolute;margin-left:28px;margin-top:-4px}
.phone .row{gap:16px}
.phone .pc{flex:0 0 400px}.phone .pc .ph{height:226px}
.phone .po{flex:0 0 260px}.phone .po .ph{height:390px}
.phone .hero{height:420px}
.phone .sbox{font-size:28px;padding:22px}
.phone .g3{display:grid;grid-template-columns:repeat(3,1fr);gap:16px}
.phone .g3 .ph{height:360px}
.phone .btn{font-size:30px;padding:20px 28px}

.listb{display:inline-flex;align-items:center;gap:10px;background:rgba(0,0,0,.35);border:1px solid var(--ln);border-radius:999px;padding:8px 14px;font-size:20px;max-width:300px;color:var(--tx)}
.listb i{width:10px;height:10px;border-radius:50%;background:var(--grn);flex:none}
.listb.warn i{background:var(--warn)}
.pills{display:flex;gap:8px;flex-wrap:wrap;margin:8px 0}
.pill{display:inline-flex;align-items:center;gap:6px;background:var(--s2);border-radius:999px;padding:6px 12px;font-size:18px}
.pill b{font-weight:700}
.pill em{color:var(--tx2);font-style:normal;font-size:16px}
.newb{position:absolute;top:10px;right:10px;display:inline-flex;align-items:center;gap:6px;background:rgba(0,0,0,.62);color:#fff;font-size:15px;font-weight:700;padding:5px 10px;border-radius:999px;z-index:1}
.newb i{width:8px;height:8px;border-radius:50%;background:var(--grn)}
.selbar{box-shadow:inset 4px 0 0 var(--acc)}
.glassbar{background:rgba(var(--osd),.55);backdrop-filter:blur(16px);border:1px solid rgba(255,255,255,.08);border-radius:var(--rad);padding:16px 18px}
.info{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin-top:12px}
.info div{background:rgba(255,255,255,.06);border-radius:12px;padding:10px 12px}
.info b{display:block;font-size:22px}
.info span{color:var(--tx2);font-size:16px}
.hint{background:var(--s2);border-left:4px solid var(--warn);padding:12px 16px;border-radius:12px;color:var(--tx);font-size:20px;margin:12px 0}
.tgrid2{display:grid;grid-template-columns:1fr 1fr;gap:14px;max-width:520px}
.contrast{margin-top:16px;padding:16px;background:var(--s2);border-radius:var(--rad)}
.chcount{color:var(--tx2);font-size:20px;margin:0 0 10px}
.timechips{display:flex;gap:10px;margin:10px 0 4px}
/* focus demo */
.fd{display:flex;gap:40px;align-items:flex-end;margin-top:40px}
.fd .lab{text-align:center;color:var(--tx2);margin-top:12px;font-size:22px}
.wrap{display:none}
.wrap.on{display:block}
"""


def ico(name: str, size: int = 30) -> str:
    paths = {
        "user": '<circle cx="12" cy="8" r="4"/><path d="M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6"/>',
        "search": '<circle cx="11" cy="11" r="7"/><path d="M20 20l-4-4"/>',
        "home": '<path d="M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/>',
        "live": '<rect x="2" y="5" width="20" height="14" rx="2"/><path d="M8 2l4 3 4-3"/>',
        "guide": '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4M7 14h4M13 14h4"/>',
        "movies": '<rect x="3" y="3" width="18" height="18" rx="2"/><path d="M7 3v18M17 3v18M3 8h4M3 16h4M17 8h4M17 16h4"/>',
        "series": '<rect x="2" y="7" width="20" height="14" rx="2"/><path d="M6 3h12M9 11l5 3-5 3z"/>',
        "list": '<path d="M12 3l2.8 5.7 6.2.9-4.5 4.4 1 6.2L12 17.3 6.5 20.2l1-6.2L3 9.6l6.2-.9z"/>',
        "gear": '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>',
        "prev": '<path d="M19 12H7"/><path d="M11 6l-6 6 6 6"/><path d="M5 6v12"/>',
        "rew": '<path d="M11 7l-6 5 6 5V7z"/><path d="M19 7l-6 5 6 5V7z"/>',
        "pause": '<path d="M8 6h3v12H8zM13 6h3v12h-3z" fill="currentColor" stroke="none"/>',
        "ff": '<path d="M5 7l6 5-6 5V7z"/><path d="M13 7l6 5-6 5V7z"/>',
        "next": '<path d="M5 12h12"/><path d="M13 6l6 6-6 6"/><path d="M19 6v12"/>',
    }
    return (
        f'<svg viewBox="0 0 24 24" width="{size}" height="{size}" fill="none" '
        f'stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">'
        f'{paths[name]}</svg>'
    )


PAL = [
    ("#163152", "#0A1A2F", "#33B5C4"),
    ("#1e3a2f", "#0c1c16", "#3DBE4B"),
    ("#3a2430", "#1a1016", "#E3A857"),
    ("#243044", "#101820", "#6AA6F2"),
    ("#2a2640", "#141222", "#B09AF2"),
    ("#3a3020", "#1a160e", "#E8C547"),
    ("#203040", "#101820", "#A6BCD6"),
    ("#2c1f28", "#140e12", "#D4534A"),
    ("#1a3340", "#0c1a22", "#33B5C4"),
    ("#263028", "#121814", "#7dba7a"),
    ("#332840", "#18141f", "#c7a0e8"),
    ("#2a3828", "#141c12", "#c4d48a"),
]


def poster(title: str, extra: str = "", wide: bool = False, progress: float | None = None, badge: str = "", label: bool = True) -> str:
    i = sum(map(ord, title)) % len(PAL)
    a, b, c = PAL[i]
    mon = "".join(w[0] for w in title.split()[:2]).upper()
    deg = 140 + (i * 17) % 50
    pb = f'<div class="pb"><s style="width:{int(progress*100)}%"></s></div>' if progress is not None else ""
    bd = f'<span class="newb"><i></i>{badge}</span>' if badge else ""
    lab = f'<span class="ptitle">{title}</span>' if label else ""
    return (
        f'<div class="ph" style="background:linear-gradient({deg}deg,{a},{b});box-shadow:inset 0 0 0 1px {c}44">'
        f'<span class="mono" style="color:{c}">{mon}</span>'
        f'{bd}{lab}{pb}{extra}</div>'
    )


def rail(active: str, klass: str = "") -> str:
    items = [
        ("user", "Profil", "profile"),
        ("search", "Szukaj", "search"),
        ("home", "Home", "home"),
        ("live", "Na żywo", "live"),
        ("movies", "Filmy", "movies"),
        ("series", "Seriale", "series"),
        ("list", "Moja lista", "mylist"),
        ("gear", "Ustawienia", "settings"),
    ]
    parts = [f'<nav class="rail"><div class="mark" style="background-image:url({LOGO})"></div>']
    for ic, label, key in items:
        act = " act" if key == active else ""
        extra = '<i class="ng"></i>' if key == "series" else ""
        if key == "mylist":
            parts.append('<div class="rsep"></div>')
        parts.append(f'<div class="ri{act}"><b>{ico(ic)}</b><em>{label}</em>{extra}</div>')
    parts.append("</nav>")
    return "".join(parts)


def top(title: str, extra: str = "") -> str:
    badge = '<span class="listb"><i></i>Dom · połączenia 1 z 2</span>'
    return (
        f'<div class="topbar"><h1>{title}</h1>{extra}'
        f'<div class="tb-r">{badge}<span class="clock">19:42</span></div></div>'
    )


TABS_EPG = '<div class="tabs"><span class="tab on">Siatka</span><span class="tab">Radar</span><span class="tab">Moje zainteresowania</span></div>'
TABS_RADAR = '<div class="tabs"><span class="tab">Siatka</span><span class="tab on">Radar</span><span class="tab">Moje zainteresowania</span></div>'

# --- Catalog (single source; used on every screen) ---
# Now = Saturday 31.10.2026 19:42
MOVIES = [
    ("Złote miasto", "2024 · ★ 8,6"),
    ("Cichy świadek", "2023 · ★ 7,5"),
    ("Dom nad jeziorem", "2022 · ★ 6,4"),
    ("Szklany dom", "2022 · ★ 9,1"),
    ("Echo", "2021 · ★ 8,0"),
    ("Ostatni pociąg", "2021 · ★ 9,3"),
    ("Granica", "2020 · ★ 7,9"),
    ("Nocny lot", "2020 · ★ 8,2"),
    ("Piąta pora roku", "2019 · ★ 6,8"),
    ("Czerwona glina", "2019 · IMDb 7,1"),
    ("Most nad rzeką", "2018 · ★ 6,0"),
    ("Burza", "2018 · ★ 7,7"),
]
SERIES = [
    ("Komisariat 7", "2020 · 3 sez.", "1"),
    ("Dynastia", "2021 · 4 sez.", "3"),
    ("Wyspa", "2022 · 5 sez.", "2"),
    ("Kod zero", "2023 · 6 sez.", "1"),
    ("Ród", "2024 · 3 sez.", "4"),
    ("Szpital Miejski", "2025 · 4 sez.", "3"),
    ("Nocna zmiana", "2020 · 5 sez.", ""),
    ("Przystań", "2021 · 6 sez.", "1"),
    ("Linia frontu", "2022 · 3 sez.", ""),
    ("Pensjonat", "2023 · 4 sez.", ""),
    ("Archiwum", "2024 · 5 sez.", ""),
    ("Rejs", "2025 · 6 sez.", "2"),
]
CHANNELS = [
    ("101", "JEDEN", "Kanał Jeden", "Wiadomości wieczorne", "19:30–20:00", 0.40),
    ("102", "INFO24", "Info 24", "Rozmowa dnia", "19:15–20:15", 0.45),
    ("103", "SPORT+", "Sport Plus", "Magazyn piłkarski", "19:00–20:30", 0.47),
    ("104", "KINO", "Kino Premium", "Film fabularny", "18:45–20:50", 0.46),
    ("105", "MINI", "Mini TV", "Bajki na dobranoc", "19:40–20:00", 0.10),
    ("106", "DOKU", "Doku HD", "Natura z bliska", "19:00–20:00", 0.70),
    ("107", "HITS", "Hity Muzyka", "Lista przebojów", "19:00–21:00", 0.35),
]
# EPG cells: start hour as float from 19:00, duration hours, title — unique per channel genre
EPG = {
    "101": [("19:00", 0.5, "Wiadomości"), ("19:30", 0.5, "Wiadomości wieczorne"), ("20:00", 0.5, "Magazyn ekonomiczny"), ("20:30", 1.0, "Reportaż: Ludzie gór")],
    "102": [("19:00", 0.25, "Pogoda i komunikacja"), ("19:15", 1.0, "Rozmowa dnia"), ("20:15", 0.75, "Serwis informacyjny"), ("21:00", 0.5, "Podsumowanie dnia")],
    "103": [("19:00", 1.5, "Magazyn piłkarski"), ("20:30", 1.0, "Liga: skrót kolejki")],
    "104": [("18:45", 2.083, "Film fabularny"), ("20:50", 1.0, "Kino nocne: Ostatni pociąg")],
    "105": [("19:00", 0.666, "Klub odkrywców"), ("19:40", 0.333, "Bajki na dobranoc"), ("20:00", 1.0, "Teatrzyk Mini")],
    "106": [("19:00", 1.0, "Natura z bliska"), ("20:00", 1.0, "Warsztat rzemieślnika"), ("21:00", 0.5, "Notatki podróżnika")],
    "107": [("19:00", 2.0, "Lista przebojów"), ("21:00", 0.5, "Teledyski wieczorne")],
}



def rating_pills(*pairs: tuple[str, str]) -> str:
    bits = "".join(f'<span class="pill"><b>{val}</b><em>{src}</em></span>' for src, val in pairs)
    return f'<div class="pills">{bits}</div>'


def epg_left(start: str) -> float:
    h, m = map(int, start.split(":"))
    return ((h * 60 + m) - (19 * 60)) / (3 * 60)  # 19:00-22:00 window displayed? slots 19:00-21:30 = 2.5h
    # Use 19:00 to 22:00 = 3 hours for 6 slots of 30 min: 19:00, 19:30, 20:00, 20:30, 21:00, 21:30


def screen_tv(fid: str, klass: str, inner: str, theme: str = "morski") -> str:
    return (
        f'<section class="wrap" id="{fid}"><div class="stage">'
        f'<div class="frame {klass} theme-{theme}">{inner}</div></div></section>'
    )


def home(klass: str, sid: str) -> str:
    cont = [
        ("Komisariat 7", "S2 E5 · zostało 18 min", 0.62),
        ("Czerwona glina", "1:12 / 2:04", 0.58),
        ("Natura z bliska", "S1 E3 · 25 min", 0.40),
        ("Kanał Jeden", "Wiadomości · na żywo", 0.40),
    ]
    pcs = []
    for i, (t, cap, pr) in enumerate(cont):
        foc = " f" if i == 0 else ""
        pcs.append(f'<div class="pc{foc}">{poster(t, progress=pr, label=False)}<div class="cap"><b>{t}</b><span>{cap}</span></div></div>')
    news = []
    for t, meta, n in SERIES[:7]:
        if not n:
            continue
        news.append(f'<div class="po">{poster(t, badge="nowość", label=False)}<div class="cap"><b>{t}</b><span>S3 · {n} nowe odcinki</span></div></div>')
    live_now = [
        f'<div class="pc">{poster("Rozmowa dnia", label=False)}<div class="cap"><b>Info 24</b><span>Rozmowa dnia · 19:15–20:15</span></div></div>',
        f'<div class="pc">{poster("Wiadomości wieczorne", label=False)}<div class="cap"><b>Kanał Jeden</b><span>Wiadomości wieczorne · 19:30–20:00</span></div></div>',
        f'<div class="pc">{poster("Magazyn piłkarski", label=False)}<div class="cap"><b>Sport Plus</b><span>Magazyn piłkarski · 19:00–20:30</span></div></div>',
    ]
    radar = [
        f'<div class="pc">{poster("Rozmowa dnia", label=False)}<div class="cap"><b>TOP DNIA</b><span>Info 24 · 19:15</span></div></div>',
        f'<div class="pc">{poster("Natura z bliska", label=False)}<div class="cap"><b>Dokument</b><span>Doku HD · 19:00</span></div></div>',
    ]
    inner = rail("home") + f'<main class="ct">{top("Home")}<section class="hero">'
    inner += f'<div class="bg">{poster("Złote miasto", label=False)}</div><div class="hg"></div>'
    inner += f'<div class="hpo">{poster("Złote miasto")}</div>'
    inner += (
        '<div class="hi"><div class="kick">Mam czas na…</div>'
        '<div class="timechips"><span class="chip">30 min</span><span class="chip on">90 min</span>'
        '<span class="chip">2 h</span><span class="chip">Zaskocz mnie</span></div>'
        '<h2>Złote miasto</h2>'
        '<div class="meta">2024 · 2 h 10 min · Dramat, Thriller</div>' + rating_pills(("IMDb", "8,6"), ("TMDB", "8,1"), ("RT", "84%")) +
        "<p>Zespół urbanistów odkrywa pod nowym miastem ślad dawnej cywilizacji. Dwa zdania opisu, czytelne z kanapy.</p>"
        '<span class="btn pri">▶ Oglądaj</span><span class="btn">+ Moja lista</span><span class="btn">Szczegóły</span>'
        "</div></section>"
        f'<div class="rowh"><b>Kontynuuj oglądanie</b></div><div class="row">{"".join(pcs)}</div>'
        f'<div class="rowh"><b>Nowe odcinki ●</b></div><div class="row">{"".join(news)}</div>'
        f'<div class="rowh"><b>Na żywo teraz</b></div><div class="row">{"".join(live_now)}</div>'
        f'<div class="rowh"><b>Radar · TOP DNIA</b></div><div class="row">{"".join(radar)}</div>'
        "</main>"
    )
    return screen_tv(sid, klass, inner)


def live(klass: str, sid: str) -> str:
    groups = [("★ Ulubione", "12"), ("Ostatnie", "8"), ("Wszystkie", "642"), ("Informacje", "24", True), ("Sport", "38"), ("Filmy", "56"), ("Dzieci", "18"), ("Muzyka", "22"), ("Dokument", "30")]
    ghtml = ""
    for g in groups:
        foc = " f" if len(g) == 3 else ""
        name, n = g[0], g[1]
        ghtml += f'<div class="gi{foc}"><b>{name}</b><em>{n}</em></div>'
    # wait, Informacje should be selected not focused? Channel 102 is focused. Group Informacje is active (selected).
    ghtml = ""
    for name, n, *rest in [
        ("★ Ulubione", "12"),
        ("Ostatnie", "8"),
        ("Wszystkie", "642"),
        ("Informacje", "24"),
        ("Sport", "38"),
        ("Filmy", "56"),
        ("Dzieci", "18"),
        ("Muzyka", "22"),
        ("Dokument", "30"),
    ]:
        act = " act" if name == "Informacje" else ""
        # reuse .ri.act style via background
        style = ' style="background:var(--s3)"' if name == "Informacje" else ""
        ghtml += f'<div class="gi"{style}><b>{name}</b><em>{n}</em></div>'
    chs = ""
    for num, code, name, show, hours, pr in CHANNELS:
        foc = " f" if num == "102" else ""
        chs += (
            f'<div class="li{foc}"><span class="num">{num}</span><span class="chl">{code}</span>'
            f'<div class="grow"><b>{name}</b><span>{show}</span><div class="minibar"><s style="width:{int(pr*100)}%"></s></div></div>'
            f'<span class="meta">{hours}</span></div>'
        )
    preview = (
        f'{poster("Rozmowa dnia", label=False)}'
        f'<div class="liveb">NA ŻYWO</div>'
        f'<h2>Rozmowa dnia</h2>'
        f'<div class="meta">102 · Info 24 · 19:15–20:15</div>'
        f'<div class="minibar" style="margin:12px 0"><s style="width:45%"></s></div>'
        f'<p>Goście studia rozmawiają o wydarzeniach dnia. Ten sam opis i godziny są na przewodniku i w odtwarzaczu.</p>'
        f'<div class="next">Następnie: <b>20:15 Serwis informacyjny</b></div>'
        f'<div class="src">Źródło: Lista 1 · Dostawca A</div>'
    )
    inner = rail("live") + f'<main class="ct">{top("Na żywo") + '<div class="chcount">Informacje · 24 z 642 kanałów</div>'}<div class="cols">'
    inner += f'<div class="panel"><h3>Grupy</h3>{ghtml}</div>'
    inner += f'<div class="panel"><h3>Kanały · Informacje</h3>{chs}</div>'
    inner += f'<div class="panel">{preview}</div></div></main>'
    return screen_tv(sid, klass, inner)


def epg_screen(klass: str, sid: str) -> str:
    times = "".join(f"<span>{t}</span>" for t in ["19:00", "19:30", "20:00", "20:30", "21:00", "21:30"])
    rows = ""
    window = 3.0 * 60  # 19:00-22:00 in minutes
    for num, code, name, *_ in CHANNELS:
        rows += f'<div class="ech"><span class="num">{num}</span><span class="chl">{code}</span><div><b>{name}</b></div></div>'
        cells = ""
        for start, dur_h, title in EPG[num]:
            left_min = (int(start[:2]) * 60 + int(start[3:]) - 19 * 60)
            width_min = dur_h * 60
            left_pct = max(0, left_min / window * 100)
            width_pct = width_min / window * 100
            # clip if starts before 19:00
            if left_min < 0:
                width_pct = (width_min + left_min) / window * 100
                left_pct = 0
            foc = ""
            if num == "102" and title == "Rozmowa dnia":
                foc = " f"
            cells += (
                f'<div class="ec{foc}" style="left:{left_pct:.2f}%;width:{width_pct:.2f}%">'
                f"<b>{title}</b><span>{start}</span></div>"
            )
        # now line at 19:42 -> 42 minutes into 180 = 23.33%
        now = 42 / 180 * 100
        rows += f'<div class="ecs">{cells}<div class="nowline" style="left:{now:.2f}%"></div></div>'
    inner = rail("live") + f'<main class="ct">{top("Przewodnik", extra=TABS_EPG)}'
    inner += (
        '<div class="epgd">'
        f'{poster("Rozmowa dnia", label=False)}'
        '<div><div class="kick">Teraz · Info 24</div><h2>Rozmowa dnia</h2>'
        '<div class="meta">19:15–20:15 · Publicystyka · ★ Radar 8,1</div>'
        "<p>Opis zaznaczonej audycji — ten sam program co na Na żywo i w odtwarzaczu (102 · 19:15–20:15).</p>"
        "</div></div>"
        f'<div class="etimes">{times}</div>'
        f'<div class="grid-epg">{rows}</div>'
        "</main>"
    )
    return screen_tv(sid, klass, inner)


def radar(klass: str, sid: str) -> str:
    sections = [
        ("TOP DNIA", [("Rozmowa dnia", "Info 24 · 19:15–20:15 · ★ 8,1"), ("Natura z bliska", "Doku HD · 19:00–20:00 · ★ 7,6"), ("Magazyn piłkarski", "Sport Plus · 19:00–20:30 · ★ 7,2")]),
        ("Teraz", [("Wiadomości wieczorne", "Kanał Jeden · 19:30–20:00"), ("Lista przebojów", "Hity Muzyka · 19:00–21:00"), ("Bajki na dobranoc", "Mini TV · 19:40–20:00")]),
        ("Za chwilę", [("Serwis informacyjny", "Info 24 · 20:15"), ("Liga: skrót kolejki", "Sport Plus · 20:30")]),
    ]
    body = ""
    for title, items in sections:
        cards = "".join(
            f'<div class="pc{" f" if title=="TOP DNIA" and i==0 else ""}">{poster(n)}<div class="cap"><b>{n}</b><span>{m}</span></div></div>'
            for i, (n, m) in enumerate(items)
        )
        body += f'<div class="rowh"><b>{title}</b></div><div class="row">{cards}</div>'
    inner = rail("live") + f'<main class="ct">{top("Przewodnik", extra=TABS_RADAR)}'
    inner += body + "</main>"
    return screen_tv(sid, klass, inner)


def movies(klass: str, sid: str) -> str:
    cats = ["Wszystkie", "Ostatnio dodane", "Akcja", "Komedia", "Dramat", "Dokument", "Dla dzieci", "Polskie"]
    cath = "".join(('<div class="gi" style="background:var(--s3)"><b>'+c+"</b></div>" if c=="Wszystkie" else '<div class="gi"><b>'+c+"</b></div>") for c in cats)
    chips = "".join(f'<span class="chip{" on" if c=="Najnowsze" else ""}">{c}</span>' for c in ["Najnowsze", "A–Z", "Ocena", "Rok"])
    grid = ""
    for i, (t, m) in enumerate(MOVIES):
        year, _, star = m.partition(" · ★ ")
        foc = " f selbar" if i == 0 else ""
        pills = rating_pills(("IMDb", star)) if star else ""
        grid += f'<div class="{foc}">{poster(t, label=False)}<div class="cap"><b>{t}</b><span>{year}</span>{pills}</div></div>'
    inner = rail("movies") + f'<main class="ct">{top("Filmy")}<div class="sbox">Szukaj w: Filmy</div>'
    inner += f'<div class="vod"><div class="panel">{cath}</div><div><div class="chips">{chips}</div><div class="g6">{grid}</div></div></div></main>'
    return screen_tv(sid, klass, inner)


def series(klass: str, sid: str) -> str:
    cats = ["Wszystkie", "Ostatnio dodane", "Akcja", "Komedia", "Dramat", "Dokument", "Dla dzieci", "Polskie"]
    cath = "".join(("<div class=\"gi\" style=\"background:var(--s3)\"><b>%s</b></div>" % c if c=="Wszystkie" else "<div class=\"gi\"><b>%s</b></div>" % c) for c in cats)
    chips = "".join(
        f'<span class="chip{" on" if c=="Najnowsze" else ""}">{c}</span>'
        for c in ["Najnowsze", "A–Z", "Ocena", "Rok", "Z nowymi odcinkami"]
    )
    grid = ""
    for i, (t, m, n) in enumerate(SERIES):
        foc = " f" if i == 0 else ""
        bd = "nowość" if n else ""
        grid += f'<div class="{foc}">{poster(t, badge=bd, label=False)}<div class="cap"><b>{t}</b><span>{m}</span></div></div>'
    inner = rail("series") + f'<main class="ct">{top("Seriale")}<div class="sbox">Szukaj w: Seriale</div>'
    inner += f'<div class="vod"><div class="panel">{cath}</div><div><div class="chips">{chips}</div><div class="g6">{grid}</div></div></div></main>'
    return screen_tv(sid, klass, inner)


def details(klass: str, sid: str) -> str:
    eps = ""
    for i, (title, mins, new) in enumerate([
        ("Powrót na komisariat", "42 min", False),
        ("Świadek z rzeki", "43 min", False),
        ("Cicha zmiana", "44 min", True),
        ("List w szafce", "45 min", True),
        ("Nocna odprawa", "46 min", True),
    ]):
        foc = ""
        badge = "nowość" if new else ""
        eps += f'<div class="pc">{poster(title, badge=badge)}<div class="cap"><b>{i+1}. {title}</b><span>{mins}</span></div></div>'
    sims = "".join(f'<div class="po">{poster(t, label=False)}<div class="cap"><b>{t}</b></div></div>' for t, *_ in SERIES[1:8])
    inner = rail("series") + f'<main class="ct">{top("Serial")}'
    inner += (
        '<div class="kick">Serial</div><h2>Komisariat 7</h2>'
        '<div class="meta">2022–2026 · 3 sezony · Kryminał, Dramat · 16+</div>' + rating_pills(("IMDb", "8,2"), ("TMDB", "7,9")) +
        "<p>Komisariat w nadmorskim mieście prowadzi sprawy, których nikt nie chce ruszać. Cztery linie opisu bez przewijania na 1080p — to ten sam serial co w Kontynuuj oglądanie (S2 E5 / tu wznawiamy S3 E2).</p>"
        '<span class="btn pri f">▶ Wznów S3 E2 · 14:05</span><span class="btn">Od początku</span><span class="btn">Zwiastun</span><span class="btn">✓ Moja lista</span><span class="btn">Źródła (3)</span><div class="hint">Brak kluczy TMDB/OMDb — wpisz je w Ustawieniach → Oceny i opisy, żeby dociągnąć opisy i oceny ze źródłem.</div>'
        '<div class="chips" style="margin-top:22px"><span class="chip">Sezon 1</span><span class="chip">Sezon 2</span><span class="chip on">Sezon 3 ●</span></div>'
        f'<div class="row eps">{eps}</div>'
        f'<div class="rowh"><b>Podobne</b></div><div class="row">{sims}</div>'
        "</main>"
    )
    return screen_tv(sid, klass, inner)


def player(klass: str, sid: str) -> str:
    btns = [
        ("prev", False), ("rew", False), ("pause", True), ("ff", False), ("next", False),
    ]
    ctl = "".join(f'<span class="cb{" f" if foc else ""}">{ico(n, 32)}</span>' for n, foc in btns)
    inner = (
        f'<div class="player"><div class="bg">{poster("Rozmowa dnia", label=False)}</div><div class="ogt"></div>'
        '<div class="osdt"><div><div class="kick">102 · Info 24</div><h2>Rozmowa dnia</h2>'
        '<div class="meta">19:15–20:15 · Następnie: 20:15 Serwis informacyjny</div></div>'
        '<div><span class="srcb">Lista 2 · Dostawca B · bufor 6 s</span><div class="clock" style="text-align:right;margin-top:8px">19:42</div></div></div>'
        '<div class="toast">Przełączono na Lista 2 · Dostawca B (Lista 1 nie odpowiada)</div>'
        '<div class="osdb glassbar"><div class="seek"><span>19:15</span><div class="track"><s></s><u></u></div><span>20:15</span></div>'
        f'<div class="ctl">{ctl}<span class="sp"></span>'
        '<span class="btn sm">Audio</span><span class="btn sm">Napisy</span><span class="btn sm">Źródło</span>'
        '<span class="btn sm">Info</span><span class="btn sm">⋯</span></div>'
        '<div class="info"><div><b>6,2 s</b><span>Bufor</span></div><div><b>14 Mb/s</b><span>Łącze</span></div>'
        '<div><b>92</b><span>Stabilność</span></div><div><b>1 z 2</b><span>Połączenia · Dom</span></div></div></div></div>'
    )
    return screen_tv(sid, klass, inner)


def settings(klass: str, sid: str) -> str:
    cats = ["Listy i dostawcy", "Odtwarzanie", "Telewizja i przewodnik", "Filmy i seriale", "Nagrywanie", "Sieć i VPN", "Profile i kontrola rodzicielska", "Wygląd", "Pilot", "Usługi zewnętrzne", "Dane i pamięć", "O aplikacji"]
    cl = ""
    for c in cats:
        style = ' style="background:var(--s3)"' if c == "Wygląd" else ""
        cl += f'<div class="gi"{style}><b>{c}</b></div>'
    inner = rail("settings") + f'<main class="ct">{top("Ustawienia")}<div class="sbox">Szukaj ustawienia</div><div class="set">'
    inner += f'<div class="panel">{cl}</div><div>'
    inner += '<div class="kick">Ustawienia › Wygląd</div>'
    inner += '<p style="max-width:none">Motywy v5 z Palette.kt: Morski (domyślny), Grafit, Czerń OLED i Własny (odcień 195°). Akcent osobno — zieleń tylko dla nowych odcinków.</p>'
    inner += '<div class="tgrid2">'
    inner += '<div class="tcard f" style="width:auto;margin:0"><div class="mini" style="background:#0B1112;box-shadow:inset 0 0 0 3px #35C2B4"></div><b>Morski</b><span style="color:var(--tx2)">Domyślny · #35C2B4</span></div>'
    inner += '<div class="tcard" style="width:auto;margin:0"><div class="mini" style="background:#121315"></div><b>Grafit</b><span style="color:var(--tx2)">#121315</span></div>'
    inner += '<div class="tcard" style="width:auto;margin:0"><div class="mini" style="background:#000"></div><b>Czerń</b><span style="color:var(--tx2)">OLED · akcent biel</span></div>'
    inner += '<div class="tcard" style="width:auto;margin:0"><div class="mini" style="background:#0C1113"></div><b>Własny</b><span style="color:var(--tx2)">odcień 195°</span></div>'
    inner += '</div>'
    inner += '<div class="contrast"><b>Podgląd akcentu</b><span style="display:block;color:var(--tx2);margin:6px 0 10px">Tekst na przycisku · kontrast 12,6:1 (AA)</span><span class="btn pri">Oglądaj</span></div>'
    inner += '<div class="rowh"><b>Kolor akcentu</b></div>'
    for hex_, name, on in [("#33B5C4", "Turkus", True), ("#EDEDED", "Biel", False), ("#E3A857", "Bursztyn", False), ("#E8C547", "Żółty", False), ("#6AA6F2", "Błękit", False), ("#B09AF2", "Fiolet", False)]:
        ring = "outline:4px solid #fff;outline-offset:3px;" if on else ""
        inner += f'<span class="swc" style="background:{hex_};{ring}"></span>'
    inner += '<div class="opt" style="margin-top:20px;background:var(--s2)"><div><b>Przezroczystość nakładek</b><span>Panele nad wideo i OSD</span></div><em>80% ›</em></div>'
    inner += '<div class="opt" style="background:var(--s2)"><div><b>Gęstość przewodnika</b><span>Liczba kanałów na ekranie EPG</span></div><em>Standard (7) ›</em></div>'
    inner += "</div></div></main>"
    return screen_tv(sid, klass, inner)


def search(klass: str, sid: str) -> str:
    inner = rail("search") + f'<main class="ct">{top("Szukaj")}<div class="sbox f">rozm <span style="opacity:.4">|</span></div>'
    rows = [
        ("Kanały", [("Info 24", "Kanał · 102 · na żywo: Rozmowa dnia")]),
        ("Audycje EPG", [("Rozmowa dnia", "Info 24 · dziś 19:15–20:15")]),
        ("Seriale", [("Komisariat 7", "3 sezony · wznów S3 E2")]),
        ("Filmy", [("Czerwona glina", "2019 · IMDb 7,1")]),
    ]
    for title, items in rows:
        cards = "".join(f'<div class="pc">{poster(n)}<div class="cap"><b>{n}</b><span>{m}</span></div></div>' for n, m in items)
        inner += f'<div class="rowh"><b>{title}</b></div><div class="row">{cards}</div>'
    inner += "</main>"
    return screen_tv(sid, klass, inner)


def mylist(klass: str, sid: str) -> str:
    tabs = '<div class="tabs"><span class="tab on">Moja lista</span><span class="tab">Ulubione kanały</span><span class="tab">Kontynuuj</span><span class="tab">Historia</span><span class="tab">Nagrania</span></div>'
    cards = "".join(
        f'<div class="po{" f" if i==0 else ""}">{poster(t, label=False)}<div class="cap"><b>{t}</b><span>{m}</span></div></div>'
        for i, (t, m) in enumerate([("Komisariat 7", "Serial"), ("Złote miasto", "Film · 2024"), ("Czerwona glina", "Film · 2019"), ("Wyspa", "Serial")])
    )
    inner = rail("mylist") + f'<main class="ct">{top("Moja lista", extra=tabs)}<div class="row" style="margin-top:20px">{cards}</div></main>'
    return screen_tv(sid, klass, inner)


def onboarding(klass: str, sid: str) -> str:
    tiles = [
        ("M3U / M3U8", "Adres URL listy", True),
        ("Xtream Codes", "Serwer, login, hasło", False),
        ("Stalker / MAG", "Portal i adres MAC", False),
        ("Import TXT", "Wiele list naraz", False),
        ("Importuj z BK Player", "Przenieś listy i ustawienia", False),
    ]
    t = "".join(f'<div class="tc{" f" if foc else ""}"><b>{a}</b><span>{b}</span></div>' for a, b, foc in tiles)
    inner = (
        f'<div class="onb"><div class="onl"><img src="{LOGO}" alt="Ketchup Player"/>'
        '<div class="steps"><div class="stp done">1 · Język i profil</div><div class="stp act">2 · Dodaj listę</div><div class="stp">3 · Ładowanie</div></div></div>'
        '<div class="onr"><div class="kick">Krok 2 z 3</div><h2>Dodaj pierwszą listę</h2>'
        "<p>Wybierz sposób dodania dostawcy. Możesz dodać kolejne później w Ustawieniach.</p>"
        f'<div class="tgrid">{t}</div>'
        '<label>Adres URL listy M3U</label><div class="inp">http://przyklad.pl/lista.m3u</div>'
        '<p class="warn">Ten dostawca używa HTTP — login i hasło są przesyłane jawnie.</p>'
        '<div class="qr"><div class="qrb"></div><span>Długi adres? Zeskanuj kod telefonem i wklej URL na stronie boxa.</span></div>'
        '<div style="margin-top:24px"><span class="btn">‹ Wstecz</span><span class="btn pri">Dalej ›</span></div>'
        "</div></div>"
    )
    return screen_tv(sid, klass, inner)


def onboarding_load(klass: str, sid: str) -> str:
    inner = (
        f'<div class="onb"><div class="onl"><img src="{LOGO}" alt="Ketchup Player"/>'
        '<div class="steps"><div class="stp done">1 · Język i profil</div><div class="stp done">2 · Dodaj listę</div><div class="stp act">3 · Ładowanie</div></div></div>'
        '<div class="onr"><div class="kick">Krok 3 z 3</div><h2>Ładowanie listy</h2>'
        "<p>Katalog pojawi się, gdy skończymy import. Możesz pominąć VOD i wejść wcześniej.</p>"
        '<div class="opt" style="background:var(--s2)"><div><b>Kanały</b><span>642 z 642</span></div><em>Gotowe</em></div>'
        '<div class="opt" style="background:var(--s2)"><div><b>EPG</b><span>Źródło epg.ovh · 58%</span></div><em>W toku</em></div>'
        '<div class="minibar" style="margin:0 0 18px"><s style="width:58%"></s></div>'
        '<div class="opt" style="background:var(--s2)"><div><b>VOD i seriale</b><span>Oczekuje</span></div><em>—</em></div>'
        '<div style="margin-top:28px"><span class="btn">Pomiń ładowanie VOD</span><span class="btn pri" style="opacity:.45">Przejdź do aplikacji</span></div>'
        "</div></div>"
    )
    return screen_tv(sid, klass, inner)


def pin(klass: str, sid: str) -> str:
    # dimmed home-ish + dialog
    keys = "".join(f'<div class="key{" f" if k=="5" else ""}">{k}</div>' for k in list("123456789") + ["⌫", "0", "OK"])
    inner = (
        rail("home")
        + '<main class="ct" style="filter:blur(2px);opacity:.45">'
        + top("Home")
        + "<h2>Złote miasto</h2></main>"
        + '<div class="pinbox"><div class="pinc"><div class="kick">Kontrola rodzicielska</div><h2>Wpisz PIN</h2>'
        + "<p>Profil dziecięcy jest zablokowany. PIN ma 4–12 cyfr.</p>"
        + '<div class="dots"><i class="dot on"></i><i class="dot on"></i><i class="dot"></i><i class="dot"></i></div>'
        + f'<div class="keys">{keys}</div></div></div>'
    )
    return screen_tv(sid, klass, inner)


def empty(klass: str, sid: str) -> str:
    inner = rail("mylist") + f'<main class="ct">{top("Moja lista")}<div class="center"><div>'
    inner += "<h2>Lista jest pusta</h2><p>Dodaj filmy i seriale przyciskiem + Moja lista na szczegółach albo długim OK na plakacie.</p>"
    inner += '<span class="btn pri f">Przeglądaj filmy</span></div></div></main>'
    return screen_tv(sid, klass, inner)


def error(klass: str, sid: str) -> str:
    inner = rail("live") + f'<main class="ct">{top("Na żywo")}<div class="center"><div>'
    inner += '<div class="kick" style="color:var(--danger)">Błąd sieci</div>'
    inner += "<h2>Brak połączenia</h2><p>Katalog i EPG pochodzą z pamięci podręcznej (EPG z 18:10). Sprawdź sieć albo VPN.</p>"
    inner += '<span class="btn pri f">Spróbuj ponownie</span><span class="btn">Szczegóły · NET-TIMEOUT</span></div></div></main>'
    return screen_tv(sid, klass, inner)


def loading(klass: str, sid: str) -> str:
    sk = '<div class="skel" style="height:280px;margin-bottom:20px"></div>' + "".join(
        '<div class="skel" style="width:260px;height:150px;display:inline-block;margin-right:16px"></div>' for _ in range(5)
    )
    inner = rail("home") + f'<main class="ct">{top("Home")}<div class="kick">Ładowanie</div>{sk}</main>'
    return screen_tv(sid, klass, inner)


def focus_demo(klass: str, sid: str) -> str:
    inner = rail("home") + f'<main class="ct">{top("Stany focusu D-pad")}'
    inner += "<p>Każdy fokusowalny element ma co najmniej dwa sygnały: skalę 1,08, obrys 4 px w kolorze akcentu i poświatę. Animacja ≤ 150 ms.</p>"
    inner += '<div class="fd">'
    inner += f'<div><div class="po">{poster("Spoczynek")}</div><div class="lab">Spoczynek</div></div>'
    inner += f'<div><div class="po f">{poster("Focus")}</div><div class="lab">Focus (D-pad)</div></div>'
    inner += '<div><span class="btn">Zwykły przycisk</span><div class="lab">Spoczynek</div></div>'
    inner += '<div><span class="btn pri f">Przycisk główny</span><div class="lab">Focus + akcja główna</div></div>'
    inner += "</div></main>"
    return screen_tv(sid, klass, inner)


def phone_nav(active: str) -> str:
    items = [("home", "Home", "home"), ("live", "Na żywo", "live"), ("movies", "Filmy", "movies"), ("series", "Seriale", "series"), ("list", "Więcej", "more")]
    html = '<nav class="pnav">'
    for ic, label, key in items:
        act = " act" if key == active else ""
        extra = '<i class="ng"></i>' if key == "series" else ""
        html += f'<div class="pn{act}">{extra}{ico(ic, 36)}<span>{label}</span></div>'
    html += "</nav>"
    return html


def phone_home() -> str:
    cont = "".join(
        f'<div class="pc{" f" if i==0 else ""}">{poster(t, progress=p)}<div class="cap"><b>{t}</b><span>{c}</span></div></div>'
        for i, (t, c, p) in enumerate([("Komisariat 7", "S2 E5 · 18 min", 0.62), ("Czerwona glina", "1:12 / 2:04", 0.58)])
    )
    inner = (
        '<div class="phead"><h1>Home</h1><div class="tb-r"><span class="clock">19:42</span></div></div>'
        '<div class="pbody"><section class="hero"><div class="bg">' + poster("Złote miasto") + "</div>"
        '<div class="hg"></div><div class="hi"><div class="kick">Polecane</div><h2>Złote miasto</h2>'
        '<div class="meta">2024 · IMDb 8,6</div><span class="btn pri">▶ Oglądaj</span></div></section>'
        f'<div class="rowh"><b>Kontynuuj oglądanie</b></div><div class="row">{cont}</div>'
        '<div class="rowh"><b>Nowe odcinki</b></div><div class="row">'
        + "".join(f'<div class="po">{poster(t, badge=n, label=False)}<div class="cap"><b>{t}</b></div></div>' for t, _, n in SERIES[:4] if n)
        + "</div></div>"
        + phone_nav("home")
    )
    return screen_tv("phone-home", "phone", inner)


def phone_live() -> str:
    chs = ""
    for num, code, name, show, hours, pr in CHANNELS:
        foc = " f" if num == "102" else ""
        chs += f'<div class="li{foc}"><span class="num">{num}</span><span class="chl">{code}</span><div class="grow"><b>{name}</b><span>{show} · {hours}</span></div></div>'
    inner = (
        '<div class="phead"><h1>Na żywo</h1><div class="clock">19:42</div></div>'
        f'<div class="pbody">{poster("Rozmowa dnia", label=False)}<h2>Rozmowa dnia</h2>'
        "<div class=\"meta\">102 · Info 24 · 19:15–20:15</div>"
        f'{chs}</div>' + phone_nav("live")
    )
    return screen_tv("phone-live", "phone", inner)


def phone_movies() -> str:
    grid = "".join(f"<div>{poster(t)}<div class=\"cap\"><b>{t}</b><span>{m}</span></div></div>" for t, m in MOVIES[:6])
    inner = (
        '<div class="phead"><h1>Filmy</h1></div>'
        f'<div class="pbody"><div class="sbox">Szukaj w: Filmy</div><div class="chips"><span class="chip on">Najnowsze</span><span class="chip">A–Z</span></div>'
        f'<div class="g3">{grid}</div></div>' + phone_nav("movies")
    )
    return screen_tv("phone-movies", "phone", inner)


def phone_search() -> str:
    inner = (
        '<div class="phead"><h1>Szukaj</h1></div>'
        '<div class="pbody"><div class="sbox f">rozm |</div>'
        f'<div class="rowh"><b>Audycje EPG</b></div><div class="pc">{poster("Rozmowa dnia", label=False)}<div class="cap"><b>Rozmowa dnia</b><span>Info 24 · 19:15–20:15</span></div></div>'
        f'<div class="rowh"><b>Seriale</b></div><div class="pc">{poster("Komisariat 7")}<div class="cap"><b>Komisariat 7</b><span>Wznów S3 E2</span></div></div>'
        "</div>" + phone_nav("more")
    )
    return screen_tv("phone-search", "phone", inner)


def phone_player() -> str:
    ctl = "".join(f'<span class="cb{" f" if n=="pause" else ""}">{ico(n, 36)}</span>' for n in ["rew", "pause", "ff"])
    inner = (
        f'<div class="player"><div class="bg">{poster("Rozmowa dnia", label=False)}</div><div class="ogt"></div>'
        '<div class="osdt" style="left:48px;right:48px;top:64px"><div><div class="kick">102 · Info 24</div><h2>Rozmowa dnia</h2>'
        '<div class="meta">19:15–20:15</div></div><div class="clock">19:42</div></div>'
        '<div class="osdb" style="left:48px;right:48px;bottom:48px"><div class="seek"><span>19:15</span><div class="track"><s></s><u></u></div><span>20:15</span></div>'
        f'<div class="ctl" style="justify-content:center">{ctl}</div></div></div>'
    )
    return screen_tv("phone-player", "phone", inner)



def comparison() -> str:
    cards = [
        ("Morski", "morski", "#0B1112", "#35C2B4", "tło #0B1112 · akcent turkus"),
        ("Grafit", "grafit", "#121315", "#35C2B4", "tło #121315 · ten sam turkus"),
        ("Czerń", "czern", "#000000", "#EDEDED", "OLED · akcent biel #EDEDED"),
        ("Własny", "wlasny", "#0C1113", "#35C2B4", "hue 195° z Palette.custom"),
    ]
    cells = ""
    for name, key, bg, acc, cap in cards:
        cells += (
            f'<div class="theme-{key}" style="background:var(--bg);border-radius:var(--rad);padding:22px;border:1px solid var(--ln)">'
            f'<div style="height:120px;border-radius:14px;background:{bg};box-shadow:inset 0 0 0 3px {acc}"></div>'
            f'<h2 style="font-size:32px;margin-top:16px">{name}</h2><div class="meta">{cap}</div>'
            f'<div class="pills"><span class="pill"><b>Aa</b><em>tekst</em></span>'
            f'<span class="pill" style="background:var(--acc);color:var(--onacc)"><b>OK</b><em>akcent</em></span>'
            f'<span class="newb" style="position:static"><i></i>nowość</span></div></div>'
        )
    inner = (
        '<main class="ct" style="left:96px">' + top("Motywy v5")
        + '<p>Tokeny z Palette.kt. Pełny zestaw ekranów poniżej jest w motywie Morskim; Home pokazujemy też w trzech pozostałych.</p>'
        + f'<div class="tgrid2" style="max-width:none;height:calc(100% - 180px)">{cells}</div></main>'
    )
    return screen_tv("tv1080-themes", "tv1080", inner)


NAV = [
    ("tv1080-themes", "TV 1080 · Porównanie motywów"),
    ("tv1080-onboarding", "TV 1080 · Onboarding"),
    ("tv1080-onboarding-load", "TV 1080 · Ładowanie listy"),
    ("tv1080-home", "TV 1080 · Home · Morski"),
    ("tv1080-home-grafit", "TV 1080 · Home · Grafit"),
    ("tv1080-home-czern", "TV 1080 · Home · Czerń"),
    ("tv1080-home-wlasny", "TV 1080 · Home · Własny"),
    ("tv1080-live", "TV 1080 · Na żywo"),
    ("tv1080-epg", "TV 1080 · EPG"),
    ("tv1080-radar", "TV 1080 · Radar"),
    ("tv1080-movies", "TV 1080 · Filmy"),
    ("tv1080-series", "TV 1080 · Seriale"),
    ("tv1080-details", "TV 1080 · Szczegóły"),
    ("tv1080-player", "TV 1080 · Odtwarzacz"),
    ("tv1080-settings", "TV 1080 · Ustawienia"),
    ("tv1080-search", "TV 1080 · Szukaj"),
    ("tv1080-mylist", "TV 1080 · Moja lista"),
    ("tv1080-pin", "TV 1080 · PIN"),
    ("tv1080-empty", "TV 1080 · Pusty"),
    ("tv1080-error", "TV 1080 · Błąd"),
    ("tv1080-loading", "TV 1080 · Szkielet"),
    ("tv1080-focus", "TV 1080 · Focus D-pad"),
    ("tv720-home", "TV 720 · Home"),
    ("tv720-live", "TV 720 · Na żywo"),
    ("tv720-epg", "TV 720 · EPG"),
    ("tv720-player", "TV 720 · Odtwarzacz"),
    ("phone-home", "Telefon · Home"),
    ("phone-live", "Telefon · Na żywo"),
    ("phone-movies", "Telefon · Filmy"),
    ("phone-search", "Telefon · Szukaj"),
    ("phone-player", "Telefon · Odtwarzacz"),
]


def build() -> str:
    links = "".join(f'<a href="#{i}" data-k="{i}">{l}</a>' for i, l in NAV)
    parts = [
        "<!doctype html><html lang=\"pl\"><head><meta charset=\"utf-8\">",
        '<meta name="viewport" content="width=device-width, initial-scale=1">',
        "<title>Ketchup Player — makiety v5.1</title><style>",
        TOKENS, CSS, "</style></head><body>",
        '<header class="doc"><b>KETCHUP PLAYER — makiety v5.1 · tokeny Palette.kt · pełny zestaw: Morski</b>',
        links, "<span>← / → przełącza ekrany · F = skala do okna</span></header>",
    ]
    def themed(fn, sid, theme):
        html = fn("tv1080", sid)
        return html.replace("theme-morski", f"theme-{theme}", 1)

    parts += [
        comparison(),
        onboarding("tv1080", "tv1080-onboarding"),
        onboarding_load("tv1080", "tv1080-onboarding-load"),
        home("tv1080", "tv1080-home"),
        themed(home, "tv1080-home-grafit", "grafit"),
        themed(home, "tv1080-home-czern", "czern"),
        themed(home, "tv1080-home-wlasny", "wlasny"),
        live("tv1080", "tv1080-live"),
        epg_screen("tv1080", "tv1080-epg"),
        radar("tv1080", "tv1080-radar"),
        movies("tv1080", "tv1080-movies"),
        series("tv1080", "tv1080-series"),
        details("tv1080", "tv1080-details"),
        player("tv1080", "tv1080-player"),
        settings("tv1080", "tv1080-settings"),
        search("tv1080", "tv1080-search"),
        mylist("tv1080", "tv1080-mylist"),
        pin("tv1080", "tv1080-pin"),
        empty("tv1080", "tv1080-empty"),
        error("tv1080", "tv1080-error"),
        loading("tv1080", "tv1080-loading"),
        focus_demo("tv1080", "tv1080-focus"),
        home("tv720", "tv720-home"),
        live("tv720", "tv720-live"),
        epg_screen("tv720", "tv720-epg"),
        player("tv720", "tv720-player"),
        phone_home(),
        phone_live(),
        phone_movies(),
        phone_search(),
        phone_player(),
    ]
    parts.append(
        """<script>
const o=[...document.querySelectorAll('header.doc a')].map(a=>a.dataset.k);
function sh(k){
  document.querySelectorAll('.wrap').forEach(e=>e.classList.toggle('on', e.id===k));
  document.querySelectorAll('.doc a').forEach(a=>a.classList.toggle('on', a.dataset.k===k));
  location.hash=k;
  fit();
}
function cur(){return location.hash.slice(1)||o[0]}
let F=true;
function fit(){
  const frame=document.querySelector('.wrap.on .frame');
  if(!frame) return;
  const w=frame.offsetWidth,h=frame.offsetHeight;
  const s=F?Math.min((innerWidth-56)/w,(innerHeight-220)/h):1;
  frame.style.transform=s===1?'none':'scale('+s+')';
  frame.style.transformOrigin='0 0';
  frame.parentElement.style.width=(w*s)+'px';
  frame.parentElement.style.height=(h*s)+'px';
}
document.querySelectorAll('.doc a').forEach(a=>a.onclick=e=>{e.preventDefault();sh(a.dataset.k)});
addEventListener('keydown',e=>{
  let i=o.indexOf(cur());
  if(e.key==='ArrowRight')sh(o[(i+1)%o.length]);
  if(e.key==='ArrowLeft')sh(o[(i+o.length-1)%o.length]);
  if(e.key.toLowerCase()==='f'){F=!F;fit()}
});
addEventListener('resize',fit);
sh(cur());
</script></body></html>"""
    )
    return "\n".join(parts)


def main() -> None:
    html = build()
    OUT.write_text(html, encoding="utf-8")
    print(f"wrote {OUT} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
