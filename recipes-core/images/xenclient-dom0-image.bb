# XenClient dom0 image.

LICENSE = "GPLv2 & MIT"
LIC_FILES_CHKSUM = " \
    file://${COMMON_LICENSE_DIR}/GPL-2.0;md5=801f80980d171dd6425610833a22dbe6 \
    file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302 \
"

inherit openxt-selinux-image
inherit openxt-vm-common

INITRD_VM = "xenclient-initramfs-image"
INSTALL_VM_INITRD = "1"

IMAGE_FEATURES += " \
    package-management \
    read-only-rootfs \
    root-bash-shell \
    wildcard-sshd-argo \
    allow-root-login \
"
IMAGE_FSTYPES = "ext3.gz"
export IMAGE_BASENAME = "xenclient-dom0-image"

COMPATIBLE_MACHINE = "(xenclient-dom0)"


# xserver-xorg should not live in dom0, but UIVM.
BAD_RECOMMENDATIONS += " \
    xserver-xorg \
    avahi-daemon \
    avahi-autoipd \
    ${@bb.utils.contains('IMAGE_FEATURES', 'web-certificates', '', 'ca-certificates', d)} \
    libivc \
"

IMAGE_INSTALL += "\
    initscripts \
    packagegroup-core-boot \
    packagegroup-base \
    packagegroup-xenclient-common \
    packagegroup-xenclient-dom0 \
    packagegroup-openxt-test \
    argo-module \
    slaunch-eventlog-module \
    xenclient-preload-hs-libs \
    linux-firmware-i915 \
    devicemodel-stubdom \
    ${@bb.utils.contains('IMAGE_FEATURES', 'debug-tweaks', 'packagegroup-selinux-policycoreutils audit', '' ,d)} \
"

inherit xenclient-licences

require xenclient-version.inc

# Workspace boot directory - can be overridden in local.conf
# Default: ${TOPDIR}/../boot/ (assumes build directory is in workspace subdirectory)
WORKSPACE_BOOT_DIR ?= "${TOPDIR}/../boot"

post_rootfs_shell_commands() {
    mkdir -p ${IMAGE_ROOTFS}/config/etc
    mv ${IMAGE_ROOTFS}/etc/passwd ${IMAGE_ROOTFS}/config/etc
    mv ${IMAGE_ROOTFS}/etc/shadow ${IMAGE_ROOTFS}/config/etc
    ln -s ../config/etc/passwd ${IMAGE_ROOTFS}/etc/passwd
    ln -s ../config/etc/shadow ${IMAGE_ROOTFS}/etc/shadow
    ln -s ../config/etc/.pwd.lock ${IMAGE_ROOTFS}/etc/.pwd.lock

    rm ${IMAGE_ROOTFS}/etc/hosts
    ln -s /var/run/hosts ${IMAGE_ROOTFS}/etc/hosts
    ln -s /var/volatile/etc/resolv.conf ${IMAGE_ROOTFS}/etc/resolv.conf

    echo 'kernel.printk_ratelimit = 0' >> ${IMAGE_ROOTFS}/etc/sysctl.conf

    # Create mountpoint for /mnt/secure
    mkdir -p ${IMAGE_ROOTFS}/mnt/secure

    # Create mountpoint for /mnt/upgrade
    mkdir -p ${IMAGE_ROOTFS}/mnt/upgrade

    # Create mountpoint for boot/system
    mkdir -p ${IMAGE_ROOTFS}/boot/system

    # Create XL-related files and directories
    mkdir -p ${IMAGE_ROOTFS}/var/lib/xen
    mkdir -p ${IMAGE_ROOTFS}/etc/xen
    touch ${IMAGE_ROOTFS}/etc/xen/xl.conf

    # Write coredumps in /var/cores
    echo 'kernel.core_pattern = /var/cores/%e-%t.%p.core' >> ${IMAGE_ROOTFS}/etc/sysctl.conf

    # Copy kernel files from workspace boot directory
    # WORKSPACE_BOOT_DIR can be set in local.conf to point to custom location
    if [ -d "${WORKSPACE_BOOT_DIR}" ]; then
        bbnote "Copying kernel files from workspace boot directory: ${WORKSPACE_BOOT_DIR}"
        copied_count=0
        # Copy all files from workspace boot directory to image boot directory
        for file in "${WORKSPACE_BOOT_DIR}"/*; do
            if [ -f "$file" ]; then
                filename=$(basename "$file")
                # Skip if file already exists (don't overwrite existing files)
                if [ ! -f "${IMAGE_ROOTFS}/boot/$filename" ]; then
                    bbnote "  Copying $filename to /boot/"
                    install -m 0644 "$file" "${IMAGE_ROOTFS}/boot/$filename"
                    copied_count=$(expr $copied_count + 1)
                else
                    bbnote "  Skipping $filename (already exists in /boot/)"
                fi
            fi
        done
        if [ "$copied_count" -gt 0 ]; then
            bbnote "Copied $copied_count file(s) from workspace boot directory"
        else
            bbnote "No new files copied from workspace boot directory"
        fi
    else
        bbnote "Workspace boot directory not found at ${WORKSPACE_BOOT_DIR}, skipping kernel file copy"
        bbnote "  (Set WORKSPACE_BOOT_DIR in local.conf to enable this feature)"
    fi

    # Install installer files (rootfs.gz and vmlinuz) for testing installer boot process
    # These files are used by grub-efi-installer.cfg for testing the installer boot process
    # rootfs.gz is the installer image cpio.gz (built for openxt-installer machine)
    # Try multiple possible locations for installer image
    INSTALLER_ROOTFS=""
    for possible_path in \
        "${DEPLOY_DIR_IMAGE}/xenclient-installer-image-openxt-installer.cpio.gz" \
        "${DEPLOY_DIR_IMAGE}/../openxt-installer/xenclient-installer-image-openxt-installer.cpio.gz" \
        "${TOPDIR}/tmp-glibc/deploy/images/openxt-installer/xenclient-installer-image-openxt-installer.cpio.gz"; do
        if [ -f "$possible_path" ]; then
            INSTALLER_ROOTFS="$possible_path"
            break
        fi
    done
    
    if [ -n "$INSTALLER_ROOTFS" ] && [ -f "$INSTALLER_ROOTFS" ]; then
        bbnote "Installing installer rootfs.gz to /boot/ from $INSTALLER_ROOTFS"
        install -m 0644 "$INSTALLER_ROOTFS" "${IMAGE_ROOTFS}/boot/rootfs.gz"
    else
        bbnote "Installer rootfs.gz not found, skipping (installer image must be built separately for openxt-installer machine)"
    fi

    # vmlinuz is typically the kernel image - try to find it or create from current kernel
    # Check for vmlinuz in deploy directory (may be created by installer build)
    if [ -f "${DEPLOY_DIR_IMAGE}/vmlinuz" ]; then
        bbnote "Installing vmlinuz to /boot/ from deploy directory"
        install -m 0644 "${DEPLOY_DIR_IMAGE}/vmlinuz" "${IMAGE_ROOTFS}/boot/vmlinuz"
    elif [ -f "${DEPLOY_DIR_IMAGE}/${KERNEL_IMAGETYPE}" ]; then
        # If vmlinuz doesn't exist, copy from kernel image (for testing)
        bbnote "Creating vmlinuz from ${KERNEL_IMAGETYPE} for installer boot testing"
        install -m 0644 "${DEPLOY_DIR_IMAGE}/${KERNEL_IMAGETYPE}" "${IMAGE_ROOTFS}/boot/vmlinuz"
    else
        bbnote "vmlinuz not found, skipping (kernel image: ${KERNEL_IMAGETYPE})"
    fi
}
ROOTFS_POSTPROCESS_COMMAND += "post_rootfs_shell_commands; "

# Get rid of unneeded initscripts
remove_initscripts() {
    remove_initscript "rmnologin.sh"
    remove_initscript "finish.sh"
}
ROOTFS_POSTPROCESS_COMMAND += "remove_initscripts; "

# After ensuring that the correct number of xenstored daemon(s) are installed,
# enforce that the init script is active:
activate_xenstored_initscript() {
    update-rc.d -r ${IMAGE_ROOTFS} xenstored defaults 05
}
ROOTFS_POSTPROCESS_COMMAND += "activate_xenstored_initscript; "

# Handle required configuration of the rootfs to store persistent files on
# encripted /config partition.
rw_config_partition() {
    # If we are using openssh but want the persistent data to be stored in the
    # encrypted config partition, replace or append SYSCONFDIR in
    # /etc/default/ssh.
    # This should only be done after read_only_rootfs_hook(s) have been done.
    if [ -d ${IMAGE_ROOTFS}${sysconfdir}/ssh ]; then
        sed -i -e '/^SYSCONFDIR=/{h;s/=.*/=\$\{SYSCONFDIR:-\/config\/etc\/ssh\}/};${x;/^$/{s//SYSCONFDIR=\$\{SYSCONFDIR:-\/config\/etc\/ssh\}/;H};x}' ${IMAGE_ROOTFS}${sysconfdir}/default/ssh
        sed -i -e 's/HostKey .*\/ssh\/ssh_host_\(.*\)key/HostKey \/config\/etc\/ssh\/ssh_host_\1key/' ${IMAGE_ROOTFS}${sysconfdir}/ssh/sshd_config_readonly
        echo "HostKey /config/etc/ssh/ssh_host_dsa_key" >> ${IMAGE_ROOTFS}${sysconfdir}/ssh/sshd_config
        echo "HostKey /config/etc/ssh/ssh_host_rsa_key" >> ${IMAGE_ROOTFS}${sysconfdir}/ssh/sshd_config
        echo "HostKey /config/etc/ssh/ssh_host_ecdsa_key" >> ${IMAGE_ROOTFS}${sysconfdir}/ssh/sshd_config
        echo "HostKey /config/etc/ssh/ssh_host_ed25519_key" >> ${IMAGE_ROOTFS}${sysconfdir}/ssh/sshd_config
    fi
}
ROOTFS_POSTPROCESS_COMMAND += "rw_config_partition; "
ROOTFS_POSTPROCESS_COMMAND += "start_tty_on_hvc0;"
