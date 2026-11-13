#!/usr/bin/env python3
"""Generate shields-style SVG badges from the JaCoCo and JUnit XML reports.

The CI `coverage` job runs this script and publishes the two files to the `badges`
branch, so README shows live numbers instead of a value hardcoded in the docs.
"""

import argparse
import glob
import os
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

COVERAGE_LEVELS = ((0.60, "#4c1"), (0.40, "#97ca00"), (0.25, "#dfb317"))
FAILURE_COLOR = "#e05d44"
NEUTRAL_COLOR = "#9f9f9f"


def line_coverage(path):
    root = ET.parse(path).getroot()
    covered = missed = 0
    for counter in root.findall("counter"):
        if counter.get("type") == "LINE":
            covered += int(counter.get("covered", 0))
            missed += int(counter.get("missed", 0))
    total = covered + missed
    if total == 0:
        raise SystemExit(f"no LINE coverage in {path}")
    return covered / total


def test_totals(pattern):
    total = failures = 0
    for path in sorted(glob.glob(pattern, recursive=True)):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        if root.tag != "testsuite":
            continue
        total += int(root.get("tests", 0))
        failures += int(root.get("failures", 0)) + int(root.get("errors", 0))
    return total, failures


def badge(label, message, value_color):
    label_width = max(54, 7 * len(label) + 10)
    value_width = max(54, 7 * len(message) + 10)
    width = label_width + value_width
    label_x = label_width / 2
    value_x = label_width + value_width / 2
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="20" role="img" aria-label="{escape(label)}: {escape(message)}">
  <linearGradient id="s" x2="0" y2="100%">
    <stop offset="0" stop-color="#bbb" stop-opacity=".1"/>
    <stop offset="1" stop-opacity=".1"/>
  </linearGradient>
  <rect width="{width}" height="20" rx="3" fill="#555"/>
  <rect x="{label_width}" width="{value_width}" height="20" rx="3" fill="{value_color}"/>
  <rect x="{label_width}" width="4" height="20" fill="{value_color}"/>
  <rect width="{width}" height="20" rx="3" fill="url(#s)"/>
  <g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" font-size="11">
    <text x="{label_x}" y="15" fill="#010101" fill-opacity=".3">{escape(label)}</text>
    <text x="{label_x}" y="14">{escape(label)}</text>
    <text x="{value_x}" y="15" fill="#010101" fill-opacity=".3">{escape(message)}</text>
    <text x="{value_x}" y="14">{escape(message)}</text>
  </g>
</svg>
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--coverage-xml", required=True, help="JaCoCo XML report")
    parser.add_argument("--test-results", required=True, help="glob with JUnit XML reports")
    parser.add_argument("--output-dir", default="build/badges", help="directory for the SVG files")
    args = parser.parse_args()

    ratio = line_coverage(args.coverage_xml)
    coverage_message = f"{ratio * 100:.1f}%"
    coverage_color = next((color for limit, color in COVERAGE_LEVELS if ratio >= limit), FAILURE_COLOR)

    tests, failures = test_totals(args.test_results)
    if tests == 0:
        tests_message, tests_color = "no tests", NEUTRAL_COLOR
    elif failures:
        tests_message, tests_color = f"{tests} tests, {failures} failing", FAILURE_COLOR
    else:
        tests_message, tests_color = f"{tests} passing", "#4c1"

    os.makedirs(args.output_dir, exist_ok=True)
    badges = {
        "coverage.svg": badge("coverage", coverage_message, coverage_color),
        "tests.svg": badge("tests", tests_message, tests_color),
    }
    for name, svg in badges.items():
        with open(os.path.join(args.output_dir, name), "w", encoding="utf-8") as out:
            out.write(svg)
    print(f"coverage: {coverage_message}")
    print(f"tests: {tests_message}")


if __name__ == "__main__":
    main()
