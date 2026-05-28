#include <grub/types.h>
#include <grub/misc.h>
#include <grub/command.h>
#include <grub/i18n.h>
#include <grub/dl.h>
#include <grub/file.h>
#include <grub/err.h>
#include <grub/mm.h>
#include <grub/efi/api.h>
#include <grub/efi/efi.h>

GRUB_MOD_LICENSE ("GPLv3+");

/*
 * 0003-Add-Measure-function-to-the-shim-lock-protocol.patch adds Measure function, so we can use Grub
 * to perform necessary SRTM measurements of our binaries
 */
typedef grub_efi_status_t (*grub_efi_shim_lock_measure_t) (void *buffer,
                                                           grub_efi_uint32_t size,
                                                           grub_efi_uint8_t pcr);

static grub_efi_status_t
openxt_shim_lock_measure (grub_efi_shim_lock_protocol_t *shim,
                          void *buffer, grub_uint32_t size, grub_uint8_t pcr)
{
    grub_efi_shim_lock_measure_t measure = ((grub_efi_shim_lock_measure_t *)((void *)shim))[3];

    if (!measure)
        return GRUB_EFI_INVALID_PARAMETER;

    return measure (buffer, size, pcr);
}

static grub_err_t
grub_cmd_shim_measure (grub_command_t cmd __attribute__ ((unused)),
                       int argc, char **args)
{
    grub_efi_shim_lock_protocol_t *shim;
    grub_file_t file = NULL;
    void *buffer = NULL;
    grub_off_t fsize;
    grub_uint8_t pcr;
    unsigned long pcr_val;
    const char *end;
    grub_efi_status_t st;
    static grub_guid_t shim_lock_guid = GRUB_EFI_SHIM_LOCK_GUID;

    if (argc != 2)
        return grub_error (GRUB_ERR_BAD_ARGUMENT, N_("usage: shim_measure <pcr> <path>"));

    pcr_val = grub_strtoul (args[0], &end, 10);
    if (end == args[0] || *end || pcr_val > 255)
        return grub_error (GRUB_ERR_BAD_ARGUMENT, N_("invalid PCR index: %s"), args[0]);

    pcr = (grub_uint8_t) pcr_val;

    shim = grub_efi_locate_protocol (&shim_lock_guid, NULL);
    if (!shim)
        return grub_error (GRUB_ERR_FILE_NOT_FOUND, N_("SHIM_LOCK protocol not found"));

    // Make sure the file is not compressed before we read it to avoid forward sealing mismatch with initrd
    file = grub_file_open (args[1],
                           GRUB_FILE_TYPE_NONE | GRUB_FILE_TYPE_NO_DECOMPRESS);
    if (!file)
        return grub_errno;

    fsize = grub_file_size (file);
    if (fsize <= 0 || (grub_uint64_t)fsize > 0xFFFFFFFFULL)
    {
        grub_file_close (file);
        return grub_error (GRUB_ERR_BAD_FILE_TYPE,
                           N_("file size out of range: %s"), args[1]);
    }

    buffer = grub_malloc ((grub_size_t)fsize);
    if (!buffer)
    {
        grub_file_close (file);
        return grub_errno;
    }

    if (grub_file_read (file, buffer, fsize) != (grub_ssize_t)fsize)
    {
        grub_free (buffer);
        grub_file_close (file);
        return grub_error (GRUB_ERR_FILE_READ_ERROR,
                           N_("failed read on %s"), args[1]);
    }
    grub_file_close (file);

    st = openxt_shim_lock_measure (shim, buffer, (grub_efi_uint32_t)fsize, pcr);

    grub_free (buffer);

    if (st != GRUB_EFI_SUCCESS)
        return grub_error (GRUB_ERR_IO,
                           N_("shim_lock->Measure(%s, pcr=%u) failed: status=0x%lx"),
                           args[1], (unsigned)pcr, (unsigned long)st);

    return GRUB_ERR_NONE;
}

static grub_command_t cmd;

// Add shim_measure command to GRUB, to be called to perform remaining necessary SRTM measurements before slaunch
GRUB_MOD_INIT (shim_measure)
{
    cmd = grub_register_command ("shim_measure", grub_cmd_shim_measure,
                                 N_("<pcr> <path>"),
                                 N_("Read <path> from GRUB filesystem and call "
                                    "SHIM_LOCK->Measure() with the contents into "
                                    "TPM PCR <pcr>."));
}

GRUB_MOD_FINI (shim_measure)
{
    grub_unregister_command (cmd);
}
