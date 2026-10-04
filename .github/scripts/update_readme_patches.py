#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
# SPDX-License-Identifier: GPL-3.0-or-later

"""Replace the README patch list using the newest release in patches.json."""

import argparse
import html
import json
import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
START_MARKER = "<!-- PATCHES:START -->"
END_MARKER = "<!-- PATCHES:END -->"


def table_cell(text: str) -> str:
    text = html.escape(text, quote=False)
    text = re.sub(r"([\\`*_\[\]|])", r"\\\1", text)
    return "<br>".join(text.splitlines())


def render_patches(patches: list[dict]) -> str:
    groups: dict[str | None, list[tuple[dict, list[str]]]] = {}
    for patch in patches:
        compatibility = patch["compatibility"]
        if compatibility["kind"] == "universal":
            groups.setdefault(None, []).append((patch, []))
            continue
        if compatibility["kind"] != "packages":
            raise ValueError(f"Unknown compatibility kind for {patch['id']}")
        packages = compatibility["packages"]
        if not packages:
            raise ValueError(f"No compatible packages for {patch['id']}")
        for entry in packages:
            groups.setdefault(entry["package"], []).append((patch, entry["versions"]))

    sections = []
    for package in sorted(groups,
                          key=lambda package: (package is None, (package or "").casefold()), ):
        title = html.escape(package or "Universal")
        summary = f"<strong>{title}</strong>"
        lines = ["<details>", f"<summary>{summary}</summary>", "",
                 "| Name | Description | Supported versions |", "| --- | --- | --- |", ]
        for patch, versions in sorted(groups[package],
                                      key=lambda entry: (entry[0]["name"].casefold(),
                                                         entry[0]["id"]), ):
            cells = [patch["name"], patch["description"],
                     ", ".join(versions) if versions else "Any version", ]
            lines.append("| " + " | ".join(table_cell(cell) for cell in cells) + " |")
        sections.append("\n".join([*lines, "", "</details>"]))
    return "\n\n".join(sections)


def update_readme(readme: str, patches: list[dict]) -> str:
    if readme.count(START_MARKER) != 1 or readme.count(END_MARKER) != 1:
        raise ValueError("README must contain exactly one PATCHES:START and PATCHES:END marker")
    start = readme.index(START_MARKER) + len(START_MARKER)
    end = readme.index(END_MARKER)
    if start > end:
        raise ValueError("PATCHES:START must precede PATCHES:END")
    content = render_patches(patches)
    replacement = f"\n\n{content}\n\n" if content else "\n"
    return readme[:start] + replacement + readme[end:]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--patches-json", type=Path,
                        default=REPO_ROOT / "build/reseam/release/patches.json", )
    parser.add_argument("--readme", type=Path, default=REPO_ROOT / "README.md")
    args = parser.parse_args()
    try:
        index = json.loads(args.patches_json.read_text(encoding="utf-8"))
        patches = index["releases"][0]["patches"]
        if not isinstance(patches, list):
            raise ValueError("Newest release must contain a patches list")
        readme = args.readme.read_text(encoding="utf-8")
        updated = update_readme(readme, patches)
        if updated != readme:
            args.readme.write_text(updated, encoding="utf-8")
    except (OSError, ValueError, KeyError, IndexError, TypeError) as error:
        parser.exit(1, f"error: {error}\n")


if __name__ == "__main__":
    main()
