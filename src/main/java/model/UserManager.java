package model;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openrdf.http.protocol.UnauthorizedException;
import org.springframework.http.HttpStatus;

import controller.TripleStoreConnector;

public class UserManager {

	DBConnector connector;
	
	public DBConnector getConnector() {
		return connector;
	}

	public enum Role {
	    viewer, contributor, admin, superAdmin 
	}


	/**
	 * This class manages the User creation in virtuoso for authentication and
	 * graph access control
	 * 
	 * @param connectionString
	 *            Connection string to Virtuoso
	 * @param user
	 *            Virtuoso user
	 * @param password
	 *            Virtuoso password
	 * @throws SQLException 
	 */
	public UserManager(String username, String password) throws UnauthorizedException {
		connector = new DBConnector();
		connector.connect(username, password);
	}

	public void createUser(String name, String password) throws Exception {
		if (checkUserExists(name, null)) {
			throw new Exception("User " + name + " already exists");
		}
		connector.update("DB.DBA.USER_CREATE('" + name + "', '" + password + "')");
	}
	
	public void setOption(String name, String option, String value) throws SQLException {
		connector.update("DB.DBA.USER_SET_OPTION ('" + name +"', '" + option + "', '" + value + "')");
	}
	
	public void changePassword(String name, String oldPassword, String newPassword) throws SQLException {
		connector.update("DB.DBA.USER_CHANGE_PASSWORD ('" + name +"', '" + oldPassword + "', '" + newPassword + "')");
	}

	public void dropUser(String name) throws SQLException {
		connector.update("DB.DBA.USER_DROP('" + name + "', 1)");
	}

	public void grantRole(String user, String role) throws SQLException {
		connector.update("GRANT " + role + " TO \"" + user + "\""); 
	}

	public void setDefaultRdfPermissions(String user, int permissions) throws SQLException {
		connector.update("DB.DBA.RDF_DEFAULT_USER_PERMS_SET ('" + user + "', " + permissions + ")");
	}

	public void setRdfGraphPermissions(String user, String graph, int permissions) throws SQLException  {
		connector.update("DB.DBA.RDF_GRAPH_USER_PERMS_SET ('" + graph + "', '" + user + "', " + permissions + ")");
	}

	public void deleteRdfGraphPermissions(String user, String graph) throws SQLException {
		connector.update("DB.DBA.RDF_GRAPH_USER_PERMS_DEL ('" + graph + "', '" + user + "')");
	}
	
	public void createGraphGroupPermissions(String group, int permission) throws SQLException {
		connector.update("DB.DBA.RDF_GRAPH_GROUP_CREATE ('" + group + "', " + permission + ")");
	}
	
	public void setGraphGroupPermissions(String group, String graph) throws SQLException {
		connector.update("DB.DBA.RDF_GRAPH_GROUP_INS ('" + group + "', '" + graph + "')");
	}
	
	public void createGraph(String graph) {
		String query = "SPARQL CREATE GRAPH <" + graph + ">";
		try {
			connector.update(query);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public void deleteGraph(String graph) {
		String query = "SPARQL CLEAR GRAPH <" + graph + ">";
		try {
			connector.update(query);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This function grant L_O_LOOK in virtuoso which was required to solve the
	 * error: Virtuoso 42000 Error SR186: No permission to execute dpipe
	 * DB.DBA.L_O_LOOK with user ID 106, group ID 106
	 * 
	 * @param user
	 * @throws ClassNotFoundException
	 * @throws SQLException
	 */
	public void grantLOLook(String user) throws SQLException {
		connector.update("GRANT EXECUTE ON DB.DBA.L_O_LOOK TO \"" + user + "\"");
	}
	
	public List<Map<String, String>> listUsers() throws SQLException {
		String query = "select u1.U_NAME as username, u2.U_NAME as userrole from DB.DBA.SYS_USERS u1, DB.DBA.SYS_USERS u2 WHERE u1.U_GROUP = u2.U_ID and u1.U_ACCOUNT_DISABLED = 0 and u1.U_GROUP <> 0 and u1.U_GROUP <> 3 and u1.U_GROUP <> 5 and u1.U_GROUP is not null";
		Connection conn = connector.conn;
		Statement stmt = null;
		List<Map<String, String>> results = new ArrayList<Map<String,String>>();
		try {
			stmt = conn.createStatement();
			ResultSet resultSet = stmt.executeQuery(query);
			while (resultSet.next()) {
				Map<String, String> tuple = new HashMap<String, String>();
				tuple.put("username", resultSet.getString("username"));
				tuple.put("role", resultSet.getString("userrole").equals("SPARQL_SELECT") ? Role.viewer.toString() : resultSet.getString("userrole").equals("SPARQL_UPDATE") ? Role.contributor.toString() : Role.admin.toString());
				results.add(tuple);
			}
			return results;
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(e);
		} finally {
			stmt.close();
		}
	}

	public boolean checkUserExists(String username, String email) throws SQLException {
		String query = "select * from DB.DBA.SYS_USERS where U_NAME='"
				+ username + "'";
		Connection conn = connector.conn;
		Statement stmt = null;
		try {
			stmt = conn.createStatement();
			ResultSet resultSet = stmt.executeQuery(query);
			return resultSet.next();
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(e);
		} finally {
			stmt.close();
		}
	}
	
	public String getUsernameFromID(String userid) throws NoSuchFieldException, SQLException {
		String query = "select U_NAME from DB.DBA.SYS_USERS where U_ID=" + userid;
		DBConnector db = new DBConnector();
		db.connect();
		Connection conn = db.conn;
		Statement stmt = null;
		try {
			stmt = conn.createStatement();
			ResultSet resultSet = stmt.executeQuery(query);
			if (resultSet.next()) {
				return resultSet.getString("U_NAME");
			} else {
				throw new NoSuchFieldException("userid does not exist");
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(e);
		} finally {
			stmt.close();
			db.closeConnection();
		}
	}
	
	public String getUserIDFromUsername(String username) throws SQLException, NoSuchFieldException {
		String query = "select U_ID from DB.DBA.SYS_USERS where U_NAME='" + username + "'";
		DBConnector db = new DBConnector();
		db.connect();
		Connection conn = db.conn;
		Statement stmt = null;
		try {
			stmt = conn.createStatement();
			ResultSet resultSet = stmt.executeQuery(query);
			if (resultSet.next()) {
				return resultSet.getString("U_ID");
			} else {
				throw new NoSuchFieldException("username does not exist");
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(e);
		} finally {
			stmt.close();
			db.closeConnection();
		}
	}
	
	public Role getUserRole(String username) throws SQLException {
		String query = "select u1.U_NAME as username, u2.U_NAME as userrole from DB.DBA.SYS_USERS u1, DB.DBA.SYS_USERS u2 where u1.U_GROUP = u2.U_ID and u1.U_NAME='" + username + "'";
		DBConnector db = new DBConnector();
		db.connect();
		Connection conn = db.conn;
		Statement stmt = null;
		try {
			stmt = conn.createStatement();
			ResultSet resultSet = stmt.executeQuery(query);
			resultSet.next();
			Role role = resultSet.getString("userrole").equals("SPARQL_SELECT") ? Role.viewer : resultSet.getString("userrole").equals("SPARQL_UPDATE") ? Role.contributor : resultSet.getString("userrole").equals("user_admin") ? Role.admin : Role.superAdmin;
			return role;
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(e);
		} finally {
			stmt.close();
			db.closeConnection();
		}
	}
	
	public boolean isUserHasPermission(String userid, String graph, boolean canWrite) throws SQLException {
		try {
			if (getUserRole(getUsernameFromID(userid)).toString().equals("superAdmin")) {
				return true;
			}
		} catch (NoSuchFieldException e1) {
			e1.printStackTrace();
		}
		
		String query = "select id_to_iri(RGU_GRAPH_IID), RGU_PERMISSIONS from RDF_GRAPH_USER where RDF_GRAPH_USER.RGU_USER_ID = " + userid;
		DBConnector db = new DBConnector();
		db.connect();
		Connection conn = db.conn;
		boolean permission = false;
		Statement stmt = null;
		try {
			stmt = conn.createStatement();
			ResultSet resultSet = stmt.executeQuery(query);
			while (resultSet.next()) {
				if (resultSet.getString("id_to_iri").equals(graph)) {
					if (canWrite && resultSet.getInt("RGU_PERMISSIONS") >= 3) {
						permission = true;
					} else if (!canWrite && resultSet.getInt("RGU_PERMISSIONS") >= 1) {
						permission = true;
					}
					break;
				}
			}
			return permission;
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException(e);
		} finally {
			stmt.close();
			db.closeConnection();
		}
	}
	
	public void closeConnection() {
		connector.closeConnection();
	}

	/*public static void main(String[] args) {
		String username = "dba";
		String password = "dba";
		try {
			UserManager manager = new UserManager(username, password);
			if (manager.isUserHasPermission(manager.getUserIDFromUsername("dba"), "http://localhost:8890/noon", true)) {
				System.out.println("yes");
			}
			//manager.dropUser("abc");
			//manager.createUser("bbb", "bbb");
			//manager.grantRole("vamhan", "SPARQL_SELECT");
			//manager.setOption("bbb", "PRIMARY_GROUP", "SPARQL_SELECT");
			//manager.setDefaultRdfPermissions("bbb", 1);
			//manager.setRdfGraphPermissions("bbb", "http://localhost:8890/noon", 0);
			//manager.setRdfGraphPermissions("abc", "http://localhost:8890/schema/test", 1);
			//manager.createGraphGroupPermissions("http://www.openlinksw.com/schemas/virtrdf#PrivateGraphs", 0);
			//manager.setGraphGroupPermissions("http://www.openlinksw.com/schemas/virtrdf#PrivateGraphs", "http://localhost:8890/test");
			//manager.setRdfGraphPermissions("vamhan", "http://www.openlinksw.com/schemas/virtrdf#PrivateGraphs", 0);
			System.out.println(manager.listUsers());
			
			String query = "SPARQL SELECT * from <http://localhost:8890/schema/test> WHERE {"
						+ "?subject ?predicate ?object"
					+ "}";
			 
			TripleStoreConnector gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(args).getParameters(), username, password);
			System.out.println(gettingStartedApplication.queryTriples(query).get(0).getSubject());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}*/
}
