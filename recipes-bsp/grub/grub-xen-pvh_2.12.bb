require grub-xen.inc
GRUBPLATFORM = "xen_pvh"
GRUB_TARGET = "i386"
GRUBEXT = "xen-pvh"

# Override checksums for GRUB 2.12
SRC_URI[sha256sum] = "b30919fa5be280417c17ac561bb1650f60cfb80cc6237fa1e2b6f56154cb9c91"
SRC_URI[md5sum] = "364f5770d1759de8161cac64e9fe14e7"
