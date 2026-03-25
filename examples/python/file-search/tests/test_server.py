import importlib.util
import tempfile
import unittest
from pathlib import Path


SERVER_PATH = Path(__file__).resolve().parents[1] / "server.py"
SPEC = importlib.util.spec_from_file_location("file_search_server", SERVER_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Unable to load server module from {SERVER_PATH}")

file_search_server = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(file_search_server)


class FileSearchServerTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.allowed_root = self.root / "allowed"
        self.allowed_root.mkdir()
        self.escape_root = self.root / "allowed-escape"
        self.escape_root.mkdir()

        file_search_server.ALLOWED_ROOT = str(self.allowed_root)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_validate_path_rejects_sibling_prefix(self) -> None:
        leaked_file = self.escape_root / "secret.txt"
        leaked_file.write_text("top secret", encoding="utf-8")

        with self.assertRaisesRegex(ValueError, "outside the allowed directory"):
            file_search_server.validate_path(str(leaked_file))

    def test_search_files_keeps_results_inside_sandbox(self) -> None:
        inside_file = self.allowed_root / "notes.txt"
        inside_file.write_text("visible", encoding="utf-8")
        leaked_file = self.escape_root / "secret.txt"
        leaked_file.write_text("hidden", encoding="utf-8")

        inside_results = file_search_server.search_files("*.txt")
        escaped_results = file_search_server.search_files("../allowed-escape/*.txt")

        self.assertEqual(inside_results, "notes.txt")
        self.assertIn('No files found matching "../allowed-escape/*.txt"', escaped_results)

    def test_search_content_ignores_matches_outside_sandbox(self) -> None:
        leaked_file = self.escape_root / "secret.txt"
        leaked_file.write_text("hidden needle", encoding="utf-8")

        results = file_search_server.search_content(
            "needle",
            "../allowed-escape/*.txt",
        )

        self.assertIn('No matches for "needle"', results)


if __name__ == "__main__":
    unittest.main()
