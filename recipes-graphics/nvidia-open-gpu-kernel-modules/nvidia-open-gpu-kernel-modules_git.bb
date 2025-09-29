SUMMARY = "NVIDIA Linux Open GPU kernel modules"
LICENSE = "GPL-2.0-only & MIT"
LIC_FILES_CHKSUM = "file://COPYING;md5=1d5fa2a493e937d5a4b96e5e03b90f7c"

PV = "580.82.09"
SRC_URI = "https://github.com/NVIDIA/open-gpu-kernel-modules/archive/refs/tags/${PV}.tar.gz;downloadfilename=open-gpu-kernel-modules-${PV}.tar.gz"
SRC_URI[sha256sum] = "84aecb8d7a27819a8d9997cf129d248e6d147b4e4ef6f66acd07bcd9114f831c"

S = "${WORKDIR}/open-gpu-kernel-modules-${PV}"

inherit module
inherit module-signing

DEPENDS += "virtual/kernel coreutils-native"

EXTRA_OEMAKE = "\
    SYSSRC=${STAGING_KERNEL_DIR} \
    SYSOUT=${STAGING_KERNEL_BUILDDIR} \
    ARCH=${TARGET_ARCH} \
    CROSS_COMPILE=${TARGET_PREFIX} \
"

do_compile() {
    # Unset linker flags that break ld (NVIDIA build uses both cc and ld during this step)
    unset LDFLAGS
    unset LD

    oe_runmake -C ${S} modules
}

do_install() {
    oe_runmake -C ${S} \
        modules_install \
        INSTALL_MOD_PATH=${D} \
        DEPMOD=echo

    install -d ${D}${sysconfdir}/modprobe.d
    echo "options nvidia-drm modeset=1" > ${D}${sysconfdir}/modprobe.d/nvidia-kms.conf
}

FILES:${PN} += " ${sysconfdir}/modprobe.d/nvidia-kms.conf "

RDEPENDS_${PN} += " \
  kernel-module-nvidia \
  kernel-module-nvidia-modeset \
  kernel-module-nvidia-drm \
  kernel-module-nvidia-uvm \
"

EXTRA_OEMAKE += "INSTALL_HDR_PATH=${D}${prefix}"
MODULES_INSTALL_TARGET += "headers_install"

KERNEL_MODULE_AUTOLOAD += "nvidia nvidia-modeset nvidia-drm nvidia"
