package com.mostc.pftt.results;

import java.awt.Container;
import java.util.List;

import javax.swing.JFrame;

import com.mostc.pftt.host.AHost;
import com.mostc.pftt.host.Host;
import com.mostc.pftt.model.TestCase;
import com.mostc.pftt.model.core.EPhptTestStatus;
import com.mostc.pftt.model.core.PhpDebugPack;
import com.mostc.pftt.model.core.PhptTestCase;
import com.mostc.pftt.runner.LocalPhptTestPackRunner;
import com.mostc.pftt.ui.PhptDebuggerFrame;
import com.mostc.pftt.util.ErrorUtil;

public class LocalConsoleManager implements ConsoleManager {
	protected final boolean overwrite, debug_all, results_only, show_gui, disable_debug_prompt, dont_cleanup_test_pack, phpt_not_in_place, pftt_debug, no_result_file_for_pass_xskip_skip, randomize_order, thread_safety, skip_smoke_tests;
	protected final int run_test_times_all, run_test_times_list_times, run_group_times, run_group_times_list_times, max_test_read_count, thread_count;
	protected String source_pack;
	protected PhpDebugPack debug_pack;
	protected PhptDebuggerFrame gui;
	protected PhpResultPackWriter w; // TODO
	protected List<String> debug_list, run_test_times_list, run_group_times_list, skip_list;
		
	public LocalConsoleManager(String source_pack, PhpDebugPack debug_pack, boolean overwrite, boolean debug_all, boolean results_only, boolean show_gui, boolean disable_debug_prompt, boolean dont_cleanup_test_pack, boolean phpt_not_in_place, boolean pftt_debug, boolean no_result_file_for_pass_xskip_skip, boolean randomize_order, int run_test_times_all, boolean thread_safety, int run_test_times_list_times, int run_group_times, int run_group_times_list_times, List<String> debug_list, List<String> run_test_times_list, List<String> run_group_times_list, List<String> skip_list, boolean skip_smoke_tests, int max_test_read_count, int thread_count) {
		this.source_pack = source_pack;
		this.debug_pack = debug_pack;
		this.overwrite = overwrite;
		this.debug_all = debug_all;
		this.results_only = results_only;
		this.show_gui = show_gui;
		this.disable_debug_prompt = disable_debug_prompt;
		this.dont_cleanup_test_pack = dont_cleanup_test_pack;
		this.phpt_not_in_place = phpt_not_in_place;
		this.pftt_debug = pftt_debug;
		this.no_result_file_for_pass_xskip_skip = no_result_file_for_pass_xskip_skip;
		this.randomize_order = randomize_order;
		this.run_test_times_all = run_test_times_all;
		this.thread_safety = thread_safety;
		this.run_test_times_list_times = run_test_times_list_times;
		this.run_group_times = run_group_times;
		this.run_group_times_list_times = run_group_times_list_times;
		this.debug_list = debug_list;
		this.run_test_times_list = run_test_times_list;
		this.run_group_times_list = run_group_times_list; 
		this.skip_list = skip_list;
		this.skip_smoke_tests = skip_smoke_tests;
		this.max_test_read_count = max_test_read_count;
		this.thread_count = thread_count;
	}
	
	public void showGUI(LocalPhptTestPackRunner test_pack_runner) {
		if (show_gui) {
			if (gui!=null)
				((JFrame)gui.getRootPane().getParent()).dispose();
			
			gui = new PhptDebuggerFrame(test_pack_runner);
			show_gui("", gui);
		}
	}
	
	protected static void show_gui(String title, Container c) {
		JFrame jf = new JFrame("PFTT - "+title);
		jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		jf.setContentPane(c);
		jf.pack();
		jf.setExtendedState(JFrame.MAXIMIZED_BOTH);				
		jf.setVisible(true);
	}
	
	public boolean isDisableDebugPrompt() {
		return disable_debug_prompt;
	}
	
	public boolean isOverwrite() {
		return overwrite;
	}
	
	public boolean isPfttDebug() {
		return pftt_debug;
	}

	protected void finishedTest(PhptTestCase test_case, EPhptTestStatus status) {
		System.out.println(status+" "+test_case.getName());
	}
	
	@Override
	public void restartingAndRetryingTest(TestCase test_case) {
		restartingAndRetryingTest(test_case.getName());
	}
	
	@Override
	public void restartingAndRetryingTest(String test_case_name) {
		if (results_only)
			return;
		
		System.out.println("RESTARTING_AND_RETRYING "+test_case_name);
	}
	
	protected void showResult(AHost host, int totalCount, int completed, PhptTestResult result) {
		if (gui!=null)
			gui.showResult(host, totalCount, completed, result);
	}
	
	@Override
	public void println(EPrintType type, String ctx_str, String string) {
		if (results_only)
			return;
		
		switch(type) {
		case SKIP_OPERATION: // TODO UNABLE_TO_START_OPERATION
		case CANT_CONTINUE:
		case OPERATION_FAILED_CONTINUING:
			System.err.println(type+": "+ctx_str+": "+string);
			break;
		case CLUE:
			System.out.println(type+": "+ctx_str+": "+string);
			break;
		case TIP:
			System.out.println("PFTT: "+string);
			break;
		default:
			System.out.println("PFTT: "+ctx_str+": "+string);
		}
		// TODO record in result-pack
	}
	@Override
	public void println(EPrintType type, Class<?> clazz, String string) {
		println(type, Host.toContext(clazz), string);
	}
	@Override
	public void addGlobalException(EPrintType type, Class<?> clazz, String method_name, Exception ex, String msg) {
		addGlobalException(type, clazz, method_name, ex, msg, null);
	}
	@Override
	public void addGlobalException(EPrintType type, Class<?> clazz, String method_name, Exception ex, String msg, Object a) {
		addGlobalException(type, clazz, method_name, ex, msg, a, null);
	}
	@Override
	public void addGlobalException(EPrintType type, Class<?> clazz, String method_name, Exception ex, String msg, Object a, Object b) {
		addGlobalException(type, clazz, method_name, ex, msg, a, b, null);
	}
	@Override
	public void addGlobalException(EPrintType type, Class<?> clazz, String method_name, Exception ex, String msg, Object a, Object b, Object c) {
		addGlobalException(type, Host.toContext(clazz, method_name), ex, msg, a, b, c);
	}
	@Override
	public void addGlobalException(EPrintType type, String ctx_str, Exception ex, String msg) {
		addGlobalException(type, ctx_str, ex, msg, null);
	}
	@Override
	public void addGlobalException(EPrintType type, String ctx_str, Exception ex, String msg, Object a) {
		addGlobalException(type, ctx_str, ex, msg, a, null);
	}
	@Override
	public void addGlobalException(EPrintType type, String ctx_str, Exception ex, String msg, Object a, Object b) {
		addGlobalException(type, ctx_str, ex, msg, a, b, null);
	}
	@Override
	public void addGlobalException(EPrintType type, String ctx_str, Exception ex, String msg, Object a, Object b, Object c) {
		String ex_str = ErrorUtil.toString(ex);
		if (!results_only) {
			System.err.println(ex_str);
		}
		if (w!=null) {
			synchronized (w.global_exception_writer) {
				w.global_exception_writer.println(ctx_str);
				w.global_exception_writer.println(msg==null?"":msg);
				w.global_exception_writer.print(ex_str);
			}
		}
	}

	@Override
	public boolean isResultsOnly() {
		return results_only;
	}

	@Override
	public boolean isDontCleanupTestPack() {
		return dont_cleanup_test_pack;
	}

	@Override
	public boolean isPhptNotInPlace() {
		return phpt_not_in_place;
	}

	@Override
	public PhpDebugPack getDebugPack() {
		return debug_pack;
	}

	@Override
	public String getSourcePack() {
		return source_pack;
	}

	@Override
	public boolean isNoResultFileForPassSkipXSkip() {
		return no_result_file_for_pass_xskip_skip;
	}

	@Override
	public int getRunTestTimesAll() {
		return run_test_times_all;
	}

	@Override
	public boolean isRandomizeTestOrder() {
		return randomize_order;
	}

	@Override
	public boolean isDebugAll() {
		return debug_all;
	}

	@Override
	public boolean isInDebugList(TestCase test_case) {
		return debug_list != null && debug_list.contains(test_case.getName());
	}

	@Override
	public boolean isDebugList() {
		return debug_list != null && debug_list.size() > 0;
	}

	@Override
	public boolean isThreadSafety() {
		return thread_safety;
	}

	@Override
	public int getRunGroupTimesAll() {
		return run_group_times;
	}

	@Override
	public boolean isInRunTestTimesList(TestCase test_case) {
		return run_test_times_list != null && run_test_times_list.contains(test_case.getName());
	}

	@Override
	public int getRunTestTimesListTimes() {
		return run_test_times_list_times;
	}

	@Override
	public int getRunGroupTimesListTimes() {
		return run_group_times_list_times;
	}

	@Override
	public List<String> getRunGroupTimesList() {
		return run_group_times_list;
	}

	@Override
	public boolean isRunGroupTimesList() {
		return run_group_times_list != null && run_group_times_list.size() > 0;
	}

	@Override
	public boolean isInSkipList(TestCase test_case) {
		return skip_list != null && skip_list.contains(test_case.getName());
	}

	@Override
	public boolean isSkipSmokeTests() {
		return skip_smoke_tests;
	}

	@Override
	public int getMaxTestReadCount() {
		return max_test_read_count;
	}

	@Override
	public int getThreadCount() {
		return thread_count;
	}
	
} // end public class ConsoleManager
