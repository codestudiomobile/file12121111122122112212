package com.codestudio.mobile;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

public class FileUtils {

    public static String getMimeType(Context context, Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);

        if (mimeType == null || mimeType.equals("text/plain") || mimeType.equals("application/octet-stream")) {
            String extension = null;

            if ("content".equals(uri.getScheme())) {
                Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    String name = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                    if (name != null && name.contains(".")) {
                        extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
                    }
                    cursor.close();
                }
            } else {
                extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            }

            if (extension != null) {
                switch (extension) {
                    case "java":
                        return "text/x-java-source";
                    case "py":
                        return "text/x-python";
                    case "c":
                        return "text/x-c";
                    case "cpp":
                    case "cxx":
                    case "cc":
                        return "text/x-cpp";
                    case "js":
                        return "application/javascript";
                    case "php":
                        return "text/x-php";
                    case "rb":
                        return "text/x-ruby";
                    case "go":
                        return "text/x-go";
                    case "kt":
                        return "text/x-kotlin";
                    case "sh":
                    case "bash":
                        return "text/x-shellscript";
                    case "cs":
                        return "text/x-csharp";
                    case "pl":
                        return "text/x-perl";
                    case "lua":
                        return "text/x-lua";
                    default:
                        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                }
            }
        }

        return mimeType;
    }

    public static String getFileName(Context context, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst() && cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) >= 0) {
                result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    public static FileItem getFileItemFromUri(Context context, Uri uri) {
        String name = FileUtils.getFileName(context, uri); // You likely already have this
        String mimeType = FileUtils.getMimeType(context, uri);
        return new FileItem(context, uri, name, true, 0);
    }
}
