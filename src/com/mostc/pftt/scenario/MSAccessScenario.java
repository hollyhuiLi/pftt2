package com.mostc.pftt.scenario;

/** Scenario for testing the pdo_odbc and odbc extensions against a Microsoft Access database. (NOT IMPLEMENTED)
 * 
 * Access is one of 3 supported databases for the odbc and pdo_odbc extensions (the other 2 are SQL Server and IBM's DB2. We don't support DB2).
 * 
 * @see MSSQLODBCScenario
 *
 */

public class MSAccessScenario extends AbstractODBCScenario {

	@Override
	protected void name_exists(String name) {
		// TODO Auto-generated method stub

	}

	@Override
	public String getName() {
		return "ODBC-Access";
	}
	
	@Override
	public boolean isImplemented() {
		return false;
	}

}