package de.unbow.mora.testprovider;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * A deterministic, test-APK-only provider for exercising Mora's SAF boundary.
 *
 * <p>This component is deliberately implemented with Android and Java platform APIs only.
 * Android instantiates providers from the test APK before the instrumentation class loader is
 * installed, while AGP may package dependencies shared with the target APK only in that target.
 */
public final class TestDocumentsProvider extends DocumentsProvider {

    public static final String AUTHORITY = "de.unbow.mora.test.documents";
    public static final String METHOD_CONFIGURE = "configure";
    public static final String METHOD_GRANT = "grant";
    public static final String METHOD_REVOKE = "revoke";
    public static final String METHOD_RESET = "reset";

    public static final String KEY_BYTES = "bytes";
    public static final String KEY_DISPLAY_NAME = "display_name";
    public static final String KEY_DOCUMENT_FLAGS = "document_flags";
    public static final String KEY_FAIL_READS = "fail_reads";
    public static final String KEY_FAIL_WRITES = "fail_writes";
    public static final String KEY_FAIL_VERIFICATION_READ_AFTER_WRITE =
            "fail_verification_read_after_write";
    public static final String KEY_GRANT_FLAGS = "grant_flags";
    public static final String KEY_PARTIAL_WRITE_FAILURE_AFTER_BYTES =
            "partial_write_failure_after_bytes";
    public static final String KEY_REPEATED_BYTE = "repeated_byte";
    public static final String KEY_REPEATED_BYTE_COUNT = "repeated_byte_count";
    public static final String KEY_REPORTED_SIZE = "reported_size";
    public static final String KEY_REPORT_SIZE = "report_size";
    public static final String KEY_SLOW_READ_CHUNK_BYTES = "slow_read_chunk_bytes";
    public static final String KEY_SLOW_READ_DELAY_MILLIS = "slow_read_delay_millis";
    public static final String KEY_TARGET_PACKAGE = "target_package";

    private static final String ROOT_ID = "test-root";
    private static final String ROOT_DOCUMENT_ID = "root";
    private static final String DOCUMENT_DIRECTORY = "saf-provider-documents";
    private static final String MIME_TYPE_MARKDOWN = "text/markdown";
    private static final int BUFFER_SIZE = 8 * 1024;
    private static final Pattern DOCUMENT_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");
    private static final int ACCESS_GRANT_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
    private static final int URI_GRANT_FLAGS =
            ACCESS_GRANT_FLAGS | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;

    private static final String[] DEFAULT_ROOT_PROJECTION = {
        DocumentsContract.Root.COLUMN_ROOT_ID,
        DocumentsContract.Root.COLUMN_DOCUMENT_ID,
        DocumentsContract.Root.COLUMN_TITLE,
        DocumentsContract.Root.COLUMN_FLAGS,
        DocumentsContract.Root.COLUMN_ICON,
        DocumentsContract.Root.COLUMN_MIME_TYPES,
        DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,
    };

    private static final String[] DEFAULT_DOCUMENT_PROJECTION = {
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_FLAGS,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    };

    private static final class TestDocument {
        final File file;
        final String displayName;
        final int flags;
        final boolean reportSize;
        final Long reportedSizeOverride;
        final boolean failReads;
        final boolean failWrites;
        final boolean failVerificationReadAfterWrite;
        boolean pendingVerificationReadFailure;
        final long slowReadDelayMillis;
        final int slowReadChunkBytes;
        final Integer partialWriteFailureAfterBytes;

        TestDocument(
                File file,
                String displayName,
                int flags,
                boolean reportSize,
                Long reportedSizeOverride,
                boolean failReads,
                boolean failWrites,
                boolean failVerificationReadAfterWrite,
                long slowReadDelayMillis,
                int slowReadChunkBytes,
                Integer partialWriteFailureAfterBytes) {
            this.file = file;
            this.displayName = displayName;
            this.flags = flags;
            this.reportSize = reportSize;
            this.reportedSizeOverride = reportedSizeOverride;
            this.failReads = failReads;
            this.failWrites = failWrites;
            this.failVerificationReadAfterWrite = failVerificationReadAfterWrite;
            this.slowReadDelayMillis = slowReadDelayMillis;
            this.slowReadChunkBytes = slowReadChunkBytes;
            this.partialWriteFailureAfterBytes = partialWriteFailureAfterBytes;
        }
    }

    private final Object lock = new Object();
    private final Map<String, TestDocument> documents = new HashMap<>();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        String[] resolvedProjection = projection != null ? projection : DEFAULT_ROOT_PROJECTION;
        MatrixCursor cursor = new MatrixCursor(resolvedProjection);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : resolvedProjection) {
            switch (column) {
                case DocumentsContract.Root.COLUMN_ROOT_ID:
                    row.add(ROOT_ID);
                    break;
                case DocumentsContract.Root.COLUMN_DOCUMENT_ID:
                    row.add(ROOT_DOCUMENT_ID);
                    break;
                case DocumentsContract.Root.COLUMN_TITLE:
                    row.add("Mora test documents");
                    break;
                case DocumentsContract.Root.COLUMN_FLAGS:
                    row.add(DocumentsContract.Root.FLAG_LOCAL_ONLY);
                    break;
                case DocumentsContract.Root.COLUMN_ICON:
                    row.add(android.R.drawable.ic_menu_save);
                    break;
                case DocumentsContract.Root.COLUMN_MIME_TYPES:
                    row.add(MIME_TYPE_MARKDOWN);
                    break;
                case DocumentsContract.Root.COLUMN_AVAILABLE_BYTES:
                    row.add(Long.MAX_VALUE);
                    break;
                default:
                    row.add(null);
                    break;
            }
        }
        return cursor;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException {
        String[] resolvedProjection =
                projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION;
        MatrixCursor cursor = new MatrixCursor(resolvedProjection);
        if (ROOT_DOCUMENT_ID.equals(documentId)) {
            addDocumentRow(cursor, resolvedProjection, ROOT_DOCUMENT_ID, null);
        } else {
            TestDocument document;
            synchronized (lock) {
                document = documents.get(documentId);
            }
            if (document == null) {
                throw new FileNotFoundException(documentId);
            }
            addDocumentRow(cursor, resolvedProjection, documentId, document);
        }
        return cursor;
    }

    @Override
    public Cursor queryChildDocuments(
            String parentDocumentId,
            String[] projection,
            String sortOrder) throws FileNotFoundException {
        if (!ROOT_DOCUMENT_ID.equals(parentDocumentId)) {
            throw new FileNotFoundException(parentDocumentId);
        }
        String[] resolvedProjection =
                projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION;
        Map<String, TestDocument> snapshot;
        synchronized (lock) {
            snapshot = new TreeMap<>(documents);
        }
        MatrixCursor cursor = new MatrixCursor(resolvedProjection);
        for (Map.Entry<String, TestDocument> entry : snapshot.entrySet()) {
            addDocumentRow(cursor, resolvedProjection, entry.getKey(), entry.getValue());
        }
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openDocument(
            String documentId,
            String mode,
            CancellationSignal signal) throws FileNotFoundException {
        if (signal != null) {
            signal.throwIfCanceled();
        }
        TestDocument document;
        synchronized (lock) {
            document = documents.get(documentId);
        }
        if (document == null) {
            throw new FileNotFoundException(documentId);
        }

        boolean wantsWrite = mode.indexOf('w') >= 0 || mode.indexOf('a') >= 0
                || mode.indexOf('t') >= 0;
        if (!wantsWrite) {
            boolean failVerificationRead;
            synchronized (lock) {
                failVerificationRead = document.pendingVerificationReadFailure;
                document.pendingVerificationReadFailure = false;
            }
            if (document.failReads || failVerificationRead) {
                throw new FileNotFoundException("Reads are disabled for " + documentId);
            }
        }
        if (wantsWrite && (document.failWrites
                || (document.flags & DocumentsContract.Document.FLAG_SUPPORTS_WRITE) == 0)) {
            throw new FileNotFoundException("Writes are disabled for " + documentId);
        }
        if (wantsWrite && document.partialWriteFailureAfterBytes != null) {
            return openFailingWritePipe(
                    document,
                    document.partialWriteFailureAfterBytes,
                    signal);
        }
        if (!wantsWrite && document.slowReadDelayMillis > 0L) {
            return openSlowReadPipe(document, signal);
        }
        if (wantsWrite && document.failVerificationReadAfterWrite) {
            synchronized (lock) {
                document.pendingVerificationReadFailure = true;
            }
        }
        return ParcelFileDescriptor.open(document.file, ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle frameworkResult = super.call(method, arg, extras);
        if (frameworkResult != null) {
            return frameworkResult;
        }
        if (Binder.getCallingUid() != Process.myUid()) {
            throw new SecurityException("Fixture control requires the test APK UID");
        }
        switch (method) {
            case METHOD_CONFIGURE:
                configureDocument(requireDocumentId(arg), extras != null ? extras : new Bundle());
                return new Bundle();
            case METHOD_GRANT:
                grantDocument(requireDocumentId(arg), extras);
                return new Bundle();
            case METHOD_REVOKE:
                revokeDocument(requireDocumentId(arg));
                return new Bundle();
            case METHOD_RESET:
                resetDocuments();
                return new Bundle();
            default:
                return null;
        }
    }

    private void grantDocument(String documentId, Bundle extras) {
        if (extras == null) {
            throw new IllegalArgumentException("Missing grant extras");
        }
        String targetPackage = extras.getString(KEY_TARGET_PACKAGE);
        if (targetPackage == null) {
            throw new IllegalArgumentException("Missing target package");
        }
        int flags = extras.getInt(KEY_GRANT_FLAGS) & URI_GRANT_FLAGS;
        if ((flags & ACCESS_GRANT_FLAGS) == 0) {
            throw new IllegalArgumentException(
                    "At least one read or write URI permission is required");
        }
        synchronized (lock) {
            if (!documents.containsKey(documentId)) {
                throw new IllegalStateException("Unknown test document: " + documentId);
            }
        }
        withProviderIdentity(() -> {
            Context providerContext = requireProviderContext();
            Uri uri = documentUri(documentId);
            providerContext.revokeUriPermission(uri, ACCESS_GRANT_FLAGS);
            providerContext.grantUriPermission(targetPackage, uri, flags);
        });
    }

    private void revokeDocument(String documentId) {
        synchronized (lock) {
            if (!documents.containsKey(documentId)) {
                throw new IllegalStateException("Unknown test document: " + documentId);
            }
        }
        withProviderIdentity(() -> requireProviderContext().revokeUriPermission(
                documentUri(documentId),
                ACCESS_GRANT_FLAGS));
    }

    private void configureDocument(String documentId, Bundle extras) {
        Context providerContext = requireProviderContext();
        File directory = new File(providerContext.getFilesDir(), DOCUMENT_DIRECTORY);
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalStateException("Cannot create test document directory");
        }
        File file = new File(directory, documentId);
        if (!directory.equals(file.getParentFile())
                || !DOCUMENT_ID_PATTERN.matcher(documentId).matches()) {
            throw new IllegalArgumentException("Unsafe document id");
        }

        int repeatedByteCount = extras.getInt(KEY_REPEATED_BYTE_COUNT, -1);
        try {
            if (repeatedByteCount >= 0) {
                writeRepeatedBytes(
                        file,
                        repeatedByteCount,
                        (byte) extras.getInt(KEY_REPEATED_BYTE, 'a'));
            } else {
                byte[] bytes = extras.getByteArray(KEY_BYTES);
                writeBytes(file, bytes != null ? bytes : new byte[0]);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Cannot configure test document", error);
        }

        long slowReadDelayMillis = extras.getLong(KEY_SLOW_READ_DELAY_MILLIS, 0L);
        if (slowReadDelayMillis < 0L) {
            throw new IllegalArgumentException("Negative slow-read delay");
        }
        int slowReadChunkBytes = extras.getInt(KEY_SLOW_READ_CHUNK_BYTES, BUFFER_SIZE);
        if (slowReadChunkBytes <= 0) {
            throw new IllegalArgumentException("Non-positive slow-read chunk size");
        }
        Integer partialWriteFailureAfterBytes = null;
        if (extras.containsKey(KEY_PARTIAL_WRITE_FAILURE_AFTER_BYTES)) {
            int failureAfterBytes = extras.getInt(KEY_PARTIAL_WRITE_FAILURE_AFTER_BYTES);
            if (failureAfterBytes < 0) {
                throw new IllegalArgumentException("Negative partial-write threshold");
            }
            partialWriteFailureAfterBytes = failureAfterBytes;
        }
        Long reportedSizeOverride = extras.containsKey(KEY_REPORTED_SIZE)
                ? extras.getLong(KEY_REPORTED_SIZE)
                : null;
        String displayName = extras.getString(KEY_DISPLAY_NAME);

        TestDocument document = new TestDocument(
                file,
                displayName != null ? displayName : documentId + ".md",
                extras.getInt(
                        KEY_DOCUMENT_FLAGS,
                        DocumentsContract.Document.FLAG_SUPPORTS_WRITE),
                extras.getBoolean(KEY_REPORT_SIZE, true),
                reportedSizeOverride,
                extras.getBoolean(KEY_FAIL_READS, false),
                extras.getBoolean(KEY_FAIL_WRITES, false),
                extras.getBoolean(KEY_FAIL_VERIFICATION_READ_AFTER_WRITE, false),
                slowReadDelayMillis,
                slowReadChunkBytes,
                partialWriteFailureAfterBytes);
        synchronized (lock) {
            documents.put(documentId, document);
        }
    }

    private void resetDocuments() {
        Context providerContext = requireProviderContext();
        ArrayList<String> documentIds;
        synchronized (lock) {
            documentIds = new ArrayList<>(documents.keySet());
            documents.clear();
        }
        for (String documentId : documentIds) {
            withProviderIdentity(() -> providerContext.revokeUriPermission(
                    documentUri(documentId),
                    ACCESS_GRANT_FLAGS));
        }
        deleteRecursively(new File(providerContext.getFilesDir(), DOCUMENT_DIRECTORY));
    }

    private static void addDocumentRow(
            MatrixCursor cursor,
            String[] projection,
            String documentId,
            TestDocument document) {
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : projection) {
            switch (column) {
                case DocumentsContract.Document.COLUMN_DOCUMENT_ID:
                    row.add(documentId);
                    break;
                case DocumentsContract.Document.COLUMN_DISPLAY_NAME:
                    row.add(document != null ? document.displayName : "Test documents");
                    break;
                case DocumentsContract.Document.COLUMN_MIME_TYPE:
                    row.add(document != null
                            ? MIME_TYPE_MARKDOWN
                            : DocumentsContract.Document.MIME_TYPE_DIR);
                    break;
                case DocumentsContract.Document.COLUMN_FLAGS:
                    row.add(document != null ? document.flags : 0);
                    break;
                case DocumentsContract.Document.COLUMN_SIZE:
                    if (document == null || !document.reportSize) {
                        row.add(null);
                    } else if (document.reportedSizeOverride != null) {
                        row.add(document.reportedSizeOverride);
                    } else {
                        row.add(document.file.length());
                    }
                    break;
                case DocumentsContract.Document.COLUMN_LAST_MODIFIED:
                    row.add(document != null ? document.file.lastModified() : null);
                    break;
                default:
                    row.add(null);
                    break;
            }
        }
    }

    private static void writeBytes(File file, byte[] bytes) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(bytes);
        }
    }

    private static void writeRepeatedBytes(File file, int byteCount, byte value)
            throws IOException {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            byte[] buffer = new byte[Math.min(BUFFER_SIZE, Math.max(byteCount, 1))];
            java.util.Arrays.fill(buffer, value);
            int remaining = byteCount;
            while (remaining > 0) {
                int count = Math.min(buffer.length, remaining);
                output.write(buffer, 0, count);
                remaining -= count;
            }
        }
    }

    /** Streams provider latency without sleeping the Binder thread opening the document. */
    private ParcelFileDescriptor openSlowReadPipe(
            TestDocument document,
            CancellationSignal signal) throws FileNotFoundException {
        final ParcelFileDescriptor[] pipe;
        try {
            pipe = ParcelFileDescriptor.createReliablePipe();
        } catch (IOException error) {
            throw new FileNotFoundException(error.getMessage());
        }
        ParcelFileDescriptor readEnd = pipe[0];
        ParcelFileDescriptor writeEnd = pipe[1];
        if (signal != null) {
            signal.setOnCancelListener(() -> closeWithError(writeEnd, "Slow read cancelled"));
        }
        startIoThread("slow-read-" + document.file.getName(), () -> {
            try (FileInputStream input = new FileInputStream(document.file);
                    ParcelFileDescriptor.AutoCloseOutputStream output =
                            new ParcelFileDescriptor.AutoCloseOutputStream(writeEnd)) {
                byte[] buffer = new byte[document.slowReadChunkBytes];
                while (true) {
                    int count = input.read(buffer);
                    if (count < 0) {
                        break;
                    }
                    SystemClock.sleep(document.slowReadDelayMillis);
                    output.write(buffer, 0, count);
                    output.flush();
                }
            } catch (Exception ignored) {
                closeWithError(writeEnd, "Slow read consumer closed");
            }
        });
        return readEnd;
    }

    /** Models a provider that truncates, persists a prefix, and then reports failure. */
    private ParcelFileDescriptor openFailingWritePipe(
            TestDocument document,
            int failureAfterBytes,
            CancellationSignal signal) throws FileNotFoundException {
        final ParcelFileDescriptor[] pipe;
        try {
            pipe = ParcelFileDescriptor.createReliablePipe();
        } catch (IOException error) {
            throw new FileNotFoundException(error.getMessage());
        }
        ParcelFileDescriptor readEnd = pipe[0];
        ParcelFileDescriptor writeEnd = pipe[1];
        if (signal != null) {
            signal.setOnCancelListener(() -> closeWithError(readEnd, "Partial write cancelled"));
        }
        startIoThread("partial-write-" + document.file.getName(), () -> {
            try (FileOutputStream target = new FileOutputStream(document.file, false);
                    ParcelFileDescriptor.AutoCloseInputStream input =
                            new ParcelFileDescriptor.AutoCloseInputStream(readEnd)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int remaining = failureAfterBytes;
                while (remaining > 0) {
                    int count = input.read(buffer, 0, Math.min(buffer.length, remaining));
                    if (count < 0) {
                        break;
                    }
                    target.write(buffer, 0, count);
                    target.flush();
                    remaining -= count;
                }
                readEnd.closeWithError("Deterministic partial write failure");
            } catch (Exception ignored) {
                closeWithError(readEnd, "Deterministic partial write failure");
            }
        });
        return writeEnd;
    }

    private static void startIoThread(String name, Runnable block) {
        Thread thread = new Thread(block, "MoraTestProvider-" + name);
        thread.setDaemon(true);
        thread.start();
    }

    private static void closeWithError(ParcelFileDescriptor descriptor, String message) {
        try {
            descriptor.closeWithError(message);
        } catch (IOException ignored) {
            // The opposite pipe end already observed the intended close.
        }
    }

    private static String requireDocumentId(String documentId) {
        if (documentId == null || !DOCUMENT_ID_PATTERN.matcher(documentId).matches()) {
            throw new IllegalArgumentException("Unsafe document id");
        }
        return documentId;
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("Cannot delete test provider state: " + file);
        }
    }

    private static void withProviderIdentity(Runnable block) {
        long callingIdentity = Binder.clearCallingIdentity();
        try {
            block.run();
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
    }

    private Context requireProviderContext() {
        Context providerContext = getContext();
        if (providerContext == null) {
            throw new IllegalStateException("Provider is not attached");
        }
        return providerContext;
    }

    public static Uri documentUri(String documentId) {
        return DocumentsContract.buildDocumentUri(AUTHORITY, documentId);
    }
}
