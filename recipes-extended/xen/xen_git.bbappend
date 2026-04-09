# Temporarily pin to a release tag commit (RELEASE-4.21.1) as the branch tip was in flux during development, disrupting patches
SRCREV = "f1a1e629d0cc4729d10e86104ce157732bcabaca"
XEN_REL = "4.21"
LIC_FILES_CHKSUM = "file://COPYING;md5=d1a1e216f80b6d8da95fec897d0dbec9"
SRC_URI = "git://xenbits.xen.org/xen.git;nobranch=1"

require xen-common.inc
require xen-openxt.inc

DEFAULT_PREFERENCE = "1"
