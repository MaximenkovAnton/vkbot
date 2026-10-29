#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

index="docs/capabilities/index.md"
map_dir="docs/capabilities"

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

[ -f "$index" ] || fail "$index does not exist"

status=0

strip_line_range() {
    sed -E 's/:[0-9]+(-[0-9]+)?$//' <<<"$1"
}

paths_in() {
    grep -oE '`(src|docs)/[^`]+`' "$1" | tr -d '`' | sort -u || true
}

resolve() {
    if [ -f "$1" ]; then
        printf '%s\n' "$1"
    elif [ -f "$map_dir/$1" ]; then
        printf '%s\n' "$map_dir/$1"
    else
        return 1
    fi
}

links_in_index=$(grep -oE '\]\([^)[:space:]]+\.md\)' "$index" | sed -E 's/^\]\(//; s/\)$//' | sort -u || true)
[ -n "$links_in_index" ] || fail "$index lists no capability files"

checked_files="$index"

while IFS= read -r link; do
    [ -z "$link" ] && continue
    case "$link" in
        http*|/*) continue ;;
    esac
    if ! resolve "$link" >/dev/null; then
        echo "FAIL: $index links $link, which does not exist" >&2
        status=1
        continue
    fi
    checked_files="$checked_files $(resolve "$link")"
done <<< "$links_in_index"

for file in $checked_files; do
    while IFS= read -r raw; do
        [ -z "$raw" ] && continue
        path=$(strip_line_range "$raw")
        if [ ! -e "$path" ]; then
            echo "FAIL: $file references $raw, which does not exist" >&2
            status=1
        fi
    done < <(paths_in "$file")
done

for file in "$map_dir"/*.md; do
    [ -e "$file" ] || continue
    [ "$file" = "$index" ] && continue

    if ! grep -qF "]($(basename "$file"))" "$index"; then
        echo "FAIL: $file is not listed in $index" >&2
        status=1
    fi

    while IFS= read -r id; do
        [ -z "$id" ] && continue
        if ! compgen -G "docs/adr/${id}-*.md" >/dev/null; then
            echo "FAIL: $file references ADR $id, but docs/adr/${id}-*.md does not exist" >&2
            status=1
        fi
    done < <(grep -oE 'ADR [0-9]{4}' "$file" | awk '{print $2}' | sort -u || true)

    capability_status=$(grep -m1 -E '^- \*\*Статус:\*\* ' "$file" | sed -E 's/^- \*\*Статус:\*\* //' || true)
    case "$capability_status" in
        delivered|planned|deprecated) ;;
        *)
            echo "FAIL: $file has status '$capability_status'; expected delivered, planned or deprecated" >&2
            status=1
            ;;
    esac

    declared_id=$(grep -m1 -E '^- \*\*id:\*\* `[^`]+`' "$file" | sed -E 's/.*`([^`]+)`.*/\1/' || true)
    if [ -z "$declared_id" ]; then
        echo "FAIL: $file has no '- **id:**' line" >&2
        status=1
    elif [ "$declared_id" != "$(basename "$file" .md)" ]; then
        echo "FAIL: $file declares id '$declared_id' but is named $(basename "$file")" >&2
        status=1
    fi

    while IFS= read -r criterion_line; do
        block=$(tail -n +"$criterion_line" "$file" | awk 'NR > 1 && (/^- / || /^## /) { exit } { print }')
        if ! grep -qE '`src/[^`]*/src/(test|testFixtures)/[^`]+`' <<<"$block"; then
            echo "FAIL: $file:$criterion_line: acceptance criterion without a test path" >&2
            status=1
        fi
    done < <(grep -nE 'AC-[a-z0-9-]+' "$file" | cut -d: -f1 || true)
done

[ "$status" -eq 0 ] || exit 1
echo "OK: capability map paths, test paths, statuses and ADR ids resolve"
