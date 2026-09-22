#!/usr/bin/env python3
"""
Usage:
    check_app_updates.py [--dry-run] [--package <id>] [--channel stable]
                         [--base-url https://sniff.madkarma.top]
                         [--patches-json build/reseam/patches.json]

The pinned versions are read from the release index written by the
`:generatePatchesJson` Gradle task - run it first, e.g.
`./gradlew generatePatchesJson -PreleaseTag=v0.0.0-version-check`.

Environment:
    SNIFF_BASE_URL  API base URL (default https://sniff.madkarma.top).
    GH_TOKEN        token for `gh` (CI provides GITHUB_TOKEN).
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_PATCHES_JSON = REPO_ROOT / "build" / "reseam" / "patches.json"
DEFAULT_BASE_URL = "https://sniff.madkarma.top"
DEFAULT_CHANNEL = "stable"
ISSUE_LABEL = "app-update"
UA = {"User-Agent": "Mozilla/5.0"}


def normalize_version_name(raw: str) -> str:
    """Strip Play suffixes, e.g. '289.20 - Stable' -> '289.20'."""
    return raw.split(" - ")[0].strip().split()[0]


def collect_pins(patches_json: Path) -> tuple[dict[str, set[str]], dict[str, dict[str, str]]]:
    """Return (pinned versions per package, declaring patch names by id per package).

    Reads the newest release in the index written by `:generatePatchesJson`.
    Packages declared with an empty version list are recorded with an empty
    set and skipped by the caller; universal patches carry no packages.
    """
    try:
        index = json.loads(patches_json.read_text(encoding="utf-8"))
    except FileNotFoundError:
        raise SystemExit(
            f"error: {patches_json} not found; run "
            "'./gradlew generatePatchesJson -PreleaseTag=vX.Y.Z' first"
        )
    except ValueError as e:
        raise SystemExit(f"error: could not parse {patches_json}: {e}")

    try:
        patches = index["releases"][0]["patches"]
    except (KeyError, IndexError, TypeError):
        raise SystemExit(f"error: {patches_json} has no releases[0].patches")

    pinned: dict[str, set[str]] = {}
    declared: dict[str, dict[str, str]] = {}
    for patch in patches:
        compat = patch.get("compatibility", {})
        if compat.get("kind") != "packages":
            continue

        for entry in compat.get("packages", []):
            package = entry.get("package")
            if not package:
                continue

            pinned.setdefault(package, set()).update(entry.get("versions", []))
            declared.setdefault(package, {})[patch["id"]] = patch.get("name", patch["id"])

    return pinned, declared


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
        raw = gh("issue", "list", "--state", "open", "--limit", "100", "--json", "number,title")
    except RuntimeError as e:
        print(f"warning: could not list open issues: {e}", file=sys.stderr)
        return {}
    try:
        return {entry["title"]: entry["number"] for entry in json.loads(raw)}
    except (ValueError, KeyError, TypeError):
        return {}


def issue_scope(package: str, declaring: dict[str, str]) -> str:
    """Conventional-commit scope: the app segment of the patch ids when they agree."""
    apps = set()
    for pid in declaring:
        parts = pid.split(".")
        if "patches" in parts:
            idx = parts.index("patches")
            if idx + 1 < len(parts):
                apps.add(parts[idx + 1])
    return apps.pop() if len(apps) == 1 else package


def issue_title(package: str, declaring: dict[str, str], latest: str) -> str:
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
                       pinned: list[str], declaring: dict[str, str], use_label: bool) -> None:
    entries = "".join(f"  - `{pid}` ({name})\n" for pid, name in sorted(declaring.items()))
    body = (
        f"The Play Store `{DEFAULT_CHANNEL}` channel has **{title_app} ({package}) {latest}**, "
        f"but the patches only declare support for: {', '.join(pinned)}.\n\n"
        f"- Latest stable `version_name`: `{latest}`\n"
        f"- Declaring patches:\n"
        f"{entries}"
        f"\nPlease verify the patches against `{latest}` and extend `compatibleWith(...)` accordingly."
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
    parser.add_argument("--patches-json", default=str(DEFAULT_PATCHES_JSON),
                        help="release index written by :generatePatchesJson")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    pinned, patches = collect_pins(Path(args.patches_json))

    targets = sorted(pinned)
    if args.package:
        if args.package not in pinned:
            raise SystemExit(f"error: {args.package} has no entry in {args.patches_json}")
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

        ordered = sorted(versions)
        result = sniff_latest(package, args.base_url, args.channel)
        if result is None:
            continue

        latest, title_app = result
        declaring = patches.get(package, {})
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
