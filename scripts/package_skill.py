#!/usr/bin/env python3
"""Build an installable skill ZIP using Python 3.9+ and the standard library."""
import argparse
from pathlib import Path
import re
import tempfile
import zipfile

ROOT_FILES = ("SKILL.md", "LICENSE", "README.md", "taplink-protocol-reference.md")
RESOURCE_TYPES = {
    "references": {".md"},
    "assets": {".kt", ".java"},
    "scripts": {".py"},
    "agents": {".yaml", ".yml"},
}


def build_package(root: Path, output: Path) -> Path:
    root, output = root.resolve(), output.absolute()
    if output.suffix != ".zip" or output.is_symlink():
        raise ValueError("Output must be a regular .zip path")
    for name in ("SKILL.md", "LICENSE"):
        if not (root / name).is_file() or (root / name).is_symlink():
            raise ValueError(f"Missing or linked required file: {name}")
    body = (root / "SKILL.md").read_text(encoding="utf-8")
    frontmatter = re.match(r"\A---\r?\n(.*?)\r?\n---(?:\r?\n|$)", body, re.S)
    match = re.search(r"^name:\s*([a-z0-9-]+)\s*$", frontmatter[1], re.M) if frontmatter else None
    if not match or not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", match[1]) or len(match[1]) > 64:
        raise ValueError("SKILL.md needs a valid plain lowercase name in YAML frontmatter")
    name = match[1]
    files = []
    for filename in ROOT_FILES:
        path = root / filename
        if path.is_symlink():
            raise ValueError(f"Symlink excluded from distribution: {filename}")
        if path.is_file():
            files.append(path)
    for directory, extensions in RESOURCE_TYPES.items():
        folder = root / directory
        if folder.is_symlink():
            raise ValueError(f"Symlink excluded from distribution: {directory}")
        for path in folder.rglob("*"):
            if path.is_symlink():
                raise ValueError(f"Symlink excluded from distribution: {path.relative_to(root)}")
            if (path.is_file() and path.suffix in extensions
                    and not any(part.startswith(".") or part == "__pycache__"
                                for part in path.relative_to(root).parts)):
                files.append(path)
    included = set(files)
    for path in files:
        if path.suffix != ".md":
            continue
        for target in re.findall(r"\]\(([^)]+)\)", path.read_text(encoding="utf-8")):
            if re.match(r"[a-zA-Z][a-zA-Z0-9+.-]*:", target):
                continue
            destination = target.split("#", 1)[0]
            if destination and (path.parent / destination).resolve() not in included:
                raise ValueError(f"Link target missing from bundle: {path.relative_to(root)} -> {target}")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent, suffix=".zip", delete=False) as temporary:
        temp = Path(temporary.name)
    try:
        with zipfile.ZipFile(temp, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for path in sorted(files):
                archive.write(path, f"{name}/{path.relative_to(root).as_posix()}")
        temp.replace(output)
    finally:
        temp.unlink(missing_ok=True)
    return output


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=root / "dist/taplink-integration.zip")
    args = parser.parse_args()
    try:
        output = build_package(root, args.output)
    except (ValueError, OSError) as error:
        parser.exit(1, f"Package failed: {error}\n")
    print(f"Created {output} ({output.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
