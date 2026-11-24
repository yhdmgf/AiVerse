#!/usr/bin/env python3
"""Regenerate the contributor credits from this repository's own history.

The wall is drawn from a JSON snapshot. Nothing in it is typed by hand, so the credits track the
repository instead of drifting away from it: a new contributor appears, an existing one gains
commits and areas, all by running this and committing the result. The GitHub Action runs it on a
schedule, which is what keeps the wall current without anybody thinking about it.

    python scripts/update-contributors.py             regenerate the snapshot
    python scripts/update-contributors.py --check     fail if it is out of date (for CI)
    python scripts/update-contributors.py --no-network   keep known accounts, fetch nothing

What is derived, and from where:

  commits      `git log` over authored, non-merge commits reachable from HEAD. Only HEAD: an
               author whose work sits on another branch is not credited for something this app does
               not contain, and counting every ref would double-count what a fork shares with its
               upstream.
  areas        the paths each person's commits touched, so a badge is a fact and not a guess.
  account      the GitHub account behind a commit email, asked of the API. Where an email is not
               tied to an account there is no photo and the wall draws the person's initial.

Unlike the project this was ported from there is no language dimension: AiVerse ships in two
languages maintained directly in the tree rather than through a translation platform, so there is
no translator population to credit and no `values-<lang>` signal worth reading off the paths.

Everyone with a commit in HEAD is included, however few. The only table below is the one thing git
cannot tell us: which several commit identities are the same person.

Pictures are not carried in the app: the snapshot points at each account's avatar on GitHub, and
the app fetches and caches them itself. So this needs no image library, and the repository grows
no binary.

Requires: git; `gh` (authenticated) only to resolve accounts.
"""

from __future__ import annotations

import argparse
import collections
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
import urllib.parse

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_ROOT = os.path.join(REPO_ROOT, "data")
OUTPUT = os.path.join(DATA_ROOT, "contributors.json")

# Several commit identities, one person. Keys are the display name the wall shows.
ALIASES = {
    "yhdmgf": [
        "225713231+yhdmgf@users.noreply.github.com",
        "yhdmgf@users.noreply.github.com",
        "qlqqyx@qq.com",
    ],
    "aiverse-app": [
        "aiverse-app@users.noreply.github.com",
        "262981771+qldwj@users.noreply.github.com",
        "qldwj@users.noreply.github.com",
    ],
}

# Accounts that commit here but are not people: release automation and the translation import.
EXCLUDED_EMAILS = {
    "github-actions[bot]@users.noreply.github.com",
    "41898282+github-actions[bot]@users.noreply.github.com",
    "noreply@weblate.org",
    "noreply@anthropic.com",
}
EXCLUDED_NAMES = re.compile(r"\[bot\]|^weblate$|^anonymous$|^github-actions", re.IGNORECASE)

# Handles that cannot be derived from the commit email, because the email is not attached to the
# account. Checked against GitHub by hand; without them the wall would either open a stranger's
# profile or show an initial for someone who does have an account.
LOGIN_OVERRIDES = {
    "yhdmgf": "yhdmgf",
    "aiverse-app": "aiverse-app",
}

# The name each account goes by on the wall, as a fallback for runs without the network. When a
# run does reach GitHub, the display name is read fresh off the account, so this only matters
# offline - the point is to show a person, not an account handle, even then.
LOGIN_NAME_OVERRIDES = {
    "yhdmgf": "yhdmgf",
    "aiverse-app": "aiverse-app",
}

# Order matters: the first area that matches wins. Kept in sync with the Kotlin ContributorArea.
AREA_ORDER = ["APP", "CORE", "TERMINAL", "EDITOR", "GIT", "PLUGIN", "UI", "DOCS"]

# Every domain the wall can draw a badge for - the Kotlin ContributorArea enum. A hand-entered
# contributor may name any of these directly, including the ones no commit path maps to (BUILD,
# ISSUE), so this is deliberately wider than AREA_ORDER, which only ranks the domains that path
# matching can produce.
VALID_AREA_KEYS = frozenset(
    ["APP", "BUILD", "DOCS", "ISSUE", "TERMINAL", "PLUGIN", "GIT",
     "EDITOR", "UI", "CORE"]
)


def area_rank(area: str) -> int:
    """Where a domain sits in the wall's fixed order, whatever produced it.

    AREA_ORDER ranks the domains path matching can reach; a hand-entered area may name one it
    cannot (BUILD, ISSUE). Those sort after the known ones rather than raising.
    """
    if area in AREA_ORDER:
        return AREA_ORDER.index(area)
    return len(AREA_ORDER) + sorted(VALID_AREA_KEYS - set(AREA_ORDER)).index(area)


# Paths under the Java source root, written relative to `com/aiverse/`. Prefixes, not substrings,
# so a sibling that merely contains the same letters does not steal the badge.
FEATURE_AREAS = {
    "feature/terminal": "TERMINAL",
    "feature/workspace": "CORE",
    "feature/editor": "EDITOR",
    "feature/git": "GIT",
    "feature/plugins": "PLUGIN",
    "feature/settings": "UI",
    "feature/onboarding": "UI",
    "feature/agreement": "UI",
    "feature/browser": "UI",
    "feature/search": "UI",
    "feature/web": "UI",
}

# Everything else that is a real feature module but has no dedicated badge: they fold into APP so
# their authors are still credited, rather than dropped because the enum has no slot for them.
APP_FALLBACK_FEATURES = ("feature/",)

# Generated data and the automation that produces it are not a contribution domain, and neither
# are the release workflows: they are outputs, and crediting them would put a bot on the wall.
EXCLUDED_PREFIXES = (
    ".github/",
    "scripts/",
    "ci/",
    "tmp/",
    "build/",
    "data/",
)

# Module roots that are terminal work even though they sit outside the `app/` source tree.
TERMINAL_MODULES = ("terminal-emulator/", "terminal-view/")


def area_of(path: str) -> str | None:
    lowered = path.lower()
    name = lowered.rsplit("/", 1)[-1]

    # Nothing here is part of the app: automation, scratch trees, and generated data.
    if lowered.startswith(EXCLUDED_PREFIXES):
        return None

    # Documentation first, so a README beside a feature does not read as app work.
    if lowered.startswith("docs-site/") or lowered.startswith("docs/") or name.endswith(".md"):
        return "DOCS"

    # Build plumbing is core infrastructure rather than any one feature.
    if (name.endswith(".gradle") or name.endswith(".gradle.kts") or name == "settings.gradle"
            or name.startswith("gradlew") or lowered.startswith("gradle/")):
        return "CORE"

    # Terminal ships as three modules: two standalone and one inside the app.
    if lowered.startswith(TERMINAL_MODULES):
        return "TERMINAL"
    if "/feature/web/" in lowered or lowered.startswith("web-ui/"):
        return "UI"
    # `plugins/` at the root is the plugin SDK and its samples; feature/plugins is the app side.
    if lowered.startswith("plugins/") or "/feature/plugins/" in lowered:
        return "PLUGIN"

    source_root = "app/src/main/java/com/aiverse/"
    if lowered.startswith(source_root):
        relative = lowered[len(source_root):]
        if relative.startswith("core/"):
            return "CORE"
        for prefix, area in FEATURE_AREAS.items():
            if relative.startswith(prefix):
                return area
        if relative.startswith("feature/"):
            return "APP"
        return "APP"

    # The app module outside its Java tree: resources, manifest, and the like.
    if lowered.startswith("app/"):
        return "APP"

    return None


def git_history() -> tuple[collections.Counter, dict[str, str], dict[str, collections.Counter]]:
    """Every author in HEAD, with the areas their commits touched."""
    out = subprocess.run(
        ["git", "-C", REPO_ROOT, "log", "--no-merges", "--format=__A__%ae\t%an", "--name-only",
         "HEAD"],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    ).stdout

    commits: collections.Counter = collections.Counter()
    names: dict[str, str] = {}
    areas: dict[str, collections.Counter] = collections.defaultdict(collections.Counter)

    email = None
    for line in out.splitlines():
        if line.startswith("__A__"):
            _, _, rest = line.partition("__A__")
            address, _, author = rest.partition("\t")
            email = address.strip().lower()
            # First name wins: an identity that renamed itself mid-history keeps its oldest name,
            # which is the one people would recognise.
            names.setdefault(email, author.strip() or address.strip())
            commits[email] += 1
        elif line.strip() and email:
            path = line.strip()
            area = area_of(path)
            if area:
                areas[email][area] += 1
    return commits, names, areas


def resolve_account(email: str, remote: str) -> tuple[str | None, str | None]:
    """Which GitHub account, and which picture, this commit email belongs to.

    Security: parses JSON natively instead of using --jq, avoiding shell injection if an email
    contains characters that could be interpreted as jq expressions.
    """
    if shutil.which("gh") is None:
        return None, None
    query = urllib.parse.quote(email, safe="")
    result = subprocess.run(
        ["gh", "api", f"repos/{remote}/commits?author={query}&per_page=1"],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    try:
        payload = json.loads(result.stdout)
        if not payload:
            return None, None
        author = payload[0].get("author") or {}
        login = author.get("login")
        avatar_url = author.get("avatar_url")
        return login, avatar_url
    except (json.JSONDecodeError, IndexError, KeyError, TypeError):
        return None, None


def resolve_profile(login: str) -> str | None:
    """The display name a GitHub account goes by now, distinct from its immutable login.

    People rename themselves to something they would rather be called; the commit author name is
    frozen whenever that happened, so it lags behind. Read fresh off the account each run so the
    wall follows along. Returns None on any failure - the caller then falls back to the login.
    """
    if shutil.which("gh") is None:
        return None
    result = subprocess.run(
        ["gh", "api", f"users/{urllib.parse.quote(login, safe='')}"],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    try:
        payload = json.loads(result.stdout)
        return payload.get("name") or None
    except (json.JSONDecodeError, AttributeError, TypeError):
        return None


def remote_slug() -> str:
    url = subprocess.run(
        ["git", "-C", REPO_ROOT, "remote", "get-url", "origin"],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    ).stdout.strip()
    match = re.search(r"github\.com[:/](.+?)(?:\.git)?$", url)
    return match.group(1) if match else "yhdmgf/AiVerse"


def known_accounts() -> dict[str, tuple[str | None, str | None]]:
    """Accounts already recorded in the snapshot, so a run without the network changes nothing."""
    if not os.path.isfile(OUTPUT):
        return {}
    try:
        with open(OUTPUT, encoding="utf-8") as handle:
            payload = json.load(handle)
    except (OSError, ValueError):
        return {}
    found = {}
    for entry in payload.get("contributors", []):
        key = entry.get("login") or entry.get("name")
        if key:
            found[key] = (
                entry.get("login"),
                entry.get("avatarUrl"),
                entry.get("name"),
            )
    return found


def merge_same_account(people: list[dict]) -> list[dict]:
    """One account is one person.

    Two commit identities can resolve to the same GitHub account. The alias table above folds the
    ones that are known, but the account itself is the real answer - and left alone, that person is
    credited twice: two faces on the wall holding one picture, and counted twice in the badge.
    """
    merged: dict[str, dict] = {}
    for person in people:
        key = person["login"] or person["name"]
        keeper = merged.get(key)
        if keeper is None:
            merged[key] = person
            continue
        # The busier identity names the person, the way the alias table does it by hand.
        if person["commits"] > keeper["commits"]:
            keeper, person = person, keeper
        keeper["commits"] += person["commits"]
        areas = dict.fromkeys(keeper["areas"] + person["areas"])
        keeper["areas"] = sorted(areas, key=AREA_ORDER.index)[:3]
        keeper["avatarUrl"] = keeper["avatarUrl"] or person["avatarUrl"]
        merged[key] = keeper

    return sorted(merged.values(), key=lambda p: (-p["commits"], p["name"]))


def build(network: bool, remote: str) -> dict:
    commits, names, areas = git_history()
    known = known_accounts()

    # Fold the identities we know belong together, then take everything that is left as itself.
    folded: dict[str, list[str]] = {}
    for display, emails in ALIASES.items():
        folded[display] = [e.lower() for e in emails]
    assigned = {e for emails in folded.values() for e in emails}
    for email in commits:
        if email in assigned or email in EXCLUDED_EMAILS:
            continue
        folded.setdefault(names.get(email, email), []).append(email)

    people = []
    for display, emails in folded.items():
        if EXCLUDED_NAMES.search(display):
            continue
        total = sum(commits.get(email, 0) for email in emails)
        if total == 0:
            continue

        touched: collections.Counter = collections.Counter()
        for email in emails:
            touched.update(areas.get(email, {}))

        ranked = sorted(touched, key=lambda key: (-touched[key], AREA_ORDER.index(key)))
        # Three badges is what fits on one row of the card.
        areas_for_person = ranked[:3]

        people.append({
            "name": display,
            "login": None,
            "avatarUrl": None,
            "commits": total,
            "areas": areas_for_person,
            "_emails": emails,
        })

    people.sort(key=lambda p: (-p["commits"], p["name"]))

    for person in people:
        # The login comes first, keyed by the stable commit author name so a later rename cannot
        # break the lookup.
        login = LOGIN_OVERRIDES.get(person["name"])
        avatar_url = None
        if login is None:
            for email in person["_emails"]:
                if network:
                    login, avatar_url = resolve_account(email, remote)
                    if login:
                        break

        # Once the login is known, the name people go by now is read off their account: a commit's
        # author name is frozen whenever they last renamed, so it trails behind what they call
        # themselves today. Fall back to the login, which always resolves even offline.
        cached = known.get(login, (None, None, None)) if login else (None, None, None)
        display = (
            LOGIN_NAME_OVERRIDES.get(login)
            or (resolve_profile(login) if (login and network) else None)
            or cached[2]
        ) if login else None

        person["login"] = login
        person["name"] = display or login or person["name"]
        person["avatarUrl"] = avatar_url or cached[1] or (
            f"https://github.com/{login}.png?size=256" if login else None
        )
        # A note is only ever hand-entered (see --extra); a counted contributor has none to show.
        person["note"] = None
        person.pop("_emails")

    return {
        "generated": subprocess.run(
            ["git", "-C", REPO_ROOT, "log", "-1", "--format=%cs", "HEAD"],
            capture_output=True, text=True, encoding="utf-8", errors="replace",
        ).stdout.strip(),
        "contributors": people,
    }


def merge_extra(people: list[dict], extra: list[dict]) -> list[dict]:
    """Fold hand-entered contributors into the ones read off the history.

    A schedule can only ever credit what a commit records, but someone may have earned a place on
    the wall without one - a design, a translation pass, a decision. A manual run names them here,
    and they are merged rather than appended so a hand-entered entry and a counted one for the same
    account become a single face on the wall, not two.

    Each entry is matched on its login (its name where there is no login). A match adds the commits
    and unions the areas; the display name and the note are the hand-entered ones, since the person
    typing them is saying how they want to be shown and what they did. An entry for nobody counted
    is added outright. Returns the list re-sorted the way the wall reads it.
    """
    for entry in extra:
        if not isinstance(entry, dict):
            continue
        name = str(entry.get("name") or "").strip()
        if not name:
            continue
        login = str(entry.get("login") or "").strip() or None
        note = str(entry.get("note") or "").strip() or None
        try:
            commits = int(entry.get("commits") or 0)
        except (TypeError, ValueError):
            commits = 0
        raw_areas = entry.get("areas")
        areas = [a for a in raw_areas if isinstance(a, str) and a in VALID_AREA_KEYS] \
            if isinstance(raw_areas, list) else []
        # A pinned entry states the badges exactly, so they replace whatever the history derived
        # rather than piling onto it. A hand-typed --extra entry still unions, so naming one more
        # domain adds it without erasing the ones the commits earned.
        override_areas = entry.get("pinned") is True

        key = login or name
        existing = next((p for p in people if (p["login"] or p["name"]) == key), None)
        if existing is not None:
            existing["commits"] += commits
            if override_areas:
                existing["areas"] = sorted(set(areas), key=area_rank)[:3]
            else:
                union = dict.fromkeys(existing["areas"] + areas)
                existing["areas"] = sorted(union, key=area_rank)[:3]
            existing["name"] = name
            existing["note"] = note
            existing["avatarUrl"] = existing["avatarUrl"] or (
                f"https://github.com/{login}.png?size=256" if login else None
            )
            continue

        people.append({
            "name": name,
            "login": login,
            "avatarUrl": f"https://github.com/{login}.png?size=256" if login else None,
            "commits": commits,
            "areas": sorted(set(areas), key=area_rank)[:3],
            "note": note,
        })

    return sorted(people, key=lambda p: (-p["commits"], p["name"]))


def fetch_persisted_manual(remote: str) -> list[dict]:
    """Read the hand-added contributors off the last published snapshot.

    The wall is recomputed from the commit history every run, and a schedule can only ever credit
    what a commit records. Someone added by hand - a reviewer, a translator, a reporter whose
    contribution left no commit - would therefore drop off the next time the workflow fires without
    an --extra list naming them. Reading them back off the published snapshot and folding them in
    again makes a hand-added face sticky: once on the wall, it stays there until a maintainer
    removes it from the snapshot itself.

    Only entries with no commits of their own are carried over, so a real contributor is never
    duplicated or inflated by their own past snapshot. Network failures degrade to an empty list so
    an offline or first run still succeeds.
    """
    url = f"https://raw.githubusercontent.com/{remote}/data/{OUTPUT}"
    try:
        request = urllib.request.Request(url, headers={"Accept": "application/json"})
        with urllib.request.urlopen(request, timeout=30) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except (urllib.error.URLError, OSError, json.JSONDecodeError):
        return []
    if not isinstance(payload, dict):
        return []
    contributors = payload.get("contributors")
    if not isinstance(contributors, list):
        return []
    manual = []
    for entry in contributors:
        if not isinstance(entry, dict):
            continue
        commits = entry.get("commits")
        if isinstance(commits, int) and not isinstance(commits, bool) and commits > 0:
            continue
        if not str(entry.get("name") or "").strip():
            continue
        manual.append({
            "name": entry.get("name"),
            "login": entry.get("login"),
            "commits": 0,
            "note": entry.get("note"),
            "areas": entry.get("areas") if isinstance(entry.get("areas"), list) else [],
        })
    return manual


def merge_persisted_manual(people: list[dict], persisted: list[dict], remote: str) -> list[dict]:
    """Fold the hand-added contributors from the last snapshot back into this run's wall.

    Merged, not appended: a name that also shows up counted this run becomes one face, and a
    hand-added note or area the operator re-stated in --extra wins because that call runs after
    this one. Anyone only ever added by hand is carried forward verbatim.
    """
    if not persisted:
        return people
    return merge_extra(people, persisted)



def render(payload: dict) -> str:
    # Indented and newline-terminated so the diff of a refresh is readable in review.
    return json.dumps(payload, ensure_ascii=False, indent=2) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="exit non-zero if the snapshot is out of date")
    parser.add_argument("--no-network", action="store_true",
                        help="do not ask GitHub or download avatars")
    parser.add_argument("--out", default=OUTPUT, help="where to write the snapshot")
    parser.add_argument("--extra", default="",
                        help="a JSON array of hand-entered contributors to merge in")
    args = parser.parse_args()

    # A hand-entered list arrives as a JSON string. Parse it natively rather than through a shell,
    # and treat an empty or malformed one as "nobody extra" - a scheduled run passes nothing and
    # must still run, and a typo in a manual run should not take the whole snapshot down.
    extra: list[dict] = []
    if args.extra.strip():
        try:
            parsed = json.loads(args.extra)
        except json.JSONDecodeError:
            print("Ignoring --extra: it is not valid JSON.", file=sys.stderr)
            parsed = []
        if isinstance(parsed, list):
            extra = parsed

    payload = build(network=not (args.no_network or args.check), remote=remote_slug())
    payload["contributors"] = merge_extra(payload["contributors"], extra)
    # Carry forward anyone added by hand on an earlier run, so a scheduled fire without an
    # --extra list cannot silently drop them off the wall.
    payload["contributors"] = merge_persisted_manual(
        payload["contributors"], fetch_persisted_manual(remote_slug()), remote_slug()
    )
    content = render(payload)

    if args.check:
        current = open(args.out, encoding="utf-8").read() if os.path.isfile(args.out) else ""
        if current != content:
            print("Contributor credits are out of date. Run: python scripts/update-contributors.py",
                  file=sys.stderr)
            return 1
        print("Contributor credits are up to date.")
        return 0

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(content)

    total = len(json.loads(content)["contributors"])
    print(f"Wrote {os.path.relpath(args.out, REPO_ROOT)} with {total} contributors")
    return 0


if __name__ == "__main__":
    sys.exit(main())
