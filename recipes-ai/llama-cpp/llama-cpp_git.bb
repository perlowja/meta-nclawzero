# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: MIT
#
# llama.cpp — CPU-first LLM inference engine. Used on nclawzero edge
# devices for optional local inference (Nemotron Nano, Gemma 4 variants,
# small Llama models). Cloud inference via ZeroClaw gateway remains the
# default; llama-cli / llama-server are available for tier-1 consultative
# or fully offline use cases.
#
# Build is Release, shared libs, native CPU (NEON auto-enabled on aarch64),
# OpenMP for multi-threading, no CUDA.

SUMMARY = "llama.cpp — LLM inference in C/C++ (CPU, NEON, OpenMP)"
DESCRIPTION = "CPU-first LLM inference engine supporting GGUF-format models. \
    Installs llama-cli, llama-server, and shared libraries for use by \
    skills and by ZeroClaw's [provider.local] routing."
HOMEPAGE = "https://github.com/ggml-org/llama.cpp"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=223b26b3c1143120c87e2b13111d3e99"

# Track upstream master HEAD (alpha/pre-alpha posture — see STATUS.md).
# llama.cpp master moves fast and occasionally breaks; if a build fails
# here, pin back to the last known-good SHA while upstream sorts it,
# then return to AUTOREV.
SRCREV = "${AUTOREV}"
# Previous pin (kept for quick revert): 9e5647affa54ea724196db15ec9b76c4abd16d4a (tag b8840, 2026-04-18)
PV = "0+git${SRCPV}"

SRC_URI = "git://github.com/ggml-org/llama.cpp.git;branch=master;protocol=https"

S = "${WORKDIR}/git"

inherit cmake

COMPATIBLE_MACHINE = "(raspberrypi4-64|raspberrypi5|raspberrypi3-64|raspberrypi0-2w-64)"



EXTRA_OECMAKE = " \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=ON \
    -DGGML_NATIVE=OFF \
    -DGGML_OPENMP=ON \
    -DGGML_METAL=OFF \
    -DGGML_VULKAN=OFF \
    -DLLAMA_CURL=OFF \
    -DLLAMA_BUILD_EXAMPLES=OFF \
    -DLLAMA_BUILD_TESTS=OFF \
    -DLLAMA_SERVER_SSL=OFF \
    -DCMAKE_DISABLE_FIND_PACKAGE_OpenSSL=ON \
    -DCMAKE_DISABLE_FIND_PACKAGE_CURL=ON \
"

# CPU-only baseline. llama-server is always built for zeroclaw
# [provider.local] + the Gemma 4 demo.
EXTRA_OECMAKE:append = " -DLLAMA_BUILD_SERVER=ON"
EXTRA_OECMAKE:append = " -DGGML_CUDA=OFF"

# For Pi 4 / Cortex-A72 targets, NEON is guaranteed. Explicit compile
# flags for aarch64 optimisation.
CFLAGS:append = " -mcpu=cortex-a72 -mtune=cortex-a72"
CXXFLAGS:append = " -mcpu=cortex-a72 -mtune=cortex-a72"

do_install:append() {
    # Clean up any host-specific pkgconfig paths
    if [ -d ${D}${libdir}/pkgconfig ]; then
        sed -i "s|${STAGING_DIR_HOST}||g" ${D}${libdir}/pkgconfig/*.pc 2>/dev/null || true
    fi
    # llama.cpp installs convert_hf_to_gguf.py — Python conversion utility
    # not needed in image (we ship pre-converted GGUF). Drop to avoid the
    # installed-vs-shipped QA error.
    rm -f ${D}${bindir}/convert_hf_to_gguf.py
}

FILES:${PN} = " \
    ${bindir}/llama-* \
    ${libdir}/libllama*.so* \
    ${libdir}/libggml*.so* \
    ${libdir}/libmtmd*.so* \
"

# The internal shared libs (libggml-base.so.0, libllama-common.so.0,
# libmtmd.so.0) don't have a versioned SONAME that bitbake recognises
# as a standalone provider. Tell QA to stop complaining — they're
# intentionally bundled inside the llama-cpp package.

SOLIBS = ".so*"
FILES_SOLIBSDEV = ""

FILES:${PN}-dev = " \
    ${includedir} \
    ${libdir}/cmake \
    ${libdir}/pkgconfig \
    ${libdir}/*.a \
"

# llama.cpp ships some binaries un-stripped for debugging; let the Yocto
# strip step handle it.
INSANE_SKIP:${PN} = "dev-so file-rdeps"
