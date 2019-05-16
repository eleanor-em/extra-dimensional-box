package unimelb.bitbox.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Logger;

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
 * <li>{@link #createFileLoader(String, String)}</li>
 * <li>{@link #checkShortcut(String)}</li>
 * <li>{@link #checkWriteComplete(String)}</li>
 * <li>{@link #deleteDirectory(String)}</li>
 * <li>{@link #deleteFile(String, long)}</li>
 * <li>{@link #dirNameExists(String)}</li>
 * <li>{@link #fileNameExists(String)}</li>
 * <li>{@link #fileNameExists(String, String)}</li>
 * <li>{@link #generateSyncEvents()}</li>
 * <li>{@link #isSafePathName(String)}</li>
 * <li>{@link #readFile(String, long, long)}</li>
 * <li>{@link #makeDirectory(String)}</li>
 * <li>{@link #modifyFileLoader(String, String, long)}</li>
 * <li>{@link #writeFile(String, ByteBuffer, long)}</li>
 * @author Aaron Harwood
 * @author Andrew Linxi Wang (contributions to Windows compatibility)
 */
public class FileSystemManager extends Thread {
	private static Logger log = Logger.getLogger(FileSystemManager.class.getName());
	
	/**
	 * The special suffix on file names for loading files. Any files in the
	 * share directory with this suffix will never generate file system events,
	 * as they are ignored by the file system monitor.
	 */
	public final String loadingSuffix = "(bitbox)";
	
	/**
	 * Possible file system events.
	 * <li>{@link #FILE_CREATE}</li>
	 * <li>{@link #FILE_DELETE}</li>
	 * <li>{@link #FILE_MODIFY}</li>
	 * <li>{@link #DIRECTORY_CREATE}</li>
	 * <li>{@link #DIRECTORY_DELETE}</li>
	 */
	public enum EVENT {
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
		public String path;
		/**
		 * The name (not including any path) of the file/directory.
		 */
		public String name;
		/**
		 * The pathname of the file/directory, <i>relative</i> to the share
		 * directory.
		 */
		public String pathName;
		/**
		 * The type of this event. See {@link EVENT}.
		 */
		public EVENT event;
		/**
		 * Additional information for the file/directory.
		 */
		public FileDescriptor fileDescriptor;
		
		/**
		 * Constructor for file events.
		 * @param path The path to the file, including the share directory.
		 * @param name The name of the file, excluding its path.
		 * @param event The type of event.
		 * @param fileDescriptor The associated file descriptor for the file.
		 */
		public FileSystemEvent(String path, String name, EVENT event, FileDescriptor fileDescriptor) {
			this.path=path;
			this.name=name;
			this.fileDescriptor = fileDescriptor;
			pathName=path+FileSystems.getDefault().getSeparator()+name;
			pathName=pathName.substring(root.length()+1);
			this.event=event;
		}
		
		/**
		 * Constructor for directory events.
		 * @param path The path to the directory, including the share directory.
		 * @param name The name of the directory.
		 * @param event The type of event.
		 */
		public FileSystemEvent(String path, String name, EVENT event) {
			this.path=path;
			this.name=name;
			pathName=path+FileSystems.getDefault().getSeparator()+name;
			pathName=pathName.substring(root.length()+1);
			this.event=event;
		}
		
		public String toString() {
			return event.name()+" " +pathName;
		}
	}
	
	/**
	 * Additional information about a given file.
	 * 
	 */
	public class FileDescriptor {
		/**
		 * Timestamp of the last modification time of the file.
		 */
		public long lastModified;
		/**
		 * The MD5 hash of the file's content.
		 */
		public String md5;
		/**
		 * The size of the file in bytes.
		 */
		public long fileSize;
		
		/**
		 * Constructor
		 * @param lastModified the timestamp for when file was last modified
		 * @param md5 the current MD5 hash of the file's content.
		 */
		public FileDescriptor(long lastModified, String md5, long fileSize) {
			this.lastModified=lastModified;
			this.md5=md5;
			this.fileSize=fileSize;
		}

		/**
		 * Provide the {@link #Document} for this object.
		 */
		public Document toDoc() {
			Document doc = new Document();
			doc.append("lastModified", lastModified);
			doc.append("md5", md5);
			doc.append("fileSize", fileSize);
			return doc;
		}
	}
	
	/**
	 * Construct a new file system manager. If the supplied share directory is not a directory
	 * that exists then the constructor will return without starting the monitor thread.
	 * @param root The pathname to the root directory to share, called the share directory.
	 * @param fileSystemObserver The observer of the file system events, which must implement {@link FileSystemObserver}.
	 * @throws IOException Thrown if an initial scan of the share directory fails.
	 * @throws NoSuchAlgorithmException Thrown if the MD5 hash algorithm is not available.
	 */
	public FileSystemManager(String root, FileSystemObserver fileSystemObserver) throws IOException, NoSuchAlgorithmException{
		this.fileSystemObserver=fileSystemObserver;
		this.root=root;
		watchedFiles=new HashMap<String,FileDescriptor>();
		loadingFiles=new HashMap<String,FileLoader>();
		watchedDirectories=new HashSet<String>();
		hashMap=new HashMap<String,HashSet<String>>();
		File file = new File(root);
		if(!file.exists() || !file.isDirectory()) {
			log.severe("incorrect root given: "+root);
			return;
		}
		cannonicalRoot = file.getCanonicalPath();
		log.info("monitoring "+cannonicalRoot);
		scanDirectoryTree(root);
		log.info("starting file system monitor thread");
		start();
	}
	

	//////////////////
	// File System API
	//////////////////
	
	
	/**
	   * Returns true if the path name is "safe" to be used.
	   * Unsafe names should not be used as they may access
	   * data outside of the share directory.
	   * @param pathName The path name to test for safeness, relative
	   * to the share directory.
	   * @return boolean True if the path name is safe to use, false otherwise
	   * including if
	   * there was an IO error accessing the file system.
	   */
	public boolean isSafePathName(String pathName) {
		pathName=separatorsToSystem(pathName);
		File file = new File(root+FileSystems.getDefault().getSeparator()+pathName);
		String cannonicalName;
		try {
			cannonicalName = file.getCanonicalPath();
		} catch (IOException e) {
			log.severe(e.getMessage());
			return false;
		}
		//log.info(cannonicalName + " " + cannonicalRoot);
		return cannonicalName.startsWith(cannonicalRoot+FileSystems.getDefault().getSeparator()) &&
				cannonicalName.length()>cannonicalRoot.length()+1;
	}
	
	// directories
	
	/**
	   * Returns true if the directory name exists.
	   * @param pathName The name of the directory to test for, relative
	   * to the share directory.
	   * @return boolean True if the directory exists.
	   */
	public boolean dirNameExists(String pathName) {
		pathName=separatorsToSystem(pathName);
		synchronized(this) {
			return watchedDirectories.contains(root+FileSystems.getDefault().getSeparator()+pathName);
		}
	}
	

	/**
	   * Attempts to make a directory, the parent of the directory
	   * must already exist.
	   * @param pathName The name of the directory to make, relative 
	   * to the share directory.
	   * @return boolean True if directory was successfully made.
	   */
	public boolean makeDirectory(String pathName) {
		pathName=separatorsToSystem(pathName);
		synchronized(this) {
			File file = new File(root+FileSystems.getDefault().getSeparator()+pathName);
			return file.mkdir();
		}
	}
	
	/**
	   * Attempts to delete a directory, the directory must be
	   * empty. 
	   * @param pathName The name of the directory to delete, relative 
	   * to the share directory.
	   * @return boolean True if the directory was successfully deleted.
	   */
	public boolean deleteDirectory(String pathName) {
		pathName=separatorsToSystem(pathName);
		synchronized(this) {
			File file = new File(root+FileSystems.getDefault().getSeparator()+pathName);
			if(file.isDirectory()) {
				return file.delete();
			} else return false;
		}
	}
	
	// files
	
	/**
	   * Test if the file exists, ignoring the contents of the file.
	   * @param pathName The name of the file to test for, relative 
	   * to the share directory.
	   * @return boolean True if the file exists. In the case of
	   *  a file that is being created and currently loading, returns
	   *  false. In the case of a file that is being modified and
	   *  currently loading, returns true.
	   */
	public boolean fileNameExists(String pathName) {
		pathName=separatorsToSystem(pathName);
		synchronized(this) {
			return watchedFiles.containsKey(root+FileSystems.getDefault().getSeparator()+pathName);
		}
	}
	
	/**
	 * Test if the file exists and is has matching content.
	 * @param pathName The name of the file to test for, relative 
	   * to the share directory.
	 * @param md5 The MD5 hash of the file's contents to be matched.
	 * @return True if the file exists. In the case of
	   *  a file that is being created and currently loading, returns
	   *  false. In the case of a file that is being modified and
	   *  currently loading, returns true against the existing file.
	 */
	public boolean fileNameExists(String pathName, String md5) {
		pathName=separatorsToSystem(pathName);
		synchronized(this) {
			String fullPathName=root+FileSystems.getDefault().getSeparator()+pathName;
			return watchedFiles.containsKey(fullPathName) &&
					watchedFiles.get(fullPathName).md5.equals(md5);
		}
	}
	
	/**
	   * Attempt to delete a file. The file must exist and it must
	   * have a last modified timestamp less than or equal to that supplied.
	   * @param pathName The name of the file to delete, relative to
	   * the share directory.
	   * @param lastModified The timestamp to check against.
	   * @param md5 The MD5 hash of content to match against. 
	   * @return boolean True if the file was deleted.
	   */
	public boolean deleteFile(String pathName, long lastModified, String md5) {
		pathName=separatorsToSystem(pathName);
		synchronized(this) {
			String fullPathName=root+FileSystems.getDefault().getSeparator()+pathName;
			if(watchedFiles.containsKey(fullPathName) && (watchedFiles.get(fullPathName).lastModified<=lastModified||
					watchedFiles.get(fullPathName).md5.equals(md5))) {
				log.info("deleting "+fullPathName);
				File file = new File(fullPathName);
				if(file.isFile()) {
					return file.delete();
				} else return false;
			} else return false;
		}
	}
	
	/**
	   * Create a file loader for given file name. The file name must not
	   * already exist, otherwise use {@link #modifyFileLoader(String, String, long)}.
	   * The file loader maintains a place holder file with prefix {@link #loadingSuffix}
	   * on its filename, called a <i>loader file</i>. Such files never generate file system events. The file loader
	   * can be subsequently accessed via the given name using {@link #writeFile(String, ByteBuffer, long)},
	   * {@link #checkWriteComplete(String)} and {@link #checkShortcut(String)}.
	   * @param pathName The name of the file to create, when loading is complete, relative to
	   * the share directory.
	   * @param md5 The MD5 hash of the content that the file contents <i>must</i> match
	   * for file loading to be considered complete.
	   * @param length The expected length of the file when completed.
	   * @param lastModified The last modified timestamp to use for the file.
	   * @return boolean True if the file loader was successfully created.
	   * @throws IOException if any exceptions arose as the result of accessing the file system.
	   * @throws NoSuchAlgorithmException if the MD5 hash algorithm is not available.
	   */
	public boolean createFileLoader(String pathName, String md5, long length, long lastModified) throws NoSuchAlgorithmException, IOException {
		pathName=separatorsToSystem(pathName);
		synchronized(this) {
			String fullPathName=root+FileSystems.getDefault().getSeparator()+pathName;
			if(watchedFiles.containsKey(fullPathName)) return false;
			if(loadingFiles.containsKey(fullPathName)) return false;
			loadingFiles.put(fullPathName, new FileLoader(fullPathName,md5,length,lastModified));
		}
		return true;
	}
	
	/**
	 * Requests the file loader for the associated file name to write the supplied byte buffer
	 * at the supplied position in the loader file.
	 * @param pathName The name of the file to which the file loader is associated (no special prefix).
	 * @param src The bytes to be written.
	 * @param position The position to write the bytes.
	 * @return True if successfully written, false if there was no associated file loader for the given
	 * name.
	 * @throws IOException If there was an error writing the bytes.
	 */
	public boolean writeFile(String pathName, ByteBuffer src, long position) throws IOException {
		pathName=separatorsToSystem(pathName);
		synchronized(this) {
			String fullPathName=root+FileSystems.getDefault().getSeparator()+pathName;
			if(!loadingFiles.containsKey(fullPathName)) return false;
			loadingFiles.get(fullPathName).writeFile(src, position);
		}
		return true;
	}
	
	/**
	 * Read bytes from any file containing the matching specific content.
	 * @param md5 The MD5 hash of the content of the file to read from.
	 * @param position The position in the file to start reading from.
	 * @param length The number of bytes to read.
	 * @return A {@link java.nio.ByteBuffer} if the bytes are successfully read, otherwise null if 
	 * there was no such file with that content.
	 * @throws IOException If there were any problems accessing the file system.
	 * @throws NoSuchAlgorithmException  If the MD5 hash algorithm is unavailable.
	 */
	public ByteBuffer readFile(String md5, long position, long length) throws IOException, NoSuchAlgorithmException {
		synchronized(this) {
			if(hashMap.containsKey(md5)) {
				for(String attempt: hashMap.get(md5)) {
					try {
						File file = new File(attempt);
						log.info("reading file "+file);
						RandomAccessFile raf = new RandomAccessFile(file, "rw");
						FileChannel channel = raf.getChannel();
						FileLock lock = channel.lock();
						String currentMd5 = hashFile(file,attempt,watchedFiles.get(attempt).lastModified);
						if(currentMd5.equals(md5)) {
							ByteBuffer bb = ByteBuffer.allocate((int) length);
							channel.position(position);
							int read = channel.read(bb);
							lock.release();
							channel.close();
							raf.close();
							if(read<length) throw new IOException("did not read everything expected");
							return bb;
						}
						lock.release();
						channel.close();
						raf.close();
					} catch (IOException e) {
						// try another one
					}
				}
			}
			return null;
		}
	}
	
	/**
	 * Requests the file loader for the associated file name to check if all of the content for the file
	 * has been written. It does this by checking the MD5 hash of the written bytes to see if
	 * they match the MD5 hash of the intended file. <b>NOTE:</b> Hashing the file contents is
	 * time consuming and so this check should not be done often. If the check succeeds then
	 * the loader file is renamed to the name the file it should be (i.e. without the prefix),
	 *  and the loader is no longer accessible. If a file exists in its intended place at this point
	 *  in time then the file is deleted.
	 * @param pathName The name of the file to check if loading has completed.
	 * @return True if the file was completed, false if not and the loader is still waiting for more data.
	 * @throws NoSuchAlgorithmException If the MD5 hash algorithm is not available, the loader is no longer available in this case.
	 * @throws IOException If there was a problem accessing the file system, the loader is no longer available in this case.
	 */
	public boolean checkWriteComplete(String pathName) throws NoSuchAlgorithmException, IOException {
		pathName=separatorsToSystem(pathName);
		synchronized(this) {
			String fullPathName=root+FileSystems.getDefault().getSeparator()+pathName;
			if(!loadingFiles.containsKey(fullPathName)) return false;
			boolean check=false;
			try {
				check = loadingFiles.get(fullPathName).checkWriteComplete();
			} catch (IOException | NoSuchAlgorithmException e) {
				FileLoader fl = loadingFiles.get(fullPathName);
				loadingFiles.remove(fullPathName);
				fl.cancel();
				throw e;
			}
			if(check) {
				loadingFiles.remove(fullPathName);
			}
			return check;
		}
	}
	
	/**
	 * Should be called directly after creating a file loader, but can be called at any time.
	 * Requests the file loader to check if another file already exists with the same content,
	 * and if so, uses that file's content (i.e. does a copy) to create the intended file. The
	 * file loader is then complete and is no longer accessible.
	 * This is much faster than transferring the bytes of the file
	 * from a remote source.
	 * @param pathName The name of the file for the associated file loader.
	 * @return True if a shortcut was used, false otherwise.
	 * @throws NoSuchAlgorithmException If the MD5 hash algorithm is not available, the loader is no longer available in this case.
	 * @throws IOException If there were any errors accessing the file system, the loader is no longer available in this case.
	 */
	public boolean checkShortcut(String pathName) throws NoSuchAlgorithmException, IOException {
		pathName=separatorsToSystem(pathName);
		synchronized(this) {
			String fullPathName=root+FileSystems.getDefault().getSeparator()+pathName;
			if(!loadingFiles.containsKey(fullPathName)) return false;
			boolean check=false;
			try {
				check = loadingFiles.get(fullPathName).checkShortcut();
			} catch (IOException | NoSuchAlgorithmException e) {
				FileLoader fl = loadingFiles.get(fullPathName);
				loadingFiles.remove(fullPathName);
				fl.cancel();
				throw e;
			}
			if(check) {
				loadingFiles.remove(fullPathName);
			}
			return check;
		}
	}
	
	/**
	 * Called to create a file loader in the case when a file name already exists. The existing
	 * file must have a last modified timestamp that is less than or equal to the supplied one. See
	 * {@link #createFileLoader(String, String)} for more details about the file loader.
	 * @param pathName The name of the file to modify.
	 * @param md5 The MD5 hash of the content that the loaded file <i>must</i> have in order
	 * for the loading to complete.
	 * @param lastModified The existing file's timestamp must be less than this time stamp
	 * for the loader to be successfully created.
	 * @return True if the loader was successfully created.
	 * @throws IOException If there were any errors accessing the file system. 
	 */
	public boolean modifyFileLoader(String pathName, String md5, long lastModified) throws IOException {
		pathName=separatorsToSystem(pathName);
		synchronized(this) {
			String fullPathName=root+FileSystems.getDefault().getSeparator()+pathName;
			if(loadingFiles.containsKey(fullPathName)) return false;
			if(watchedFiles.containsKey(fullPathName) && watchedFiles.get(fullPathName).lastModified<=lastModified) {
				loadingFiles.put(fullPathName, new FileLoader(fullPathName,md5,
													watchedFiles.get(fullPathName).fileSize,
													lastModified));
			} else return false;
		}
		return true;
	}
	
	/**
	 * Cancel a file loader. Removes the file loader if present, including the loader file.
	 * No other actions are taken.
	 * @param pathName The name of the file loader, i.e. the associated file it was trying to load. 
	 * @return True if the file loader existed and was cancelled without problem, false otherwise. The loader is no longer available in any case.
	 * @throws IOException if there was a problem accessing the file system, the loader is no longer available in this case.
	 */
	public boolean cancelFileLoader(String pathName) throws IOException {
		pathName=separatorsToSystem(pathName);
		synchronized(this) {
			String fullPathName=root+FileSystems.getDefault().getSeparator()+pathName;
			if(loadingFiles.containsKey(fullPathName)) {
				boolean success = false;
				try {
					success = loadingFiles.get(fullPathName).cancel();
					loadingFiles.remove(fullPathName);
				} catch (IOException e) {
					FileLoader fl = loadingFiles.get(fullPathName);
					loadingFiles.remove(fullPathName);
					fl.cancel();
					throw e;
				}
				return success;
			}
		}
		return false;
	}
	
	// synchronization
	
	/**
	 * Typically called at the beginning of a connection, in order to ensure that
	 * the remote directory has all of the same contents as the local directory.
	 * @return A list of file system events that create the entire contents of the
	 * share directory.
	 */
	public ArrayList<FileSystemEvent> generateSyncEvents() {
		synchronized(this) {
			ArrayList<FileSystemEvent> pathevents=new ArrayList<FileSystemEvent>();
			ArrayList<String> keys = new ArrayList<String>(watchedDirectories);
			for(String pathname : keys) {
				File file = new File(pathname);
				pathevents.add(new FileSystemEvent(file.getParent(),file.getName(),EVENT.DIRECTORY_CREATE));
			}
			Collections.sort(pathevents,(arg0,arg1) ->
				{
					return arg0.path.length()-arg1.path.length();	
				}
			);
			keys = new ArrayList<String>(watchedFiles.keySet());
			for(String pathname : keys) {
				File file = new File(pathname);
				pathevents.add(new FileSystemEvent(file.getParent(),file.getName(),EVENT.FILE_CREATE, watchedFiles.get(pathname)));
			}
			return pathevents;
		}
	}
	
	////////////////////
	// Internals
	////////////////////
	
	private class FileLoader {
		private String md5;
		private long length;
		private long lastModified;
		private String pathName;
		private FileChannel channel;
		private FileLock lock; 
		private File file;
		private RandomAccessFile raf;
		public FileLoader(String pathName, String md5, long length, long lastModified) throws IOException {
			this.pathName=pathName;
			this.md5=md5;
			this.length=length;
			this.lastModified=lastModified;
			file = new File(pathName+loadingSuffix);
			if(file.exists()) throw new IOException("file loader already in progress");
			log.info("creating file "+file.getPath());
			file.createNewFile();
			raf = new RandomAccessFile(file, "rw");
			channel = raf.getChannel();
			lock = channel.lock();
		}
		
		public boolean cancel() throws IOException {
			lock.release();
			channel.close();
			raf.close();
			return file.delete();
		}
		
		public boolean checkShortcut() throws NoSuchAlgorithmException, IOException {
			// check for a shortcut
			boolean success=false;
			if(hashMap.containsKey(md5)) {
				for(String attempt: hashMap.get(md5)) {
					RandomAccessFile raf2 = null;
					FileChannel channel2 = null;
					FileLock lock2 = null;
					try {
						File file = new File(attempt);
						raf2 = new RandomAccessFile(file, "rw");
						channel2 = raf2.getChannel();
						lock2 = channel2.lock();
						String currentMd5 = hashFile(file,attempt,watchedFiles.get(attempt).lastModified);
						if(currentMd5.equals(md5)) {
							Path dest = Paths.get(pathName);
							CopyOption[] options = new CopyOption[]{
							          StandardCopyOption.REPLACE_EXISTING
							};
							InputStream is = Channels.newInputStream(channel2);
							Files.copy(is, dest, options);
				    		dest.toFile().setLastModified(lastModified);
							success=true;
							break;
						}
					} catch (IOException e) {
						e.printStackTrace(); // try another one
					}
					finally {
						if (lock2 != null) lock2.release();
						if (channel2 != null) channel2.close();
						if (raf2 != null) raf2.close();
					}
				}
			}
			if(success) {
				lock.release();
				channel.close();
				raf.close();
				file.delete();
			}
			return success;
		}
		public void writeFile(ByteBuffer src, long position) throws IOException {
			if(position>length) throw new IOException("trying to write bytes beyond what is expected");
			channel.write(src, position);
		}
		public boolean checkWriteComplete() throws NoSuchAlgorithmException, IOException {
			String currentMd5 = hashFile(file,pathName,0,raf);
			if(currentMd5.equals(md5)) {
				lock.release();
				channel.close();
				raf.close();
				File dest = new File(pathName);
				if(dest.exists()) dest.delete();
				file.renameTo(dest);
				dest.setLastModified(lastModified);
				return true;
			}
			return false;
		}
	}

	private HashSet<String> watchedDirectories;
	private HashMap<String,HashSet<String>> hashMap;
	private FileSystemObserver fileSystemObserver;
	private HashMap<String,FileDescriptor> watchedFiles;
	private String root;
	private String cannonicalRoot;
	private HashMap<String,FileLoader> loadingFiles;
	
	
	
	
	public void run() {
		ArrayList<FileSystemEvent> pathevents=new ArrayList<FileSystemEvent>();
		while (!isInterrupted()) {
			
			pathevents.clear();
			// check for new/modified files
			try {
				synchronized(this) {
					pathevents.addAll(scanDirectoryTree(root));
				}
			} catch (NoSuchAlgorithmException e1) {
				log.severe(e1.getMessage());
				interrupt();
				continue;
			} catch (IOException e1) {
				log.severe(e1.getMessage());
			}
			for(FileSystemEvent pathevent : pathevents) {
				log.info(pathevent.toString());
				fileSystemObserver.processFileSystemEvent(pathevent);
			}
			
			// check for deleted files
			pathevents.clear();
			synchronized(this) {
				ArrayList<String> keys = new ArrayList<String>(watchedFiles.keySet());
				for(String pathname : keys) {
					File file = new File(pathname);
					if(!file.exists()) {
						FileDescriptor fdes = watchedFiles.get(pathname);
						dropFile(pathname);
						pathevents.add(new FileSystemEvent(file.getParent(),file.getName(),EVENT.FILE_DELETE,fdes));
					}
				}
				
				// check for deleted directories
				keys = new ArrayList<String>(watchedDirectories);
				for(String pathname : keys) {
					File file = new File(pathname);
					if(!file.exists()) {
						dropDir(pathname);
						pathevents.add(new FileSystemEvent(file.getParent(),file.getName(),EVENT.DIRECTORY_DELETE));
					}
				}
			}
			// sort all of the events so they make sense
			Collections.sort(pathevents,(arg0,arg1) ->
				{
					return arg1.path.length()-arg0.path.length();	
				}
			);
			
			for(FileSystemEvent pathevent : pathevents) {
				log.info(pathevent.toString());
				fileSystemObserver.processFileSystemEvent(pathevent);
			}
			
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				log.warning(e.getMessage());
			}
			
		}
		
	}
	
	private String hashFile(File file,String name,long lastModified) throws NoSuchAlgorithmException, IOException {
		log.info("hashing file "+name);
		if(lastModified!=0 && lastModified==file.lastModified()) {
			return watchedFiles.get(name).md5;
		}
		MessageDigest md5Digest = MessageDigest.getInstance("MD5");
		String checksum = getFileChecksum(md5Digest, file);
		return checksum;
	}
	
	private String hashFile(File file,String name,long lastModified, RandomAccessFile raf) throws NoSuchAlgorithmException, IOException {
		log.info("hashing file "+name);
		if(lastModified!=0 && lastModified==file.lastModified()) {
			return watchedFiles.get(name).md5;
		}
		MessageDigest md5Digest = MessageDigest.getInstance("MD5");
		String checksum = getFileChecksum(md5Digest, raf);
		return checksum;
	}
	
	private ArrayList<FileSystemEvent> scanDirectoryTree(String name) throws IOException, NoSuchAlgorithmException {
		ArrayList<FileSystemEvent> pathEvents = new ArrayList<FileSystemEvent>();
		if(name.endsWith(loadingSuffix)) return pathEvents;
		File file = new File(name);
		if(file.isFile()) {
			long lastModified = file.lastModified();
			long fileSize = file.length();
			if(watchedFiles.containsKey(name)) {
				if(lastModified!=watchedFiles.get(name).lastModified) {
					String newHash = hashFile(file,name,0);
					modifyFile(name,newHash,lastModified,fileSize);
					FileSystemEvent pe = new FileSystemEvent(file.getParent(),file.getName(),EVENT.FILE_MODIFY,watchedFiles.get(name));
					pathEvents.add(pe);
				} else {
					// do nothing
				}
			} else {
				String newHash = hashFile(file,name,0);
				addFile(name,new FileDescriptor(lastModified,newHash,fileSize));
				FileSystemEvent pe = new FileSystemEvent(file.getParent(),file.getName(),EVENT.FILE_CREATE,watchedFiles.get(name));
				pathEvents.add(pe);
			}
		} else if(file.isDirectory()) {
			Path path = Paths.get(name);
			if(watchedDirectories.contains(name) || name.equals(root)) {
				// do nothing
			} else {
				addDir(name);
				pathEvents.add(new FileSystemEvent(file.getParent(),file.getName(),EVENT.DIRECTORY_CREATE));
			}
			DirectoryStream<Path> stream = Files.newDirectoryStream(path);
		    for (Path subpath: stream) {
		    	pathEvents.addAll(scanDirectoryTree(subpath.toString()));
		    }
		    stream.close();
		}
		return pathEvents;
	}
	
	private void removeHash(String name) {
		HashSet<String> hs = hashMap.get(watchedFiles.get(name).md5);
		hs.remove(name);
		if(hs.size()==0) hs.remove(watchedFiles.get(name).md5);
	}
	
	private void addHash(String md5, String name) {
		if(!hashMap.containsKey(md5)) {
			hashMap.put(md5, new HashSet<String>());
		}
		hashMap.get(md5).add(name);
	}
	
	private void modifyFile(String name, String md5, long lastModified, long fileSize) {
		log.info("modified file "+name);
		removeHash(name);
		watchedFiles.get(name).md5=md5;
		watchedFiles.get(name).lastModified=lastModified;
		watchedFiles.get(name).fileSize=fileSize;
		addHash(md5,name);
	}
	
	private void dropFile(String name) {
		log.info("dropping file "+name);
		removeHash(name);
		watchedFiles.remove(name);
	}
	
	private void addFile(String name, FileDescriptor fileDescriptor) {
		log.info("adding file "+name);
		addHash(fileDescriptor.md5,name);
		watchedFiles.put(name,fileDescriptor);
	}
	
	private void dropDir(String name) {
		log.info("dropping directory "+name);
		watchedDirectories.remove(name);
	}
	
	private void addDir(String name) {
		log.info("adding new directory "+name);
		watchedDirectories.add(name);
	}
	
	private static String getFileChecksum(MessageDigest digest, RandomAccessFile fis) throws IOException
	{
	    byte[] byteArray = new byte[1024];
	    int bytesCount = 0;
	    fis.seek(0);
	    while ((bytesCount = fis.read(byteArray)) != -1) {
	        digest.update(byteArray, 0, bytesCount);
	    };
	    byte[] bytes = digest.digest();
	    StringBuilder sb = new StringBuilder();
	    for(int i=0; i< bytes.length ;i++)
	    {
	        sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
	    }
	   return sb.toString();
	}
	
	private static String getFileChecksum(MessageDigest digest, File file) throws IOException
	{
		FileInputStream fis = new FileInputStream(file);
	    byte[] byteArray = new byte[1024];
	    int bytesCount = 0;
	    while ((bytesCount = fis.read(byteArray)) != -1) {
	        digest.update(byteArray, 0, bytesCount);
	    };
	    fis.close();
	    byte[] bytes = digest.digest();
	    StringBuilder sb = new StringBuilder();
	    for(int i=0; i< bytes.length ;i++)
	    {
	        sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
	    }
	   return sb.toString();
	}
	
	private static String separatorsToSystem(String res) {
	    if (res==null) return null;
	    if (File.separatorChar=='\\') {
	        // From Windows to Linux/Mac
	        return res.replace('/', File.separatorChar);
	    } else {
	        // From Linux/Mac to Windows
	        return res.replace('\\', File.separatorChar);
	    }
	}
}
