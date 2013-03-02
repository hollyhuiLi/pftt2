package com.mostc.pftt.runner;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import com.mostc.pftt.host.AHost;
import com.mostc.pftt.host.RemoteHost;
import com.mostc.pftt.model.ActiveTestPack;
import com.mostc.pftt.model.SourceTestPack;
import com.mostc.pftt.model.TestCase;
import com.mostc.pftt.model.core.PhpBuild;
import com.mostc.pftt.model.core.PhpIni;
import com.mostc.pftt.model.sapi.AbstractManagedProcessesWebServerManager;
import com.mostc.pftt.model.sapi.SAPIInstance;
import com.mostc.pftt.model.sapi.SharedSAPIInstanceTestCaseGroupKey;
import com.mostc.pftt.model.sapi.TestCaseGroupKey;
import com.mostc.pftt.model.sapi.WebServerInstance;
import com.mostc.pftt.results.ConsoleManager;
import com.mostc.pftt.results.ITestResultReceiver;
import com.mostc.pftt.results.ConsoleManager.EPrintType;
import com.mostc.pftt.runner.LocalPhpUnitTestPackRunner.PhpUnitThread;
import com.mostc.pftt.scenario.AbstractFileSystemScenario;
import com.mostc.pftt.scenario.AbstractRemoteFileSystemScenario;
import com.mostc.pftt.scenario.AbstractSAPIScenario;
import com.mostc.pftt.scenario.AbstractWebServerScenario;
import com.mostc.pftt.scenario.Scenario;
import com.mostc.pftt.scenario.ScenarioSet;
import com.mostc.pftt.scenario.AbstractFileSystemScenario.ITestPackStorageDir;

public abstract class AbstractLocalTestPackRunner<A extends ActiveTestPack, S extends SourceTestPack<A,T>, T extends TestCase> extends AbstractTestPackRunner<S, T> {
	protected static final int MAX_THREAD_COUNT = 64;
	protected S src_test_pack;
	protected final ConsoleManager cm;
	protected final ITestResultReceiver twriter;
	protected int thread_safe_test_count;
	protected A active_test_pack;
	protected AtomicReference<ETestPackRunnerState> runner_state;
	protected AtomicInteger test_count, active_thread_count;
	protected HashMap<TestCaseGroupKey,TestCaseGroup<T>> thread_safe_tests = new HashMap<TestCaseGroupKey,TestCaseGroup<T>>();
	protected HashMap<String[],NonThreadSafeExt<T>> non_thread_safe_tests = new HashMap<String[],NonThreadSafeExt<T>>();
	protected AbstractSAPIScenario sapi_scenario;
	protected AbstractFileSystemScenario file_scenario;
	protected LinkedBlockingQueue<NonThreadSafeExt<T>> non_thread_safe_exts = new LinkedBlockingQueue<NonThreadSafeExt<T>>();
	protected LinkedBlockingQueue<TestCaseGroup<T>> thread_safe_groups = new LinkedBlockingQueue<TestCaseGroup<T>>();
	
	protected static class NonThreadSafeExt<T extends TestCase> {
		protected String[] ext_names;
		protected LinkedBlockingQueue<TestCaseGroup<T>> test_groups;
		protected HashMap<TestCaseGroupKey,TestCaseGroup<T>> test_groups_by_key = new HashMap<TestCaseGroupKey,TestCaseGroup<T>>();
		
		protected NonThreadSafeExt(String[] ext_names) {
			this.ext_names = ext_names;
			test_groups = new LinkedBlockingQueue<TestCaseGroup<T>>(); 
		}
	}
	
	protected static class TestCaseGroup<T extends TestCase> {
		protected TestCaseGroupKey group_key;
		protected LinkedBlockingQueue<T> test_cases;
		
		protected TestCaseGroup(TestCaseGroupKey group_key) {
			this.group_key = group_key;
			test_cases = new LinkedBlockingQueue<T>();
		}
		
		public TestCaseGroup<T> clone() {
			TestCaseGroup<T> c = new TestCaseGroup<T>(this.group_key);
			c.test_cases.addAll(this.test_cases);
			return c;
		}

		public boolean containsTestNamed(String name) {
			for ( T test_case : test_cases ) {
				if (test_case.getName().equals(name))
					return true;
			}
			return false;
		}
	}
	
	public AbstractLocalTestPackRunner(ConsoleManager cm, ITestResultReceiver twriter, ScenarioSet scenario_set, PhpBuild build, AHost storage_host, AHost runner_host) {
		super(scenario_set, build, storage_host, runner_host);
		this.cm = cm;
		this.twriter = twriter;
		
		runner_state = new AtomicReference<ETestPackRunnerState>();
	}
	
	public void runTestList(S test_pack, List<T> test_cases) throws Exception {
		this.src_test_pack = test_pack;
		runTestList(null, test_pack, null, test_cases);
	}
	
	public void runTestList(A test_pack, List<T> test_cases) throws Exception {
		runTestList(null, null, test_pack, test_cases);
	}
	
	protected void checkHost(AHost host) {
		if (host instanceof RemoteHost) {
			RemoteHost remote_host = (RemoteHost) host;
			if (!remote_host.ensureConnected(cm))
				throw new IllegalStateException("unable to connect to remote host: "+remote_host.getAddress()+" "+remote_host);
		}
	}
	
	protected void ensureFileSystemScenario() {
		if (file_scenario==null)
			file_scenario = AbstractFileSystemScenario.getFileSystemScenario(scenario_set);
		if (file_scenario instanceof AbstractRemoteFileSystemScenario) {
			storage_host = ((AbstractRemoteFileSystemScenario)file_scenario).getRemoteHost();
		}
	}
	
	/**
	 * 
	 * @param test_cases_read
	 * @param test_cases - will be null if !test_cases_read
	 * @throws Exception 
	 */
	protected ITestPackStorageDir doSetupStorageAndTestPack(boolean test_cases_read, @Nullable List<T> test_cases) throws Exception {
		cm.println(EPrintType.IN_PROGRESS, getClass(), "preparing storage for test-pack...");
		
		ensureFileSystemScenario();
		
		// prepare storage
		ITestPackStorageDir storage_dir = file_scenario.createStorageDir(cm, runner_host);
		if (storage_dir == null) {
			cm.println(EPrintType.CANT_CONTINUE, getClass(), "unable to prepare storage for test-pack, giving up!");
			close();
			return null;
		}
		//
		
		setupStorageAndTestPack(storage_dir, test_cases);
		
		return storage_dir;
	}
	
	protected void runTestList(ITestPackStorageDir storage_dir, S test_pack, A active_test_pack, List<T> test_cases) throws Exception {
		if (test_cases.isEmpty()) {
			if (cm!=null)
				cm.println(EPrintType.COMPLETED_OPERATION, getClass(), "no test cases to run. did nothing.");
			return;
		}
		
		// if already running, wait
		while (runner_state.get()==ETestPackRunnerState.RUNNING) {
			Thread.sleep(100);
		}
		//
		
		runner_state.set(ETestPackRunnerState.RUNNING);
		sapi_scenario = AbstractSAPIScenario.getSAPIScenario(scenario_set);
		ensureFileSystemScenario();
		checkHost(storage_host);
		checkHost(runner_host);
		
		
		// ensure all scenarios are implemented
		if (!scenario_set.isImplemented()) {
			cm.println(EPrintType.SKIP_OPERATION, getClass(), "Scenario Set not implemented: "+scenario_set);
			return;
		}
		//
		
		//
		if (sapi_scenario instanceof AbstractWebServerScenario) { // TODO temp
			SAPIInstance sa = ((AbstractWebServerScenario)sapi_scenario).smgr.getWebServerInstance(cm, runner_host, scenario_set, build, new PhpIni(), null, null, null, false, null);
			
			if (sa==null) {
				cm.println(EPrintType.CANT_CONTINUE, getClass(), "SAPIInstance failed smoke tests... can't test (use -skip_smoke_tests to override)");
				return;
			}
			
			sa.close();
		}
		//
		
		
		////////////////// install test-pack onto the storage it will be run from
		// for local file system, this is just a file copy. for other scenarios, its more complicated (let the filesystem scenario deal with it)
		
		cm.println(EPrintType.IN_PROGRESS, getClass(), "loaded tests: "+test_cases.size());
		
		// ensure storage dir setup before proceeding
		if (storage_dir==null)
			storage_dir = doSetupStorageAndTestPack(true, test_cases);
		if (storage_dir==null)
			return;
		//
		
		//
		for ( Scenario scenario : scenario_set ) {
			if (scenario!=file_scenario) {
				if (!scenario.setup(cm, runner_host, build, scenario_set)) {
					cm.println(EPrintType.CANT_CONTINUE, getClass(), "Scenario setup failed: "+scenario);
					return;
				}
			}
		}
		//
		
		cm.println(EPrintType.IN_PROGRESS, getClass(), "ready to go!    scenario_set="+scenario_set+" runner_host="+runner_host+" storage_dir="+storage_dir.getClass()+" local_path="+storage_dir.getLocalPath(runner_host)+" remote_path="+storage_dir.getRemotePath(runner_host));
		
		/////////////////// installed test-pack, ready to go
		
		try {
			groupTestCases(test_cases);
			
			long start_time = System.currentTimeMillis();
			
			executeTestCases(true); // TODO false);
			
			long run_time = Math.abs(System.currentTimeMillis() - start_time);
			
			//System.out.println(test_count);
			System.out.println((run_time/1000)+" seconds"); // TODO console manager
			
			// if not -dont-cleanup-test-pack and if successful, delete test-pack (otherwise leave it behind for user to analyze the internal exception(s))
			if (!cm.isDontCleanupTestPack() &&
					this.active_test_pack != null && // TODO phpunit?
					this.active_test_pack.getStorageDirectory() != null && // TODO does phpunit need this?
					!this.active_test_pack.getStorageDirectory().equals(
							src_test_pack.getSourceDirectory())) {
				cm.println(EPrintType.IN_PROGRESS, getClass(), "deleting/cleaning-up active test-pack: "+this.active_test_pack);
				
				// cleanup, delete test-pack, disconnect storage, etc...
				storage_dir.disposeForce(cm, runner_host); 
			}
			//
		} finally {
			// be sure all running WebServerInstances, or other SAPIInstances are
			// closed by end of testing (otherwise `php.exe -S` will keep on running)
			close();
		}
	} // end public void runTestList
	
	protected abstract void setupStorageAndTestPack(ITestPackStorageDir storage_dir, List<T> test_cases) throws IOException, Exception;
	
	public void close() {
		// don't kill procs we're debugging 
		sapi_scenario.close(cm.isDebugAll() || cm.isDebugList());
	}
	
	protected void preGroup(List<T> test_cases) {
		
	}
	
	@SuppressWarnings("unchecked")
	protected void groupTestCases(List<T> test_cases) throws InterruptedException {
		preGroup(test_cases);
		
		TestCaseGroupKey group_key = null;
		LinkedList<TestCaseGroup<T>> thread_safe_list = new LinkedList<TestCaseGroup<T>>();
		thread_safe_test_count = 0;
		
		for (T test_case : test_cases) {
			try {
				if (cm.isInSkipList(test_case))
					continue; // skip
				group_key = createGroupKey(test_case, group_key);
				
				if (group_key==null)
					continue; // skip
			} catch ( Exception ex ) {
				cm.addGlobalException(EPrintType.OPERATION_FAILED_CONTINUING, getClass(), "groupTestCases", ex, "", storage_host, test_case, sapi_scenario);
				
				continue;
			}
			
			// @see -no_nts console option- if used, all test cases should go to #handleTS
			// if -no_nts not used, see if #handleNTS wants to handle them
			if (!cm.isThreadSafety() || !handleNTS(group_key, test_case)) {
				// test case is thread-safe or we're ignoring thread-safety (-no_nts)
				handleTS(thread_safe_list, group_key, test_case);
			}
			//
		} // end while
		
		//
		postGroup(thread_safe_list, test_cases);
		
		// @see -run_group_times_all console option
		final int run_group_times_all = cm.getRunGroupTimesAll();
		
		// @see -randomize_order console option
		if (cm.isRandomizeTestOrder()) {
			for ( @SuppressWarnings("rawtypes") NonThreadSafeExt ext : non_thread_safe_exts ) {
				Iterator<TestCaseGroup<T>> it = ext.test_groups.iterator();
				int run_group_times = run_group_times_all;
				while ( it.hasNext() ) {
					run_group_times = runGroupTimes(run_group_times, it.next());
					if (run_group_times != run_group_times_all)
						break; // found matching group
				}
				
				//
				if (run_group_times > 1) {
					// can't iterate over ext.test_groups and add elements (clones) to it at the same time
					LinkedBlockingQueue<TestCaseGroup<T>> clones = new LinkedBlockingQueue<TestCaseGroup<T>>();
					
					for ( Object group : ext.test_groups ) {
						for ( int i=0 ; i < run_group_times ; i++ ) {
							TestCaseGroup<T> c = ((TestCaseGroup<T>) group).clone();
	
							clones.add(c);
						}
					}
					
					ext.test_groups = clones;
				}
				//
				
				for ( Object group : ext.test_groups )
					randomizeGroup((TestCaseGroup<T>) group);
			} // end for
		} // end if
		
		for (TestCaseGroup<T> group : thread_safe_list) {
			int run_group_times = runGroupTimes(run_group_times_all, group);
			
			if (run_group_times > 1) {
				for ( int i=0  ; i < run_group_times ; i++ ) {
					TestCaseGroup<T> c = group.clone();
				
					// @see -randomize_order console option
					if (cm.isRandomizeTestOrder())
						randomizeGroup(c);
					thread_safe_groups.add(c);
				}
			} else {
				if (cm.isRandomizeTestOrder())
					randomizeGroup(group);
				
				thread_safe_groups.add(group);
			}
		}
		
		// finally, test cases are all grouped
	} // end protected void groupTestCases
	
	// @see -run_group_times_list support
	private int runGroupTimes(int run_group_times_all, TestCaseGroup<T> group) {
		if (!cm.isRunGroupTimesList())
			return run_group_times_all;
		
		for ( String name : cm.getRunGroupTimesList() ) {
			if ( group.containsTestNamed(name) ) 
				return cm.getRunGroupTimesListTimes();
		}
		return run_group_times_all;
	}

	private Random r = new Random();
	private void randomizeGroup(TestCaseGroup<T> group) {
		LinkedList<T> a = new LinkedList<T>();
		ArrayList<T> b = new ArrayList<T>(group.test_cases.size());
		group.test_cases.drainTo(a);
		while (a.size() > 0)
			b.add(a.remove(r.nextInt(a.size())));
		group.test_cases.addAll(b);
	}
	
	protected abstract TestCaseGroupKey createGroupKey(T test_case, TestCaseGroupKey group_key) throws Exception;
	
	protected abstract boolean handleNTS(TestCaseGroupKey group_key, T test_case);
	
	protected void addNTSTestCase(String[] ext_names, TestCaseGroupKey group_key, T test_case) {
		NonThreadSafeExt<T> ext = non_thread_safe_tests.get(ext_names);
		if (ext==null) {
			ext = new NonThreadSafeExt<T>(ext_names);
			non_thread_safe_exts.add(ext);
			non_thread_safe_tests.put(ext_names, ext);
		}
		
		ext.test_groups_by_key.get(group_key);
		
		//
		TestCaseGroup<T> group = ext.test_groups_by_key.get(group_key);
		if (group==null) {
			group = new TestCaseGroup<T>(group_key);
			ext.test_groups.add(group);
			ext.test_groups_by_key.put(group_key, group);
		}
		group.test_cases.add(test_case);
	}
	
	protected void handleTS(LinkedList<TestCaseGroup<T>> thread_safe_list, TestCaseGroupKey group_key, T test_case) {
		TestCaseGroup<T> group = thread_safe_tests.get(group_key);
		if (group==null) {
			group = new TestCaseGroup<T>(group_key);
			thread_safe_list.add(group);
			thread_safe_tests.put(group_key, group);
		}
		thread_safe_test_count++;
		group.test_cases.add(test_case);
	}
	
	protected void postGroup(LinkedList<TestCaseGroup<T>> thread_safe_list, List<T> test_cases) {
		
	}
	
	protected void executeTestCases(boolean parallel) throws InterruptedException, IllegalStateException, IOException {
		// decide number of threads
		// 1. ask SAPI Scenario
		// 2. limit to number of thread safe tests + number of NTS extensions (extensions with NTS tests)
		//        -exceed this number and there will be threads that won't have any tests to run
		// 3. if debugging
		// 4. ask user (-thread_count console option)
		// 5. limit to MAX_THREAD_COUNT
		
		int thread_count = sapi_scenario.getTestThreadCount(runner_host);
		if ((cm.isThreadSafety() || cm.getRunTestTimesAll()<2) && thread_count > thread_safe_test_count + non_thread_safe_exts.size()) {
			// don't start more threads than there will be work for
			// however, if -no_nts AND -run_test_times_all console option used, user wants tests run
			// as much as possible, so don't do this check (in that case, do normal number of threads, not this)
			//
			thread_count = thread_safe_test_count + non_thread_safe_exts.size(); 
		}
		if (cm.isDebugAll()) {
			// run fewer threads b/c we're running WinDebug
			// (can run WinDebug w/ same number of threads, but UI responsiveness will be really SLoow)
			thread_count = Math.max(1, thread_count / 4);
		}
		if (cm.getThreadCount()>0) {
			// let user override SAPI and debug thread count checks
			thread_count = cm.getThreadCount();
		}
		if (thread_count > MAX_THREAD_COUNT) {
			// safety check: don't run too many threads
			thread_count = MAX_THREAD_COUNT;
		}
		cm.println(EPrintType.IN_PROGRESS, getClass(), "Starting up Test Threads: thread_count="+thread_count+" runner_host="+runner_host+" sapi_scenario="+sapi_scenario);
			
		test_count = new AtomicInteger(0);
		active_thread_count = new AtomicInteger(thread_count);
		
		for ( int i=0 ; i < thread_count ; i++ ) { 
			start_thread(parallel);
		}
		
		// wait until done
		int c ; while ( ( c = active_thread_count.get() ) > 0 ) { Thread.sleep(c>3?1000:50); }
	} // end protected void executeTestCases
		
	protected void start_thread(boolean parallel) throws IllegalStateException, IOException {
		TestPackThread<T> t = createTestPackThread(parallel);
		// if running Swing UI, run thread minimum priority in favor of Swing EDT
		t.setPriority(Thread.MIN_PRIORITY);
		t.setDaemon(true);
		t.start();
	}
	
	protected abstract TestPackThread<T> createTestPackThread(boolean parallel) throws IllegalStateException, IOException;
	public abstract class TestPackThread<t extends T> extends SlowReplacementTestPackRunnerThread {
		protected final AtomicBoolean run_thread;
		protected final boolean parallel;
		protected final int run_test_times_all;
		
		protected TestPackThread(boolean parallel) {
			this.run_thread = new AtomicBoolean(true);
			this.parallel = parallel;
			
			// @see -run_test_times_all console option
			run_test_times_all = Math.max(1, cm.getRunTestTimesAll());
		}
				
		@Override
		public void run() {
			// pick a non-thread-safe(NTS) extension that isn't already running then run it
			//
			// keep doing that until they're all done, then execute all the thread-safe tests
			// (if there aren't enough NTS extensions to fill all the threads, some threads will only execute thread-safe tests)
			//
			try {
				runNonThreadSafe(); 
				
				// execute any remaining thread safe jobs
				runThreadSafe();
			} catch ( Exception ex ) {
				cm.addGlobalException(EPrintType.CANT_CONTINUE, getClass(), "run", ex, "", storage_host, build, scenario_set);
			} finally {
				if (run_thread.get())
					// if #stopThisThread not called
					active_thread_count.decrementAndGet();
			}
		} // end public void run
		
		protected void runNonThreadSafe() {
			NonThreadSafeExt<T> ext;
			TestCaseGroup<T> group;
			while(shouldRun()) {
				ext = non_thread_safe_exts.poll();
				if (ext==null)
					break;
				
				while (shouldRun()) {
					group = ext.test_groups.poll();
					if (group==null)
						break;
					
					exec_jobs(group.group_key, group.test_cases, test_count);
				}
			}
		} // end protected void runNonThreadSafe
		
		protected void runThreadSafe() {
			TestCaseGroup<T> group;
			while (shouldRun()) {
				// thread-safe can share groups between threads
				// (this allows larger groups to be distributed  between threads)
				group = thread_safe_groups.peek();
				if (group==null) {
					break;
				} else if (group.test_cases.isEmpty()) {
					thread_safe_groups.remove(group);
					continue;
				} else {
					exec_jobs(group.group_key, group.test_cases, test_count);
				}
			}
		} // end protected void runThreadSafe
		
		protected boolean shouldRun() {
			return run_thread.get() && runner_state.get()==ETestPackRunnerState.RUNNING;
		}
		
		protected void exec_jobs(TestCaseGroupKey group_key, LinkedBlockingQueue<T> jobs, AtomicInteger test_count) {
			T test_case;
			SAPIInstance sa = null;
			LinkedList<T> completed_tests = new LinkedList<T>();
			
			try {
				while ( ( 
						test_case = jobs.poll() 
						) != null 
						&& 
						shouldRun()
						) {
					completed_tests.add(test_case);
					
					if (parallel) {
						// -debug_all and -debug_list console options
						final boolean debugger_attached = (cm.isDebugAll() || cm.isInDebugList(test_case));
						
						
						// @see HttpTestCaseRunner#http_execute which calls #notifyCrash
						// make sure a WebServerInstance is still running here, so it will be shared with each
						// test runner instance (otherwise each test runner will create its own instance, which is slow)
						if (sapi_scenario instanceof AbstractWebServerScenario) { // TODO temp
							//SAPIInstance 
							sa = ((SharedSAPIInstanceTestCaseGroupKey)group_key).getSAPIInstance();
							if (sa==null||sa.isCrashedOrDebuggedAndClosed()||(debugger_attached && !((WebServerInstance)sa).isDebuggerAttached())) {
								//((SharedSAPIInstanceTestCaseGroupKey)group_key).setSAPIInstance(
								sa = ((AbstractWebServerScenario)sapi_scenario).smgr.getWebServerInstance(cm, runner_host, scenario_set, build, group_key.getPhpIni(), 
										group_key.getEnv(), this instanceof PhpUnitThread ? ((PhpUnitThread)this).my_temp_dir // TODO temp phpunit 
												:
										
										active_test_pack.getStorageDirectory(), (WebServerInstance) sa, debugger_attached, completed_tests);
								//);
								
								// TODO don't store sa on group_key! (don't share sa between threads)
								// important: this closes sa
								((SharedSAPIInstanceTestCaseGroupKey)group_key).setSAPIInstance(cm, runner_host, sa); // TODO temp
							}
						}
					}
					
					int a = run_test_times_all;
					
					// @see -run_test_times_list console option
					if (cm.isInRunTestTimesList(test_case)) {
						a = cm.getRunTestTimesListTimes();
					}
					
					for ( int i=0 ; i < a ; i++ ) {
						// CRITICAL: catch exception to record with test
						try {
							runTest(group_key, test_case);
							// TODO -delay_between_ms console option Thread.sleep(1000000);
							
							AbstractManagedProcessesWebServerManager.waitIfTooManyActiveDebuggers();
						} catch ( Throwable ex ) {
							twriter.addTestException(storage_host, scenario_set, test_case, ex, sa);
						}
					}
					
					test_count.incrementAndGet();
					Thread.yield();
				} // end while
			} finally {
				if (parallel) {
					// @see HttpTestCaseRunner#http_execute which calls #notifyCrash
					// make sure a WebServerInstance is still running here, so it will be shared with each
					// test runner instance (otherwise each test runner will create its own instance, which is slow)
					/*if (sapi_scenario instanceof AbstractWebServerScenario) { // TODO temp
						SAPIInstance sa = ((SharedSAPIInstanceTestCaseGroupKey)group_key).getSAPIInstance();*/
						if (sa!=null && (cm.isDisableDebugPrompt()||!sa.isCrashedOrDebuggedAndClosed()||!runner_host.isWindows()))
							sa.close();
					//}
				}
			} // end try
		} // end protected void exec_jobs
		
		protected abstract void runTest(TestCaseGroupKey group_key, T test_case) throws IOException, Exception, Throwable;

		@Override
		protected boolean slowCreateNewThread() {
			return active_thread_count.get() < MAX_THREAD_COUNT;
		}

		@Override
		protected void createNewThread() {
			try {
			start_thread(parallel);
			} catch ( Exception ex ) {
				ex.printStackTrace();
			}
		}

		@Override
		protected void stopThisThread() {
			// continue running current CliTestCaseRunner, but don't start any more of them
			run_thread.set(false);
			
			active_thread_count.decrementAndGet();
		}
		
	} // end public abstract class TestPackThread

	@Override
	public void setState(ETestPackRunnerState state) throws IllegalStateException {
		this.runner_state.set(state);
	}

	@Override
	public ETestPackRunnerState getState() {
		return runner_state.get();
	}

	@Override
	public void runAllTests(S test_pack) throws FileNotFoundException, IOException, Exception {
		this.src_test_pack = test_pack;
		
		ArrayList<T> test_cases = new ArrayList<T>(13000);
			
		test_pack.cleanup(cm);
		
		// PhpUnit test-packs have their storage setup FIRST, then they are read from storage SECOND
		//    -PhpUnit sets up storage with this call to #doSetupStorageAndTestPack
		//    -PhpUnit ignores the second #doSetupStorageAndTestPack in #runTestList
		// PHPT test-packs are read FIRST then have their storage setup SECOND
		//    -PHPT ignores this #doSetupStorageAndTestPack call
		//    -PHPT honors the second #doSetupStorageAndTestPack in #runTestList
		ITestPackStorageDir storage_dir = doSetupStorageAndTestPack(false, null);
		// storage_dir may be null
		
		cm.println(EPrintType.IN_PROGRESS, getClass(), "enumerating test cases from test-pack...");
		
		test_pack.read(test_cases, cm, twriter, build);
		
		cm.println(EPrintType.IN_PROGRESS, getClass(), "enumerated test cases.");
		
		runTestList(storage_dir, test_pack, null, test_cases);
	}

	public void runAllTests(A test_pack) throws FileNotFoundException, IOException, Exception {
		ArrayList<T> test_cases = new ArrayList<T>(13000);
		
		
		runTestList(test_pack, test_cases);
	}
	
} // end public abstract class AbstractLocalTestPackRunner
