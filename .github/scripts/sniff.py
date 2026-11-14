# SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
# SPDX-License-Identifier: AGPL-3.0-or-later

"""Latest-release lookups through the sniff v3 API.

Queries GET {base_url}/download/{package_name}/{channel}. Each client gets a
limited number of uncached lookups per minute, so 429 and 503 answers are
retried after their Retry-After delay.
"""

from __future__ import annotations

import json
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass

DEFAULT_BASE_URL = "https://sniff.madkarma.top/v3"
DEFAULT_CHANNEL = "stable"
USER_AGENT = {"User-Agent": "Mozilla/5.0"}
RETRYABLE_STATUSES = {429, 503}
MAX_ATTEMPTS = 4
MAX_RETRY_DELAY_SECONDS = 120


@dataclass(frozen=True)
class Release:
    version_name: str
    version_code: int | None
    title: str


def normalize_version_name(raw: str) -> str:
    """Remove Play suffixes, for example turning '289.20 - Stable' into '289.20'."""
    return raw.split(" - ")[0].strip().split()[0]


def retry_delay(error: urllib.error.HTTPError) -> int:
    raw = error.headers.get("Retry-After", "")
    delay = int(raw) if raw.isdigit() else 60
    return min(max(delay, 1), MAX_RETRY_DELAY_SECONDS)


def fetch_payload(package: str, url: str) -> object | None:
    request = urllib.request.Request(url, headers=USER_AGENT)

    for attempt in range(1, MAX_ATTEMPTS + 1):
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.load(response)
        except urllib.error.HTTPError as e:
            detail = e.read().decode("utf-8", "replace")[:200]
            if e.code not in RETRYABLE_STATUSES or attempt == MAX_ATTEMPTS:
                print(f"warning: {package}: GET {url} -> HTTP {e.code}: {detail}", file=sys.stderr)
                return None

            delay = retry_delay(e)
            print(f"  {package}: HTTP {e.code}, retrying in {delay}s", file=sys.stderr)
            time.sleep(delay)
        except urllib.error.URLError as e:
            print(f"warning: {package}: GET {url} failed: {e}", file=sys.stderr)
            return None
        except ValueError as e:
            print(f"warning: {package}: GET {url} returned invalid JSON: {e}", file=sys.stderr)
            return None

    return None


def latest_release(package: str, base_url: str, channel: str) -> Release | None:
    """Return the latest release on the channel, or None on failure."""
    url = f"{base_url.rstrip('/')}/download/{package}/{channel}"
    payload = fetch_payload(package, url)
    if payload is None:
        return None

    try:
        item = payload["data"]["item"]
        details = item["details"]["app_details"]
        raw_version = str(details["version_string"])
        title = str(item.get("title") or package)
    except KeyError, TypeError:
        print(f"warning: {package}: unexpected details shape", file=sys.stderr)
        return None

    version_code = details.get("version_code")
    if not isinstance(version_code, int):
        version_code = None
    return Release(normalize_version_name(raw_version), version_code, title)
