#!/usr/bin/env python3

"""Generate deterministic Android dependency SBOM and bundled license notices."""

from __future__ import annotations

import argparse
import datetime
import hashlib
import json
import pathlib
import re
import shutil


def parse_status(path: pathlib.Path) -> list[dict[str, str]]:
    packages: list[dict[str, str]] = []
    for paragraph in path.read_text().strip().split("\n\n"):
        fields: dict[str, str] = {}
        for line in paragraph.splitlines():
            if ": " in line:
                key, value = line.split(": ", 1)
                fields[key] = value
        if fields.get("Package") and fields.get("Architecture", "").endswith("-android"):
            packages.append(fields)
    return packages


def spdx_id(name: str) -> str:
    return "SPDXRef-Package-" + re.sub(r"[^A-Za-z0-9.-]", "-", name)


def manifest_license(path: pathlib.Path) -> str | None:
    """Return the license identifier vcpkg recorded in a port's SPDX manifest."""
    document = json.loads(path.read_text())
    for package in document.get("packages", []):
        for field in ("licenseConcluded", "licenseDeclared"):
            value = package.get(field)
            if value and value != "NOASSERTION":
                return value
    return None


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository-root", type=pathlib.Path, required=True)
    parser.add_argument("--status-file", type=pathlib.Path, required=True)
    parser.add_argument("--installed-root", type=pathlib.Path, required=True)
    parser.add_argument("--output-dir", type=pathlib.Path, required=True)
    args = parser.parse_args()

    repository_root = args.repository_root.resolve()
    status_file = (repository_root / args.status_file).resolve()
    installed_root = (repository_root / args.installed_root).resolve()
    output_dir = (repository_root / args.output_dir).resolve()
    licenses_dir = output_dir / "licenses"
    licenses_dir.mkdir(parents=True, exist_ok=True)

    unique_packages: dict[str, dict[str, str]] = {}
    for package in parse_status(status_file):
        unique_packages.setdefault(package["Package"], package)
    if not unique_packages:
        raise SystemExit(f"No Android packages found in {status_file}")

    sbom_packages = []
    relationships = []
    for name, package in sorted(unique_packages.items()):
        port_share_dir = installed_root / "share" / name
        copyright_file = port_share_dir / "copyright"
        spdx_manifest = port_share_dir / "vcpkg.spdx.json"
        license_concluded = "NOASSERTION"
        if copyright_file.is_file():
            shutil.copyfile(copyright_file, licenses_dir / f"{name}.txt")
        elif spdx_manifest.is_file():
            # Internal vcpkg helper ports ship no copyright file; fall back to the
            # license identifier recorded in their SPDX manifest.
            license_concluded = manifest_license(spdx_manifest)
            if license_concluded is None:
                raise SystemExit(
                    f"No copyright file and no SPDX license identifier for: {port_share_dir}"
                )
            (licenses_dir / f"{name}.txt").write_text(
                f"{name} ships no vcpkg copyright file.\n"
                f"License identifier declared in vcpkg.spdx.json: {license_concluded}\n"
            )
        else:
            raise SystemExit(f"Missing vcpkg copyright file: {copyright_file}")

        identifier = spdx_id(name)
        sbom_packages.append(
            {
                "SPDXID": identifier,
                "name": name,
                "versionInfo": package.get("Version", "NOASSERTION"),
                "downloadLocation": "NOASSERTION",
                "filesAnalyzed": False,
                "licenseConcluded": license_concluded,
                "licenseDeclared": "NOASSERTION",
                "copyrightText": "NOASSERTION",
                "externalRefs": [
                    {
                        "referenceCategory": "PACKAGE-MANAGER",
                        "referenceType": "purl",
                        "referenceLocator": (
                            f"pkg:vcpkg/{name}@{package.get('Version', 'unknown')}"
                        ),
                    }
                ],
            }
        )
        relationships.append(
            {
                "spdxElementId": "SPDXRef-DOCUMENT",
                "relationshipType": "DESCRIBES",
                "relatedSpdxElement": identifier,
            }
        )

    root_license = repository_root / "LICENSE.md"
    valhalla_license = repository_root / "src" / "valhalla" / "LICENSE.md"
    for source, destination in (
        (root_license, "valhalla-mobile.txt"),
        (valhalla_license, "valhalla-engine.txt"),
    ):
        if not source.is_file():
            raise SystemExit(f"Missing project license: {source}")
        shutil.copyfile(source, licenses_dir / destination)

    commit = (
        __import__("os").environ.get("GITHUB_SHA")
        or hashlib.sha256(status_file.read_bytes()).hexdigest()
    )
    now = datetime.datetime.now(datetime.UTC).replace(microsecond=0).isoformat()
    document = {
        "spdxVersion": "SPDX-2.3",
        "dataLicense": "CC0-1.0",
        "SPDXID": "SPDXRef-DOCUMENT",
        "name": "valhalla-mobile-android-vcpkg-dependencies",
        "documentNamespace": (
            "https://github.com/papagrationbiz-sketch/valhalla-mobile/"
            f"spdx/android/{commit}"
        ),
        "creationInfo": {
            "created": now,
            "creators": ["Tool: generate_android_release_metadata.py"],
        },
        "packages": sbom_packages,
        "relationships": relationships,
    }
    (output_dir / "android-dependencies.spdx.json").write_text(
        json.dumps(document, indent=2, sort_keys=True) + "\n"
    )
    shutil.copyfile(status_file, output_dir / "vcpkg-status.txt")


if __name__ == "__main__":
    main()
