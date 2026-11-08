#!/usr/bin/env python3
"""Pomocnik testów offline: uruchamia skrypt z domyślną dyspozycją SIGINT (jak z terminala), po 5 s wysyła Ctrl+C
do całej grupy procesów i wypisuje: czy_pracował kod_wyjścia czas_do_zakończenia. Proces w tle nieinteraktywnej
powłoki ignoruje SIGINT, więc sam `kill -INT` z Basha nie odtwarza Ctrl+C."""
import os
import signal
import subprocess
import time

T = os.environ["T"]
TV = os.environ["TV"]
env = {
    "PATH": T + "/bin:" + os.environ["PATH"], "HOME": T + "/home", "TMPDIR": T + "/tmp", "ADB_BIN": TV + "/tests/fake_adb",
    "FAKE_DIR": T + "/fake", "KETCHUP_OUT_BASE": T + "/dl/KetchupPlayerTest", "NAP_SCALE": "0", "KEY_DELAY": "0",
    "ADB_TIMEOUT": "30", "START_WAIT_S": "2", "FAKE_UI_SLEEP": "30",
}


def pre():
    os.setsid()
    signal.signal(signal.SIGINT, signal.SIG_DFL)


p = subprocess.Popen(["bash", os.environ["SCRIPT"], os.environ["APK"], "--bez-listy"], env=env,
                     stdout=open(T + "/stdout", "w"), stderr=open(T + "/stderr", "w"), preexec_fn=pre)
time.sleep(5)
alive = p.poll() is None
os.killpg(p.pid, signal.SIGINT)
t0 = time.time()
while p.poll() is None and time.time() - t0 < 15:
    time.sleep(0.2)
print(int(alive), p.poll(), int((time.time() - t0) * 10) / 10)
