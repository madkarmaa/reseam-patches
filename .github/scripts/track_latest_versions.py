#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
# SPDX-License-Identifier: AGPL-3.0-or-later

"""
Usage:
    track_latest_versions.py [--dry-run] [--package <id>] [--channel stable]
                             [--base-url https://sniff.madkarma.top/v3]
                             [--patches-json build/reseam/release/patches.json]
                             [--issue-title "Latest upstream app versions"]
                             [--label app-latest]

Reads every package declared in the release index, including unpinned apps.
Generate the index first with
`just index`.

Queries GET /v3/download/{package_name}/{channel} on https://sniff.madkarma.top
for the latest release. The default channel is stable. Creates or updates
one tracking issue as a daily checklist for patching the latest app versions.

Environment:
    SNIFF_BASE_URL  API base URL (default https://sniff.madkarma.top/v3).
    GH_TOKEN        token for `gh` (CI provides GITHUB_TOKEN).
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path

from sniff import DEFAULT_BASE_URL, DEFAULT_CHANNEL, latest_release

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_PATCHES_JSON = REPO_ROOT / "build" / "reseam" / "release" / "patches.json"
STICKY_TITLE = "Latest upstream app versions"
ISSUE_LABEL = "app-latest"


def collect_packages(
    patches_json: Path,
) -> tuple[dict[str, dict[str, str]], dict[str, list[str]]]:
    """Return patch names by ID and sorted pinned versions, both grouped by package.

    Reads the newest release in the index written by `just index`.
    Includes every package from compatibility entries with kind "packages",
    even when its version list is empty. Universal patches declare no packages.
    """
    try:
        index = json.loads(patches_json.read_text(encoding="utf-8"))
    except FileNotFoundError:
        raise SystemExit(
            f"error: {patches_json} not found; run "
            "'just index' first"
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
    """Create the tracking label if missing. Return True if the label is available."""
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
        help="release index written by just index",
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
        result = latest_release(package, args.base_url, args.channel)
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

        print(f"found {package}: {result.version_name} ({result.title})")
        results.append(
            {
                "package": package,
                "title": result.title,
                "latest": result.version_name,
                "version_code": result.version_code,
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
