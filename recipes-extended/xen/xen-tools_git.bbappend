# RELEASE-4.21.1
SRCREV = "f1a1e629d0cc4729d10e86104ce157732bcabaca"
XEN_REL = "4.21"
LIC_FILES_CHKSUM = "file://COPYING;md5=d1a1e216f80b6d8da95fec897d0dbec9"
SRC_URI = "git://xenbits.xen.org/xen.git;nobranch=1"

require xen-common.inc
require xen-tools-openxt.inc

# Workaround for setuptools3 overriding autotools-brokensep
B = "${S}"

DEFAULT_PREFERENCE = "1"

PACKAGES += "vchan-socket-proxy"
FILES_vchan-socket-proxy = " \
    ${bindir}/vchan-socket-proxy \
"
RDEPENDS_${PN}-libxenlight += "vchan-socket-proxy"
