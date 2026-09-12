package com.bakinngtray.passivememoryrecorder;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

final class DriveUploader {
    private static final String RECORDINGS_PATH = "Music/PassiveMemoryRecorder";
    private static final String MIME = "audio/mp4";

    private DriveUploader() {}

    static void uploadAll(Context context) {
        Uri treeUri = Prefs.getUploadTreeUri(context);
        if (treeUri == null || !hasValidatedNetwork(context)) return;

        ContentResolver resolver = context.getContentResolver();
        Map<String, Long> remoteFiles;
        try {
            remoteFiles = listRemoteFiles(resolver, treeUri);
        } catch (Exception ignored) {
            return;
        }

        Uri collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.SIZE
        };
        String selection = MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ? AND "
                + MediaStore.Audio.Media.IS_PENDING + "=0";
        String[] args = { RECORDINGS_PATH + "%" };

        try (Cursor cursor = resolver.query(
                collection,
                projection,
                selection,
                args,
                MediaStore.Audio.Media.DATE_ADDED + " ASC")) {
            if (cursor == null) return;

            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
            int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                String name = cursor.getString(nameCol);
                long size = cursor.isNull(sizeCol) ? -1L : cursor.getLong(sizeCol);
                if (name == null || !name.startsWith("PMR_") || size <= 0L) continue;

                Uri source = ContentUris.withAppendedId(collection, id);
                Long remoteSize = remoteFiles.get(name);

                if (remoteSize != null) {
                    if (remoteSize == size && resolver.delete(source, null, null) > 0) {
                        continue;
                    }
                    // Same name with different or unknown size: keep the local file rather than risk overwriting data.
                    continue;
                }

                long copied = copyOne(resolver, treeUri, source, name, size);
                if (copied == size) {
                    remoteFiles.put(name, size);
                    resolver.delete(source, null, null);
                }
            }
        } catch (Exception ignored) {
            // Deliberately no retry here. Files remain local and the next finalized recording triggers another pass.
        }
    }

    private static long copyOne(ContentResolver resolver, Uri treeUri, Uri source, String name, long expectedSize) {
        Uri destination = null;
        try {
            String treeId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId);
            destination = DocumentsContract.createDocument(resolver, parent, MIME, name);
            if (destination == null) return -1L;

            long copied = 0L;
            byte[] buffer = new byte[256 * 1024];
            try (InputStream in = resolver.openInputStream(source);
                 OutputStream out = resolver.openOutputStream(destination, "w")) {
                if (in == null || out == null) throw new IllegalStateException("Cannot open stream");
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    copied += read;
                }
                out.flush();
            }

            if (copied != expectedSize) {
                try { DocumentsContract.deleteDocument(resolver, destination); } catch (Exception ignored) {}
                return -1L;
            }
            return copied;
        } catch (Exception ignored) {
            if (destination != null) {
                try { DocumentsContract.deleteDocument(resolver, destination); } catch (Exception ignoredToo) {}
            }
            return -1L;
        }
    }

    private static Map<String, Long> listRemoteFiles(ContentResolver resolver, Uri treeUri) throws Exception {
        Map<String, Long> result = new HashMap<>();
        String treeId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE
        };

        try (Cursor cursor = resolver.query(children, projection, null, null, null)) {
            if (cursor == null) throw new IllegalStateException("Cannot list destination folder");
            int nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE);
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameCol);
                if (name == null) continue;
                long size = cursor.isNull(sizeCol) ? -1L : cursor.getLong(sizeCol);
                result.put(name, size);
            }
        }
        return result;
    }

    private static boolean hasValidatedNetwork(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
}
