Place normal unencrypted .pdf files in this folder before building the APK.
The app automatically lists every .pdf file found here.
PDFs are streamed from assets into a temporary cache file only because Android PdfRenderer requires a seekable file descriptor.
