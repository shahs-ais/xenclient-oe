# Unified GRUB configuration with TrenchBoot integration
# The main grub.cfg now includes TrenchBoot functionality directly
# No need for separate files - everything is unified

# Depend on TrenchBoot instead of tboot
RDEPENDS_${PN} += "grub-efi"

# Remove tboot dependency by overriding RDEPENDS
RDEPENDS_${PN} = "bash grub-efi"
