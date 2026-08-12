package de.unbow.mora.testprovider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

/*
 * Narrow control channel for the deterministic {@link TestDocumentsProvider}.
 *
 * The document provider remains protected by the system-only MANAGE_DOCUMENTS
 * permission. The target Debug APK can call this separate provider through a signature-only,
 * Debug-only permission; the control provider then forwards four fixture operations under the
 * test APK's own UID. Normal document reads and writes still require the explicit URI grants
 * issued by TestDocumentsProvider.
 */
public final class TestDocumentsControlProvider extends ContentProvider {

    public static final String AUTHORITY = "de.unbow.mora.test.documents.control";

    private static final String TARGET_PACKAGE = "de.unbow.mora";
    private static final Uri DOCUMENT_PROVIDER_URI =
            Uri.parse("content://" + TestDocumentsProvider.AUTHORITY);

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        enforceTargetCaller();
        if (!isSupportedMethod(method)) {
            throw new UnsupportedOperationException("Unsupported test-provider control method");
        }
        if (TestDocumentsProvider.METHOD_GRANT.equals(method)
                && (extras == null
                || !TARGET_PACKAGE.equals(
                        extras.getString(TestDocumentsProvider.KEY_TARGET_PACKAGE)))) {
            throw new SecurityException("Test document grants may target only the Mora Debug APK");
        }

        long callingIdentity = Binder.clearCallingIdentity();
        try {
            Bundle result = requireContext().getContentResolver().call(
                    DOCUMENT_PROVIDER_URI,
                    method,
                    arg,
                    extras);
            if (result == null) {
                throw new IllegalStateException("Test document provider rejected " + method);
            }
            return result;
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
    }

    private void enforceTargetCaller() {
        Context context = requireContext();
        String[] callerPackages = context.getPackageManager().getPackagesForUid(
                Binder.getCallingUid());
        if (callerPackages != null) {
            for (String callerPackage : callerPackages) {
                if (TARGET_PACKAGE.equals(callerPackage)) {
                    return;
                }
            }
        }
        throw new SecurityException("Only the Mora Debug target may control test documents");
    }

    private static boolean isSupportedMethod(String method) {
        return TestDocumentsProvider.METHOD_CONFIGURE.equals(method)
                || TestDocumentsProvider.METHOD_GRANT.equals(method)
                || TestDocumentsProvider.METHOD_REVOKE.equals(method)
                || TestDocumentsProvider.METHOD_RESET.equals(method);
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        throw new UnsupportedOperationException("Query is not supported");
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Insert is not supported");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Delete is not supported");
    }

    @Override
    public int update(
            Uri uri,
            ContentValues values,
            String selection,
            String[] selectionArgs) {
        throw new UnsupportedOperationException("Update is not supported");
    }
}
