#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# Dispensa Release Automation Script (F-Droid & Play)
#
# Usage:
#   ./scripts/release.sh --dry-run [new_version]
#   ./scripts/release.sh --dry-run --revert-dry-run [new_version]
#   ./scripts/release.sh [new_version]
#
# Options:
#   --dry-run             Simulate the full release without committing, tagging, or pushing
#   --revert-dry-run      Automatically revert modified/created files at the end of a dry-run
#   --flavor <f>          Flavor to verify build for: fdroid (default), play, or both
#   --skip-screenshots    Skip running FastlaneScreenshotsTest
#   --skip-build          Skip running assembleRelease verification
#   --fdroiddata-dir <d>  Path to local fdroiddata repo (default: /Users/alicevangelista/AndroidStudioProjects/fdroiddata)
#   --yes                 Skip interactive confirmation prompts in real mode
# ==============================================================================

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FDROIDDATA_DIR="/Users/alicevangelista/AndroidStudioProjects/fdroiddata"
DRY_RUN=false
REVERT_DRY_RUN=false
FLAVOR="fdroid"
SKIP_SCREENSHOTS=false
SKIP_BUILD=false
AUTO_YES=false
NEW_VERSION_NAME=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --revert-dry-run)
            REVERT_DRY_RUN=true
            shift
            ;;
        --flavor)
            FLAVOR="$2"
            shift 2
            ;;
        --skip-screenshots)
            SKIP_SCREENSHOTS=true
            shift
            ;;
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --fdroiddata-dir)
            FDROIDDATA_DIR="$2"
            shift 2
            ;;
        --yes|-y)
            AUTO_YES=true
            shift
            ;;
        --help|-h)
            sed -n '2,21p' "${BASH_SOURCE[0]}"
            exit 0
            ;;
        -*)
            echo "❌ Opzione sconosciuta: $1"
            exit 1
            ;;
        *)
            NEW_VERSION_NAME="$1"
            shift
            ;;
    esac
done

# Configure JAVA_HOME according to GEMINI.md (OpenJDK 23), with fallback to Android Studio JBR
OPENJDK23_HOME="/Users/alicevangelista/Library/Java/JavaVirtualMachines/openjdk-23.0.1/Contents/Home"
AS_JBR_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

if [[ -z "${JAVA_HOME:-}" ]]; then
    if [[ -d "$OPENJDK23_HOME" ]]; then
        export JAVA_HOME="$OPENJDK23_HOME"
    elif [[ -d "$AS_JBR_HOME" ]]; then
        export JAVA_HOME="$AS_JBR_HOME"
    fi
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi

BUILD_GRADLE="$REPO_ROOT/app/build.gradle.kts"
FDROID_YML="$FDROIDDATA_DIR/metadata/eu.frigo.dispensa.yml"

CURRENT_VERSION_CODE=$(sed -n 's/.*versionCode *= *\([0-9][0-9]*\).*/\1/p' "$BUILD_GRADLE" | head -n 1)
CURRENT_VERSION_NAME=$(sed -n 's/.*versionName *= *"\([^"]*\)".*/\1/p' "$BUILD_GRADLE" | head -n 1)

if [[ -z "$CURRENT_VERSION_CODE" || -z "$CURRENT_VERSION_NAME" ]]; then
    echo "❌ Impossibile leggere versionCode o versionName da $BUILD_GRADLE"
    exit 1
fi

NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))

if [[ -z "$NEW_VERSION_NAME" ]]; then
    IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_VERSION_NAME"
    NEW_VERSION_NAME="${MAJOR}.${MINOR}.$((PATCH + 1))"
fi

LAST_TAG=$(git -C "$REPO_ROOT" describe --tags --abbrev=0 2>/dev/null || echo "")

echo "=================================================================="
if [[ "$DRY_RUN" == "true" ]]; then
    echo "🧪 MODALITÀ DRY-RUN (Nessun commit, tag o push verrà eseguito)"
else
    echo "🚀 MODALITÀ RELEASE REALE"
fi
echo "=================================================================="
echo "• JAVA_HOME        : ${JAVA_HOME:-system default}"
echo "• Versione attuale : ${CURRENT_VERSION_NAME} (versionCode ${CURRENT_VERSION_CODE})"
echo "• Nuova versione   : ${NEW_VERSION_NAME} (versionCode ${NEW_VERSION_CODE})"
echo "• Flavour F-Droid  : ${NEW_VERSION_NAME}-fdroid"
echo "• Flavour Build    : ${FLAVOR}"
echo "• Ultimo tag Git   : ${LAST_TAG:-nessuno}"
echo "=================================================================="

# Save backups for safe --revert-dry-run without losing unrelated uncommitted changes
BUILD_GRADLE_BAK=$(mktemp)
cp "$BUILD_GRADLE" "$BUILD_GRADLE_BAK"

cleanup_backups() {
    rm -f "$BUILD_GRADLE_BAK"
}
trap cleanup_backups EXIT

# ------------------------------------------------------------------------------
# 1. Fastlane Changelogs (it-IT & en-US)
# ------------------------------------------------------------------------------
CHANGELOG_IT="$REPO_ROOT/fastlane/metadata/android/it-IT/changelogs/${NEW_VERSION_CODE}.txt"
CHANGELOG_EN="$REPO_ROOT/fastlane/metadata/android/en-US/changelogs/${NEW_VERSION_CODE}.txt"
CREATED_CHANGELOG_IT=false
CREATED_CHANGELOG_EN=false

mkdir -p "$(dirname "$CHANGELOG_IT")" "$(dirname "$CHANGELOG_EN")"

if [[ ! -f "$CHANGELOG_IT" ]]; then
    echo "📝 Changelog IT mancante ($CHANGELOG_IT): genero bozza dai commit..."
    if [[ -n "$LAST_TAG" ]]; then
        git -C "$REPO_ROOT" log "${LAST_TAG}..HEAD" --no-merges --pretty=format:"• %s" | head -c 480 > "$CHANGELOG_IT"
        echo "" >> "$CHANGELOG_IT"
    else
        echo "• Nuove funzionalità e miglioramenti di stabilità" > "$CHANGELOG_IT"
    fi
    CREATED_CHANGELOG_IT=true
fi

if [[ ! -f "$CHANGELOG_EN" ]]; then
    echo "📝 Changelog EN mancante ($CHANGELOG_EN): genero bozza dai commit..."
    if [[ -n "$LAST_TAG" ]]; then
        git -C "$REPO_ROOT" log "${LAST_TAG}..HEAD" --no-merges --pretty=format:"• %s" | head -c 480 > "$CHANGELOG_EN"
        echo "" >> "$CHANGELOG_EN"
    else
        echo "• New features and stability improvements" > "$CHANGELOG_EN"
    fi
    CREATED_CHANGELOG_EN=true
fi

for cl_file in "$CHANGELOG_IT" "$CHANGELOG_EN"; do
    CHAR_COUNT=$(wc -c < "$cl_file" | tr -d ' ')
    if (( CHAR_COUNT > 500 )); then
        echo "❌ Errore: $cl_file supera i 500 byte consentiti da Fastlane (${CHAR_COUNT} byte)."
        exit 1
    fi
done

echo ""
echo "📄 Contenuto Changelog it-IT (${NEW_VERSION_CODE}.txt):"
cat "$CHANGELOG_IT"
echo ""
echo "📄 Contenuto Changelog en-US (${NEW_VERSION_CODE}.txt):"
cat "$CHANGELOG_EN"
echo ""

# ------------------------------------------------------------------------------
# 2. Fastlane UI Screenshots (Robolectric Native JVM)
# ------------------------------------------------------------------------------
if [[ "$SKIP_SCREENSHOTS" == "false" ]]; then
    echo "📸 Generazione screenshot UI per it-IT e en-US (FastlaneScreenshotsTest)..."
    "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :app:testFdroidDebugUnitTest \
        --tests "eu.frigo.dispensa.screenshots.FastlaneScreenshotsTest" \
        -Pfastlane.screenshots=true
else
    echo "⏭️  Generazione screenshot saltata (--skip-screenshots)."
fi

# ------------------------------------------------------------------------------
# 3. Aggiornamento app/build.gradle.kts & Verifica Build per Flavor
# ------------------------------------------------------------------------------
echo "🔧 Aggiornamento versionCode=${NEW_VERSION_CODE} e versionName=\"${NEW_VERSION_NAME}\" in app/build.gradle.kts..."
python3 - "$BUILD_GRADLE" "$NEW_VERSION_CODE" "$NEW_VERSION_NAME" <<'PYEOF'
import re
import sys

path, new_code, new_name = sys.argv[1], sys.argv[2], sys.argv[3]
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

content = re.sub(r'(versionCode\s*=\s*)\d+', rf'\g<1>{new_code}', content, count=1)
content = re.sub(r'(versionName\s*=\s*)"[^"]*"', rf'\g<1>"{new_name}"', content, count=1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
PYEOF

if [[ "$SKIP_BUILD" == "false" ]]; then
    case "$FLAVOR" in
        fdroid)
            echo "🔨 Verifica compilazione :app:assembleFdroidRelease..."
            "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :app:assembleFdroidRelease
            ;;
        play)
            echo "🔨 Verifica compilazione :app:assemblePlayRelease..."
            "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :app:assemblePlayRelease
            ;;
        both)
            echo "🔨 Verifica compilazione :app:assembleFdroidRelease e :app:assemblePlayRelease..."
            "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :app:assembleFdroidRelease :app:assemblePlayRelease
            ;;
        *)
            echo "❌ Flavor non valido: $FLAVOR (usa fdroid, play o both)"
            exit 1
            ;;
    esac
else
    echo "⏭️  Verifica compilazione assembleRelease saltata (--skip-build)."
fi

echo ""
echo "🔍 Diff in dispensa (app/build.gradle.kts):"
git -C "$REPO_ROOT" diff --no-index -- "$BUILD_GRADLE_BAK" "$BUILD_GRADLE" || true

# ------------------------------------------------------------------------------
# 4. Commit, Tag e Push su dispensa (SOLO se NON in --dry-run)
# ------------------------------------------------------------------------------
if [[ "$DRY_RUN" == "true" ]]; then
    COMMIT_SHA=$(git -C "$REPO_ROOT" rev-parse HEAD)
    echo ""
    echo "🧪 [DRY-RUN] Salto commit, tag (${NEW_VERSION_NAME}) e push su dispensa."
    echo "🧪 [DRY-RUN] Uso l'hash HEAD attuale (${COMMIT_SHA}) per simulare l'aggiornamento di fdroiddata."
else
    if [[ "$AUTO_YES" == "false" ]]; then
        read -r -p "❓ Vuoi fare commit, creare il tag '${NEW_VERSION_NAME}' e fare push su origin? [y/N] " response
        if [[ ! "$response" =~ ^[Yy]$ ]]; then
            echo "🛑 Operazione annullata dall'utente."
            exit 1
        fi
    fi

    git -C "$REPO_ROOT" add "$BUILD_GRADLE" "$REPO_ROOT/fastlane/metadata/android"
    git -C "$REPO_ROOT" commit -m "Release ${NEW_VERSION_NAME} (${NEW_VERSION_CODE})"
    git -C "$REPO_ROOT" tag "${NEW_VERSION_NAME}"
    COMMIT_SHA=$(git -C "$REPO_ROOT" rev-parse HEAD)
    git -C "$REPO_ROOT" push origin HEAD
    git -C "$REPO_ROOT" push origin "${NEW_VERSION_NAME}"
    echo "✅ Tag ${NEW_VERSION_NAME} (${COMMIT_SHA}) pushato su origin!"
fi

# ------------------------------------------------------------------------------
# 5. Aggiornamento metadata/eu.frigo.dispensa.yml su fdroiddata
# ------------------------------------------------------------------------------
FDROID_MODIFIED_IN_PLACE=false
if [[ ! -f "$FDROID_YML" ]]; then
    echo "⚠️  File $FDROID_YML non trovato. Salto lo step fdroiddata."
else
    echo ""
    echo "📦 Aggiornamento $FDROID_YML..."
    TARGET_YML="$FDROID_YML"
    TEMP_YML=""
    if [[ "$DRY_RUN" == "true" && "$REVERT_DRY_RUN" == "true" ]] || [[ ! -w "$FDROID_YML" ]]; then
        TEMP_YML=$(mktemp)
        cp "$FDROID_YML" "$TEMP_YML"
        TARGET_YML="$TEMP_YML"
    else
        FDROID_MODIFIED_IN_PLACE=true
    fi

    python3 - "$TARGET_YML" "${NEW_VERSION_NAME}-fdroid" "$NEW_VERSION_CODE" "$COMMIT_SHA" <<'PYEOF'
import re
import sys

yml_path, ver_name, ver_code, commit_sha = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]

with open(yml_path, "r", encoding="utf-8") as f:
    content = f.read()

new_build_block = f"""  - versionName: {ver_name}
    versionCode: {ver_code}
    commit: {commit_sha}
    subdir: app
    gradle:
      - fdroid
    prebuild:
      - rm -rf ../sync-gdrive
      - sed -i 's/":sync-gdrive"//g' ../settings.gradle.kts
      - sed -i 's/":sync-gdrive",//g' ../settings.gradle.kts
      - sed -i '/"playImplementation"(project(":sync-gdrive"))/d' build.gradle.kts
      - sed -i '/"playImplementation"(libs.play.services.auth)/d' build.gradle.kts

"""

if f"versionName: {ver_name}" not in content:
    if "\nAutoUpdateMode:" in content:
        content = content.replace("\nAutoUpdateMode:", "\n" + new_build_block + "AutoUpdateMode:", 1)
    else:
        content = content.rstrip() + "\n\n" + new_build_block

content = re.sub(r'^CurrentVersion:.*$', f'CurrentVersion: {ver_name}', content, flags=re.MULTILINE)
content = re.sub(r'^CurrentVersionCode:.*$', f'CurrentVersionCode: {ver_code}', content, flags=re.MULTILINE)

with open(yml_path, "w", encoding="utf-8") as f:
    f.write(content)
PYEOF

    echo "🔍 Diff in fdroiddata (metadata/eu.frigo.dispensa.yml):"
    if [[ -n "$TEMP_YML" ]]; then
        git -C "$REPO_ROOT" diff --no-index -- "$FDROID_YML" "$TEMP_YML" || true
        rm -f "$TEMP_YML"
    else
        git -C "$FDROIDDATA_DIR" diff metadata/eu.frigo.dispensa.yml
    fi

    if [[ "$DRY_RUN" == "true" ]]; then
        echo ""
        echo "🧪 [DRY-RUN] Salto creazione branch, commit e push su fdroiddata."
    else
        BRANCH_NAME="dispensa-${NEW_VERSION_NAME}"
        if [[ "$AUTO_YES" == "false" ]]; then
            read -r -p "❓ Vuoi creare il branch '${BRANCH_NAME}', committare e fare push su fdroiddata? [y/N] " response
            if [[ ! "$response" =~ ^[Yy]$ ]]; then
                echo "🛑 Push su fdroiddata saltato. Le modifiche sono rimaste in $FDROID_YML."
                exit 0
            fi
        fi

        git -C "$FDROIDDATA_DIR" checkout -B "$BRANCH_NAME"
        git -C "$FDROIDDATA_DIR" add metadata/eu.frigo.dispensa.yml
        git -C "$FDROIDDATA_DIR" commit -m "Update eu.frigo.dispensa to ${NEW_VERSION_NAME}-fdroid (${NEW_VERSION_CODE})"
        git -C "$FDROIDDATA_DIR" push -u origin "$BRANCH_NAME"
        echo "✅ Branch ${BRANCH_NAME} pushato su fdroiddata!"
    fi
fi

# ------------------------------------------------------------------------------
# 6. Ripristino opzionale a fine Dry-Run (--revert-dry-run)
# ------------------------------------------------------------------------------
if [[ "$DRY_RUN" == "true" && "$REVERT_DRY_RUN" == "true" ]]; then
    echo ""
    echo "🧹 [--revert-dry-run] Ripristino dei file modificati durante il dry-run..."
    cp "$BUILD_GRADLE_BAK" "$BUILD_GRADLE"
    if [[ "$CREATED_CHANGELOG_IT" == "true" ]]; then rm -f "$CHANGELOG_IT"; fi
    if [[ "$CREATED_CHANGELOG_EN" == "true" ]]; then rm -f "$CHANGELOG_EN"; fi
    if [[ "$FDROID_MODIFIED_IN_PLACE" == "true" && -f "$FDROID_YML" ]]; then
        git -C "$FDROIDDATA_DIR" checkout -- metadata/eu.frigo.dispensa.yml
    fi
    echo "✅ Tutti i file sono stati ripristinati allo stato iniziale."
elif [[ "$DRY_RUN" == "true" ]]; then
    echo ""
    echo "ℹ️  I file generati/modificati sono stati lasciati nella working copy per ispezione."
    echo "   Per annullarli esegui:"
    echo "   git checkout -- app/build.gradle.kts && git -C \"$FDROIDDATA_DIR\" checkout -- metadata/eu.frigo.dispensa.yml"
fi
