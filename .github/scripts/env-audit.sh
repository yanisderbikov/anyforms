#!/usr/bin/env bash
# Сравнивает ${ENV} плейсхолдеры и property-ключи в application.properties
# между base-веткой и текущим деревом. Печатает markdown в stdout.
# Использование: env-audit.sh <base-ref> [path-to-properties]
set -euo pipefail

BASE_REF="${1:?base ref required}"
FILE="${2:-src/main/resources/application.properties}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

placeholders() { grep -oE '\$\{[A-Za-z0-9_.-]+(:[^}]*)?\}' | sed -E 's/^\$\{//; s/\}$//' | sort -u; }
prop_keys()    { grep -E '^[[:space:]]*[A-Za-z0-9_.-]+[[:space:]]*=' | sed -E 's/^[[:space:]]*([^=[:space:]]+).*/\1/' | sort -u; }
# key -> "prop=env" пары, чтобы ловить переезд env между ключами
prop_env_map() { grep -oE '^[[:space:]]*[A-Za-z0-9_.-]+[[:space:]]*=.*\$\{[A-Za-z0-9_.-]+' | sed -E 's/^[[:space:]]*([^=[:space:]]+)[[:space:]]*=.*\$\{([A-Za-z0-9_.-]+).*/\2 \1/' | sort -u; }

if git cat-file -e "${BASE_REF}:${FILE}" 2>/dev/null; then
  git show "${BASE_REF}:${FILE}" > "$TMP/base.properties"
else
  : > "$TMP/base.properties"
fi
cp "$FILE" "$TMP/head.properties"

placeholders < "$TMP/base.properties" > "$TMP/base_ph.txt" || true
placeholders < "$TMP/head.properties" > "$TMP/head_ph.txt" || true
cut -d: -f1 "$TMP/base_ph.txt" | sort -u > "$TMP/base_names.txt"
cut -d: -f1 "$TMP/head_ph.txt" | sort -u > "$TMP/head_names.txt"
comm -13 "$TMP/base_names.txt" "$TMP/head_names.txt" > "$TMP/added.txt"
comm -23 "$TMP/base_names.txt" "$TMP/head_names.txt" > "$TMP/removed.txt"

# дефолты: name -> default (пусто если нет двоеточия)
defaults() { awk -F: '{ n=$1; d=""; if (index($0,":")>0) d=substr($0,length(n)+2); print n "\t" d "\t" (index($0,":")>0 ? "1" : "0") }'; }
defaults < "$TMP/base_ph.txt" > "$TMP/base_def.tsv"
defaults < "$TMP/head_ph.txt" > "$TMP/head_def.tsv"

# изменившийся дефолт (есть в обоих, строка отличается)
comm -12 "$TMP/base_names.txt" "$TMP/head_names.txt" > "$TMP/common.txt"
: > "$TMP/changed.txt"
while read -r n; do
  [ -z "$n" ] && continue
  b=$(awk -F'\t' -v n="$n" '$1==n {print $3 ":" $2}' "$TMP/base_def.tsv")
  h=$(awk -F'\t' -v n="$n" '$1==n {print $3 ":" $2}' "$TMP/head_def.tsv")
  if [ "$b" != "$h" ]; then
    fmt() { case "$1" in 0:*) echo "без дефолта";; 1:*) echo "дефолт \`${1#1:}\`";; esac; }
    echo "- \`$n\`: $(fmt "$b") → $(fmt "$h")" >> "$TMP/changed.txt"
  fi
done < "$TMP/common.txt"

# переезд env между property-ключами
prop_env_map < "$TMP/base.properties" > "$TMP/base_map.txt" || true
prop_env_map < "$TMP/head.properties" > "$TMP/head_map.txt" || true
while read -r n; do
  [ -z "$n" ] && continue
  bk=$(awk -v n="$n" '$1==n {print $2}' "$TMP/base_map.txt" | paste -sd, -)
  hk=$(awk -v n="$n" '$1==n {print $2}' "$TMP/head_map.txt" | paste -sd, -)
  if [ -n "$bk" ] && [ -n "$hk" ] && [ "$bk" != "$hk" ]; then
    echo "- \`$n\`: читался в \`$bk\`, теперь в \`$hk\`" >> "$TMP/changed.txt"
  fi
done < "$TMP/common.txt"

prop_keys < "$TMP/base.properties" > "$TMP/base_keys.txt" || true
prop_keys < "$TMP/head.properties" > "$TMP/head_keys.txt" || true
comm -13 "$TMP/base_keys.txt" "$TMP/head_keys.txt" > "$TMP/keys_added.txt"
comm -23 "$TMP/base_keys.txt" "$TMP/head_keys.txt" > "$TMP/keys_removed.txt"

awk -F'\t' '$3=="0" {print $1}' "$TMP/head_def.tsv" > "$TMP/required.txt"
awk -F'\t' '$3=="1" {print $1 "=" $2}' "$TMP/head_def.tsv" > "$TMP/defaulted.txt"

list_or_none() { if [ -s "$1" ]; then sed 's/^/- `/; s/$/`/' "$1"; else echo "нет"; fi; }
where_used() {
  # property-ключи, читающие env, и Java-файлы с этим ключом
  local n="$1"
  local keys; keys=$(awk -v n="$n" '$1==n {print $2}' "$TMP/head_map.txt")
  local out=""
  for k in $keys; do
    local files; files=$(grep -rl --include='*.java' -F "\${$k" src/main/java 2>/dev/null | sed 's#src/main/java/##' | paste -sd, - || true)
    out="${out}\`$k\`${files:+ → $files}; "
  done
  echo "${out%; }"
}

REQ_N=$(wc -l < "$TMP/required.txt" | tr -d ' ')
DEF_N=$(wc -l < "$TMP/defaulted.txt" | tr -d ' ')

echo "<!-- env-audit -->"
echo "## 🔧 Env и application.properties (сравнение с \`${BASE_REF#origin/}\`)"
echo
echo "### ➕ Добавили — проставь на прод до мержа"
if [ -s "$TMP/added.txt" ]; then
  echo "| Env | Дефолт | Где читается |"
  echo "| --- | --- | --- |"
  while read -r n; do
    d=$(awk -F'\t' -v n="$n" '$1==n {print ($3=="1" ? "`" $2 "`" : "**нет — обязательна**")}' "$TMP/head_def.tsv")
    echo "| \`$n\` | $d | $(where_used "$n") |"
  done < "$TMP/added.txt"
  echo
  req_added=$(comm -12 "$TMP/added.txt" "$TMP/required.txt" | paste -sd' ' -)
  if [ -n "$req_added" ]; then
    echo "> ⚠️ **Без дефолта:** \`$(echo "$req_added" | sed 's/ /`, `/g')\`. Приложение не стартует, пока их нет в \`.env\` на проде. Добавь до мержа."
  fi
else
  echo "нет"
fi
echo
echo "### ➖ Убрали — можно удалить с прода"
list_or_none "$TMP/removed.txt"
echo
echo "### ✏️ Изменили"
if [ -s "$TMP/changed.txt" ]; then cat "$TMP/changed.txt"; else echo "нет"; fi
echo
echo "### Property-ключи"
echo "Добавлены: $(if [ -s "$TMP/keys_added.txt" ]; then paste -sd, "$TMP/keys_added.txt" | sed 's/,/`, `/g; s/^/`/; s/$/`/'; else echo нет; fi)"
echo
echo "Удалены: $(if [ -s "$TMP/keys_removed.txt" ]; then paste -sd, "$TMP/keys_removed.txt" | sed 's/,/`, `/g; s/^/`/; s/$/`/'; else echo нет; fi)"
echo
echo "<details><summary>Полный список env, которые должен иметь прод после мержа: ${REQ_N} обязательных, ${DEF_N} с дефолтом</summary>"
echo
echo "**Обязательные (без дефолта), ${REQ_N}:**"
echo
echo '```'
cat "$TMP/required.txt"
echo '```'
echo
echo "**С дефолтом, ${DEF_N} (можно не задавать, тогда применится дефолт):**"
echo
echo '```'
cat "$TMP/defaulted.txt"
echo '```'
echo "</details>"

# сигнал для workflow
if [ -s "$TMP/added.txt" ] || [ -s "$TMP/removed.txt" ] || [ -s "$TMP/changed.txt" ]; then
  echo "changed=true" >> "${GITHUB_OUTPUT:-/dev/null}"
else
  echo "changed=false" >> "${GITHUB_OUTPUT:-/dev/null}"
fi
