#!/usr/bin/env bash
set -euo pipefail

echo "========================================"
echo "  Version Check Report"
echo "========================================"
echo ""

# --- Gradle ---
echo "--- Gradle ---"
current_gradle=$(sed -n 's|.*gradle-\([0-9][0-9.]*\)-.*|\1|p' gradle/wrapper/gradle-wrapper.properties)
echo "  Current: $current_gradle"
latest_gradle=$(curl -s https://services.gradle.org/versions/current | sed -n 's|.*"version" *: *"\([0-9][0-9.]*\)".*|\1|p')
if [ -n "$latest_gradle" ]; then
  if [ "$current_gradle" = "$latest_gradle" ]; then
    echo "  Latest:  $latest_gradle (up to date)"
  else
    echo "  Latest:  $latest_gradle (upgrade available)"
  fi
else
  echo "  Latest:  (could not fetch)"
fi
echo ""

# --- Java (Zulu via sdkman) ---
echo "--- Java (Zulu via sdkman) ---"
if command -v java &>/dev/null; then
  current_java=$(java -version 2>&1 | head -1 | sed -n 's|.*"\(.*\)".*|\1|p')
  echo "  Current: $current_java"
else
  echo "  Current: (java not found)"
fi
if [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
  set +u
  source "$HOME/.sdkman/bin/sdkman-init.sh"
  echo "  Available Zulu builds:"
  zulu_lines=$(sdk list java 2>/dev/null | grep -i "zulu" | grep -v "\.fx" | head -10 || true)
  set -u
  if [ -n "$zulu_lines" ]; then
    echo "$zulu_lines" | while IFS= read -r line; do
      echo "    $line"
    done
  else
    echo "    (no Zulu builds found)"
  fi
else
  echo "  sdkman not installed, skipping"
fi
echo ""

# --- Dependencies ---
echo "--- Dependencies ---"
echo "  Running ./gradlew dependencyUpdates ..."
echo ""
./gradlew dependencyUpdates -q 2>&1 | tail -n +2
