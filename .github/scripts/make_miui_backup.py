"""Erzeugt eine Test-Sicherung im Xiaomi-Format (MIUI-Header + Android-Backup + Launcher-Datenbank)."""
import io, os, sqlite3, sys, tarfile, tempfile, zlib

comps = [l.strip() for l in open(sys.argv[1]) if "/" in l]
provider = open(sys.argv[2]).read().strip() if os.path.exists(sys.argv[2]) else ""
out = sys.argv[3]
comps = [c for c in comps if not c.startswith("com.cleanlauncher")]

tmp = tempfile.mkdtemp()
dbp = os.path.join(tmp, "launcher4x6.db")
db = sqlite3.connect(dbp)
db.execute("CREATE TABLE favorites (_id INTEGER PRIMARY KEY, title TEXT, intent TEXT, container INTEGER, screen INTEGER, cellX INTEGER, cellY INTEGER, spanX INTEGER, spanY INTEGER, itemType INTEGER, appWidgetId INTEGER, appWidgetProvider TEXT)")
db.execute("CREATE TABLE screens (_id INTEGER PRIMARY KEY, screenOrder INTEGER)")
for sid, order in [(10, 0), (11, 1), (12, 2)]:
    db.execute("INSERT INTO screens VALUES (?,?)", (sid, order))

def intent(c):
    return "#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x10200000;component=%s;end" % c

nid = [100]
def add(title, it, container, screen, x, y, sx=1, sy=1, t=0, prov=None):
    nid[0] += 1
    db.execute("INSERT INTO favorites VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", (nid[0], title, it, container, screen, x, y, sx, sy, t, -1, prov))
    return nid[0]

c = list(comps)
# Dock
for i in range(min(4, len(c))):
    add("", intent(c.pop(0)), -101, i, i, 0)
# Seite 1: fester Startbildschirm – unten eine Reihe, oben links eine App
if c: add("", intent(c.pop(0)), -100, 10, 0, 0)
for x in range(4):
    if c: add("", intent(c.pop(0)), -100, 10, x, 5)
# Seite 2: Ordner + Apps
fid = add("Google", None, -100, 11, 0, 0, t=2)
for i in range(3):
    if c: add("", intent(c.pop(0)), fid, 0, i, 0)
x, y = 1, 0
while c:
    add("", intent(c.pop(0)), -100, 11, x, y)
    x += 1
    if x == 4:
        x, y = 0, y + 1
# Seite 3: eigenes Widget + MIUI-Gadget (wird übersprungen)
if provider:
    add("", None, -100, 12, 0, 0, 4, 2, t=4, prov=provider)
add("", None, -100, 12, 0, 3, 2, 2, t=5)
db.commit(); db.close()

tbuf = io.BytesIO()
with tarfile.open(fileobj=tbuf, mode="w", format=tarfile.USTAR_FORMAT) as tf:
    tf.add(dbp, arcname="apps/com.miui.home/db/launcher4x6.db")
data = b"MIUI BACKUP\n2\ncom.miui.home\nStartbildschirm\n-1\n0\n" + b"ANDROID BACKUP\n5\n1\nnone\n" + zlib.compress(tbuf.getvalue())
open(out, "wb").write(data)
print("backup", out, len(data), "bytes")
