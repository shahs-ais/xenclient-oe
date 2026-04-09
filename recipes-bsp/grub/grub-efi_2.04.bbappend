# Disable OpenEmbedded Core GRUB-EFI 2.04 in favor of our GRUB-EFI 2.12
# This prevents OE Core from defaulting to GRUB-EFI 2.04

# Make this recipe incompatible with all hosts to disable it
COMPATIBLE_HOST = "null"
