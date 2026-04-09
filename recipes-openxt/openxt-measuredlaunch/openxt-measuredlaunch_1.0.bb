DESCRIPTION = "scripts to aid in the configuration and maintenance of measured launch"
LICENSE = "GPLv2"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0;md5=801f80980d171dd6425610833a22dbe6"

SRC_URI = " \
    file://ml-functions \
    file://seal-system \
    file://recovery-method \
    file://dump-slaunch-eventlog \
    file://tpm2-evt-log-parser.awk \
    file://tpm-evt-log-utils.awk \
    file://openxt-tpm2-evt-log-parser.in \
"

FILES_${PN} = "\
    ${libdir}/openxt/ml-functions \
    ${libdir}/openxt/tpm2-evt-log-parser.awk \
    ${libdir}/openxt/tpm-evt-log-utils.awk \
    ${sbindir}/seal-system \
    ${sbindir}/recovery-method \
    ${sbindir}/dump-slaunch-eventlog \
    ${bindir}/openxt-tpm2-evt-log-parser \
    "

do_install() {
    install -d ${D}${libdir}/openxt
    install -d ${D}${sbindir}
    install -m 0755 ${WORKDIR}/ml-functions ${D}${libdir}/openxt
    install -m 0644 ${WORKDIR}/tpm2-evt-log-parser.awk ${D}${libdir}/openxt/
    install -m 0644 ${WORKDIR}/tpm-evt-log-utils.awk ${D}${libdir}/openxt/
    install -m 0755 ${WORKDIR}/seal-system ${D}${sbindir}
    install -m 0755 ${WORKDIR}/recovery-method ${D}${sbindir}
    install -m 0755 ${WORKDIR}/dump-slaunch-eventlog ${D}${sbindir}
    install -d ${D}${bindir}
    sed -e "s|@LIBDIR@|${libdir}|g" -e "s|@BINDIR@|${bindir}|g" \
        ${WORKDIR}/openxt-tpm2-evt-log-parser.in > ${D}${bindir}/openxt-tpm2-evt-log-parser
    chmod 0755 ${D}${bindir}/openxt-tpm2-evt-log-parser
    install -d ${D}${sysconfdir}/openxt
}

RDEPENDS_${PN} = " \
    bash \
    openxt-keymanagement \
    tpm2-tools \
    gawk \
    coreutils \
"
# Provide xxd if available for PCR expected value logs in event parser
RRECOMMENDS_${PN} += "vim"