import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / "scripts/package_skill.py"
spec = importlib.util.spec_from_file_location("package_skill", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PackageTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "repo-name"
        self.root.mkdir()
        (self.root / "SKILL.md").write_text(
            "---\nname: taplink-integration\ndescription: Integrate payments\n---\n"
            "[Guide](references/start.md)\n", encoding="utf-8")
        (self.root / "LICENSE").write_text("MIT", encoding="utf-8")
        (self.root / "references").mkdir()
        (self.root / "references/start.md").write_text(
            "[Example](../assets/Example.java)\n", encoding="utf-8")
        (self.root / "assets").mkdir()
        (self.root / "assets/Example.java").write_text("class Example {}", encoding="utf-8")
        self.output = Path(self.temp.name) / "delivery.zip"

    def test_complete_installable_bundle_excludes_local_state(self):
        for name in [".env", "local.secrets.properties", ".idea/workspace.xml",
                     ".git/config", "assets/.DS_Store", "assets/local.properties"]:
            p = self.root / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text("LOCAL_SECRET_SENTINEL", encoding="utf-8")
        module.build_package(self.root, self.output)
        with zipfile.ZipFile(self.output) as archive:
            names = set(archive.namelist())
            self.assertEqual(names, {"taplink-integration/SKILL.md", "taplink-integration/LICENSE",
                                     "taplink-integration/references/start.md",
                                     "taplink-integration/assets/Example.java"})
            self.assertFalse(any(b"LOCAL_SECRET_SENTINEL" in archive.read(n) for n in names))

    def test_missing_link_fails_without_replacing_existing_delivery(self):
        self.output.write_bytes(b"existing delivery")
        (self.root / "assets/Example.java").unlink()
        with self.assertRaises(ValueError):
            module.build_package(self.root, self.output)
        self.assertEqual(self.output.read_bytes(), b"existing delivery")

    def test_external_symlink_is_rejected(self):
        outside = Path(self.temp.name) / "outside.java"
        outside.write_text("private data", encoding="utf-8")
        (self.root / "assets/Linked.java").symlink_to(outside)
        with self.assertRaises(ValueError):
            module.build_package(self.root, self.output)
        self.assertFalse(self.output.exists())

    def test_resource_directory_symlink_is_rejected(self):
        outside = Path(self.temp.name) / "outside"
        outside.mkdir()
        (self.root / "scripts").symlink_to(outside, target_is_directory=True)
        with self.assertRaises(ValueError):
            module.build_package(self.root, self.output)

    def test_invalid_skill_name_is_rejected(self):
        (self.root / "SKILL.md").write_text("---\nname: ../outside\n---\n", encoding="utf-8")
        with self.assertRaises(ValueError):
            module.build_package(self.root, self.output)


if __name__ == "__main__":
    unittest.main()
