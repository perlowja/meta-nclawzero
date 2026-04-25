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
# OpenMP for multi-threading, no CUDA (Pi 4 has no NVIDIA GPU; Jetson will
# use a separate recipe with CUDA enabled under meta-tegra).

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

# Inherit meta-tegra's cuda class — exports CUDA_TOOLKIT_ROOT, CUDACXX,
# CUDA_NVCC_EXECUTABLE, adds cuda-cudart + cuda-nvcc to DEPENDS via
# DEPENDS:append:cuda, and wires cmake's CMAKE_CUDA_* toolchain into the
# generated toolchain.cmake. The class only affects builds when "cuda" is
# in OVERRIDES (which meta-tegra's machine .conf files set on Jetson).
# On non-Tegra targets this recipe is not pulled into any image, so
# parse-time cost is zero there.
inherit cuda

# Gate the recipe to Tegra machines explicitly. llama-cpp-cuda is overkill
# on ARMv7/ARMv8-no-CUDA targets; those can use llama-cpp-cpu (future split
# if needed). Keeps non-Tegra builds from confusedly parsing this recipe.
COMPATIBLE_MACHINE = "(tegra)"



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

# CPU-only baseline; Jetson overrides below enable CUDA offload (~3–5× tok/s
# on Orin Nano for 7B Q4 GGUF). llama-server is always built — required by
# zeroclaw [provider.local] + the Gemma 4 demo.
EXTRA_OECMAKE:append = " -DLLAMA_BUILD_SERVER=ON"
EXTRA_OECMAKE:append = " -DGGML_CUDA=OFF"

# Target sm_87 (Ampere — Orin Nano/Super/NX). meta-tegra's cuda class
# (inherited above) handles the CUDA_TOOLKIT_ROOT / CUDACXX / nvcc path
# resolution automatically. Thor needs sm_100+ once meta-tegra adds
# Thor machines.
EXTRA_OECMAKE:append:tegra = " -DGGML_CUDA=ON -DCMAKE_CUDA_ARCHITECTURES=87"
CUDA_ARCHITECTURES = "87"

# For Pi 4 / Cortex-A72 / Jetson Orin targets, NEON is guaranteed. Explicit
# compile flags for aarch64 optimisation.
CFLAGS:append = " -mcpu=cortex-a72 -mtune=cortex-a72"
CXXFLAGS:append = " -mcpu=cortex-a72 -mtune=cortex-a72"

do_install:append() {
    # Clean up any host-specific pkgconfig paths
    if [ -d ${D}${libdir}/pkgconfig ]; then
        sed -i "s|${STAGING_DIR_HOST}||g" ${D}${libdir}/pkgconfig/*.pc 2>/dev/null || true
    fi
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
