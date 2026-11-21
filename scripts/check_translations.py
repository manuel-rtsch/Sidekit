#!/usr/bin/env python3
"""Checks that every translation matches the English strings it translates.

For each module's res/ folder, every translatable <string> and <plurals> in values/ must exist in
each values-<lang>/ folder (and nothing extra), with the same format placeholders, and no string
may contain an unescaped apostrophe or double quote, which aapt rejects or silently drops.

    python3 scripts/check_translations.py          # exits 1 and lists problems, if any

Lint's MissingTranslation and StringFormatMatches catch most of this too, but take minutes; this
runs in a second. See docs/localisation.md.
"""
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
MODULES = ["app/src/main/res", "sidekit/src/main/res", "sidekit-sdk/src/main/res"]
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[sdfxXc%]")


def text(element):
    return "".join(element.itertext())


def raw(element):
    """The element's source text, before XML entity decoding, for escape checks."""
    return (element.text or "") + "".join(ET.tostring(child, encoding="unicode") for child in element)


def load(folder):
    """{name: (kind, {quantity or "": text}, raw texts)} for one values folder."""
    out = {}
    for path in sorted(folder.glob("*.xml")):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as e:
            sys.exit(f"{path.relative_to(ROOT)}: {e}")
        for el in root:
            if el.get("translatable") == "false":
                continue
            name = el.get("name")
            if el.tag == "string":
                out[name] = ("string", {"": text(el)}, [raw(el)], path)
            elif el.tag == "plurals":
                items = {i.get("quantity"): text(i) for i in el.findall("item")}
                out[name] = ("plurals", items, [raw(i) for i in el.findall("item")], path)
    return out


def placeholders(s):
    return sorted(p for p in PLACEHOLDER.findall(s) if p != "%%")


def main():
    problems = []
    for module in MODULES:
        res = ROOT / module
        if not (res / "values").is_dir():
            continue
        base = load(res / "values")
        for name, (_, _, raws, path) in base.items():
            for r in raws:
                if re.search(r"(?<!\\)'", r) or re.search(r'(?<!\\)"', r):
                    problems.append(f"{path.relative_to(ROOT)}: {name}: escape ' and \" as \\' and \\\"")
        for folder in sorted(res.glob("values-*")):
            if not re.fullmatch(r"values-[a-z]{2,3}(-r[A-Z]{2})?", folder.name):
                continue  # values-night, values-v31, ...
            lang = load(folder)
            if not lang:
                continue
            where = folder.relative_to(ROOT)
            for name in sorted(base.keys() - lang.keys()):
                problems.append(f"{where}: missing {name}")
            for name in sorted(lang.keys() - base.keys()):
                problems.append(f"{where}: {name} is not in values/ (or is translatable=\"false\" there)")
            for name in sorted(base.keys() & lang.keys()):
                kind, items, _, _ = base[name]
                lkind, litems, lraws, _ = lang[name]
                if kind != lkind:
                    problems.append(f"{where}: {name} is a {lkind}, not a {kind}")
                    continue
                for r in lraws:
                    if re.search(r"(?<!\\)'", r) or re.search(r'(?<!\\)"', r):
                        problems.append(f"{where}: {name}: escape ' and \" as \\' and \\\"")
                want = placeholders(items.get("other", items.get("", "")))
                for quantity, value in litems.items():
                    got = placeholders(value)
                    # A plural's "one" may spell out the number instead of using %d.
                    if got != want and not (kind == "plurals" and quantity != "other" and set(got) <= set(want)):
                        problems.append(f"{where}: {name}[{quantity or 'text'}] placeholders {got}, expected {want}")
                if kind == "plurals" and "other" not in litems:
                    problems.append(f"{where}: {name} has no quantity=\"other\"")
    for p in problems:
        print(p)
    if problems:
        print(f"\n{len(problems)} problem(s)")
        return 1
    print("Translations OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
