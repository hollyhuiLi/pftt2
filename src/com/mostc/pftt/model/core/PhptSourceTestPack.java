package com.mostc.pftt.model.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.github.mattficken.io.StringUtil;
import com.mostc.pftt.host.AHost;
import com.mostc.pftt.host.LocalHost;
import com.mostc.pftt.main.Config;
import com.mostc.pftt.model.SourceTestPack;
import com.mostc.pftt.results.ConsoleManager;
import com.mostc.pftt.results.EPrintType;
import com.mostc.pftt.results.ITestResultReceiver;
import com.mostc.pftt.results.PhpResultPackWriter;

/** manages a test-pack of PHPT tests
 * 
 * @author Matt Ficken
 *
 */

public class PhptSourceTestPack implements SourceTestPack<PhptActiveTestPack, PhptTestCase> {
	// CRITICAL: on Windows, must use \\ not /
	//    -some tests fail b/c the path to php will have / in it, which it can't execute via `shell_exec`
	protected String test_pack;
	protected File test_pack_file;
	protected AHost host;
	protected final LinkedList<File> non_phpt_files;
	protected final HashMap<String,PhptTestCase> test_cases_by_name;
	protected SoftReference<ArrayList<PhptTestCase>> _ref_test_cases;
	
	public PhptSourceTestPack(String test_pack) {
		this.test_pack_file = new File(test_pack);
		this.test_pack = this.test_pack_file.getAbsolutePath();
		
		test_cases_by_name = new HashMap<String,PhptTestCase>();
		non_phpt_files = new LinkedList<File>();
	}
	
	@Override
	public String toString() {
		return getSourceDirectory();
	}
	
	public boolean open(ConsoleManager cm, AHost host) {
		if (StringUtil.endsWithIC(this.test_pack, ".zip")) {
			// automatically decompress build
			String zip_file = test_pack;
			this.test_pack = host.uniqueNameFromBase(AHost.removeFileExt(test_pack));
				
			if (!host.unzip(cm, zip_file, test_pack))
				return false;
		}
		
		this.host = host;
		this.test_pack = host.fixPath(test_pack);
		return host.exists(this.test_pack);
	}
	
	@Override
	public String getSourceDirectory() {
		return test_pack;
	}
	
	/** cleans up this test-pack from previous runs of PFTT or run-test.php that were interrupted
	 * or that otherwise failed to cleanup after themselves (deleting temporary directories, etc...)
	 * 
	 * This is important. Otherwise tests that create temporary directories, etc... but fail to delete
	 * them (can happen because of several reasons), can cause future test runs to fail if the
	 * test pack is run in place (its faster to cleanup than to copy the test pack, which is only really
	 * needed for remote file system scenarios).
	 * 
	 */
	@Override
	public void cleanup(ConsoleManager cm) {
		cm.println(EPrintType.IN_PROGRESS, getClass(), "cleaning source-test-pack from previous PFTT or run-test.php run");
		// these are symlinks(junctions) which may cause an infinite loop
		//
		// normally, they are deleted, but if certain tests were interrupted, they may still be there
		host.deleteIfExists(test_pack+"/ext/standard/tests/file/12345");
		host.deleteIfExists(test_pack+"/ext/standard/tests/file/clearstatcache_001.php_link1");
		host.deleteIfExists(test_pack+"/ext/standard/tests/file/clearstatcache_001.php_link2");
		host.deleteIfExists(test_pack+"/ext/standard/tests/file/copy_variation15");
		host.deleteIfExists(test_pack+"/ext/standard/tests/file/is_dir_variation2_symlink");
		host.deleteIfExists(test_pack+"/ext/standard/tests/file/mkdir");
		host.deleteIfExists(test_pack+"/ext/standard/tests/file/mkdir_variation2");
		host.deleteIfExists(test_pack+"/ext/standard/tests/file/realpath_basic");
		host.deleteIfExists(test_pack+"/ext/standard/tests/file/rename_variation");
		host.deleteIfExists(test_pack+"/ext/standard/tests/file/rename_variation.tmp");
		host.deleteIfExists(test_pack+"/ext/standard/tests/file/rename_variation_dir");
		host.deleteIfExists(test_pack+"/ext/standard/tests/file/rename_variation_link.tmp");
		host.deleteIfExists(test_pack+"/ext/standard/tests/file/symlink_link_linkinfo_is_link_basic1");
		host.deleteIfExists(test_pack+"/ext/standard/tests/file/symlink_link_linkinfo_is_link_basic2");
		host.deleteIfExists(test_pack+"/ext/standard/tests/file/symlink_link_linkinfo_is_link_variation7");
		host.deleteIfExists(test_pack+"/ext/standard/tests/file/symlink_link_linkinfo_is_link_variation9");
		host.deleteIfExists(test_pack+"/ext/standard/tests/file/unlink_variation1");
		host.deleteIfExists(test_pack+"/ext/standard/tests/file/windows_links/directory");
		host.deleteIfExists(test_pack+"/ext/standard/tests/file/windows_links/mounted_volume");
		host.deleteIfExists(test_pack+"/ext/standard/tests/file/windows_links/mnt");
		host.deleteIfExists(test_pack+"/tests/security/globtest1");
		host.deleteIfExists(test_pack+"/tests/security/globtest2");
		host.deleteIfExists(test_pack+"/ext/zip/tests/51353_unpack");
		
		// clean out these files from the test-pack so they don't have to be stored or copied (to other hosts), etc...
		// (when installing test-pack on another host, it should just copy all files so its identical, less variability.
		//  also this improves performance for the installation process)
		host.deleteFileExtension(test_pack, ".skip.php");
		host.deleteFileExtension(test_pack, ".cmd");
		host.deleteFileExtension(test_pack, ".sh");
		// don't delete .php (specifically run-test.php) in root of test-pack (user may want it later)
		host.deleteFileExtension(test_pack+"/ext/bcmath", ".php");
		host.deleteFileExtension(test_pack+"/ext/bz2", ".php");
		host.deleteFileExtension(test_pack+"/ext/calendar", ".php");
		host.deleteFileExtension(test_pack+"/ext/com_dotnet", ".php");
		host.deleteFileExtension(test_pack+"/ext/ctype", ".php");
		host.deleteFileExtension(test_pack+"/ext/curl", ".php");
		host.deleteFileExtension(test_pack+"/ext/date", ".php");
		host.deleteFileExtension(test_pack+"/ext/dba", ".php");
		host.deleteFileExtension(test_pack+"/ext/dom", ".php");
		host.deleteFileExtension(test_pack+"/ext/enchant", ".php");
		host.deleteFileExtension(test_pack+"/ext/ereg", ".php");
		host.deleteFileExtension(test_pack+"/ext/exif", ".php");
		host.deleteFileExtension(test_pack+"/ext/fileinfo", ".php");
		host.deleteFileExtension(test_pack+"/ext/filter", ".php");
		host.deleteFileExtension(test_pack+"/ext/ftp", ".php");
		host.deleteFileExtension(test_pack+"/ext/gd", ".php");
		host.deleteFileExtension(test_pack+"/ext/gettext", ".php");
		host.deleteFileExtension(test_pack+"/ext/gmp", ".php");
		host.deleteFileExtension(test_pack+"/ext/hash", ".php");
		host.deleteFileExtension(test_pack+"/ext/iconv", ".php");
		host.deleteFileExtension(test_pack+"/ext/imap", ".php");
		host.deleteFileExtension(test_pack+"/ext/interbase", ".php");
		host.deleteFileExtension(test_pack+"/ext/intl", ".php");
		host.deleteFileExtension(test_pack+"/ext/json", ".php");
		host.deleteFileExtension(test_pack+"/ext/ldap", ".php");
		host.deleteFileExtension(test_pack+"/ext/libxml", ".php");
		host.deleteFileExtension(test_pack+"/ext/mbstring", ".php");
		host.deleteFileExtension(test_pack+"/ext/mcrypt", ".php");
		host.deleteFileExtension(test_pack+"/ext/mysql", ".php");
		host.deleteFileExtension(test_pack+"/ext/mysqli", ".php");
		host.deleteFileExtension(test_pack+"/ext/oci8", ".php");
		host.deleteFileExtension(test_pack+"/ext/odbc", ".php");
		host.deleteFileExtension(test_pack+"/ext/opcache", ".php");
		host.deleteFileExtension(test_pack+"/ext/openssl", ".php");
		host.deleteFileExtension(test_pack+"/ext/pcntl", ".php");
		host.deleteFileExtension(test_pack+"/ext/pcre", ".php");
		host.deleteFileExtension(test_pack+"/ext/pdo", ".php");
		host.deleteFileExtension(test_pack+"/ext/pdo_dblib", ".php");
		host.deleteFileExtension(test_pack+"/ext/pdo_firebird", ".php");
		host.deleteFileExtension(test_pack+"/ext/pdo_mysql", ".php");
		host.deleteFileExtension(test_pack+"/ext/pdo_oci", ".php");
		host.deleteFileExtension(test_pack+"/ext/pdo_odbc", ".php");
		host.deleteFileExtension(test_pack+"/ext/pdo_pgsql", ".php");
		host.deleteFileExtension(test_pack+"/ext/pdo_sqlite", ".php");
		host.deleteFileExtension(test_pack+"/ext/pgsql", ".php");
		// CRITICAL: don't delete .php inside phar. it uses those files for tests (especially in ext/phar/tests/files/)
		//    @see ext/phar/tests/fatal_error_web_phar
		//host.deleteFileExtension(test_pack+"/ext/phar", ".php");
		host.deleteFileExtension(test_pack+"/ext/posix", ".php");
		host.deleteFileExtension(test_pack+"/ext/pspell", ".php");
		host.deleteFileExtension(test_pack+"/ext/readline", ".php");
		host.deleteFileExtension(test_pack+"/ext/reflection", ".php");
		host.deleteFileExtension(test_pack+"/ext/session", ".php");
		host.deleteFileExtension(test_pack+"/ext/shmop", ".php");
		host.deleteFileExtension(test_pack+"/ext/simplexml", ".php");
		host.deleteFileExtension(test_pack+"/ext/skeleton", ".php");
		host.deleteFileExtension(test_pack+"/ext/snmp", ".php");
		host.deleteFileExtension(test_pack+"/ext/soap", ".php");
		host.deleteFileExtension(test_pack+"/ext/sockets", ".php");
		host.deleteFileExtension(test_pack+"/ext/spl", ".php");
		host.deleteFileExtension(test_pack+"/ext/sqlite3", ".php");
		host.deleteFileExtension(test_pack+"/ext/standard", ".php");
		host.deleteFileExtension(test_pack+"/ext/sybase_ct", ".php");
		host.deleteFileExtension(test_pack+"/ext/sysvmsg", ".php");
		host.deleteFileExtension(test_pack+"/ext/sysvsem", ".php");
		host.deleteFileExtension(test_pack+"/ext/sysvshm", ".php");
		host.deleteFileExtension(test_pack+"/ext/tidy", ".php");
		host.deleteFileExtension(test_pack+"/ext/tokenizer", ".php");
		host.deleteFileExtension(test_pack+"/ext/wddx", ".php");
		host.deleteFileExtension(test_pack+"/ext/xml", ".php");
		host.deleteFileExtension(test_pack+"/ext/xmlreader", ".php");
		host.deleteFileExtension(test_pack+"/ext/xmlrpc", ".php");
		host.deleteFileExtension(test_pack+"/ext/xmlwriter", ".php");
		host.deleteFileExtension(test_pack+"/ext/xsl", ".php");
		host.deleteFileExtension(test_pack+"/ext/zip", ".php");
		host.deleteFileExtension(test_pack+"/ext/zlib", ".php");
		host.deleteFileExtension(test_pack+"/tests", ".php");
		host.deleteFileExtension(test_pack+"/zend", ".php");
		host.deleteFileExtension(test_pack+"/sapi", ".php");
	}
	
	@Override
	public void read(Config config, List<PhptTestCase> test_cases, List<String> names, ConsoleManager cm, PhpResultPackWriter twriter, PhpBuild build) throws FileNotFoundException, IOException, Exception {
		read(config, test_cases, names, cm, twriter, build, false);
	}
	
	@Override
	public void read(Config config, List<PhptTestCase> test_cases, List<String> names, ConsoleManager cm, PhpResultPackWriter twriter, PhpBuild build, boolean ignore_missing) throws FileNotFoundException, IOException, Exception {
		//
		ArrayList<PhptTestCase> _test_cases;
		if (_ref_test_cases!=null) {
			_test_cases = _ref_test_cases.get();
			if (_test_cases!=null) {
				test_cases.addAll(_test_cases);
				return;
			}
		}
		//
		config.processPHPTTestPack(this, twriter, build);
		// normalize name fragments
		if (names.size()>0){
			ArrayList<String> normal_names = new ArrayList<String>(names.size());
			for ( String name : names )
				normal_names.add(PhptTestCase.normalizeTestCaseName(name));
			names = normal_names;
		}
		
		// TODO should only return test cases matching names, but load and cache all of them
		//     (if no names, return all test cases)
		LinkedList<PhptTestCase> redirect_targets = new LinkedList<PhptTestCase>();
		
		// (some) names may be exact names of tests, check for those first
		Iterator<String> name_it = names.iterator();
		String name;
		File file;
		PhptTestCase test_case;
		while (name_it.hasNext()) {
			name = name_it.next();
			
			if (name.endsWith(PhptTestCase.PHPT_FILE_EXTENSION)) {
				file = new File(test_pack_file, host.fixPath(name));
				if (file.exists()) {
					// String is exact name of test
					
					test_case = PhptTestCase.load(host, this, name, twriter);
					
					add_test_case(config, test_case, test_cases, names, cm, twriter, build, null, redirect_targets);
					
					// don't need to search for it
					name_it.remove();
				}
			}
		}
		
		if (names.size() > 0) {
			// assume any remaining names are name fragments and search for tests with matching names
			
			add_test_files(config, test_pack_file.listFiles(), test_cases, names, cm, twriter, build, null, redirect_targets);
		}
		
		if (!ignore_missing && names.size() > 0) {
			// one or more test names not matched to an actual test
			throw new FileNotFoundException(names.toString());
		}

		// sort alphabetically
		Collections.sort(test_cases, new Comparator<PhptTestCase>() {
				@Override
				public int compare(PhptTestCase o1, PhptTestCase o2) {
					return o2.getName().compareTo(o2.getName());
				}
			});
		
		//
		// cache for use next time
		_test_cases = new ArrayList<PhptTestCase>(test_cases.size());
		_test_cases.addAll(test_cases);
		_ref_test_cases = new SoftReference<ArrayList<PhptTestCase>>(_test_cases);
		//
	} // end public void read

	@Override
	public void read(Config config, List<PhptTestCase> test_cases, ConsoleManager cm, ITestResultReceiver twriter, PhpBuild build) throws FileNotFoundException, IOException, Exception {
		//
		ArrayList<PhptTestCase> _test_cases;
		if (_ref_test_cases!=null) {
			_test_cases = _ref_test_cases.get();
			if (_test_cases!=null) {
				test_cases.addAll(_test_cases);
				return;
			}
		}
		//
		
		config.processPHPTTestPack(this, (PhpResultPackWriter)twriter, build);
		
		test_pack_file = new File(test_pack);
		test_pack = test_pack_file.getAbsolutePath(); // normalize path
		add_test_files(config, test_pack_file.listFiles(), test_cases, null, cm, twriter, build, null, new LinkedList<PhptTestCase>());
		
		//
		// cache for use next time
		_test_cases = new ArrayList<PhptTestCase>(test_cases.size());
		_test_cases.addAll(test_cases);
		_ref_test_cases = new SoftReference<ArrayList<PhptTestCase>>(_test_cases);
	}
	
	private void add_test_files(Config config, File[] files, List<PhptTestCase> test_files, List<String> names, ConsoleManager cm, ITestResultReceiver twriter, PhpBuild build, PhptTestCase redirect_parent, List<PhptTestCase> redirect_targets) throws FileNotFoundException, IOException, Exception {
		if (files==null)
			return;
		main_loop:
		for ( File f : files ) {
			if (f.getName().toLowerCase().endsWith(PhptTestCase.PHPT_FILE_EXTENSION)) {
				if (names!=null) {
					boolean match = false;
					for(String name: names) {
						if (PhptTestCase.normalizeTestCaseName(f.getPath()).contains(name)) {
							match = true;
							break;
						}
					}
					// test doesn't match any name, ignore it
					if (!match)
						continue main_loop;
				}
					
				String test_name = f.getAbsolutePath().substring(test_pack.length());
				if (test_name.startsWith("/") || test_name.startsWith("\\"))
					test_name = test_name.substring(1);
				
				PhptTestCase test_case = PhptTestCase.load(host, this, false, test_name, twriter, redirect_parent);
				
				add_test_case(config, test_case, test_files, names, cm, twriter, build, redirect_parent, redirect_targets);
			} else if (f.isFile()) {
				String n = f.getName().toLowerCase();
				if (!(n.endsWith(".sh") && n.endsWith(".php") && n.endsWith(".diff") && n.endsWith(".out") && n.endsWith(".exp") && n.endsWith(".cmd") && n.endsWith(".stdin"))) {
					// ignore these files. they may be left over if the user ran run-test.php or PFTT and aren't actually used for testing
					// 
					// test files we need are usually .inc but may also be .db... may be others (especially in future)
					// have to copy them all just in case they are needed
					if (!non_phpt_files.contains(f))
						non_phpt_files.add(f);
				}
			}
			add_test_files(config, f.listFiles(), test_files, names, cm, twriter, build, redirect_parent, redirect_targets);
		}
	}
	
	private void add_test_case(Config config, PhptTestCase test_case, List<PhptTestCase> test_cases, List<String> names, ConsoleManager cm, ITestResultReceiver twriter, PhpBuild build, PhptTestCase redirect_parent, List<PhptTestCase> redirect_targets) throws FileNotFoundException, IOException, Exception {
		if (cm.getMaxTestReadCount() > 0 && test_cases_by_name.size() >= cm.getMaxTestReadCount())
			return;
		
		test_cases_by_name.put(test_case.getName(), test_case);
		
		if (test_case.containsSection(EPhptSection.REDIRECTTEST)) {
			if (build==null || redirect_parent!=null) {
				// ignore the test
			} else {
				// execute php code in the REDIRECTTEST section to get the test(s) to load
				for ( String target_test_name : test_case.readRedirectTestNames(cm, host, build) ) {
					
					// test may actually be a directory => load all the PHPT tests from that directory
					File dir = new File(test_pack+host.dirSeparator()+target_test_name);
					if (dir.isDirectory()) {
						// add all PHPTs in directory 
						add_test_files(config, dir.listFiles(), test_cases, names, cm, twriter, build, redirect_parent, redirect_targets);
						
					} else {
						// test refers to a specific test, load it
						try {
						test_case = PhptTestCase.load(host, this, false, target_test_name, twriter, redirect_parent);
						} catch ( Exception ex ) {
							ex.printStackTrace();
							continue; // TODO
						}
						
						if (redirect_targets.contains(test_case))
							// can only have 1 level of redirection
							return;
						test_cases_by_name.put(test_case.getName(), test_case);
						
						redirect_targets.add(test_case);
						
						config.processPHPT(test_case);
						test_cases.add(test_case);
						
						if (cm.getMaxTestReadCount() > 0 && test_cases_by_name.size() >= cm.getMaxTestReadCount())
							return;
					}
				}
			}
		} else {
			if (redirect_parent!=null) {
				if (redirect_targets.contains(test_case))
					return;
				// can only have 1 level of redirection
				redirect_targets.add(test_case);
			}
			
			config.processPHPT(test_case);
			test_cases.add(test_case);
		}
	}
	
	/** returns the contents of the named file from this test pack
	 * 
	 * @param host
	 * @param name
	 * @return
	 * @throws IOException
	 */
	public String getContents(AHost host, String name) throws IOException {
		return host.getContentsDetectCharset(new File(test_pack_file, name).getAbsolutePath(), PhptTestCase.newCharsetDeciderDecoder());
	}
	
	/** installs the test-pack in its source location (so its not copied or uploaded or downloaded anywhere)
	 * 
	 * @return
	 */
	@Override
	public PhptActiveTestPack installInPlace(ConsoleManager cm, AHost host) {
		return new PhptActiveTestPack(this.getSourceDirectory(), this.getSourceDirectory());
	}

	/** installs this test pack into to the given location to actively run it from.
	 * 
	 * location can be local or remote.
	 * 
	 * @see #read - must have already been called
	 * @param cm
	 * @param host
	 * @param test_pack_dir
	 * @param remote_test_pack_dir 
	 * @return
	 * @throws IllegalStateException
	 * @throws IOException
	 * @throws Exception
	 */
	@Override
	public PhptActiveTestPack install(ConsoleManager cm, AHost host, String local_test_pack_dir, String remote_test_pack_dir) throws IllegalStateException, IOException, Exception {
		cm.println(EPrintType.IN_PROGRESS, getClass(), "install test-pack local="+local_test_pack_dir+" remote="+remote_test_pack_dir+" "+this.host.getClass()+" "+host.getClass());
		if (!this.host.isRemote() || this.host.equals(host)) {
			// installing from local host to remote host OR from remote|local host to itself
			host.uploadCompressWith7Zip(cm, getClass(), test_pack, this.host, remote_test_pack_dir);
		} else if (!host.isRemote()) {
			// installing from remote host to local host
			host.download7ZipFileAndDecompress(cm, getClass(), test_pack, this.host, remote_test_pack_dir);
		} else {
			// installing from 1 remote host(src) to a different remote host (dst)
			LocalHost local_host = new LocalHost();
			
			// decide file names
			String local_7zip_file = local_host.mktempname(getClass(), ".7z");
			String src_7zip_file = this.host.mktempname(getClass(), ".7z");
			String dst_7zip_file = host.mktempname(getClass(), ".7z");
			
			// compress and download/upload and decompress
			this.host.compress(cm, host, test_pack, src_7zip_file);
			this.host.download(src_7zip_file, local_7zip_file);
			host.upload(local_7zip_file, dst_7zip_file);
			host.decompress(cm, this.host, dst_7zip_file, remote_test_pack_dir);
			
			// cleanup
			this.host.delete(dst_7zip_file);
			host.delete(src_7zip_file);
			local_host.delete(local_7zip_file);
		}
		return new PhptActiveTestPack(test_pack, local_test_pack_dir);
	} // end public PhptActiveTestPack install
	
	/** installs only the named test cases from test pack into to the given location to actively run it from.
	 * @param host
	 * @param test_cases
	 * @param test_pack_dir
	 * 
	 * @see #read - must have already been called
	 * @return
	 * @throws IllegalStateException
	 * @throws IOException
	 * @throws Exception
	 */
	public PhptActiveTestPack installNamed(ConsoleManager cm, AHost host, String test_pack_dir, List<PhptTestCase> test_cases) throws IllegalStateException, IOException, Exception {
		if (!this.host.isRemote() || this.host.equals(host)) {
			// installing from local host to remote host OR from remote host to itself
			uploadNonTestCaseFiles(host, test_pack, test_pack_dir);
			for ( PhptTestCase test_case : test_cases )
				host.upload(test_pack+"/"+test_case.getName(), test_pack_dir+"/"+test_case.getName());
		} else if (!host.isRemote()) {
			// installing from remote host to local host
			downloadNonTestCaseFiles(host, test_pack, test_pack_dir);
			for ( PhptTestCase test_case : test_cases )
				host.download(test_pack+"/"+test_case.getName(), test_pack_dir+"/"+test_case.getName());
		} else {
			// installing from 1 remote host to a different remote host
			LocalHost local_host = new LocalHost();
			String local_dir = local_host.mktempname(getClass());
			downloadNonTestCaseFiles(this.host, test_pack, test_pack_dir);
			for ( PhptTestCase test_case : test_cases )
				this.host.download(test_pack+"/"+test_case.getName(), local_dir+"/"+test_case.getName());
			uploadNonTestCaseFiles(host, local_dir, test_pack_dir);
			for ( PhptTestCase test_case : test_cases )
				host.upload(local_dir+"/"+test_case.getName(), test_pack_dir+"/"+test_case.getName());
			local_host.delete(local_dir);
		}
		return new PhptActiveTestPack(test_pack, test_pack_dir);
	}

	protected void uploadNonTestCaseFiles(AHost dst_host, String src_dir, String dst_dir) throws IllegalStateException, IOException, Exception {
		String remote_name;
		for ( File f : non_phpt_files ) {
			remote_name = AHost.pathFrom(test_pack, f.getAbsolutePath());
			
			dst_host.upload(f.getAbsolutePath(), dst_dir+"/"+remote_name);
		}
	}

	protected void downloadNonTestCaseFiles(AHost dst_host, String src_dir, String dst_dir) throws IllegalStateException, IOException, Exception {
		String remote_name;
		for ( File f : non_phpt_files ) {
			remote_name = AHost.pathFrom(test_pack, f.getAbsolutePath());
			
			dst_host.download(src_dir+"/"+remote_name, dst_dir+"/"+remote_name);
		}
	}

	/** gets the branch of this test-pack
	 * 
	 * @return
	 */
	public EBuildBranch getVersionBranch() {
		String dir = AHost.basename(test_pack);
		if (dir.contains("5.4")||dir.contains("5-4")||dir.contains("5_4")||dir.contains("54"))
			return EBuildBranch.PHP_5_4;
		else if (dir.contains("5.3")||dir.contains("5-3")||dir.contains("5_3")||dir.contains("53"))
			return EBuildBranch.PHP_5_3;
		else if (dir.contains("5.5")||dir.contains("5-5")||dir.contains("5_5")||dir.contains("55"))
			return EBuildBranch.PHP_5_5;
		else if (dir.contains("5.6")||dir.contains("5-6")||dir.contains("5_6")||dir.contains("56"))
			return EBuildBranch.PHP_5_6;
		else if (dir.toLowerCase().contains("master"))
			return EBuildBranch.PHP_Master;
		else
			return null;
	}

	/** gets the revision number of this test-pack
	 * 
	 * @return
	 */
	public String getVersion() {
		String[] split = AHost.basename(test_pack).split("[\\.|\\-]");
		return split.length==0?null:split[split.length-1];
	}

	/** returns test case by name
	 * 
	 * @see #read - must have already been called
	 * @param name
	 * @return
	 */
	public PhptTestCase getByName(String name) {
		PhptTestCase test_case = test_cases_by_name.get(name);
		if (test_case!=null)
			return test_case;
		name = name.toLowerCase();
		test_case = test_cases_by_name.get(name);
		if (test_case!=null)
			return test_case;
		return test_cases_by_name.get(name+".phpt");
	}

	@Override
	public EBuildBranch getTestPackBranch() {
		return getVersionBranch();
	}

	@Override
	public String getTestPackVersionRevision() {
		return getVersion();
	}

	@Override
	public String getNameAndVersionString() {
		return AHost.basename(getSourceDirectory());
	}
	
} // end public class PhptSourceTestPack
