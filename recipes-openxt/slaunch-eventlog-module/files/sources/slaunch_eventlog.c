#include <linux/module.h>
#include <linux/debugfs.h>
#include <linux/fs.h>
#include <linux/io.h>
#include <linux/uaccess.h>

/*
 * Default Secure Launch eventlog range:
 *   base = 0xa59e4000
 *   end  = 0xa59ebfff
 *   size = 0x8000
 */
static unsigned long log_base = 0xa59e4000;
static unsigned long log_size = 0x8000;
module_param_named(log_base, log_base, ulong, 0444);
MODULE_PARM_DESC(log_base, "Physical base address of Secure Launch eventlog");
module_param_named(log_size, log_size, ulong, 0444);
MODULE_PARM_DESC(log_size, "Secure Launch eventlog size in bytes");

static struct dentry *dbg_dir;
static struct dentry *dbg_file;

static ssize_t evtlog_read(struct file *file, char __user *buf, size_t count,
			   loff_t *ppos)
{
	void *mapped;
	phys_addr_t phys;
	size_t to_copy;
	ssize_t ret;

	if (log_size == 0)
		return -EINVAL;

	if (*ppos < 0)
		return -EINVAL;

	if (*ppos >= log_size)
		return 0;

	to_copy = min_t(size_t, count, log_size - *ppos);
	phys = (phys_addr_t)log_base + *ppos;

	mapped = memremap(phys, to_copy, MEMREMAP_WB);
	if (!mapped)
		return -EFAULT;

	if (copy_to_user(buf, mapped, to_copy))
		ret = -EFAULT;
	else {
		*ppos += to_copy;
		ret = to_copy;
	}

	memunmap(mapped);
	return ret;
}

static const struct file_operations evtlog_fops = {
	.owner = THIS_MODULE,
	.read = evtlog_read,
	.llseek = default_llseek,
};

static int __init slaunch_eventlog_init(void)
{
	dbg_dir = debugfs_create_dir("slaunch_eventlog", NULL);
	if (IS_ERR_OR_NULL(dbg_dir)) {
		pr_err("slaunch_eventlog: failed to create debugfs dir\n");
		return -ENODEV;
	}

	dbg_file = debugfs_create_file("raw.bin", 0400, dbg_dir, NULL, &evtlog_fops);
	if (IS_ERR_OR_NULL(dbg_file)) {
		pr_err("slaunch_eventlog: failed to create debugfs file\n");
		debugfs_remove_recursive(dbg_dir);
		dbg_dir = NULL;
		return -ENODEV;
	}

	pr_info("slaunch_eventlog: base=%#lx size=%#lx\n", log_base, log_size);
	return 0;
}

static void __exit slaunch_eventlog_exit(void)
{
	debugfs_remove_recursive(dbg_dir);
}

module_init(slaunch_eventlog_init);
module_exit(slaunch_eventlog_exit);

MODULE_AUTHOR("OpenXT");
MODULE_DESCRIPTION("Expose Secure Launch event log via debugfs");
MODULE_LICENSE("GPL");
