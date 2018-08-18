package com.ericrobertbrewer.podium.web;

import java.io.File;
import java.io.IOException;

public class FileUtils {

    /**
     * Create a new, writable file at the given directory (folder) with the given name.
     * @param folder Where the file should be created. May be relative.
     * @param fileName Of the file.
     * @return The file.
     * @throws IOException When the file already exists, cannot be created, or cannot be written to.
     */
    public static File newFile(File folder, String fileName) throws IOException {
        final File file = new File(folder, fileName);
        if (!file.createNewFile()) {
            throw new IOException("Unable to create file in `" + folder.getName() + "`.");
        }
        if (!file.canWrite() && !file.setWritable(true)) {
            throw new IOException("Unable to write to file: `" + file.getPath() + "`.");
        }
        return file;
    }

    private FileUtils() {
    }
}
