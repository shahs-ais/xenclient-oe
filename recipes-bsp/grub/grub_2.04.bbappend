# Disable OpenEmbedded Core GRUB 2.04 in favor of our GRUB 2.12
# This prevents OE Core from defaulting to GRUB 2.04

# Make this recipe incompatible with all hosts to disable it
COMPATIBLE_HOST = "null"
