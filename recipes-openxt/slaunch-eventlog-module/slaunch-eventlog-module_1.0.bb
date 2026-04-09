SUMMARY = "Out-of-tree module to expose Secure Launch event log via debugfs."
DESCRIPTION = "Maps the Secure Launch reserved physical memory range with memremap() \
and exports it as a read-only binary file in debugfs for user-space parsing."
LICENSE = "GPLv2"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0;md5=801f80980d171dd6425610833a22dbe6"

SRC_URI = " \
    file://sources/Kbuild \
    file://sources/Makefile \
    file://sources/slaunch_eventlog.c \
"

S = "${WORKDIR}/sources"

inherit module
inherit module-signing

KERNEL_MODULE_AUTOLOAD += "slaunch_eventlog"
