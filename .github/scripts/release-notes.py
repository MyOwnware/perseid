#!/usr/bin/env python3
import argparse
import json
import re
import subprocess
from pathlib import Path

VERSION = re.compile(r"v(0|[1-9][0-9]{0,5})\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)")
GROUPS = {
    "1: New": "✨ New",
    "2: Improvements": "🛠 Improvements",
    "3: Fixes": "🐞 Fixes",
    "4: Under the hood": "🔧 Under the hood",
}


def output(*command):
    return subprocess.check_output(command, text=True).strip()


def changes(revision):
    tags = output(
        "git", "tag", "--merged", f"{revision}^", "--sort=-version:refname",
    ).splitlines()
    previous = next((tag for tag in tags if VERSION.fullmatch(tag)), None)
    history = f"{previous}..{revision}" if previous else revision
    releases = json.loads(output("git-cliff", "--config", "cliff.toml", "--context", history))
    groups = {key: [] for key in GROUPS}
    for release in releases:
        for commit in release["commits"]:
            groups[commit["group"]].append(commit["message"])
    return groups


def summary(tag, dry_run):
    path = Path("docs/releases") / f"{tag}.md"
    if dry_run and not path.is_file():
        path = Path(".github/fixtures/release-preview.md")
    if not path.is_file():
        raise SystemExit(f"Missing release summary at {path}")
    title, _, description = path.read_text(encoding="utf-8").strip().partition("\n")
    if not title.startswith("# ") or not title[2:].strip() or not description.strip():
        raise SystemExit(f"{path} must contain a title and summary")
    return title[2:].strip(), description.strip()


def main():
    parser = argparse.ArgumentParser(description="Render the Perseid GitHub Release notes")
    parser.add_argument("tag")
    parser.add_argument("repository")
    parser.add_argument("--certificate")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    if not VERSION.fullmatch(args.tag):
        parser.error("expected a stable vX.Y.Z tag")
    if not re.fullmatch(r"[\w.-]+/[\w.-]+", args.repository, re.ASCII):
        parser.error("expected a GitHub repository in owner/name form")
    if not args.dry_run and not re.fullmatch(r"[A-Fa-f0-9]{64}", args.certificate or ""):
        parser.error("a verified SHA-256 certificate fingerprint is required")

    ref = "HEAD" if args.dry_run else f"refs/tags/{args.tag}"
    revision = output("git", "rev-parse", "--verify", f"{ref}^{{commit}}")
    title, description = summary(args.tag, args.dry_run)
    name = f"perseid-video-downloader-{args.tag}-arm64.apk"
    repository = f"https://github.com/{args.repository}"
    source = f"{repository}/tree/{revision}"
    licensed = f"{repository}/blob/{revision}"
    download = f"{repository}/releases/download/{args.tag}"
    apk = f"`{name}`" if args.dry_run else f"[{name}]({download}/{name})"
    checksum = f"`{name}.sha256`" if args.dry_run else f"[{name}.sha256]({download}/{name}.sha256)"
    fingerprint = args.certificate or "Available on a signed release. This dry run used no release secrets."

    print(f"## Perseid {args.tag} – {title}\n\n{description}")
    for group, commits in changes(revision).items():
        print(f"\n### {GROUPS[group]}\n")
        for message in commits or ["None."]:
            print(f"- {message}")
    print(
        f"\n### 📦 Download\n\n"
        f"Download {apk} for Android 10 or later on arm64-v8a, along with {checksum}.\n"
        f"\n### 🔐 Verify\n\n"
        f"```sh\nsha256sum -c {name}.sha256\n"
        f"apksigner verify --print-certs {name}\n```\n\n"
        f"Compare the signer certificate SHA-256 digest with this release fingerprint.\n\n"
        f"`{fingerprint}`\n\n"
        f"[Source code]({source}) · [GPL-3.0 license]({licensed}/LICENSE) · "
        f"[Engine notices and corresponding sources]({licensed}/app/src/main/assets/licenses/download-engine.txt)"
    )


if __name__ == "__main__":
    main()
