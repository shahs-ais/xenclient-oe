require grub2.inc
#SRC_URI = "git://github.com/TrenchBoot/grub.git;branch=tb-2.12-284-v4;protocol=https"
#S = "${WORKDIR}/git"
#PV = "2.12+trenchboot"
#SRCREV = "4df3af03c8ffb059c887c13d3fff2635d4e5b442"

SRC_URI:pn-grub-efi = "git://github.com/TrenchBoot/grub.git;branch=tb-2.12-284-v4;protocol=https"
SRCREV:pn-grub-efi = "4df3af03c8ffb059c887c13d3fff2635d4e5b442"
PV:pn-grub-efi = "2.12+trenchboot"
S:pn-grub-efi = "${WORKDIR}/git"

RDEPENDS:${PN}-common += "${PN}-editenv"
RDEPENDS:${PN} += "${PN}-common"
RDEPENDS:${PN}:class-native = ""

RPROVIDES:${PN}-editenv += "${PN}-efi-editenv"

PROVIDES:append:class-native = " grub-efi-native"

PACKAGES =+ "${PN}-editenv ${PN}-common"
FILES:${PN}-editenv = "${bindir}/grub-editenv"
FILES:${PN}-common = " \
    ${bindir} \
    ${sysconfdir} \
    ${sbindir} \
    ${datadir}/grub \
"
ALLOW_EMPTY:${PN} = "1"

do_install:append () {
    # Avoid conflicts with the EFI package for systems such as arm64 where we
    # need to build grub and grub-efi but only EFI is supported by removing EFI
    # from this package.
    rm -rf ${D}${libdir}/grub/*-efi/
    rmdir --ignore-fail-on-non-empty ${D}${libdir}/grub ${D}${libdir}

    install -d ${D}${sysconfdir}/grub.d
    # Remove build host references...
    find "${D}" -name modinfo.sh -type f -exec \
        sed -i \
        -e 's,--sysroot=${STAGING_DIR_TARGET},,g' \
        -e 's|${DEBUG_PREFIX_MAP}||g' \
        -e 's:${RECIPE_SYSROOT_NATIVE}::g' \
        {} +
}

INSANE_SKIP:${PN} = "arch"
INSANE_SKIP:${PN}-dbg = "arch"

BBCLASSEXTEND = "native nativesdk"
