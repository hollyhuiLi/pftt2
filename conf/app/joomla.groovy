
def scenarios() {
	// install Joomla CMS
	new JoomlaScenario()
}

/** Joomla-Platform != Joomla-CMS
 *
 * @see https://github.com/joomla/joomla-platform
 * 
 */
abstract class SymfonyPhpUnitTestPack extends PhpUnitSourceTestPack {
	
	@Override
	protected boolean openAfterInstall(ConsoleManager cm, AHost host) throws Exception {
		// 1.
		addBlacklist("vendor/kriswallsmith/assetic/tests/assetic/test/filter/sass/sassfiltertest.php");
		addBlacklist("vendor/sensio/generator-bundle/sensio/bundle/generatorbundle/resources/skeleton/bundle/defaultcontrollertest.php");
		addBlacklist("vendor/symfony/symfony/vendor/kriswallsmith/assetic/tests/assetic/test/filter/sass/sassfiltertest.php");
		addBlacklist("vendor/symfony/symfony/vendor/sensio/generator-bundle/sensio/bundle/generatorbundle/resources/skeleton/bundle/defaultcontrollertest.php");
		addBlacklist("vendor/symfony/symfony/vendor/twig/twig/test/twig/tests/integrationtest.php");
		addBlacklist("vendor/twig/twig/test/twig/tests/integrationtest.php");
		
		// 2.
		setRoot("C:\\php-sdk\\PFTT\\current\\cache\\working\\Symfony");
		//addPhpUnitDist(getRoot()+"/vendor/symfony/symfony/src", getRoot()+"/vendor/symfony/symfony/autoload.php.dist");
		//addPhpUnitDist(getRoot()+"/vendor/doctrine/common/tests", getRoot()+"/vendor/doctrine/common/tests/Doctrine/Tests/TestInit.php");
		addIncludeDirectory(getRoot()+"/vendor/symfony/symfony/src");
		
		// 3.
		if (!host.exists(getRoot()+"/vendor/symfony/symfony/vendor")) {
			// copy Vendors, which is part of the Symfony install process
			//
			//
			String tmp_dir = host.mktempname(getClass());
			// have to move to a temp directory because it'll cause a loop otherwise
			host.copy(getRoot()+"/vendor", tmp_dir+"/vendor");
			
			host.move(tmp_dir+"/vendor", getRoot()+"/vendor/symfony/symfony/vendor");
			
			host.delete(tmp_dir);
		}
		
		return true;
	} // end public boolean openAfterInstall
	@Override
	public void prepareINI(ConsoleManager cm, AHost host, ScenarioSet scenario_set, PhpBuild build, PhpIni ini) {
		ini.putSingle("zend_optimizerplus.save_comments", 0);
		ini.putSingle("zend_optimizerplus.load_comments", 0);
	}
} // end class SymfonyPhpUnitTestPack
class JoomlaPlatformPhpUnitTestPack extends SymfonyPhpUnitTestPack {
	
	@Override
	public String getNameAndVersionString() {
		return "Joomla-Platform-12.3";
	}
	
	@Override
	protected String getSourceRoot(AHost host) {
		return host.getPfttDir()+"/cache/working/joomla-platform";
	}

	@Override
	protected boolean openAfterInstall(ConsoleManager cm, AHost host) throws Exception {
		// 1. dependency on SymfonyPhpUnitTestPack (Joomla-Platform depends on Symfony)
		super.openAfterInstall(cm, host);
		
		// 2.
		addPhpUnitDist(getRoot()+"/tests/suites/database", getRoot()+"/tests/bootstrap.php");
		addPhpUnitDist(getRoot()+"/tests/suites/unit", getRoot()+"/tests/bootstrap.php");
		addPhpUnitDist(getRoot()+"/tests/suites/legacy", getRoot()+"/tests/bootstrap.legacy.php");
		
		// 3. run this code including joomla's bootstrap or bootstrap.legacy
		//     for some reason, joomla's bootstrap doesn't seem to include them
		setPreambleCode(
				"jimport('joomla.filesystem.file');\n" +
				"jimport('joomla.filesystem.path');\n" +
				"jimport('joomla.base.adapter');\n" +
				// this could be replaced by an include file, but the jimport calls can't be
				"require_once '"+getRoot()+"/tests/suites/unit/joomla/application/stubs/JApplicationWebInspector.php';"
			);

		return true;
	} // end public boolean open
	
} // end class JoomlaPlatformPhpUnitTestPack

def getPhpUnitSourceTestPack() {
	// test Joomla Platform
	return new JoomlaPlatformPhpUnitTestPack();
}
