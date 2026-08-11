ARG FDROID_BASE_IMAGE=registry.gitlab.com/fdroid/fdroidserver@sha256:9bae53bb4ddbf8fa5bb7385bf2e62e7c6318f99ab0d25b2a551ad38abb528068
ARG FDROID_BUILD_PLATFORM=linux/amd64
FROM --platform=${FDROID_BUILD_PLATFORM} ${FDROID_BASE_IMAGE}

USER root
ENV DEBIAN_FRONTEND=noninteractive
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk

# The pinned upstream buildserver layer contains zero-length files from several
# Debian packages. Reinstall them before invoking Python-based fdroidserver and
# replace its unmanaged curl binary with Debian's matching OpenSSL build.
RUN apt-get update \
    && apt-get install --reinstall -y --no-install-recommends libexpat1 libpython3.13-stdlib \
    && apt-get install -y --no-install-recommends curl fdroidserver git gradle rsync shellcheck unzip \
    && apt-get install --reinstall -y --no-install-recommends libcurl4t64 \
    && rm -rf /var/lib/apt/lists/*

RUN test "$(fdroid --version)" = '2.4.2' \
    && curl --version >/dev/null \
    && shellcheck --version >/dev/null

RUN yes | sdkmanager --licenses --sdk_root="$ANDROID_SDK_ROOT" >/dev/null 2>&1 || true; \
    sdkmanager --install --sdk_root="$ANDROID_SDK_ROOT" \
        'platforms;android-35' 'build-tools;35.0.0'; \
    test -s "$ANDROID_SDK_ROOT/platforms/android-35/android.jar"; \
    test -x "$ANDROID_SDK_ROOT/build-tools/35.0.0/aapt2"

WORKDIR /build
