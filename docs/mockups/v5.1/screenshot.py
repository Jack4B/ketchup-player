#!/usr/bin/env python3
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parent
HTML = ROOT / "ketchup-player-makiety-v5.1.html"
OUT = ROOT / "screenshots"

SCREENS = {
    "tv-1080": [
        "tv1080-themes", "tv1080-onboarding", "tv1080-onboarding-load",
        "tv1080-home", "tv1080-home-grafit", "tv1080-home-czern", "tv1080-home-wlasny",
        "tv1080-live", "tv1080-epg", "tv1080-radar", "tv1080-movies", "tv1080-series",
        "tv1080-details", "tv1080-player", "tv1080-settings", "tv1080-search",
        "tv1080-mylist", "tv1080-pin", "tv1080-empty", "tv1080-error", "tv1080-loading",
        "tv1080-focus",
    ],
    "tv-720": ["tv720-home", "tv720-live", "tv720-epg", "tv720-player"],
    "phone": ["phone-home", "phone-live", "phone-movies", "phone-search", "phone-player"],
}

def main() -> None:
    url = HTML.resolve().as_uri()
    with sync_playwright() as p:
        browser = p.chromium.launch(channel="chrome", args=["--allow-file-access-from-files"])
        page = browser.new_page()
        page.goto(url, wait_until="domcontentloaded")
        page.evaluate("F=false; fit()")
        for folder, ids in SCREENS.items():
            dest = OUT / folder
            dest.mkdir(parents=True, exist_ok=True)
            for sid in ids:
                page.evaluate("(k) => sh(k)", sid)
                page.wait_for_timeout(80)
                el = page.locator(f"#{sid} .frame")
                el.wait_for()
                el.screenshot(path=str(dest / f"{sid}.png"), type="png")
                print("ok", folder, sid)
        browser.close()

if __name__ == "__main__":
    main()
