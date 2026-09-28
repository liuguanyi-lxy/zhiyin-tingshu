import json
import subprocess
import sys

SKILL = r"C:\Users\LONG XING YU\.claude\skills\archify"
DEFAULT = r"D:\java_study\谷粒随享\代码\后端代码\tingshu-parent\tingshu-parent\docs\architecture.runtime.json"

dtype = sys.argv[1] if len(sys.argv) > 1 else "architecture"
cand = sys.argv[2] if len(sys.argv) > 2 else DEFAULT

out = subprocess.run(
    ["node", "bin/archify.mjs", "validate", dtype, cand, "--quality", "showcase", "--json"],
    cwd=SKILL, capture_output=True, text=True, encoding="utf-8", errors="replace",
)
raw = out.stdout.strip()
try:
    d = json.loads(raw)
except Exception:
    print("NON-JSON OUTPUT (exit %s):" % out.returncode)
    print(raw[:3000])
    print("STDERR:", out.stderr[:1500])
    raise SystemExit(1)

diags = d.get("diagnostics", []) or []
errs = [x for x in diags if x.get("severity") == "error"]
warns = [x for x in diags if x.get("severity") == "warning"]

print("type =", dtype, "| ok =", d.get("ok"), "| errors =", len(errs), "| warnings =", len(warns))
if d.get("error"):
    print("top-level error:", str(d["error"])[:1800])
print("--- diagnostics ---")
for x in diags[:12]:
    print(x.get("severity"), "|", x.get("code"))
    print("   ", str(x.get("message", ""))[:320])
    sf = x.get("supportedFixes")
    if sf:
        print("    fix:", "; ".join(sf)[:200])
print("--- artifact checks ---")
for c in d.get("checks", []) or []:
    print(" ", c.get("id") or c.get("name"), "ok=" + str(c.get("ok")))
