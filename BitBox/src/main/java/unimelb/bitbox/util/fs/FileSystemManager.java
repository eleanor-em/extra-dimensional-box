package unimelb.bitbox.util.fs;

import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.functional.algebraic.Result;
import unimelb.bitbox.util.functional.throwing.ThrowingFunction;
import unimelb.bitbox.util.network.FileTransfer;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * A file system manager, that recursively monitors a given share directory,
 * and emits events when files and directories change. The order that events are emitted
 * in is natural in the sense that may be reproduced in the order supplied on a remote
 * system without reordering required. E.g. if a nested directory structure is deleted,
 * then the deepest deletions are always given first, up to the shallowest deletions. Similarly
 * when creating nested directories, and for files that appear in directories.
 * <br/>
 * The file system manager also provides an API
 * for safely making modifications to the files and directories in the share directory:
 * <li>{@link #cancelFileLoader(String)}</li>
 * <li>{@link #createFileLoader(FileDescriptor)}</li>
 * <li>{@link #checkShortcut(FileDescriptor)}</li>
 * <li>{@link #checkWriteComplete(FileDescriptor)}</li>
 * <li>{@link #deleteDirectory(String)}</li>
 * <li>{@link #deleteFile(FileDescriptor)}</li>
 * <li>{@link #dirNameExists(String)}</li>
 * <li>{@link #fileExists(FileDescriptor)}</li>
 * <li>{@link #fileMatches(FileDescriptor)}</li>
 * <li>{@link #generateSyncEvents()}</li>
 * <li>{@link #isSafePathName(String)}</li>
 * <li>{@link #readFile(String, long, long)}</li>
 * <li>{@link #makeDirectory(String)}</li>
 * <li>{@link #modifyFileLoader(String, String, long, long)}</li>
 * <li>{@link #writeFile(String, ByteBuffer, long)}</li>
 *
 * @author Aaron Harwood
 * @author Andrew Linxi Wang (contributions to Windows compatibility)
 * @author Eleanor McMurtry (improvements to error reporting, fix modify loader, remove old failed transfers)
 */
public final class FileSystemManager extends Thread {
    /**
     * The special suffix on file names for loading files. Any files in the
     * share directory with this suffix will never generate file system events,
     * as they are ignored by the file system monitor.
     */
    private final String loadingSuffix = "(bitbox)";

    /**
     * Possible file system events.
     * <li>{@link #FILE_CREATE}</li>
     * <li>{@link #FILE_DELETE}</li>
     * <li>{@link #FILE_MODIFY}</li>
     * <li>{@link #DIRECTORY_CREATE}</li>
     * <li>{@link #DIRECTORY_DELETE}</li>
     */
    public enum FileEventType {
        /**
         * A new file has been created. The parent directory must
         * exist for this event to be emitted.
         */
        FILE_CREATE,
        /**
         * An existing file has been deleted.
         */
        FILE_DELETE,
        /**
         * An existing file has been modified.
         */
        FILE_MODIFY,
        /**
         * A new directory has been created. The parent directory must
         * exist for this event to be emitted.
         */
        DIRECTORY_CREATE,
        /**
         * An existing directory has been deleted. The directory must
         * be empty for this event to be emitted, and its parent
         * directory must exist.
         */
        DIRECTORY_DELETE
    }

    /**
     * Describes the case when a file is
     * created/deleted/modified or when a directory is created/deleted.
     * <li>{@link #name}</li>
     * <li>{@link #path}</li>
     * <li>{@link #pathName}</li>
     * <li>{@link #event}</li>
     * <li>{@link #fileDescriptor}</li>
     */
    public class FileSystemEvent {
        /**
         * The path (not including the name) of the file/directory, including
         * the share directory.
         */
        public final String path;
        /**
         * The name (not including any path) of the file/directory.
         */
        public final String name;
        /**
         * The pathname of the file/directory, <i>relative</i> to the share
         * directory.
         */
        public final String pathName;
        /**
         * The type of this event. See {@link FileEventType}.
         */
        public final FileEventType event;
        /**
         * Additional information for the file/directory.
         */
        public final FileDescriptor fileDescriptor;

        /**
         * Constructor for file events.
         *
         * @param path           The path to the file, including the share directory.
         * @param name           The name of the file, excluding its path.
         * @param event          The type of event.
         * @param fileDescriptor The associated file descriptor for the file.
         */
        FileSystemEvent(String path, String name, FileEventType event, FileDescriptor fileDescriptor) {
            this.path = path;
            this.name = name;
            pathName = (path + FileSystems.getDefault().getSeparator() + name).substring(root.length() + 1);
            this.fileDescriptor = FileDescriptor.rename(fileDescriptor, pathName);
            this.event = event;
        }

        /**
         * Constructor for directory events.
         *
         * @param path  The path to the directory, including the share directory.
         * @param name  The name of the directory.
         * @param event The type of event.
         */
        FileSystemEvent(String path, String name, FileEventType event) {
            this.path = path;
            this.name = name;
            pathName = (path + FileSystems.getDefault().getSeparator() + name).substring(root.length() + 1);
            fileDescriptor = FileDescriptor.directory(pathName);
            this.event = event;
        }

        public String toString() {
            return event.name() + " " + pathName;
        }
    }

    /**
     * Construct a new file system manager. If the supplied share directory is not a directory
     * that exists then the constructor will return without starting the monitor thread.
     *
     * @param root               The pathname to the root directory to share, called the share directory.
     * @param fileSystemObserver The observer of the file system events, which must implement {@link FileSystemObserver}.
     * @throws IOException              Thrown if an initial scan of the share directory fails.
     */
    public FileSystemManager(String root, FileSystemObserver fileSystemObserver) throws IOException {
        this.fileSystemObserver = fileSystemObserver;
        this.root = root;
        watchedFiles = new HashMap<>();
        loadingFiles = new HashMap<>();
        watchedDirectories = new HashSet<>();
        hashMap = new HashMap<>();
        File file = new File(root);
        if (!file.exists() || !file.isDirectory()) {
            PeerServer.log().severe("incorrect root given: " + root);
            return;
        }
        canonicalRoot = file.getCanonicalPath();
        PeerServer.log().info("monitoring " + canonicalRoot);
        initialScanDirectoryTree(root);
        PeerServer.log().info("starting file system monitor thread");
        start();
    }


    //////////////////
    // File System API
    //////////////////


    /**
     * Returns true if the path name is "safe" to be used.
     * Unsafe names should not be used as they may access
     * data outside of the share directory.
     *
     * @param pathName The path name to test for safeness, relative
     *                 to the share directory.
     * @return boolean True if the path name is safe to use, false otherwise
     * including if
     * there was an IO error accessing the file system.
     */
    public boolean isSafePathName(String pathName) {
        pathName = separatorsToSystem(pathName);
        File file = new File(root + FileSystems.getDefault().getSeparator() + pathName);
        String canonicalName;
        try {
            canonicalName = file.getCanonicalPath();
        } catch (IOException e) {
            return false;
        }
        return canonicalName.startsWith(canonicalRoot + FileSystems.getDefault().getSeparator()) &&
                canonicalName.length() > canonicalRoot.length() + 1;
    }

    // directories

    /**
     * Returns true if the directory name exists.
     *
     * @param pathName The name of the directory to test for, relative
     *                 to the share directory.
     * @return boolean True if the directory exists.
     */
    public boolean dirNameExists(String pathName) {
        pathName = separatorsToSystem(pathName);
        synchronized (this) {
            return watchedDirectories.contains(root + FileSystems.getDefault().getSeparator() + pathName);
        }
    }


    /**
     * Attempts to make a directory, the parent of the directory
     * must already exist.
     *
     * @param pathName The name of the directory to make, relative
     *                 to the share directory.
     */
    public void makeDirectory(String pathName) throws FileManagerException {
        pathName = separatorsToSystem(pathName);
        synchronized (this) {
            File file = new File(root + FileSystems.getDefault().getSeparator() + pathName);
            FileManagerException.check(file.mkdir(), "Failed creating directory " + pathName);
        }
    }

    /**
     * Attempts to delete a directory, the directory must be
     * empty.
     *
     * @param pathName The name of the directory to delete, relative
     *                 to the share directory.
     */
    public void deleteDirectory(String pathName) throws FileManagerException {
        pathName = separatorsToSystem(pathName);
        synchronized (this) {
            File file = new File(root + FileSystems.getDefault().getSeparator() + pathName);
            if (file.isDirectory()) {
                FileManagerException.check(file.delete(), "Failed deleting directory " + pathName);
            } else {
                throw new FileManagerException("Path " + pathName + " is not a directory");
            }
        }
    }

    // files

    /**
     * Test if the file exists, ignoring the contents of the file.
     *
     * @return boolean True if the file exists. In the case of
     * a file that is being created and currently loading, returns
     * false. In the case of a file that is being modified and
     * currently loading, returns true.
     */
    public boolean fileExists(FileDescriptor fd) {
        String pathName = separatorsToSystem(fd.pathName);
        synchronized (this) {
            return watchedFiles.containsKey(root + FileSystems.getDefault().getSeparator() + pathName);
        }
    }

    /**
     * Test if the file exists and is has matching content.
     *
     * @return True if the file exists. In the case of
     * a file that is being created and currently loading, returns
     * false. In the case of a file that is being modified and
     * currently loading, returns true against the existing file.
     */
    public boolean fileMatches(FileDescriptor fd) {
        String pathName = separatorsToSystem(fd.pathName);
        synchronized (this) {
            String fullPathName = root + FileSystems.getDefault().getSeparator() + pathName;
            return watchedFiles.containsKey(fullPathName) &&
                    watchedFiles.get(fullPathName).md5.equals(fd.md5);
        }
    }

    /**
     * Attempt to delete a file. The file must exist and it must
     * have a last modified timestamp less than or equal to that supplied.
     */
    public void deleteFile(FileDescriptor fd) throws FileManagerException {
        String pathName = separatorsToSystem(fd.pathName);
        synchronized (this) {
            String fullPathName = root + FileSystems.getDefault().getSeparator() + pathName;
            FileManagerException.check(watchedFiles.containsKey(fullPathName), "file " + pathName + " does not exist");
            FileManagerException.check(watchedFiles.get(fullPathName).lastModified <= fd.lastModified || watchedFiles.get(fullPathName).md5.equals(fd.md5),
                                      "unexpected content for " + pathName);
            File file = new File(fullPathName);
            FileManagerException.check(file.delete(), "failed deleting " + pathName);
            PeerServer.log().info("deleting " + fullPathName);
        }
    }

    /**
     * Create a file loader for given file name. The file name must not
     * already exist, otherwise use {@link #modifyFileLoader(String, String, long, long)}.
     * The file loader maintains a place holder file with prefix {@link #loadingSuffix}
     * on its filename, called a <i>loader file</i>. Such files never generate file system events. The file loader
     * can be subsequently accessed via the given name using {@link #writeFile(String, ByteBuffer, long)},
     * {@link #checkWriteComplete(FileDescriptor)} and {@link #checkShortcut(FileDescriptor)}.
     *
     * @param fd           The file descriptor of the file to create.
     * @throws IOException              if any exceptions arose as the result of accessing the file system.
     */
    public void createFileLoader(FileDescriptor fd) throws IOException {
        String pathName = separatorsToSystem(fd.pathName);
        synchronized (this) {
            String fullPathName = root + FileSystems.getDefault().getSeparator() + pathName;
            FileManagerException.check(!watchedFiles.containsKey(fullPathName), "File " + pathName + " already exists");
            FileManagerException.check(!loadingFiles.containsKey(fullPathName), "File loader for " + pathName + " already exists");
            loadingFiles.put(fullPathName, new FileLoader(fullPathName, fd));
        }
    }

    public void createIfNotLoading(String pathName, FileDescriptor fd) throws IOException {
        pathName = separatorsToSystem(pathName);
        synchronized (this) {
            String fullPathName = root + FileSystems.getDefault().getSeparator() + pathName;
            FileManagerException.check(!watchedFiles.containsKey(fullPathName), "File " + pathName + " already exists");
            if (!loadingFiles.containsKey(fullPathName)) {
                loadingFiles.put(fullPathName, new FileLoader(fullPathName, fd));
            }
        }
    }

    /**
     * Requests the file loader for the associated file name to write the supplied byte buffer
     * at the supplied position in the loader file.
     *
     * @param pathName The name of the file to which the file loader is associated (no special prefix).
     * @param src      The bytes to be written.
     * @param position The position to write the bytes.
     * @throws IOException If there was an error writing the bytes.
     */
    public void writeFile(String pathName, ByteBuffer src, long position) throws IOException {
        pathName = separatorsToSystem(pathName);
        synchronized (this) {
            String fullPathName = root + FileSystems.getDefault().getSeparator() + pathName;
            FileManagerException.check(loadingFiles.containsKey(fullPathName), "File " + pathName + " does not exist");
            loadingFiles.get(fullPathName).writeFile(src, position);
        }
    }

    /**
     * Read bytes from any file containing the matching specific content.
     *
     * @param md5      The MD5 hash of the content of the file to read from.
     * @param position The position in the file to start reading from.
     * @param length   The number of bytes to read.
     * @return A {@link java.nio.ByteBuffer} if the bytes are successfully read, otherwise
     *         an error describing the unsuccessful state.
     */
    public Result<IOException, ByteBuffer> readFile(String md5, long position, long length) {
        return Result.of(() -> {
            synchronized (this) {
                if (hashMap.containsKey(md5)) {
                    for (String attempt : hashMap.get(md5)) {
                        File file = new File(attempt);
                        PeerServer.log().info("reading file " + file);
                        RandomAccessFile raf = new RandomAccessFile(file, "rw");
                        FileChannel channel = raf.getChannel();
                        FileLock lock = channel.lock();
                        String currentMd5 = hashFile(file, attempt, watchedFiles.get(attempt).lastModified);
                        if (currentMd5.equals(md5)) {
                            ByteBuffer bb = ByteBuffer.allocate((int) length);
                            channel.position(position);
                            int read = channel.read(bb);
                            lock.release();
                            channel.close();
                            raf.close();
                            if (read < length) throw new IOException("did not read everything expected");
                            return bb;
                        }
                        lock.release();
                        channel.close();
                        raf.close();
                    }
                }
                throw new FileManagerException("file not found");
            }
        });
    }

    /**
     * Requests the file loader for the associated file name to check if all of the content for the file
     * has been written. It does this by checking the MD5 hash of the written bytes to see if
     * they match the MD5 hash of the intended file. <b>NOTE:</b> Hashing the file contents is
     * time consuming and so this check should not be done often. If the check succeeds then
     * the loader file is renamed to the name the file it should be (i.e. without the prefix),
     * and the loader is no longer accessible. If a file exists in its intended place at this point
     * in time then the file is deleted.
     *
     * @return True if the file was completed, false if not and the loader is still waiting for more data.
     */
    public Result<IOException, Boolean> checkWriteComplete(FileDescriptor fd) {
        return check(fd.pathName, FileLoader::checkWriteComplete);
    }

    /**
     * Should be called directly after creating a file loader, but can be called at any time.
     * Requests the file loader to check if another file already exists with the same content,
     * and if so, uses that file's content (i.e. does a copy) to create the intended file. The
     * file loader is then complete and is no longer accessible.
     * This is much faster than transferring the bytes of the file
     * from a remote source.
     *
     * @return True if a shortcut was used, false otherwise.
     */
    public Result<IOException, Boolean> checkShortcut(FileDescriptor fd) {
        return check(fd.pathName, FileLoader::checkShortcut);
    }

    /**
     * Checks the file described by `pathName` with the predicate `f`.
     */
    private Result<IOException, Boolean> check(String pathName, ThrowingFunction<? super FileLoader, Boolean, ? extends IOException> f) {
        return Result.of(() -> {
            synchronized (this) {
                String fullPathName = root + FileSystems.getDefault().getSeparator() + separatorsToSystem(pathName);
                if (!loadingFiles.containsKey(fullPathName)) return false;
                boolean check;
                try {
                    check = f.apply(loadingFiles.get(fullPathName));
                } catch (IOException e) {
                    FileLoader fl = loadingFiles.get(fullPathName);
                    loadingFiles.remove(fullPathName);
                    try {
                        fl.cancel();
                    } catch (IOException e2) {
                        PeerServer.log().severe("error while trying to handle error:");
                        e2.printStackTrace();
                        PeerServer.log().severe("caused by:");
                        e.printStackTrace();
                    }
                    throw e;
                }
                if (check) {
                    loadingFiles.remove(fullPathName);
                }
                return check;
            }
        });
    }

    /**
     * Called to create a file loader in the case when a file name already exists. The existing
     * file must have a last modified timestamp that is less than or equal to the supplied one. See
     * {@link #createFileLoader(FileDescriptor)} for more details about the file loader.
     *
     * @param pathName     The name of the file to modify.
     * @param md5          The MD5 hash of the content that the loaded file <i>must</i> have in order
     *                     for the loading to complete.
     * @param lastModified The existing file's timestamp must be less than this time stamp
     *                     for the loader to be successfully created.
     * @throws IOException If there were any errors accessing the file system.
     */
    private void modifyFileLoader(String pathName, String md5, long lastModified, long newFileSize) throws IOException {
        pathName = separatorsToSystem(pathName);
        synchronized (this) {
            String fullPathName = root + FileSystems.getDefault().getSeparator() + pathName;
            FileManagerException.check(watchedFiles.containsKey(fullPathName), "File " + pathName + " does not exist");
            FileManagerException.check(!loadingFiles.containsKey(fullPathName), "File loader for " + pathName + " already exists");
            FileManagerException.check(watchedFiles.get(fullPathName).lastModified <= lastModified || watchedFiles.get(fullPathName).md5.equals(md5),
                    "Unexpected content for " + pathName);
            loadingFiles.put(fullPathName, new FileLoader(fullPathName, new FileDescriptor(pathName, lastModified, md5, newFileSize)));
        }
    }
    public void modifyFileLoader(FileDescriptor fd) throws IOException {
        modifyFileLoader(fd.pathName, fd.md5, fd.lastModified, fd.fileSize);
    }

    /**
     * Cancel a file loader. Removes the file loader if present, including the loader file.
     * No other actions are taken.
     *
     * @param pathName The name of the file loader, i.e. the associated file it was trying to load.
     * @return True if the file loader existed and was cancelled without problem, false otherwise. The loader is no longer available in any case.
     */
    public Result<IOException, Boolean> cancelFileLoader(String pathName) {
        return Result.of(() -> {
            synchronized (this) {
                String fullPathName = root + FileSystems.getDefault().getSeparator() + separatorsToSystem(pathName);
                if (loadingFiles.containsKey(fullPathName)) {
                    try {
                        loadingFiles.get(fullPathName).cancel();
                        loadingFiles.remove(fullPathName);
                    } catch (IOException e) {
                        FileLoader fl = loadingFiles.get(fullPathName);
                        loadingFiles.remove(fullPathName);
                        try {
                            fl.cancel();
                        } catch (IOException e2) {
                            PeerServer.log().severe("error occurred while trying to handle error:");
                            e2.printStackTrace();
                            PeerServer.log().severe("caused by:");
                            e.printStackTrace();
                        }
                        throw e;
                    }
                    return true;
                }
            }
            return false;
        });
    }
    public Result<IOException, Boolean> cancelFileLoader(FileTransfer ft) {
        return cancelFileLoader(ft.pathName());
    }

    // synchronization

    /**
     * Typically called at the beginning of a connection, in order to ensure that
     * the remote directory has all of the same contents as the local directory.
     *
     * @return A list of file system events that create the entire contents of the
     * share directory.
     */
    public Iterable<FileSystemEvent> generateSyncEvents() {
        synchronized (this) {
            List<FileSystemEvent> pathEvents = new ArrayList<>();
            List<String> keys = new ArrayList<>(watchedDirectories);
            for (String pathname : keys) {
                File file = new File(pathname);
                pathEvents.add(new FileSystemEvent(file.getParent(), file.getName(), FileEventType.DIRECTORY_CREATE));
            }
            pathEvents.sort(Comparator.comparingInt(arg0 -> arg0.path.length()));
            keys = new ArrayList<>(watchedFiles.keySet());
            for (String pathname : keys) {
                File file = new File(pathname);
                pathEvents.add(new FileSystemEvent(file.getParent(), file.getName(), FileEventType.FILE_CREATE, watchedFiles.get(pathname)));
            }
            return pathEvents;
        }
    }

    ////////////////////
    // Internals
    ////////////////////

    private class FileLoader {
        private final FileDescriptor fileDescriptor;
        private final String pathName;
        private final FileChannel channel;
        private final FileLock lock;
        private final File file;
        private final RandomAccessFile raf;

        FileLoader(String pathName, FileDescriptor fileDescriptor) throws IOException {
            this.pathName = pathName;
            this.fileDescriptor = fileDescriptor;
            file = new File(pathName + loadingSuffix);
            if (file.exists()) throw new IOException("file loader already in progress: " + pathName);
            PeerServer.log().info("creating file " + file.getPath());
            if (!file.createNewFile()) throw new IOException("failed to create file: "+ pathName);
            raf = new RandomAccessFile(file, "rw");
            channel = raf.getChannel();
            lock = channel.lock();
        }

        void cancel() throws IOException {
            if (file.exists()) {
                lock.release();
                channel.close();
                raf.close();
                FileManagerException.check(file.delete(), "Failed deleting file " + pathName);
            }
        }

        boolean checkShortcut() throws IOException {
            // check for a shortcut
            boolean success = false;
            if (hashMap.containsKey(fileDescriptor.md5)) {
                for (String attempt : hashMap.get(fileDescriptor.md5)) {
                    RandomAccessFile raf2 = null;
                    FileChannel channel2 = null;
                    FileLock lock2 = null;
                    try {
                        File file = new File(attempt);
                        raf2 = new RandomAccessFile(file, "rw");
                        channel2 = raf2.getChannel();
                        lock2 = channel2.lock();
                        String currentMd5 = hashFile(file, attempt, watchedFiles.get(attempt).lastModified);
                        if (currentMd5.equals(fileDescriptor.md5)) {
                            Path dest = Paths.get(pathName);
                            CopyOption[] options = { StandardCopyOption.REPLACE_EXISTING };
                            InputStream is = Channels.newInputStream(channel2);
                            Files.copy(is, dest, options);
                            FileManagerException.check(dest.toFile().setLastModified(fileDescriptor.lastModified), "Failed setting modified date of " + dest.toString());
                            success = true;
                            break;
                        }
                    } catch (IOException e) {
                        e.printStackTrace(); // try another one
                    } finally {
                        if (lock2 != null) lock2.release();
                        if (channel2 != null) channel2.close();
                        if (raf2 != null) raf2.close();
                    }
                }
            }
            if (success) {
                lock.release();
                channel.close();
                raf.close();
                if (!file.delete()) {
                    PeerServer.log().warning("Failed deleting file " + file.getPath());
                    success = false;
                }
            }
            return success;
        }

        void writeFile(ByteBuffer src, long position) throws IOException {
            FileManagerException.check(position <= fileDescriptor.fileSize, "trying to write bytes beyond what is expected for " + file.getPath());
            FileManagerException.check(file.exists(), "file deleted during transfer: " + file.getPath());
            channel.write(src, position);
        }

        boolean checkWriteComplete() throws IOException {
            String currentMd5 = hashFile(pathName, raf);
            if (currentMd5.equals(fileDescriptor.md5)) {
                lock.release();
                channel.close();
                raf.close();
                File dest = new File(pathName);
                if (dest.exists()) {
                    FileManagerException.check(dest.delete(), "failed deleting existing file " + dest.getPath());
                }
                FileManagerException.check(file.renameTo(dest), "failed renaming loading file to " + dest.getPath());
                FileManagerException.check(dest.setLastModified(fileDescriptor.lastModified), "failed setting modified date of " + dest.getPath());
                return true;
            }
            return false;
        }
    }

    private final HashSet<String> watchedDirectories;
    private final HashMap<String, HashSet<String>> hashMap;
    private final FileSystemObserver fileSystemObserver;
    private final HashMap<String, FileDescriptor> watchedFiles;
    private final String root;
    private String canonicalRoot;
    private final HashMap<String, FileLoader> loadingFiles;


    public void run() {
        List<FileSystemEvent> pathEvents = new ArrayList<>();
        while (!isInterrupted()) {

            pathEvents.clear();
            // check for new/modified files
            try {
                synchronized (this) {
                    pathEvents.addAll(scanDirectoryTree(root));
                }
            } catch (IOException e1) {
                PeerServer.log().severe(e1.getMessage());
            }
            for (FileSystemEvent pathEvent : pathEvents) {
                PeerServer.log().info(pathEvent.toString());
                fileSystemObserver.processFileSystemEvent(pathEvent);
            }

            // check for deleted files
            pathEvents.clear();
            synchronized (this) {
                List<String> keys = new ArrayList<>(watchedFiles.keySet());
                for (String pathname : keys) {
                    File file = new File(pathname);
                    if (!file.exists()) {
                        FileDescriptor fd = watchedFiles.get(pathname);
                        dropFile(pathname);
                        pathEvents.add(new FileSystemEvent(file.getParent(), file.getName(), FileEventType.FILE_DELETE, fd));
                    }
                }

                // check for deleted directories
                keys = new ArrayList<>(watchedDirectories);
                for (String pathname : keys) {
                    File file = new File(pathname);
                    if (!file.exists()) {
                        dropDir(pathname);
                        pathEvents.add(new FileSystemEvent(file.getParent(), file.getName(), FileEventType.DIRECTORY_DELETE));
                    }
                }
            }
            // sort all of the events so they make sense
            pathEvents.sort((arg0, arg1) ->
                    arg1.path.length() - arg0.path.length());

            for (FileSystemEvent pathEvent : pathEvents) {
                PeerServer.log().info(pathEvent.toString());
                fileSystemObserver.processFileSystemEvent(pathEvent);
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                PeerServer.log().warning(e.getMessage());
            }
        }
    }

    private String hashFile(File file, String name, long lastModified) throws IOException {
        PeerServer.log().info("hashing file " + name);
        if (lastModified != 0 && lastModified == file.lastModified()) {
            return watchedFiles.get(name).md5;
        }
        try {
            MessageDigest md5Digest = MessageDigest.getInstance("MD5");
            return getFileChecksum(md5Digest, file);
        } catch (NoSuchAlgorithmException e) {
            // If MD5 isn't available, we're screwed anyway.
            throw new RuntimeException(e);
        }
    }

    private String hashFile(String name, RandomAccessFile raf) throws IOException {
        PeerServer.log().info("hashing file " + name);
        try {
            MessageDigest md5Digest = MessageDigest.getInstance("MD5");
            return getFileChecksum(md5Digest, raf);
        } catch (NoSuchAlgorithmException e) {
            // If MD5 isn't available, we're screwed anyway.
            throw new RuntimeException(e);
        }

    }

    private void initialScanDirectoryTree(String name)
            throws IOException {
        scanDirectoryTree(name, true);
    }

    private Collection<FileSystemEvent> scanDirectoryTree(String name)
            throws IOException {
        return scanDirectoryTree(name, false);
    }

    private Collection<FileSystemEvent> scanDirectoryTree(String name, boolean clearFiles)
        throws IOException {
        List<FileSystemEvent> pathEvents = new ArrayList<>();
        File file = new File(name);

        // Don't add files that are loading
        if (name.endsWith(loadingSuffix)) {
            if (clearFiles) {
                if (file.delete()) {
                    PeerServer.log().info("Deleting old transfer " + file.getPath());
                } else {
                    PeerServer.log().warning("Failed deleting " + file.getPath());
                }
                watchedFiles.remove(file.getPath());
            }
        } else if (file.isFile()) {
            long lastModified = file.lastModified();
            long fileSize = file.length();
            if (watchedFiles.containsKey(name)) {
                if (lastModified != watchedFiles.get(name).lastModified) {
                    String newHash = hashFile(file, name, 0);
                    modifyFile(name, newHash, lastModified, fileSize);
                    FileSystemEvent pe = new FileSystemEvent(file.getParent(), file.getName(), FileEventType.FILE_MODIFY, watchedFiles.get(name));
                    pathEvents.add(pe);
                }
            } else {
                String newHash = hashFile(file, name, 0);
                addFile(name, new FileDescriptor(name, lastModified, newHash, fileSize));
                FileSystemEvent pe = new FileSystemEvent(file.getParent(), file.getName(), FileEventType.FILE_CREATE, watchedFiles.get(name));
                pathEvents.add(pe);
            }
        } else if (file.isDirectory()) {
            Path path = Paths.get(name);
            if (!watchedDirectories.contains(name) && !name.equals(root)) {
                addDir(name);
                pathEvents.add(new FileSystemEvent(file.getParent(), file.getName(), FileEventType.DIRECTORY_CREATE));
            }

            DirectoryStream<Path> stream = Files.newDirectoryStream(path);
            for (Path subpath : stream) {
                pathEvents.addAll(scanDirectoryTree(subpath.toString(), clearFiles));
            }
            stream.close();
        }
        return pathEvents;
    }

    private void removeHash(String name) {
        Set<String> hs = hashMap.get(watchedFiles.get(name).md5);
        hs.remove(name);
        if (!hs.isEmpty()) hs.remove(watchedFiles.get(name).md5);
    }

    private void addHash(String md5, String name) {
        if (!hashMap.containsKey(md5)) {
            hashMap.put(md5, new HashSet<>());
        }
        hashMap.get(md5).add(name);
    }

    private void modifyFile(String name, String md5, long lastModified, long fileSize) {
        PeerServer.log().info("modified file " + name);
        removeHash(name);
        watchedFiles.get(name).md5 = md5;
        watchedFiles.get(name).lastModified = lastModified;
        watchedFiles.get(name).fileSize = fileSize;
        addHash(md5, name);
    }

    private void dropFile(String name) {
        PeerServer.log().info("dropping file " + name);
        removeHash(name);
        watchedFiles.remove(name);
    }

    private void addFile(String name, FileDescriptor fileDescriptor) {
        PeerServer.log().info("adding file " + name);
        addHash(fileDescriptor.md5, name);
        watchedFiles.put(name, fileDescriptor);
    }

    private void dropDir(String name) {
        PeerServer.log().info("dropping directory " + name);
        watchedDirectories.remove(name);
    }

    private void addDir(String name) {
        PeerServer.log().info("adding new directory " + name);
        watchedDirectories.add(name);
    }

    private static String getFileChecksum(MessageDigest digest, RandomAccessFile fis) throws IOException {
        byte[] byteArray = new byte[1024];
        int bytesCount;
        fis.seek(0);
        while ((bytesCount = fis.read(byteArray)) != -1) {
            digest.update(byteArray, 0, bytesCount);
        }
        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte aByte : bytes) {
            sb.append(Integer.toString((aByte & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    private static String getFileChecksum(MessageDigest digest, File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        byte[] byteArray = new byte[1024];
        int bytesCount;
        while ((bytesCount = fis.read(byteArray)) != -1) {
            digest.update(byteArray, 0, bytesCount);
        }
        fis.close();
        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte aByte : bytes) {
            sb.append(Integer.toString((aByte & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    private static String separatorsToSystem(String res) {
        assert res != null;
        // From Windows to Linux/Mac
        // From Linux/Mac to Windows
        return File.separatorChar == '\\'
             ? res.replace('/', File.separatorChar)
             : res.replace('\\', File.separatorChar);
    }
}