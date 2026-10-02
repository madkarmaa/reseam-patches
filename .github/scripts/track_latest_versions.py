#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
# SPDX-License-Identifier: GPL-3.0-or-later

"""
Usage:
    track_latest_versions.py [--dry-run] [--package <id>] [--channel stable]
                             [--base-url https://sniff.madkarma.top]
                             [--patches-json build/reseam/patches.json]
                             [--issue-title "Latest upstream app versions"]
                             [--label app-latest]

Collects every package declared by the patches (pinned or not) from the
release index written by the `:generatePatchesJson` Gradle task - run it
first, e.g. `./gradlew generatePatchesJson -PreleaseTag=v0.0.0-latest-check` -
queries the sniff API (see https://sniff.madkarma.top/docs,
GET /v1/details/{package_name}/{channel}) for the latest stable release,
and upserts a single sticky issue tracking the results.

Packages with an empty version list are NOT
skipped: the point is a daily re-patch checklist, including unpinned apps.

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
import tempfile
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_PATCHES_JSON = REPO_ROOT / "build" / "reseam" / "patches.json"
DEFAULT_BASE_URL = "https://sniff.madkarma.top"
DEFAULT_CHANNEL = "stable"
STICKY_TITLE = "Latest upstream app versions"
ISSUE_LABEL = "app-latest"
UA = {"User-Agent": "Mozilla/5.0"}


def normalize_version_name(raw: str) -> str:
    """Strip Play suffixes, e.g. '289.20 - Stable' -> '289.20'."""
    return raw.split(" - ")[0].strip().split()[0]


def collect_packages(
    patches_json: Path,
) -> tuple[dict[str, dict[str, str]], dict[str, list[str]]]:
    """Return (declaring patch names by id per package, sorted pinned versions per package).

    Reads the newest release in the index written by `:generatePatchesJson`.
    Every package with kind == "packages" is included, even when its version
    list is empty (unpinned). Universal patches carry no packages.
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
    except KeyError, IndexError, TypeError:
        raise SystemExit(f"error: {patches_json} has no releases[0].patches")

    declared: dict[str, dict[str, str]] = {}
    pinned: dict[str, set[str]] = {}
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

    return declared, {package: sorted(versions) for package, versions in pinned.items()}


def sniff_latest(package: str, base_url: str, channel: str) -> tuple[str, int | None, str] | None:
    """Return (normalized version_name, version_code or None, app title) or None on failure."""
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
    except KeyError, TypeError:
        print(f"warning: {package}: unexpected details shape", file=sys.stderr)
        return None

    version_code = details.get("version_code")
    if not isinstance(version_code, int):
        version_code = None
    return normalize_version_name(raw_version), version_code, title


def gh(*args: str) -> str:
    proc = subprocess.run(["gh", *args], capture_output=True, text=True, cwd=REPO_ROOT)

    if proc.returncode != 0:
        raise RuntimeError(f"gh {' '.join(args)} failed: {proc.stderr.strip()}")

    return proc.stdout


def find_sticky_issue(title: str) -> int | None:
    try:
        raw = gh(
            "issue",
            "list",
            "--state",
            "open",
            "--limit",
            "100",
            "--json",
            "number,title",
        )
    except RuntimeError as e:
        raise RuntimeError(f"could not list open issues: {e}")
    try:
        for entry in json.loads(raw):
            if entry.get("title") == title:
                return int(entry["number"])
    except ValueError, KeyError, TypeError:
        return None
    return None


def ensure_label(label: str) -> bool:
    """Best-effort: make sure the sticky label exists. Return whether usable."""
    try:
        names = {entry["name"] for entry in json.loads(gh("label", "list", "--json", "name"))}
    except RuntimeError:
        return False
    if label in names:
        return True
    try:
        gh(
            "label",
            "create",
            label,
            "--description",
            "Tracks latest upstream app releases for re-patch attempts",
            "--color",
            "1D76DB",
        )
        return True
    except RuntimeError as e:
        print(f"warning: could not create label {label!r}: {e}", file=sys.stderr)
        return False


def escape_cell(text: str) -> str:
    return text.replace("|", "\\|").replace("\n", " ")


def build_body(results: list[dict], channel: str, base_url: str) -> str:
    checked = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    lines = [
        "# Latest upstream app versions",
        "",
        f"Last checked: {checked} (`{channel}` channel, `{base_url}`).",
        "",
        "Daily checklist for re-patching the current Play release, "
        "including unpinned apps. Pin status is informational only.",
        "",
        "| App | Package | Latest stable | Pinned versions | Declaring patches |",
        "| --- | --- | --- | --- | --- |",
    ]
    for result in results:
        if result["latest"] is None:
            latest = "sniff lookup failed"
            app = result["package"]
        else:
            code = f" (code {result['version_code']})" if result["version_code"] is not None else ""
            latest = f"`{result['latest']}`{code}"
            app = result["title"]
        pinned = ", ".join(f"`{version}`" for version in result["pinned"]) or "unpinned"
        patches = (
            "<br>".join(
                f"`{patch_id}` ({name})" for patch_id, name in sorted(result["declaring"].items())
            )
            or "-"
        )
        lines.append(
            f"| {escape_cell(app)} | `{result['package']}` | {latest} | {pinned} | {
                escape_cell(patches)
            } |"
        )
    lines += [
        "",
        f"{len(results)} app(s) tracked.",
        "",
    ]
    return "\n".join(lines)


def upsert_sticky_issue(
    number: int | None, title: str, body: str, label: str, use_label: bool
) -> None:
    with tempfile.NamedTemporaryFile("w", suffix=".md", delete=False, encoding="utf-8") as tmp:
        tmp.write(body)
        body_file = tmp.name
    try:
        if number is None:
            cmd = ["issue", "create", "--title", title, "--body-file", body_file]
            if use_label:
                cmd += ["--label", label]
            try:
                out = gh(*cmd)
            except RuntimeError:
                if use_label:
                    out = gh(*[a for a in cmd if a != label and a != "--label"])
                else:
                    raise
            print(f"created sticky issue: {out.strip()}")
            return

        gh("issue", "edit", str(number), "--body-file", body_file)
        print(f"updated sticky issue #{number}")
        if use_label:
            try:
                gh("issue", "edit", str(number), "--add-label", label)
            except RuntimeError as e:
                print(f"warning: could not label sticky issue: {e}", file=sys.stderr)
    finally:
        Path(body_file).unlink(missing_ok=True)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Track latest upstream app versions in a sticky issue."
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="print the sticky issue body without creating/updating anything",
    )
    parser.add_argument("--package", default=None, help="only check this package id (default: all)")
    parser.add_argument("--channel", default=DEFAULT_CHANNEL, help="sniff release channel")
    parser.add_argument(
        "--base-url",
        default=os.environ.get("SNIFF_BASE_URL", DEFAULT_BASE_URL),
        help="sniff API base URL",
    )
    parser.add_argument(
        "--patches-json",
        default=str(DEFAULT_PATCHES_JSON),
        help="release index written by :generatePatchesJson",
    )
    parser.add_argument("--issue-title", default=STICKY_TITLE, help="sticky issue title")
    parser.add_argument("--label", default=ISSUE_LABEL, help="sticky issue label")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    declared, pinned = collect_packages(Path(args.patches_json))

    targets = sorted(declared)
    if args.package:
        if args.package not in declared:
            raise SystemExit(f"error: {args.package} has no entry in {args.patches_json}")
        targets = [args.package]

    results = []
    for package in targets:
        result = sniff_latest(package, args.base_url, args.channel)
        if result is None:
            print(f"unknown {package}: sniff lookup failed")
            results.append(
                {
                    "package": package,
                    "title": package,
                    "latest": None,
                    "version_code": None,
                    "pinned": pinned.get(package, []),
                    "declaring": declared.get(package, {}),
                }
            )
            continue

        latest, version_code, title = result
        print(f"found {package}: {latest} ({title})")
        results.append(
            {
                "package": package,
                "title": title,
                "latest": latest,
                "version_code": version_code,
                "pinned": pinned.get(package, []),
                "declaring": declared.get(package, {}),
            }
        )

    body = build_body(results, args.channel, args.base_url)
    if args.dry_run:
        print(body)
        return 0

    try:
        use_label = ensure_label(args.label)
        number = find_sticky_issue(args.issue_title)
        upsert_sticky_issue(number, args.issue_title, body, args.label, use_label)
    except RuntimeError as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
