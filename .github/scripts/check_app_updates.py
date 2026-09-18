#!/usr/bin/env python3
"""
Usage:
    check_app_updates.py [--dry-run] [--package <id>] [--channel stable]
                         [--base-url https://sniff.madkarma.top]

Environment:
    SNIFF_BASE_URL  API base URL (default https://sniff.madkarma.top).
    GH_TOKEN        token for `gh` (CI provides GITHUB_TOKEN).
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
PATCH_GLOB = "apps/*/patch/src/main/kotlin/**/*.kt"
DEFAULT_BASE_URL = "https://sniff.madkarma.top"
DEFAULT_CHANNEL = "stable"
ISSUE_LABEL = "app-update"
UA = {"User-Agent": "Mozilla/5.0"}

PINNED_RE = re.compile(r'compatibleWith\(\s*"([^"]+)"\s*\(([^)]*)\)\s*\)')
QUOTED_RE = re.compile(r'"([^"]+)"')
UNPINNED_RE = re.compile(r'compatibleWith\(\s*"([^"]+)"\s*\)')
CONST_CALL_RE = re.compile(
    r"compatibleWith\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)\s*\)"
)
CONST_VAL_RE = re.compile(
    r"(?:internal\s+)?const\s+val\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*\"([^\"]+)\""
)


def version_key(version: str) -> tuple:
    """Sortable key: numeric parts compare numerically, others lexically."""
    return tuple(
        (0, int(part)) if part.isdigit() else (1, part)
        for part in re.findall(r"\d+|[A-Za-z]+", version)
    )


def normalize_version_name(raw: str) -> str:
    """Strip Play suffixes, e.g. '289.20 - Stable' -> '289.20'."""
    return raw.split(" - ")[0].strip().split()[0]


def collect_pins() -> tuple[dict[str, set[str]], dict[str, set[str]]]:
    """Return (pinned versions per package, declaring files per package).

    Packages declared via `compatibleWith("pkg")` with no versions are
    recorded with an empty set and skipped by the caller.
    """
    kt_files = sorted(REPO_ROOT.glob(PATCH_GLOB))
    if not kt_files:
        raise SystemExit(f"error: no patch sources found at {REPO_ROOT}/{PATCH_GLOB}")

    consts: dict[str, str] = {}
    texts: dict[Path, str] = {}
    for path in kt_files:
        text = path.read_text(encoding="utf-8")
        texts[path] = text
        for name, value in CONST_VAL_RE.findall(text):
            consts[name] = value

    pinned: dict[str, set[str]] = {}
    files: dict[str, set[str]] = {}
    for path, text in texts.items():
        rel = str(path.relative_to(REPO_ROOT))
        for package, raw_versions in PINNED_RE.findall(text):
            if QUOTED_RE.findall(raw_versions):
                pinned.setdefault(package, set()).update(QUOTED_RE.findall(raw_versions))
                files.setdefault(package, set()).add(rel)
        for pkg_const, ver_const in CONST_CALL_RE.findall(text):
            package, version = consts.get(pkg_const), consts.get(ver_const)
            if package and version:
                pinned.setdefault(package, set()).add(version)
                files.setdefault(package, set()).add(rel)
            else:
                print(
                    f"warning: unresolvable compatibleWith({pkg_const}({ver_const})) in {rel}, skipping",
                    file=sys.stderr,
                )

    scrubbed = dict(texts)
    for path, text in texts.items():
        scrubbed[path] = PINNED_RE.sub("", text)
        scrubbed[path] = CONST_CALL_RE.sub("", scrubbed[path])
    for path, text in scrubbed.items():
        rel = str(path.relative_to(REPO_ROOT))
        for package in UNPINNED_RE.findall(text):
            pinned.setdefault(package, set())
            files.setdefault(package, set()).add(rel)

    return pinned, files


def sniff_latest(package: str, base_url: str, channel: str) -> tuple[str, str] | None:
    """Return (normalized version_name, app title) or None on 404."""
    url = f"{base_url}/v1/details/{package}/{channel}"
    req = urllib.request.Request(url, headers=UA)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            payload = json.load(resp)
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace")[:200]
        print(f"warning: {package}: GET {url} -> HTTP {e.code}: {detail}", file=sys.stderr)
        return None
    except urllib.error.URLError as e:
        print(f"warning: {package}: GET {url} failed: {e}", file=sys.stderr)
        return None

    try:
        item = payload["data"]["item"]
        details = item["details"]["app_details"]
        raw_version = str(details["version_string"])
        title = str(item.get("title", package))
    except (KeyError, TypeError):
        print(f"warning: {package}: unexpected details shape", file=sys.stderr)
        return None
    return normalize_version_name(raw_version), title


def gh(*args: str) -> str:
    proc = subprocess.run(
        ["gh", *args], capture_output=True, text=True, cwd=REPO_ROOT
    )
    if proc.returncode != 0:
        raise RuntimeError(f"gh {' '.join(args)} failed: {proc.stderr.strip()}")
    return proc.stdout


def open_issue_titles() -> dict[str, int]:
    try:
        raw = gh("issue", "list", "--state", "open", "--limit", "100",
                 "--json", "number,title")
    except RuntimeError as e:
        print(f"warning: could not list open issues: {e}", file=sys.stderr)
        return {}
    try:
        return {entry["title"]: entry["number"] for entry in json.loads(raw)}
    except (ValueError, KeyError, TypeError):
        return {}


def issue_scope(package: str, declaring: list[str]) -> str:
    """Conventional-commit scope: the app dir when all declaring files agree."""
    dirs = {parts[1] for path in declaring
            if (parts := Path(path).parts)[:1] == ("apps",) and len(parts) > 1}
    return dirs.pop() if len(dirs) == 1 else package


def issue_title(package: str, declaring: list[str], latest: str) -> str:
    return f"feat({issue_scope(package, declaring)}): add support for {latest}"


def ensure_label() -> bool:
    """Best-effort: make sure ISSUE_LABEL exists. Return whether usable."""
    try:
        names = {entry["name"] for entry in json.loads(gh("label", "list", "--json", "name"))}
    except RuntimeError:
        return False
    if ISSUE_LABEL in names:
        return True
    try:
        gh("label", "create", ISSUE_LABEL,
           "--description", "Upstream app release needs patch support",
           "--color", "FBCA04")
        return True
    except RuntimeError as e:
        print(f"warning: could not create label {ISSUE_LABEL!r}: {e}", file=sys.stderr)
        return False


def open_support_issue(title: str, package: str, title_app: str, latest: str,
                       pinned: list[str], declaring: list[str], use_label: bool) -> None:
    body = (
        f"The Play Store `{DEFAULT_CHANNEL}` channel has **{title_app} ({package}) {latest}**, "
        f"but the patches only declare support for: {', '.join(pinned)}.\n\n"
        f"- Latest stable `version_name` (via sniff): `{latest}`\n"
        f"- Declared in:\n"
        + "".join(f"  - `{path}`\n" for path in declaring)
        + f"\nPlease verify the patches against `{latest}` and extend `compatibleWith(...)` accordingly."
    )
    cmd = ["issue", "create", "--title", title, "--body", body]
    if use_label:
        cmd += ["--label", ISSUE_LABEL]
    try:
        out = gh(*cmd)
    except RuntimeError:
        if use_label:
            out = gh(*[a for a in cmd if a != ISSUE_LABEL and a != "--label"])
        else:
            raise
    print(f"opened issue: {out.strip()} ({title})")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Check pinned app versions against Play releases.")
    parser.add_argument("--dry-run", action="store_true",
                        help="print what would be done without opening issues")
    parser.add_argument("--package", default=None,
                        help="only check this package id (default: all)")
    parser.add_argument("--channel", default=DEFAULT_CHANNEL, help="sniff release channel")
    parser.add_argument("--base-url", default=os.environ.get("SNIFF_BASE_URL", DEFAULT_BASE_URL),
                        help="sniff API base URL")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    pinned, files = collect_pins()

    targets = sorted(pinned)
    if args.package:
        if args.package not in pinned:
            raise SystemExit(f"error: {args.package} has no compatibleWith declaration")
        targets = [args.package]

    use_label = False if args.dry_run else ensure_label()
    existing = {} if args.dry_run else open_issue_titles()
    opened = skipped_ok = skipped_unpinned = 0

    for package in targets:
        versions = pinned[package]
        if not versions:
            print(f"skip {package}: no pinned version")
            skipped_unpinned += 1
            continue
        ordered = sorted(versions, key=version_key)
        result = sniff_latest(package, args.base_url, args.channel)
        if result is None:
            continue
        latest, title_app = result
        declaring = sorted(files.get(package, []))
        if latest in versions:
            print(f"ok {package}: latest {latest} is pinned ({', '.join(ordered)})")
            skipped_ok += 1
            continue
        print(f" outdated {package}: latest {latest} not in pinned ({', '.join(ordered)})")
        title = issue_title(package, declaring, latest)
        if existing.get(title):
            print(f"  open issue #{existing[title]} already exists, skipping")
            continue
        if args.dry_run:
            print(f"  dry-run: would open '{title}'")
            continue
        try:
            open_support_issue(title, package, title_app, latest, ordered,
                               declaring, use_label)
            existing[title] = -1
            opened += 1
        except RuntimeError as e:
            print(f"error: {e}", file=sys.stderr)
            return 1

    print(f"done: {opened} opened, {skipped_ok} up to date, {skipped_unpinned} unpinned skipped")
    return 0


if __name__ == "__main__":
    sys.exit(main())
