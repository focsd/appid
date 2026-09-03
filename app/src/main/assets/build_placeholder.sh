#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
TERMUX_APP_HOME="${HOME:-/data/data/com.termux/files/home}"
ROOT="${FOCSD_APPID_ROOT:-$TERMUX_APP_HOME/.com.focsd.appid}"
TEMPLATE_DIR="$ROOT/template"
WORK_ROOT="$ROOT/work"
PRIVATE_OUTPUT="$ROOT/output"
KEYSTORE="$ROOT/placeholder-signing.keystore"
PASSFILE="$ROOT/placeholder-signing.pass"
ALIAS="focsd-appid"

PACKAGE_ID=""
TITLE_B64=""
REASON_B64=""
ACTION_B64=""
TRIGGER_B64=""
COLOR_HEX="F2F2F2"
INSTALL_AFTER=0
PREPARE_ONLY=0
PROGRESS_TOKEN=""

progress() {
    [ -n "$PROGRESS_TOKEN" ] || return 0
    local android_user
    android_user=$(($(id -u) / 100000))
    /system/bin/am broadcast \
        --user "$android_user" \
        -a com.focsd.appid.BUILD_PROGRESS \
        -p com.focsd.appid \
        --es token "$PROGRESS_TOKEN" \
        --es stage "$1" \
        --es detail "${2:-}" </dev/null >/dev/null 2>&1 || true
}

on_exit() {
    local code="$?"
    if [ "$code" -ne 0 ]; then
        progress "Failed" "APK build stopped with exit code $code."
    fi
}
trap on_exit EXIT

usage() {
    cat <<'USAGE'
AppId placeholder builder

Usage:
  build_placeholder.sh --package com.example.app --title-b64 BASE64 --reason-b64 BASE64 --action-b64 BASE64 --trigger-b64 BASE64 --color F2F2F2 [--install 0|1]
  build_placeholder.sh --prepare-only
USAGE
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --package)
            PACKAGE_ID="${2:-}"
            shift 2
            ;;
        --title-b64)
            TITLE_B64="${2:-}"
            shift 2
            ;;
        --reason-b64)
            REASON_B64="${2:-}"
            shift 2
            ;;
        --action-b64)
            ACTION_B64="${2:-}"
            shift 2
            ;;
        --trigger-b64)
            TRIGGER_B64="${2:-}"
            shift 2
            ;;
        --color)
            COLOR_HEX="${2:-}"
            shift 2
            ;;
        --install)
            INSTALL_AFTER="${2:-0}"
            shift 2
            ;;
        --prepare-only)
            PREPARE_ONLY=1
            shift
            ;;
        --progress-token)
            PROGRESS_TOKEN="${2:-}"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            printf 'Unknown argument: %s\n' "$1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

mkdir -p "$ROOT" "$TEMPLATE_DIR" "$WORK_ROOT" "$PRIVATE_OUTPUT"
chmod 700 "$ROOT" || true

find_android_jar() {
    local candidate
    for candidate in \
        "$ROOT/android.jar" \
        "$PREFIX/share/java/android.jar" \
        "$PREFIX/share/aapt/android.jar"
    do
        if [ -f "$candidate" ]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done
    return 1
}

ANDROID_JAR="$(find_android_jar || true)"
if [ -z "$ANDROID_JAR" ]; then
    printf 'ERROR: android.jar was not found. Run AppId Termux setup again.\n' >&2
    exit 10
fi

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        printf 'ERROR: required command not found: %s\n' "$1" >&2
        exit 11
    fi
}

require_command javac
require_command java
require_command jar
require_command d8
require_command aapt
require_command apksigner
require_command zip
require_command keytool

write_template_source() {
    mkdir -p "$TEMPLATE_DIR/src/app/placeholder" "$TEMPLATE_DIR/classes" "$TEMPLATE_DIR/dex"
    cat > "$TEMPLATE_DIR/src/app/placeholder/TemplateActivity.java" <<'JAVA'
package app.placeholder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.UnsupportedEncodingException;

public final class TemplateActivity extends Activity {
    private String placeholderTitle = "Pause";
    private String reason = "I want to focus on what matters.";
    private String trigger = "";
    private String replacementAction = "DO SOMETHING BETTER";
    private String colorHex = "F2F2F2";
    private int foregroundColor;
    private int secondaryColor;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // The manifest intentionally avoids framework style resources because
        // the tiny Termux build links only against android.jar. Hide the
        // platform's default action bar in code so the adaptive header below
        // is the single, consistent top bar on every Android version.
        if (getActionBar() != null) getActionBar().hide();
        loadMetadata();
        render();
    }

    private void loadMetadata() {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(
                    getPackageName(), PackageManager.GET_META_DATA);
            Bundle meta = info.metaData;
            if (meta == null) return;

            String titleB64 = meta.getString("app.placeholder.titleB64", "UGF1c2U=");
            String reasonB64 = meta.getString("app.placeholder.reasonB64", "");
            String actionB64 = meta.getString("app.placeholder.actionB64", "");
            colorHex = meta.getString("app.placeholder.color", "F2F2F2");
            try {
                placeholderTitle = new String(Base64.decode(titleB64, Base64.DEFAULT), "UTF-8");
                reason = decodeOrDefault(reasonB64, reason);
                replacementAction = decodeOrDefault(actionB64, replacementAction);
                trigger = decodeOrDefault(meta.getString("app.placeholder.triggerB64", ""), "");
            } catch (UnsupportedEncodingException ignored) {
                placeholderTitle = "Pause";
            }
        } catch (Exception ignored) {
        }
    }

    private void render() {
        // The selected color belongs to the launcher icon only. The opened
        // placeholder deliberately uses the device's current appearance so
        // an original blue, red, or purple app never tints this screen.
        boolean night = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int background = night ? Color.rgb(28, 30, 34) : Color.rgb(250, 250, 252);
        int headerBackground = night ? Color.rgb(36, 39, 45) : Color.rgb(242, 243, 247);
        foregroundColor = night ? Color.rgb(242, 242, 246) : Color.rgb(45, 45, 50);
        secondaryColor = night ? Color.rgb(190, 194, 204) : Color.rgb(92, 96, 105);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(background);

        View header = new View(this);
        header.setBackgroundColor(headerBackground);
        root.addView(header, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(64), Gravity.TOP));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(dp(30), dp(76), dp(30), dp(48));
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        scrollParams.topMargin = dp(64);
        root.addView(scroll, scrollParams);

        TextView reasonHeading = messageText("You removed " + placeholderTitle + " because:", 22f);
        content.addView(reasonHeading);

        TextView reasonText = messageText("\"" + reason + "\"", 20f);
        reasonText.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
        LinearLayout.LayoutParams reasonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        reasonParams.setMargins(0, dp(24), 0, dp(34));
        content.addView(reasonText, reasonParams);

        if (!trigger.isEmpty()) {
            TextView triggerText = messageText("Usually triggered by: " + trigger, 16f);
            triggerText.setTextColor(secondaryColor);
            LinearLayout.LayoutParams triggerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            triggerParams.setMargins(0, 0, 0, dp(20));
            content.addView(triggerText, triggerParams);
        }

        TextView insteadHeading = messageText("Instead:", 22f);
        content.addView(insteadHeading);

        TextView actionText = messageText(replacementAction.toUpperCase(java.util.Locale.ROOT), 28f);
        actionText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        actionParams.setMargins(0, dp(22), 0, 0);
        content.addView(actionText, actionParams);

        TextView menu = new TextView(this);
        menu.setText("⋮");
        menu.setTextSize(30f);
        menu.setTextColor(secondaryColor);
        menu.setGravity(Gravity.CENTER);
        menu.setClickable(true);
        menu.setFocusable(true);
        menu.setContentDescription("Menu");
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(
                dp(56), dp(56), Gravity.TOP | Gravity.END);
        menuParams.topMargin = dp(8);
        menuParams.rightMargin = dp(8);
        root.addView(menu, menuParams);

        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View anchor) {
                PopupMenu popup = new PopupMenu(TemplateActivity.this, anchor);
                popup.getMenu().add("About");
                popup.getMenu().add("Close");
                popup.setOnMenuItemClickListener(item -> {
                    String label = String.valueOf(item.getTitle());
                    if ("About".equals(label)) {
                        new AlertDialog.Builder(TemplateActivity.this)
                                .setTitle(placeholderTitle)
                                .setMessage("You chose to replace this app with:\n\n" + replacementAction + "\n\nCreated with AppId.")
                                .setPositiveButton("OK", null)
                                .show();
                        return true;
                    }
                    if ("Close".equals(label)) {
                        if (Build.VERSION.SDK_INT >= 21) {
                            finishAndRemoveTask();
                        } else {
                            finish();
                        }
                        return true;
                    }
                    return false;
                });
                popup.show();
            }
        });

        setContentView(root);

        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(darken(background, 0.90f));
            getWindow().setNavigationBarColor(darken(background, 0.90f));
        }
    }

    private String decodeOrDefault(String encoded, String fallback)
            throws UnsupportedEncodingException {
        if (encoded == null || encoded.isEmpty()) return fallback;
        String decoded = new String(Base64.decode(encoded, Base64.DEFAULT), "UTF-8").trim();
        return decoded.isEmpty() ? fallback : decoded;
    }

    private TextView messageText(String value, float size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(foregroundColor);
        text.setGravity(Gravity.CENTER_HORIZONTAL);
        return text;
    }

    private int darken(int color, float factor) {
        int r = Math.max(0, Math.min(255, Math.round(Color.red(color) * factor)));
        int g = Math.max(0, Math.min(255, Math.round(Color.green(color) * factor)));
        int b = Math.max(0, Math.min(255, Math.round(Color.blue(color) * factor)));
        return Color.rgb(r, g, b);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
JAVA
}

write_icon_generator_source() {
    mkdir -p "$TEMPLATE_DIR/src/com/focsd/appid/icon"
    cat > "$TEMPLATE_DIR/src/com/focsd/appid/icon/IconGenerator.java" <<'JAVA'
package com.focsd.appid.icon;

import java.awt.image.BufferedImage;
import java.io.File;
import java.text.Normalizer;
import javax.imageio.ImageIO;

public final class IconGenerator {
    private static final int SIZE = 192;
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789?";
    private static final String[] GLYPHS = {
        "01110/10001/10001/11111/10001/10001/10001",
        "11110/10001/10001/11110/10001/10001/11110",
        "01111/10000/10000/10000/10000/10000/01111",
        "11110/10001/10001/10001/10001/10001/11110",
        "11111/10000/10000/11110/10000/10000/11111",
        "11111/10000/10000/11110/10000/10000/10000",
        "01111/10000/10000/10111/10001/10001/01111",
        "10001/10001/10001/11111/10001/10001/10001",
        "11111/00100/00100/00100/00100/00100/11111",
        "00111/00010/00010/00010/10010/10010/01100",
        "10001/10010/10100/11000/10100/10010/10001",
        "10000/10000/10000/10000/10000/10000/11111",
        "10001/11011/10101/10101/10001/10001/10001",
        "10001/11001/10101/10011/10001/10001/10001",
        "01110/10001/10001/10001/10001/10001/01110",
        "11110/10001/10001/11110/10000/10000/10000",
        "01110/10001/10001/10001/10101/10010/01101",
        "11110/10001/10001/11110/10100/10010/10001",
        "01111/10000/10000/01110/00001/00001/11110",
        "11111/00100/00100/00100/00100/00100/00100",
        "10001/10001/10001/10001/10001/10001/01110",
        "10001/10001/10001/10001/10001/01010/00100",
        "10001/10001/10001/10101/10101/10101/01010",
        "10001/10001/01010/00100/01010/10001/10001",
        "10001/10001/01010/00100/00100/00100/00100",
        "11111/00001/00010/00100/01000/10000/11111",
        "01110/10001/10011/10101/11001/10001/01110",
        "00100/01100/00100/00100/00100/00100/01110",
        "01110/10001/00001/00010/00100/01000/11111",
        "11110/00001/00001/01110/00001/00001/11110",
        "00010/00110/01010/10010/11111/00010/00010",
        "11111/10000/10000/11110/00001/00001/11110",
        "01110/10000/10000/11110/10001/10001/01110",
        "11111/00001/00010/00100/01000/01000/01000",
        "01110/10001/10001/01110/10001/10001/01110",
        "01110/10001/10001/01111/00001/00001/01110",
        "01110/10001/00001/00010/00100/00000/00100"
    };

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !args[1].matches("[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("Expected: TITLE RRGGBB OUTPUT.png");
        }
        char initial = initialFrom(args[0]);
        int background = 0xff000000 | Integer.parseInt(args[1], 16);
        int foreground = relativeLuminance(background) > 0.48
                ? 0xff1d2733 : 0xffffffff;
        int border = mix(background, foreground, 0.28);

        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (insideRoundedRect(x, y, 6, 6, 185, 185, 43)) {
                    image.setRGB(x, y, insideRoundedRect(x, y, 10, 10, 181, 181, 39)
                            ? background : border);
                }
            }
        }
        drawGlyph(image, glyphFor(initial), foreground);

        File output = new File(args[2]);
        File parent = output.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create icon resource directory");
        }
        if (!ImageIO.write(image, "png", output)) {
            throw new IllegalStateException("PNG writer is unavailable");
        }
    }

    private static char initialFrom(String title) {
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFD).toUpperCase();
        for (int index = 0; index < normalized.length(); index++) {
            char candidate = normalized.charAt(index);
            if ((candidate >= 'A' && candidate <= 'Z') ||
                    (candidate >= '0' && candidate <= '9')) {
                return candidate;
            }
        }
        return '?';
    }

    private static String glyphFor(char value) {
        int index = CHARACTERS.indexOf(value);
        return GLYPHS[index < 0 ? GLYPHS.length - 1 : index].replace("/", "");
    }

    private static void drawGlyph(BufferedImage image, String glyph, int color) {
        int cell = 20;
        int inset = 2;
        int startX = (SIZE - 5 * cell) / 2;
        int startY = (SIZE - 7 * cell) / 2;
        for (int row = 0; row < 7; row++) {
            for (int column = 0; column < 5; column++) {
                if (glyph.charAt(row * 5 + column) != '1') continue;
                int left = startX + column * cell + inset;
                int top = startY + row * cell + inset;
                for (int y = top; y < top + cell - inset * 2; y++) {
                    for (int x = left; x < left + cell - inset * 2; x++) {
                        image.setRGB(x, y, color);
                    }
                }
            }
        }
    }

    private static boolean insideRoundedRect(
            int x, int y, int left, int top, int right, int bottom, int radius) {
        if (x < left || x > right || y < top || y > bottom) return false;
        int centerX = Math.max(left + radius, Math.min(x, right - radius));
        int centerY = Math.max(top + radius, Math.min(y, bottom - radius));
        int dx = x - centerX;
        int dy = y - centerY;
        return dx * dx + dy * dy <= radius * radius;
    }

    private static int mix(int first, int second, double amount) {
        int red = (int) Math.round(((first >> 16) & 0xff) * (1 - amount) +
                ((second >> 16) & 0xff) * amount);
        int green = (int) Math.round(((first >> 8) & 0xff) * (1 - amount) +
                ((second >> 8) & 0xff) * amount);
        int blue = (int) Math.round((first & 0xff) * (1 - amount) +
                (second & 0xff) * amount);
        return 0xff000000 | red << 16 | green << 8 | blue;
    }

    private static double relativeLuminance(int color) {
        return (0.2126 * ((color >> 16) & 0xff) + 0.7152 * ((color >> 8) & 0xff) +
                0.0722 * (color & 0xff)) / 255.0;
    }
}
JAVA
}

prepare_icon_generator() {
    local generator_version="2"
    local source="$TEMPLATE_DIR/src/com/focsd/appid/icon/IconGenerator.java"
    local class_file="$TEMPLATE_DIR/icon-generator/com/focsd/appid/icon/IconGenerator.class"
    local version_file="$TEMPLATE_DIR/icon-generator-version"
    if [ -s "$class_file" ] && [ -f "$version_file" ] &&
            [ "$(tr -d '\r\n' < "$version_file")" = "$generator_version" ]; then
        return 0
    fi
    printf 'Compiling launcher icon generator...\n'
    rm -rf "$TEMPLATE_DIR/icon-generator"
    mkdir -p "$TEMPLATE_DIR/icon-generator"
    write_icon_generator_source
    javac -encoding UTF-8 -source 8 -target 8 \
        -d "$TEMPLATE_DIR/icon-generator" "$source"
    if [ ! -s "$class_file" ]; then
        printf 'ERROR: launcher icon generator was not compiled.\n' >&2
        exit 13
    fi
    printf '%s\n' "$generator_version" > "$version_file"
}

prepare_template() {
    local source="$TEMPLATE_DIR/src/app/placeholder/TemplateActivity.java"
    local dex="$TEMPLATE_DIR/dex/classes.dex"
    local version_file="$TEMPLATE_DIR/template-version"
    local template_version="5"

    if [ -s "$dex" ] && [ -f "$source" ] && [ -f "$version_file" ] &&
            [ "$(tr -d '\r\n' < "$version_file")" = "$template_version" ]; then
        return 0
    fi

    printf 'Compiling reusable placeholder Activity...\n'
    rm -rf "$TEMPLATE_DIR/classes" "$TEMPLATE_DIR/dex" "$TEMPLATE_DIR/template.jar"
    mkdir -p "$TEMPLATE_DIR/classes" "$TEMPLATE_DIR/dex"
    write_template_source

    javac \
        -encoding UTF-8 \
        -source 8 \
        -target 8 \
        -classpath "$ANDROID_JAR" \
        -d "$TEMPLATE_DIR/classes" \
        "$source"

    jar cf "$TEMPLATE_DIR/template.jar" -C "$TEMPLATE_DIR/classes" .
    d8 \
        --min-api 23 \
        --lib "$ANDROID_JAR" \
        --output "$TEMPLATE_DIR/dex" \
        "$TEMPLATE_DIR/template.jar"

    if [ ! -s "$dex" ]; then
        printf 'ERROR: D8 did not create classes.dex.\n' >&2
        exit 12
    fi
    printf '%s\n' "$template_version" > "$version_file"
}

ensure_signing_key() {
    if [ ! -f "$PASSFILE" ]; then
        umask 077
        head -c 24 /dev/urandom | base64 | tr -d '\r\n' > "$PASSFILE"
    fi
    chmod 600 "$PASSFILE" || true

    local pass
    pass="$(cat "$PASSFILE")"
    if [ ! -f "$KEYSTORE" ]; then
        printf 'Creating local placeholder signing key...\n'
        keytool -genkeypair \
            -keystore "$KEYSTORE" \
            -storepass "$pass" \
            -keypass "$pass" \
            -alias "$ALIAS" \
            -keyalg RSA \
            -keysize 2048 \
            -validity 36500 \
            -dname "CN=AppId by FOCSD Placeholder,O=Local Placeholder,C=XX" \
            -noprompt >/dev/null 2>&1
        chmod 600 "$KEYSTORE" || true
    fi
}

prepare_template
prepare_icon_generator
ensure_signing_key

if [ "$PREPARE_ONLY" = "1" ]; then
    printf 'Template ready.\n'
    printf 'android.jar: %s\n' "$ANDROID_JAR"
    printf 'Signing key: %s\n' "$KEYSTORE"
    progress "Template ready" "Reusable placeholder code is compiled."
    exit 0
fi

progress "Validating" "Checking the selected app, reason, replacement action, and tools…"
if ! [[ "$PACKAGE_ID" =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$ ]]; then
    printf 'ERROR: invalid package ID: %s\n' "$PACKAGE_ID" >&2
    exit 20
fi
if ! [[ "$COLOR_HEX" =~ ^[0-9A-Fa-f]{6}$ ]]; then
    printf 'ERROR: invalid color. Expected six hex digits, for example F2F2F2.\n' >&2
    exit 21
fi
if [ -z "$TITLE_B64" ]; then
    printf 'ERROR: missing title.\n' >&2
    exit 22
fi
if [ -z "$REASON_B64" ] || ! printf '%s' "$REASON_B64" | base64 -d >/dev/null 2>&1; then
    printf 'ERROR: reason is missing or is not valid base64.\n' >&2
    exit 25
fi
if [ -z "$ACTION_B64" ] || ! printf '%s' "$ACTION_B64" | base64 -d >/dev/null 2>&1; then
    printf 'ERROR: replacement action is missing or is not valid base64.\n' >&2
    exit 26
fi
if ! printf '%s' "$TITLE_B64" | base64 -d >/dev/null 2>&1; then
    printf 'ERROR: title is not valid base64.\n' >&2
    exit 23
fi
if [ "$INSTALL_AFTER" != "0" ] && [ "$INSTALL_AFTER" != "1" ]; then
    printf 'ERROR: --install must be 0 or 1.\n' >&2
    exit 24
fi

TITLE="$(printf '%s' "$TITLE_B64" | base64 -d | tr '\r\n\t' '   ')"
if [ -z "$TITLE" ]; then
    TITLE="Pause"
fi

xml_escape() {
    printf '%s' "$1" | sed \
        -e 's/&/\&amp;/g' \
        -e 's/</\&lt;/g' \
        -e 's/>/\&gt;/g' \
        -e 's/"/\&quot;/g'
}

TITLE_XML="$(xml_escape "$TITLE")"
VERSION_CODE="$(date +%s)"
WORK="$WORK_ROOT/$PACKAGE_ID"
rm -rf "$WORK"
mkdir -p "$WORK"

MANIFEST="$WORK/AndroidManifest.xml"
UNSIGNED="$WORK/unsigned.apk"
ALIGNED="$WORK/aligned.apk"
SIGNED="$WORK/signed.apk"
ICON="$WORK/res/drawable/ic_launcher.png"

printf '[1/6] Generating launcher icon...\n'
progress "Build 1/6" "Generating the launcher icon from the title initial…"
java -Djava.awt.headless=true \
    -cp "$TEMPLATE_DIR/icon-generator" \
    com.focsd.appid.icon.IconGenerator "$TITLE" "$COLOR_HEX" "$ICON"

cat > "$MANIFEST" <<MANIFEST
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="$PACKAGE_ID"
    android:versionCode="$VERSION_CODE"
    android:versionName="1.0-placeholder">

    <uses-sdk android:minSdkVersion="23" android:targetSdkVersion="34" />

    <application
        android:allowBackup="false"
        android:icon="@drawable/ic_launcher"
        android:label="$TITLE_XML">

        <meta-data
            android:name="app.placeholder.creator"
            android:value="com.focsd.appid" />
        <meta-data
            android:name="app.placeholder.titleB64"
            android:value="$TITLE_B64" />
        <meta-data
            android:name="app.placeholder.reasonB64"
            android:value="$REASON_B64" />
        <meta-data
            android:name="app.placeholder.actionB64"
            android:value="$ACTION_B64" />
        <meta-data
            android:name="app.placeholder.triggerB64"
            android:value="$TRIGGER_B64" />
        <meta-data
            android:name="app.placeholder.color"
            android:value="$COLOR_HEX" />

        <activity
            android:name="app.placeholder.TemplateActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
MANIFEST

printf '\n=== Building placeholder ===\n'
printf 'Title:   %s\n' "$TITLE"
printf 'Package: %s\n' "$PACKAGE_ID"
printf 'Color:   #%s\n' "$COLOR_HEX"
printf 'Target:  API 34\n\n'

printf '[2/6] Packaging manifest and icon...\n'
progress "Build 2/6" "Packaging the Android manifest and launcher icon…"
aapt package \
    -f \
    -M "$MANIFEST" \
    -S "$WORK/res" \
    -I "$ANDROID_JAR" \
    -F "$UNSIGNED"

printf '[3/6] Adding classes.dex...\n'
progress "Build 3/6" "Adding the compiled placeholder activity…"
zip -q -j "$UNSIGNED" "$TEMPLATE_DIR/dex/classes.dex"

SIGN_INPUT="$UNSIGNED"
if command -v zipalign >/dev/null 2>&1; then
    printf '[4/6] Aligning APK...\n'
    progress "Build 4/6" "Aligning the APK…"
    zipalign -f 4 "$UNSIGNED" "$ALIGNED"
    SIGN_INPUT="$ALIGNED"
else
    printf '[4/6] zipalign not present; continuing without alignment...\n'
    progress "Build 4/6" "Alignment unavailable; continuing safely…"
fi

printf '[5/6] Signing APK...\n'
progress "Build 5/6" "Signing and verifying the APK…"
PASS="$(cat "$PASSFILE")"
apksigner sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "$ALIAS" \
    --ks-pass "pass:$PASS" \
    --key-pass "pass:$PASS" \
    --out "$SIGNED" \
    "$SIGN_INPUT"

apksigner verify "$SIGNED"

OUTPUT_DIR="$PRIVATE_OUTPUT"
if [ -d "$HOME/storage/downloads" ] && [ -w "$HOME/storage/downloads" ]; then
    OUTPUT_DIR="$TERMUX_APP_HOME/storage/downloads/com.focsd.appid"
    mkdir -p "$OUTPUT_DIR"
fi

OUTPUT_APK="$OUTPUT_DIR/${PACKAGE_ID}-placeholder.apk"
printf '[6/6] Copying finished APK...\n'
progress "Build 6/6" "Copying the finished APK…"
cp -f "$SIGNED" "$OUTPUT_APK"

printf '\nBUILD SUCCESSFUL\n'
printf 'APK: %s\n' "$OUTPUT_APK"
printf 'Signing key: %s\n' "$KEYSTORE"
printf '\nKeep the signing key and password file if you want future builds of the same placeholder package to update each other.\n'

APK_SIZE="$(wc -c < "$OUTPUT_APK")"
if [ "$APK_SIZE" -gt 60000 ]; then
    printf 'ERROR: APK is too large for the AppId library handoff (%s bytes).\n' "$APK_SIZE" >&2
    exit 30
fi
if [ "$INSTALL_AFTER" = "1" ]; then
    printf '\nTransferring APK to AppId for installation...\n'
    progress "Opening installer" "The APK is ready; transferring it to Android…"
else
    printf '\nTransferring APK to the AppId library...\n'
    progress "Saving APK" "Adding the finished APK to the built APK library…"
fi
printf 'FOCSD_APPID_APK_BASE64_BEGIN\n'
base64 < "$OUTPUT_APK" | tr -d '\n'
printf '\nFOCSD_APPID_APK_BASE64_END\n'

progress "APK ready" "$OUTPUT_APK"
