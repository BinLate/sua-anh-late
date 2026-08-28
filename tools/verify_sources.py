#!/usr/bin/env python3
"""Static verification for the sua-anh-late Android project.

Runs without JDK/Android SDK: checks that all required project files exist and
that every XML resource is well-formed. Exits 0 on success, 1 on failure.
"""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

REQUIRED_FILES = [
    "settings.gradle.kts",
    "build.gradle.kts",
    "gradle.properties",
    "gradle/libs.versions.toml",
    "gradle/wrapper/gradle-wrapper.properties",
    "app/build.gradle.kts",
    "app/proguard-rules.pro",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/binlate/suaanh/MainActivity.kt",
    "app/src/main/java/com/binlate/suaanh/editor/CanvasOverlay.kt",
    "app/src/main/java/com/binlate/suaanh/editor/EditorProcessor.kt",
    "app/src/main/java/com/binlate/suaanh/editor/EditorRenderer.kt",
    "app/src/main/java/com/binlate/suaanh/editor/EditorViewModel.kt",
    "app/src/main/java/com/binlate/suaanh/editor/model/Models.kt",
    "app/src/main/java/com/binlate/suaanh/ui/EditorScreen.kt",
    "app/src/main/java/com/binlate/suaanh/ui/components/ColorPickerDialog.kt",
    "app/src/main/java/com/binlate/suaanh/ui/components/TextContentDialog.kt",
    "app/src/main/java/com/binlate/suaanh/ui/components/ToolControls.kt",
    "app/src/main/java/com/binlate/suaanh/ui/components/Toolbars.kt",
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values/colors.xml",
    "app/src/main/res/values/themes.xml",
    "app/src/main/res/drawable/ic_launcher_foreground.xml",
]

# Every feature named in the request must appear in the Kotlin sources.
FEATURE_MARKERS = {
    "pen drawing": ["fun beginStroke", "EditorTool.PEN"],
    "highlight pen": ["EditorTool.HIGHLIGHT", "highlightAlpha"],
    "text overlay": ["EditorTool.TEXT", "addText"],
    "cover (pixelate/solid)": ["CoverMode.PIXELATE", "CoverMode.SOLID"],
    "shape tool": ["EditorTool.SHAPE", "ShapeKind", "Layer.Shape", "drawShape"],
    "blur brush tool": ["EditorTool.BLUR", "Layer.BlurStroke", "drawBlurStroke", "previewBlurredBitmap"],
    "color picker": ["ColorPickerDialog"],
    "size controls": ["SizeControl"],
    "undo/redo": ["fun undo", "fun redo"],
    "export/save": ["exportAndGetUri", "MediaStore"],
}

REQUIRED_STRINGS = [
    "app_name", "tool_pen", "tool_highlight", "tool_text", "tool_cover",
]


def main() -> int:
    failures: list[str] = []

    for rel in REQUIRED_FILES:
        if not (ROOT / rel).is_file():
            failures.append(f"missing required file: {rel}")

    sources = ""
    for kt in ROOT.glob("app/src/main/java/**/*.kt"):
        sources += kt.read_text(encoding="utf-8")

    for feature, markers in FEATURE_MARKERS.items():
        for marker in markers:
            if marker not in sources:
                failures.append(f"feature '{feature}': marker not found: {marker}")

    strings_path = ROOT / "app/src/main/res/values/strings.xml"
    if strings_path.is_file():
        try:
            xml_text = strings_path.read_text(encoding="utf-8")
            for name in REQUIRED_STRINGS:
                if f'name="{name}"' not in xml_text:
                    failures.append(f"strings.xml missing entry: {name}")
        except Exception as exc:  # noqa: BLE001
            failures.append(f"strings.xml unreadable: {exc}")

    for xml_file in ROOT.glob("app/src/main/res/**/*.xml"):
        try:
            ET.parse(xml_file)
        except Exception as exc:  # noqa: BLE001
            failures.append(f"malformed XML {xml_file.relative_to(ROOT)}: {exc}")

    manifest = ROOT / "app/src/main/AndroidManifest.xml"
    if manifest.is_file():
        try:
            ET.parse(manifest)
        except Exception as exc:  # noqa: BLE001
            failures.append(f"malformed manifest: {exc}")

    if failures:
        print("FAILED static verification:")
        for f in failures:
            print(f"  - {f}")
        return 1

    print(f"OK: {len(REQUIRED_FILES)} required files present, "
          f"{len(FEATURE_MARKERS)} features verified, all XML resources well-formed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
