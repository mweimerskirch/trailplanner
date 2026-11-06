/*
 * trailplan-mtp: the backend's access to USB devices over MTP (libmtp), for syncing routes.
 * One folder at the top of the device's storage is read or changed; nothing else is touched.
 *
 *   trailplan-mtp [--match TEXT] list FOLDER OUTDIR
 *       Prints "DEVICE\t<manufacturer>\t<model>\t<serial>", then "FILE\t<size>\t<name>" for each
 *       file in FOLDER, and copies those files into OUTDIR.
 *   trailplan-mtp [--match TEXT] apply FOLDER UPLOADDIR [DELETE_NAME...]
 *       Copies every file in UPLOADDIR into FOLDER (replacing files of the same name), then deletes
 *       the named files from FOLDER. Prints "SENT\t<name>" and "DELETED\t<name>".
 *   trailplan-mtp [--match TEXT] files FOLDER
 *       Prints "FILE\t<size>\t<name>" for each file in FOLDER, without copying anything.
 *   trailplan-mtp [--match TEXT] get FOLDER OUTDIR NAME...
 *       Copies the named files from FOLDER into OUTDIR and prints their FILE lines. Names that are
 *       not in FOLDER are skipped.
 *
 * --match picks the first device whose "<manufacturer> <model>" contains TEXT (case-insensitive).
 * Exit codes: 0 ok, 1 other error, 2 no device connected, 3 device busy or not accessible,
 * 4 folder not found, 64 usage. Errors are explained on stderr.
 */
#define _GNU_SOURCE
#include <dirent.h>
#include <libmtp.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>

enum { OK = 0, FAILED = 1, NO_DEVICE = 2, BUSY = 3, NO_FOLDER = 4, USAGE = 64 };

static void dump_errors(LIBMTP_mtpdevice_t *device) {
  LIBMTP_Dump_Errorstack(device);
  LIBMTP_Clear_Errorstack(device);
}

/** A plain file name that is safe to use as a path component. */
static int safe_name(const char *name) {
  return name && name[0] && strcmp(name, ".") != 0 && strcmp(name, "..") != 0 &&
         strchr(name, '/') == NULL;
}

static char *text(char *value) { return value ? value : strdup(""); }

/** Opens the first device matching `match` (NULL: any); prints its DEVICE line. */
static LIBMTP_mtpdevice_t *open_device(const char *match, int *status) {
  LIBMTP_raw_device_t *raw = NULL;
  int count = 0;
  LIBMTP_error_number_t err = LIBMTP_Detect_Raw_Devices(&raw, &count);
  if (err == LIBMTP_ERROR_NO_DEVICE_ATTACHED || count == 0) {
    fprintf(stderr, "No device connected.\n");
    *status = NO_DEVICE;
    return NULL;
  }
  if (err != LIBMTP_ERROR_NONE) {
    fprintf(stderr, "Could not look for devices (libmtp error %d).\n", err);
    *status = FAILED;
    return NULL;
  }
  int opened_any = 0;
  for (int i = 0; i < count; i++) {
    LIBMTP_mtpdevice_t *device = LIBMTP_Open_Raw_Device_Uncached(&raw[i]);
    if (device == NULL) continue;
    opened_any = 1;
    char *manufacturer = text(LIBMTP_Get_Manufacturername(device));
    char *model = text(LIBMTP_Get_Modelname(device));
    char *serial = text(LIBMTP_Get_Serialnumber(device));
    char *both = NULL;
    if (asprintf(&both, "%s %s", manufacturer, model) < 0) both = NULL;
    int matches = match == NULL || (both && strcasestr(both, match) != NULL);
    if (matches) printf("DEVICE\t%s\t%s\t%s\n", manufacturer, model, serial);
    free(both);
    free(manufacturer);
    free(model);
    free(serial);
    if (matches) {
      free(raw);
      return device;
    }
    LIBMTP_Release_Device(device);
  }
  free(raw);
  if (opened_any) {
    fprintf(stderr, "No matching device connected.\n");
    *status = NO_DEVICE;
  } else {
    fprintf(stderr,
            "The device was found but could not be opened. It may be open in another program "
            "(e.g. a file manager), or this user may lack access to it.\n");
    *status = BUSY;
  }
  return NULL;
}

/** Finds FOLDER at the top of any storage; sets storage and folder ids. */
static int find_folder(LIBMTP_mtpdevice_t *device, const char *name, uint32_t *storage_id,
                       uint32_t *folder_id) {
  if (LIBMTP_Get_Storage(device, LIBMTP_STORAGE_SORTBY_NOTSORTED) != 0) {
    dump_errors(device);
    fprintf(stderr, "Could not read the device's storage. Is it unlocked and switched on?\n");
    return BUSY;
  }
  for (LIBMTP_devicestorage_t *s = device->storage; s != NULL; s = s->next) {
    LIBMTP_file_t *files = LIBMTP_Get_Files_And_Folders(device, s->id, LIBMTP_FILES_AND_FOLDERS_ROOT);
    int found = 0;
    while (files != NULL) {
      LIBMTP_file_t *next = files->next;
      if (!found && files->filetype == LIBMTP_FILETYPE_FOLDER && files->filename &&
          strcmp(files->filename, name) == 0) {
        *storage_id = s->id;
        *folder_id = files->item_id;
        found = 1;
      }
      LIBMTP_destroy_file_t(files);
      files = next;
    }
    if (found) return OK;
  }
  fprintf(stderr, "The device has no \"%s\" folder.\n", name);
  return NO_FOLDER;
}

static LIBMTP_file_t *folder_files(LIBMTP_mtpdevice_t *device, uint32_t storage, uint32_t folder) {
  return LIBMTP_Get_Files_And_Folders(device, storage, folder);
}

static void free_files(LIBMTP_file_t *files) {
  while (files != NULL) {
    LIBMTP_file_t *next = files->next;
    LIBMTP_destroy_file_t(files);
    files = next;
  }
}

static int wanted(const char *name, char **names, int count) {
  if (names == NULL) return 1;
  for (int i = 0; i < count; i++) {
    if (strcmp(names[i], name) == 0) return 1;
  }
  return 0;
}

/**
 * Prints the FILE line of each file in the folder. With an outdir, copies the files there first;
 * with names, only those files.
 */
static int list(LIBMTP_mtpdevice_t *device, uint32_t storage, uint32_t folder, const char *outdir,
                char **names, int name_count) {
  LIBMTP_file_t *files = folder_files(device, storage, folder);
  int status = OK;
  for (LIBMTP_file_t *f = files; f != NULL; f = f->next) {
    if (f->filetype == LIBMTP_FILETYPE_FOLDER || !safe_name(f->filename)) continue;
    if (!wanted(f->filename, names, name_count)) continue;
    if (outdir == NULL) {
      printf("FILE\t%llu\t%s\n", (unsigned long long)f->filesize, f->filename);
      continue;
    }
    char *path = NULL;
    if (asprintf(&path, "%s/%s", outdir, f->filename) < 0) {
      status = FAILED;
      break;
    }
    if (LIBMTP_Get_File_To_File(device, f->item_id, path, NULL, NULL) != 0) {
      dump_errors(device);
      fprintf(stderr, "Could not read %s from the device.\n", f->filename);
      status = FAILED;
    } else {
      printf("FILE\t%llu\t%s\n", (unsigned long long)f->filesize, f->filename);
    }
    free(path);
  }
  free_files(files);
  return status;
}

/** Deletes the file called `name` in the folder, if there is one. */
static int delete_named(LIBMTP_mtpdevice_t *device, uint32_t storage, uint32_t folder,
                        const char *name) {
  LIBMTP_file_t *files = folder_files(device, storage, folder);
  int status = OK;
  for (LIBMTP_file_t *f = files; f != NULL; f = f->next) {
    if (f->filetype == LIBMTP_FILETYPE_FOLDER || !f->filename || strcmp(f->filename, name) != 0)
      continue;
    if (LIBMTP_Delete_Object(device, f->item_id) != 0) {
      dump_errors(device);
      fprintf(stderr, "Could not delete %s on the device.\n", name);
      status = FAILED;
    }
  }
  free_files(files);
  return status;
}

static int apply(LIBMTP_mtpdevice_t *device, uint32_t storage, uint32_t folder,
                 const char *uploaddir, char **deletions, int deletion_count) {
  DIR *dir = opendir(uploaddir);
  if (dir == NULL) {
    perror(uploaddir);
    return FAILED;
  }
  int status = OK;
  struct dirent *entry;
  while (status == OK && (entry = readdir(dir)) != NULL) {
    if (entry->d_name[0] == '.') continue;
    char *path = NULL;
    if (asprintf(&path, "%s/%s", uploaddir, entry->d_name) < 0) {
      status = FAILED;
      break;
    }
    struct stat st;
    if (stat(path, &st) != 0 || !S_ISREG(st.st_mode)) {
      free(path);
      continue;
    }
    // MTP cannot overwrite: remove the old version first.
    status = delete_named(device, storage, folder, entry->d_name);
    if (status == OK) {
      LIBMTP_file_t *meta = LIBMTP_new_file_t();
      meta->filename = strdup(entry->d_name);
      meta->filesize = (uint64_t)st.st_size;
      meta->filetype = LIBMTP_FILETYPE_UNKNOWN;
      meta->parent_id = folder;
      meta->storage_id = storage;
      if (LIBMTP_Send_File_From_File(device, path, meta, NULL, NULL) != 0) {
        dump_errors(device);
        fprintf(stderr, "Could not copy %s to the device.\n", entry->d_name);
        status = FAILED;
      } else {
        printf("SENT\t%s\n", entry->d_name);
      }
      LIBMTP_destroy_file_t(meta);
    }
    free(path);
  }
  closedir(dir);
  for (int i = 0; status == OK && i < deletion_count; i++) {
    status = delete_named(device, storage, folder, deletions[i]);
    if (status == OK) printf("DELETED\t%s\n", deletions[i]);
  }
  return status;
}

static int usage(void) {
  fprintf(stderr,
          "usage: trailplan-mtp [--match TEXT] list FOLDER OUTDIR\n"
          "       trailplan-mtp [--match TEXT] apply FOLDER UPLOADDIR [DELETE_NAME...]\n"
          "       trailplan-mtp [--match TEXT] files FOLDER\n"
          "       trailplan-mtp [--match TEXT] get FOLDER OUTDIR NAME...\n");
  return USAGE;
}

int main(int argc, char **argv) {
  int arg = 1;
  const char *match = NULL;
  if (argc > 2 && strcmp(argv[1], "--match") == 0) {
    match = argv[2];
    arg = 3;
  }
  int count = argc - arg; // command and its arguments
  if (count < 2) return usage();
  const char *command = argv[arg];
  const char *folder_name = argv[arg + 1];
  if (!safe_name(folder_name)) return usage();
  enum { LIST, APPLY, FILES, GET } mode;
  if (strcmp(command, "list") == 0 && count == 3) {
    mode = LIST;
  } else if (strcmp(command, "apply") == 0 && count >= 3) {
    mode = APPLY;
  } else if (strcmp(command, "files") == 0 && count == 2) {
    mode = FILES;
  } else if (strcmp(command, "get") == 0 && count >= 4) {
    mode = GET;
  } else {
    return usage();
  }

  LIBMTP_Init();
  int status = OK;
  LIBMTP_mtpdevice_t *device = open_device(match, &status);
  if (device == NULL) return status;
  uint32_t storage = 0, folder = 0;
  status = find_folder(device, folder_name, &storage, &folder);
  if (status == OK) {
    char **rest = argv + arg + 3;
    int rest_count = count - 3;
    switch (mode) {
      case LIST: status = list(device, storage, folder, argv[arg + 2], NULL, 0); break;
      case APPLY: status = apply(device, storage, folder, argv[arg + 2], rest, rest_count); break;
      case FILES: status = list(device, storage, folder, NULL, NULL, 0); break;
      case GET: status = list(device, storage, folder, argv[arg + 2], rest, rest_count); break;
    }
  }
  fflush(stdout);
  LIBMTP_Release_Device(device);
  return status;
}
