package com.codestudio.mobile;

import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

public class FileItem {
    public Uri uri;
    public String displayName;
    public boolean isDirectory;
    public boolean isExpanded;
    public int depth;
    public String mimeType;
    public int iconResource;

    public FileItem(Context context, Uri uri, String displayName, boolean isDirectory, int depth) {
        this.uri = uri;
        this.displayName = displayName;
        this.isDirectory = isDirectory;
        this.isExpanded = false;
        this.depth = depth;
        this.mimeType = resolveMimeType(context, uri);
        this.iconResource = R.drawable.ic_unsupported_file; // default icon, can be updated later
    }
// Inside your FileItem class (assuming it has a public field int iconResource)

    public FileItem(Uri uri, String displayName, boolean isDirectory, int depth, String mimeType) {
        this.uri = uri;
        this.displayName = displayName;
        this.isDirectory = isDirectory;
        this.isExpanded = false;
        this.depth = depth;
        this.mimeType = mimeType;
        this.iconResource = R.drawable.ic_unsupported_file;
    }

    public static String resolveMimeType(Context context, Uri uri) {
        String type = context.getContentResolver().getType(uri);
        if (type == null) {
            String url = uri.toString();
            int queryIndex = url.indexOf('?');
            if (queryIndex != -1) {
                url = url.substring(0, queryIndex);
            }
            String extension = MimeTypeMap.getFileExtensionFromUrl(url);
            if (extension != null && !extension.isEmpty()) {
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.trim().toLowerCase());
            }
        }
        return type != null ? type : "application/octet-stream";
    }

    public void updateIconResource(String mimeType) {
        if (mimeType == null) {
            this.iconResource = R.drawable.ic_unsupported_file;
        } else if (mimeType.startsWith("image/")) {
            this.iconResource = R.drawable.ic_image_file;
        } else if (mimeType.startsWith("audio/")) {
            this.iconResource = R.drawable.ic_audio_file;
        } else if (mimeType.startsWith("video/")) {
            this.iconResource = R.drawable.ic_video_file;
        } else if (mimeType.startsWith("text/") || mimeType.equals("application/json") || mimeType.equals("application/xml")) {
            this.iconResource = R.drawable.ic_text_file;
        } else {
            this.iconResource = R.drawable.ic_unsupported_file;
        }
    }
}
