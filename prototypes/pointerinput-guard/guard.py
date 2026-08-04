#!/usr/bin/env python3
"""PROTOTYPE — wipe me. Issue #115.

Rough chain-aware check for ADR 0020's rule: a `pointerInput` that publishes no
semantics is an actionable node nothing can reach. Flags a modifier chain that
contains `pointerInput` and no accessibility sibling.

Not a lint rule — this exists to measure what such a rule would flag, on real
code and on synthetic probes, before deciding whether the real one is worth
building. Run: python3 prototypes/pointerinput-guard/guard.py [paths...]
"""
import re
import sys
from pathlib import Path

GESTURE = {"pointerInput"}
# What counts as "this chain publishes something a service or focus can reach".
EXEMPT = {
    "semantics",
    "clearAndSetSemantics",
    "clickable",
    "combinedClickable",
    "toggleable",
    "selectable",
}
CHAIN_STARTS = re.compile(r"\b(Modifier|modifier|this|base)\b")


def strip_noise(src: str) -> str:
    """Blank out comments and string literals, preserving offsets."""
    out = list(src)
    i, n = 0, len(src)
    while i < n:
        two = src[i : i + 2]
        if two == "//":
            j = src.find("\n", i)
            j = n if j < 0 else j
            for k in range(i, j):
                out[k] = " "
            i = j
        elif two == "/*":
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            for k in range(i, j):
                out[k] = " "
            i = j
        elif src[i] == '"':
            triple = src[i : i + 3] == '"""'
            close = '"""' if triple else '"'
            j = src.find(close, i + len(close))
            j = n if j < 0 else j + len(close)
            for k in range(i, j):
                out[k] = " "
            i = j
        else:
            i += 1
    return "".join(out)


def skip_balanced(src: str, i: int, open_c: str, close_c: str) -> int:
    """Index just past the balanced group starting at src[i] == open_c."""
    depth, n = 0, len(src)
    while i < n:
        if src[i] == open_c:
            depth += 1
        elif src[i] == close_c:
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return n


def read_chain(src: str, i: int):
    """From a chain-start token end, consume `.name(...)`/`.name {...}` segments."""
    members, n = [], len(src)
    while True:
        j = i
        while j < n and src[j] in " \t\r\n":
            j += 1
        if j >= n or src[j] != ".":
            return members, i
        j += 1
        while j < n and src[j] in " \t\r\n":
            j += 1
        m = re.match(r"[A-Za-z_]\w*", src[j:])
        if not m:
            return members, i
        members.append(m.group(0))
        j += m.end()
        # A member may take (args), then a trailing lambda, or just a lambda.
        while j < n:
            k = j
            while k < n and src[k] in " \t\r\n":
                k += 1
            if k < n and src[k] == "(":
                j = skip_balanced(src, k, "(", ")")
            elif k < n and src[k] == "{":
                j = skip_balanced(src, k, "{", "}")
            else:
                break
        i = j


def chains(src: str):
    """Every modifier chain in the file, as (line, [member names])."""
    found, pos, n = [], 0, len(src)
    while pos < n:
        m = CHAIN_STARTS.search(src, pos)
        if not m:
            break
        members, end = read_chain(src, m.end())
        if members:
            found.append((src.count("\n", 0, m.start()) + 1, members))
            pos = max(end, m.end())
        else:
            pos = m.end()
    return found


def scan(path: Path):
    src = strip_noise(path.read_text())
    for line, members in chains(src):
        if GESTURE & set(members) and not (EXEMPT & set(members)):
            yield line, members


def main(argv):
    roots = [Path(a) for a in argv[1:]] or [Path("app/src"), Path("engine/src")]
    files = [f for r in roots for f in r.rglob("*.kt") if "/build/" not in str(f)]
    hits = 0
    for f in sorted(files):
        for line, members in scan(f):
            hits += 1
            print(f"{f}:{line}  chain: {'.'.join(members)}")
    print(f"\n{hits} flagged across {len(files)} files")
    return 1 if hits else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
