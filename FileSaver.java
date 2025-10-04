package com.codestudio.mobile;

import android.content.Context;
import android.net.Uri;

import java.io.OutputStream;

public class FileSaver {
    public static void saveFile(Context context, Uri uri, byte[] content) throws Exception {
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
            if (outputStream != null) {
                outputStream.write(content);
            } else {
                throw new Exception("Could not open output stream for URI: " + uri);
            }
        }
    }
}
