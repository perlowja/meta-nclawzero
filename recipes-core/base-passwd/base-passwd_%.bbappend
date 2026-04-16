# Workaround for Ubuntu 25.10 AppArmor blocking pseudo's LD_PRELOAD
# in extend_recipe_sysroot. The postinst tries to chown /etc to root:root
# which fails without real fakeroot. Skip the postinst in sysroot context.
pkg_postinst:${PN}:prepend() {
    if [ -n "$D" ]; then
        exit 0
    fi
}
