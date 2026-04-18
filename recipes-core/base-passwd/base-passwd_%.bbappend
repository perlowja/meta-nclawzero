# Workaround for Ubuntu 25.10 where the host `install` binary is the Rust
# uutils coreutils version, which attempts chown() even without -o/-g flags
# and fails outside of a fakeroot/pseudo context.
#
# Two fixes needed:
#
# 1. basepasswd_sysroot_postinst() (upstream in poky/meta) generates a helper
#    `postinst-base-passwd` that extend_recipe_sysroot runs at build time
#    for any recipe depending on base-passwd. That script uses `install`
#    and blows up under Rust coreutils. Override to use mkdir + cp + chmod.
#
# 2. pkg_postinst:${PN} also calls `install` in the default shell fn.
#    Short-circuit it in sysroot context ($D set) out of caution.

basepasswd_sysroot_postinst() {
#!/bin/sh -e
mkdir -p ${STAGING_DIR_TARGET}${sysconfdir}
chmod 755 ${STAGING_DIR_TARGET}${sysconfdir}
for i in passwd group; do
    cp -p ${STAGING_DIR_TARGET}${datadir}/base-passwd/\$i.master ${STAGING_DIR_TARGET}${sysconfdir}/\$i
    chmod 644 ${STAGING_DIR_TARGET}${sysconfdir}/\$i
done

for script in ${STAGING_DIR_TARGET}${bindir}/postinst-useradd-*; do
    if [ -f \$script ]; then
        \$script
    fi
done
}

pkg_postinst:${PN}:prepend() {
    if [ -n "\$D" ]; then
        exit 0
    fi
}
