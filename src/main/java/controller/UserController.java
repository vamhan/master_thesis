package controller;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import model.Triple;
import model.UserManager;
import model.UserManager.Role;

import org.apache.commons.codec.binary.Base64;
import org.openrdf.http.protocol.UnauthorizedException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
public class UserController {

	@RequestMapping("/users")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> listUsers(@RequestHeader("Authorization") String Authorization) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		List<Map<String, String>> tuples = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		UserManager manager = null;
		try {
			
			manager = new UserManager(username, password);
			Role role = manager.getUserRole(username);
			if (role != Role.superAdmin) {
				status = HttpStatus.FORBIDDEN;
			} else {
				tuples = manager.listUsers();
				status = HttpStatus.OK;
			}

		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<List<Map<String, String>>>(tuples, status);
	}
	
	@RequestMapping(value="/users", method=RequestMethod.POST)
	@ResponseBody
	public HttpEntity<String> createUser(@RequestHeader("Authorization") String Authorization, @RequestParam String username, @RequestParam String password, @RequestParam String role) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		String message = "Adding user fails";
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String ausername = us_pass[0];
		String apassword = us_pass[1];
		
		UserManager manager = null;
		
		try {
			
			manager = new UserManager(ausername, apassword);
			Role arole = manager.getUserRole(ausername);
			if (!arole.equals(Role.superAdmin)) {
				status = HttpStatus.FORBIDDEN;
			} else {
				boolean hasRole = false;
				for (Role r : Role.values()) {
					if (r.toString().equals(role)) {
						hasRole = true;
					}
				}
				if (hasRole) {
					manager.createUser(username, password);
					manager.setOption(username, "PRIMARY_GROUP", role.equals(Role.viewer.toString()) ? "SPARQL_SELECT" : role.equals(Role.contributor.toString()) ? "SPARQL_UPDATE" : role.equals(Role.admin.toString()) ? "user_admin" : "dba");
					manager.setDefaultRdfPermissions(username, 0);
					status = HttpStatus.OK;
					message = "Adding user succeeds";
				} else {
					status = HttpStatus.BAD_REQUEST;
					message = "Role " + role + " not exist";
				}
			}

		} catch (SQLException e) {
			status = HttpStatus.INTERNAL_SERVER_ERROR;
			e.printStackTrace();
		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (Exception e) {
			message = e.getMessage();
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} finally {
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<String>(message, status);
	}
	
	@RequestMapping(value="/users/{userid}", method=RequestMethod.DELETE)
	@ResponseBody
	public HttpEntity<String> deleteUser(@RequestHeader("Authorization") String Authorization, @PathVariable String userid) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		String message = "Deleting user fails";
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String ausername = us_pass[0];
		String apassword = us_pass[1];
		
		UserManager manager = null;
		
		try {
			
			manager = new UserManager(ausername, apassword);
			Role arole = manager.getUserRole(ausername);
			if (arole != Role.superAdmin) {
				status = HttpStatus.FORBIDDEN;
			} else {
				manager.dropUser(manager.getUsernameFromID(userid));
				status = HttpStatus.OK;
				message = "Deleting user succeeds";
			}

		} catch (SQLException e) {
			e.printStackTrace();
		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (NoSuchFieldException e) {
			message = e.getMessage();
			status = HttpStatus.BAD_REQUEST;
		} finally {
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<String>(message, status);
	}
	
	@RequestMapping(value="/users/{userid}/role", method=RequestMethod.PUT)
	@ResponseBody
	public HttpEntity<String> editRole(@RequestHeader("Authorization") String Authorization, @PathVariable String userid, @RequestParam String role) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		String message = "Changing user's role fails";
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String ausername = us_pass[0];
		String apassword = us_pass[1];
		
		UserManager manager = null;
		
		try {
			
			manager = new UserManager(ausername, apassword);
			Role arole = manager.getUserRole(ausername);
			if (!arole.equals(Role.superAdmin)) {
				status = HttpStatus.FORBIDDEN;
			} else {
				boolean hasRole = false;
				for (Role r : Role.values()) {
					if (r.toString().equals(role)) {
						hasRole = true;
					}
				}
				if (hasRole) {
					manager.setOption(manager.getUsernameFromID(userid), "PRIMARY_GROUP", role.equals(Role.viewer.toString()) ? "SPARQL_SELECT" : role.equals(Role.contributor.toString()) ? "SPARQL_UPDATE" : role.equals(Role.admin.toString()) ? "user_admin" : "dba");
					status = HttpStatus.OK;
					message = "Changing user's role succeeds";
				} else {
					status = HttpStatus.BAD_REQUEST;
					message = "Role " + role + " not exist";
				}
			}

		} catch (SQLException e) {
			status = HttpStatus.INTERNAL_SERVER_ERROR;
			e.printStackTrace();
		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (NoSuchFieldException e) {
			message = e.getMessage();
			status = HttpStatus.BAD_REQUEST;
		} finally {
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<String>(message, status);
	}
	
	@RequestMapping(value="/users/{userid}/password", method=RequestMethod.PUT)
	@ResponseBody
	public HttpEntity<String> changePassword(@RequestHeader("Authorization") String Authorization, @PathVariable String userid, @RequestParam String old_password, @RequestParam String new_password) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		String message = "Changing user's password fails";
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String ausername = us_pass[0];
		String apassword = us_pass[1];
		
		UserManager manager = null;
		
		try {
			
			manager = new UserManager(ausername, apassword);
			Role arole = manager.getUserRole(ausername);
			if (!arole.equals(Role.superAdmin)) {
				status = HttpStatus.FORBIDDEN;
			} else {
				manager.changePassword(manager.getUsernameFromID(userid), old_password, new_password);
				status = HttpStatus.OK;
				message = "Changing user's password succeeds";
			}

		} catch (SQLException e) {
			status = HttpStatus.INTERNAL_SERVER_ERROR;
			e.printStackTrace();
		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (NoSuchFieldException e) {
			message = e.getMessage();
			status = HttpStatus.BAD_REQUEST;
		} finally {
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<String>(message, status);
	}
	
	@RequestMapping(value="/repositories", method=RequestMethod.POST)
	@ResponseBody
	public HttpEntity<String> createRepo(@RequestHeader("Authorization") String Authorization, @RequestParam String repo_name) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		String message = "Create new repository fails";
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String ausername = us_pass[0];
		String apassword = us_pass[1];
		
		UserManager manager = null;
		
		try {
			
			manager = new UserManager(ausername, apassword);
			Role arole = manager.getUserRole(ausername);
			if (!arole.equals(Role.superAdmin)) {
				status = HttpStatus.FORBIDDEN;
			} else {
				manager.createGraph(repo_name + "/model");
				manager.createGraph(repo_name + "/instance");
				status = HttpStatus.OK;
				message = "Create new repository fails succeeds";
			}

		} catch (SQLException e) {
			status = HttpStatus.INTERNAL_SERVER_ERROR;
			e.printStackTrace();
		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} finally {
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<String>(message, status);
	}
	
	@RequestMapping(value="/repositories/permission", method=RequestMethod.POST)
	@ResponseBody
	public HttpEntity<String> assignRepoPermission(@RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, @RequestParam String userid, @RequestParam int permission) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		String message = "Setting permission fails";
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String ausername = us_pass[0];
		String apassword = us_pass[1];
		
		UserManager manager = null;
		
		try {
			manager = new UserManager(ausername, apassword);
			Role arole = manager.getUserRole(ausername);
			if (!arole.equals(Role.superAdmin)) {
				status = HttpStatus.FORBIDDEN;
			} else {
				if (permission == 2) {
					manager.setRdfGraphPermissions(manager.getUsernameFromID(userid), repo_name + "/model", 1);
					manager.setRdfGraphPermissions(manager.getUsernameFromID(userid), repo_name + "/instance", 3);
				} else {
					manager.setRdfGraphPermissions(manager.getUsernameFromID(userid), repo_name + "/model", permission);
					manager.setRdfGraphPermissions(manager.getUsernameFromID(userid), repo_name + "/instance", permission);
				}
				status = HttpStatus.OK;
				message = "Setting permission succeeds";
			}

		} catch (SQLException e) {
			status = HttpStatus.INTERNAL_SERVER_ERROR;
			e.printStackTrace();
		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (NoSuchFieldException e) {
			message = e.getMessage();
			status = HttpStatus.BAD_REQUEST;
		} finally {
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<String>(message, status);
	}
	
	@RequestMapping(value="/repositories", method=RequestMethod.DELETE)
	@ResponseBody
	public HttpEntity<String> deleteRepo(@RequestHeader("Authorization") String Authorization, @RequestParam String repo_name) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		String message = "Deleting repository fails";
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String ausername = us_pass[0];
		String apassword = us_pass[1];
		
		UserManager manager = null;
		
		try {
			
			manager = new UserManager(ausername, apassword);
			Role arole = manager.getUserRole(ausername);
			if (arole != Role.superAdmin) {
				status = HttpStatus.FORBIDDEN;
			} else {
				manager.deleteGraph(repo_name + "/model");
				manager.deleteGraph(repo_name + "/instance");
				status = HttpStatus.OK;
				message = "Deleting repository succeeds";
			}

		} catch (SQLException e) {
			e.printStackTrace();
		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} finally {
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<String>(message, status);
	}
	
	private static SimpleDateFormat logTimestamp = new SimpleDateFormat("HH:mm:ss ");
	
	private static void log(String message) {
		System.out.println(logTimestamp.format(new Date()) + message);
	}
	
	private String[] getUsernameAndPassword(String secret) {
		String[] result = null;
		if (secret != null && secret.toUpperCase().startsWith("BASIC ")) {
			try {
				String str = new String(Base64.decodeBase64(secret.substring(6)), "UTF-8");
				result = str.split(":");
				System.out.println(result);
			}
			catch (Exception e) { e.printStackTrace(); }
		}
		return result;
	}
}
