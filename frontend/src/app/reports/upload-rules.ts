/**
 * Client-side checks before an upload, mirroring the server's rules so the
 * user gets an instant, specific message. The server checks again by file
 * contents (a renamed file is caught there).
 */
export const MAX_UPLOAD_MB = 250;
export const MAX_PHOTOS = 40;

const DOCUMENT_EXT = ['pdf', 'xlsx', 'xls', 'csv', 'docx', 'txt'];
const PHOTO_EXT = ['jpg', 'jpeg', 'png', 'tif', 'tiff', 'gif', 'bmp'];
const SINGLE_IMAGE_EXT = [...PHOTO_EXT, 'webp'];

export const ACCEPT = [...DOCUMENT_EXT, ...SINGLE_IMAGE_EXT].map((e) => '.' + e).join(',');

export function extensionOf(name: string): string {
  const dot = name.lastIndexOf('.');
  return dot < 0 ? '' : name.slice(dot + 1).toLowerCase();
}

/** Returns a message explaining why these files can't be uploaded, or null when they can. */
export function checkUpload(files: File[]): string | null {
  if (files.length === 0) {
    return 'Choose a file to upload.';
  }
  const heic = files.find((f) => ['heic', 'heif'].includes(extensionOf(f.name)));
  if (heic) {
    return `${heic.name} is an iPhone HEIC photo, which can't be read here. Export it as JPG (on iPhone: Settings → Camera → Formats → Most Compatible) and upload again.`;
  }
  const empty = files.find((f) => f.size === 0);
  if (empty) {
    return `${empty.name} is empty.`;
  }
  if (files.length > 1) {
    if (files.length > MAX_PHOTOS) {
      return `Upload at most ${MAX_PHOTOS} page photos at once.`;
    }
    const notPhoto = files.find((f) => !PHOTO_EXT.includes(extensionOf(f.name)));
    if (notPhoto) {
      return `Several files can only be combined when they are all page photos (JPG, PNG, TIFF, GIF or BMP). ${notPhoto.name} isn't; upload PDFs and spreadsheets one at a time.`;
    }
  } else {
    const ext = extensionOf(files[0].name);
    if (![...DOCUMENT_EXT, ...SINGLE_IMAGE_EXT].includes(ext)) {
      return `${files[0].name}: unsupported file type. Upload a PDF, an image (PNG, JPG, WEBP, TIFF), a spreadsheet (XLSX, XLS, CSV), a Word file or a text file.`;
    }
  }
  const total = files.reduce((n, f) => n + f.size, 0);
  if (total > MAX_UPLOAD_MB * 1024 * 1024) {
    const mb = Math.ceil(total / 1024 / 1024);
    return `${files.length > 1 ? 'These files are' : files[0].name + ' is'} ${mb} MB; the limit is ${MAX_UPLOAD_MB} MB. Upload just the financial statements pages, or compress the PDF (photos make up most of the size).`;
  }
  return null;
}

/** Friendly sizes for the progress line: "37 of 137 MB". */
export function mb(bytes: number): string {
  const v = bytes / 1024 / 1024;
  return v >= 10 ? `${Math.round(v)} MB` : `${v.toFixed(1)} MB`;
}
