/* tcg2_srtm_test.c - Read firmware (SRTM) TPM event log via EFI TCG2 protocol
 *
 *  GRUB  --  GRand Unified Bootloader
 *  Copyright (C) 2026 OpenXT / TrenchBoot bring-up
 *
 *  GRUB is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  GRUB is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 */

#include <grub/types.h>
#include <grub/misc.h>
#include <grub/command.h>
#include <grub/i18n.h>
#include <grub/efi/api.h>
#include <grub/efi/efi.h>

GRUB_MOD_LICENSE ("GPLv3+");

/* EFI_TCG2_PROTOCOL_GUID (TCG PC Client EFI Protocol Specification, EDK2) */
static const grub_guid_t grub_efi_tcg2_protocol_guid = {
  0x607f766c, 0x7455, 0x42be,
  { 0x93, 0x0b, 0xe4, 0xd7, 0x6d, 0xb2, 0x72, 0x0f }
};

#define GRUB_EFI_TCG2_EVENT_LOG_FORMAT_TCG_1_2 0x00000001
#define GRUB_EFI_TCG2_EVENT_LOG_FORMAT_TCG_2   0x00000002

struct grub_efi_tcg2_protocol
{
  grub_efi_status_t (__grub_efi_api *get_capability) (
      struct grub_efi_tcg2_protocol *this,
      void *protocol_capability);
  grub_efi_status_t (__grub_efi_api *get_event_log) (
      struct grub_efi_tcg2_protocol *this,
      grub_uint32_t log_format,
      grub_efi_physical_address_t *event_log_location,
      grub_efi_physical_address_t *event_log_last_entry,
      grub_efi_boolean_t *event_log_truncated);
};

struct grub_efi_tcg2_version
{
  grub_uint8_t major;
  grub_uint8_t minor;
};

/* Keep native EFI alignment (do not pack). */
struct grub_efi_tcg2_boot_service_capability
{
  grub_uint8_t size;
  struct grub_efi_tcg2_version structure_version;
  struct grub_efi_tcg2_version protocol_version;
  grub_uint32_t hash_algorithm_bitmap;
  grub_uint32_t supported_event_logs;
  grub_efi_boolean_t tpm_present_flag;
  grub_uint16_t max_command_size;
  grub_uint16_t max_response_size;
  grub_uint32_t manufacturer_id;
  grub_uint32_t number_of_pcr_banks;
  grub_uint32_t active_pcr_banks;
};

static void
hex_dump (const grub_uint8_t *data, grub_size_t len)
{
  grub_size_t i;

  for (i = 0; i < len; i++)
    {
      if ((i % 16) == 0)
	grub_printf ("%04llx: ", (unsigned long long) i);
      grub_printf ("%02x ", data[i]);
      if ((i % 16) == 15 || i + 1 == len)
	grub_printf ("\n");
    }
}

/* Parse decimal size 1..1MiB; returns 0 on failure. */
static int
parse_max_bytes (const char *s, grub_size_t *out)
{
  grub_size_t v = 0;

  if (!s || !*s)
    return 0;
  for (; *s; s++)
    {
      if (*s < '0' || *s > '9')
	return 0;
      v = v * 10 + (grub_size_t) (*s - '0');
      if (v > (grub_size_t) (1024 * 1024))
	return 0;
    }
  if (v == 0)
    return 0;
  *out = v;
  return 1;
}

static grub_err_t
grub_cmd_tcg2_srtm_test (grub_command_t cmd __attribute__ ((unused)),
			 int argc, char **args)
{
  struct grub_efi_tcg2_protocol *tcg2;
  struct grub_efi_tcg2_boot_service_capability cap;
  grub_efi_physical_address_t log_loc = 0;
  grub_efi_physical_address_t last_ent = 0;
  grub_efi_boolean_t truncated = 0;
  grub_efi_status_t st;
  grub_uint32_t fmt;
  const grub_uint8_t *base;
  grub_size_t dump_len = 4096;

  if (argc >= 1)
    {
      if (!parse_max_bytes (args[0], &dump_len))
	return grub_error (GRUB_ERR_BAD_ARGUMENT,
			   N_("MAX_BYTES must be a decimal value in 1..1048576"));
    }

  tcg2 = (struct grub_efi_tcg2_protocol *) grub_efi_locate_protocol (
      &grub_efi_tcg2_protocol_guid, NULL);
  if (!tcg2)
    return grub_error (GRUB_ERR_FILE_NOT_FOUND,
		       N_("EFI TCG2 protocol not found (TPM2 firmware interface missing?)"));

  grub_memset (&cap, 0, sizeof (cap));
  cap.size = sizeof (cap);
  st = tcg2->get_capability (tcg2, &cap);
  if (st != GRUB_EFI_SUCCESS)
    return grub_error (GRUB_ERR_IO,
		       N_("TCG2 GetCapability failed (status=0x%lx)"),
		       (unsigned long) st);

  grub_printf (N_("TCG2 capability:\n"));
  grub_printf (N_("  struct_ver=%u.%u protocol_ver=%u.%u\n"),
	       (unsigned) cap.structure_version.major,
	       (unsigned) cap.structure_version.minor,
	       (unsigned) cap.protocol_version.major,
	       (unsigned) cap.protocol_version.minor);
  grub_printf (N_("  tpm_present=%u supported_logs=0x%x hash_bitmap=0x%x\n"),
	       (unsigned) cap.tpm_present_flag,
	       (unsigned) cap.supported_event_logs,
	       (unsigned) cap.hash_algorithm_bitmap);
  grub_printf (N_("  manufacturer=0x%x pcr_banks=%u active_banks=0x%x\n"),
	       (unsigned) cap.manufacturer_id,
	       (unsigned) cap.number_of_pcr_banks,
	       (unsigned) cap.active_pcr_banks);
  grub_printf (N_("  max_cmd=%u max_rsp=%u\n"),
	       (unsigned) cap.max_command_size,
	       (unsigned) cap.max_response_size);
  grub_printf (N_("  cap.size(out)=%u cap.size(sent)=%u\n"),
	       (unsigned) cap.size,
	       (unsigned) sizeof (cap));

  if (!cap.tpm_present_flag)
    grub_printf (N_("WARNING: TCG2 reports no TPM present; trying GetEventLog anyway.\n"));

  if (cap.supported_event_logs & GRUB_EFI_TCG2_EVENT_LOG_FORMAT_TCG_2)
    fmt = GRUB_EFI_TCG2_EVENT_LOG_FORMAT_TCG_2;
  else if (cap.supported_event_logs & GRUB_EFI_TCG2_EVENT_LOG_FORMAT_TCG_1_2)
    fmt = GRUB_EFI_TCG2_EVENT_LOG_FORMAT_TCG_1_2;
  else
    return grub_error (GRUB_ERR_IO,
		       N_("TCG2 reports no supported event log format"));

  st = tcg2->get_event_log (tcg2, fmt, &log_loc, &last_ent, &truncated);

  if (st != GRUB_EFI_SUCCESS)
    return grub_error (GRUB_ERR_IO,
		       N_("TCG2 GetEventLog failed (status=0x%lx)"),
		       (unsigned long) st);

  if (log_loc == 0)
    return grub_error (GRUB_ERR_IO, N_("TCG2 returned null event log address"));

  base = (const grub_uint8_t *) (grub_addr_t) log_loc;

  grub_printf (N_("TCG2 SRTM event log (format=0x%x):\n"), fmt);
  grub_printf (N_("  log_phys=0x%llx last_entry_phys=0x%llx truncated=%u\n"),
	       (unsigned long long) log_loc,
	       (unsigned long long) last_ent,
	       (unsigned) truncated);

  if (last_ent >= log_loc && (last_ent - log_loc) + 512ULL < (grub_uint64_t) dump_len)
    dump_len = (grub_size_t) ((last_ent - log_loc) + 512ULL);
  if (dump_len < 64)
    dump_len = 64;

  grub_printf (N_("First %llu bytes (identity-mapped read):\n"),
		 (unsigned long long) dump_len);
  hex_dump (base, dump_len);

  return GRUB_ERR_NONE;
}

static grub_command_t cmd;

GRUB_MOD_INIT (tcg2_srtm_test)
{
  cmd = grub_register_command ("tcg2_srtm_test", grub_cmd_tcg2_srtm_test,
			       N_("[MAX_BYTES]"),
			       N_("Test: dump TPM firmware event log via EFI TCG2 GetEventLog."));
}

GRUB_MOD_FINI (tcg2_srtm_test)
{
  grub_unregister_command (cmd);
}
