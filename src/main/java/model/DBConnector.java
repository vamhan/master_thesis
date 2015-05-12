package model;

import java.sql.CallableStatement;
import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.openrdf.http.protocol.UnauthorizedException;
import org.openrdf.query.Binding;
import org.openrdf.query.BindingSet;
import org.openrdf.query.Operation;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;

import virtuoso.jdbc4.*;

public class DBConnector {

	static final String urlDB = "jdbc:virtuoso://localhost:1111";
	static final String username = "dba";
	static final String password = "dba";
	static final String database = "";

	Connection conn;

	public void connect() {
		try {
			connect(username, password);
		} catch (UnauthorizedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void connect(String username, String password) throws UnauthorizedException {
		try {
			if (conn == null || conn.isClosed()) {
				Class.forName("virtuoso.jdbc4.Driver");
				conn = DriverManager.getConnection(urlDB, username, password);
				System.out.println("DB connect success");
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
			throw new UnauthorizedException();
		}
	}

	/*public List<Instance> queryInstances(String query) {
		List<Instance> results = new ArrayList<Instance>();
		try {
			Statement stmt = conn.createStatement();
			stmt.execute(query);
			ResultSet rs = stmt.getResultSet();
			while (rs.next()) {
				String s = rs.getString(1);
				Object o = ((VirtuosoResultSet) rs).getObject(1);
				Instance ins = new Instance();
				if (o instanceof VirtuosoExtendedString) {
					VirtuosoExtendedString vs = (VirtuosoExtendedString) o;
					if (vs.iriType == VirtuosoExtendedString.IRI
							&& (vs.strType & 0x01) == 0x01) {
						ins.setValue("<" + vs.str + ">");
					} else if (vs.iriType == VirtuosoExtendedString.BNODE) {
						ins.setValue("<" + vs.str + ">");
					}
				} else if (o instanceof VirtuosoRdfBox) {
					VirtuosoRdfBox rb = (VirtuosoRdfBox) o;
					ins.setValue(rb.rb_box + " lang=" + rb.getLang() + " type="
							+ rb.getType());
				} else if (stmt.getResultSet().wasNull()) {
					ins.setValue("NULL");
				} else {
					ins.setValue(s);
				}
				results.add(ins);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return results;
	}

	public List<Triple> queryTriples(String query) {
		List<Triple> results = new ArrayList<Triple>();
		try {
			Statement stmt = conn.createStatement();
			stmt.execute(query);

			ResultSetMetaData data = stmt.getResultSet().getMetaData();
			ResultSet rs = stmt.getResultSet();
			while (rs.next()) {
				Triple triple = new Triple();
				for (int i = 1; i <= data.getColumnCount(); i++) {
					String s = rs.getString(i);
					Object o = ((VirtuosoResultSet) rs).getObject(i);
					String value = "";
					if (o instanceof VirtuosoExtendedString) {
						VirtuosoExtendedString vs = (VirtuosoExtendedString) o;
						if (vs.iriType == VirtuosoExtendedString.IRI
								&& (vs.strType & 0x01) == 0x01) {
							value = "<" + vs.str + ">";
						} else if (vs.iriType == VirtuosoExtendedString.BNODE) {
							value = "<" + vs.str + ">";
						}
					} else if (o instanceof VirtuosoRdfBox) {
						VirtuosoRdfBox rb = (VirtuosoRdfBox) o;
						value = rb.rb_box + " lang=" + rb.getLang() + " type="
								+ rb.getType();
					} else if (stmt.getResultSet().wasNull()) {
						value = "NULL";
					} else {
						value = s;
					}
					if (i == 1) {
						triple.setSubject(value);
					} else if (i == 2) {
						triple.setPredicate(value);
					} else if (i == 3) {
						triple.setObject(value);
					}
				}
				results.add(triple);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return results;
	}*/

	public List<Map<String, String>> queryTuples(String query) throws SQLException {
		List<Map<String, String>> results = new ArrayList<Map<String, String>>();
		Statement stmt = conn.createStatement();
		stmt.execute(query);
		
		try {

			ResultSetMetaData data = stmt.getResultSet().getMetaData();
			ResultSet rs = stmt.getResultSet();
			while (rs.next()) {
				Map<String, String> tuple = new HashMap<String, String>();
				for (int i = 1; i <= data.getColumnCount(); i++) {
					if (!data.getColumnName(i).equals("datatype")) {
						String s = rs.getString(i);
						Object o = ((VirtuosoResultSet) rs).getObject(i);
						String value = "";
						if (o instanceof VirtuosoExtendedString) {
							VirtuosoExtendedString vs = (VirtuosoExtendedString) o;
							if (vs.iriType == VirtuosoExtendedString.IRI
									&& (vs.strType & 0x01) == 0x01) {
								value = "<" + vs.str + ">";
							} else if (vs.iriType == VirtuosoExtendedString.BNODE) {
								value = "<" + vs.str + ">";
							}
						} else if (o instanceof VirtuosoRdfBox) {
							VirtuosoRdfBox rb = (VirtuosoRdfBox) o;
							value = rb.rb_box + " lang=" + rb.getLang() + " type=" + (rs.getString("datatype") == null ? "http://www.w3.org/2001/XMLSchema#string" : rs.getString("datatype"));
						} else if (stmt.getResultSet().wasNull()) {
							value = "NULL";
						} else {
							value = s + " lang=null type=" + (rs.getString("datatype") == null ? "http://www.w3.org/2001/XMLSchema#string" : rs.getString("datatype"));
						}
						tuple.put(data.getColumnName(i), value);
					}
				}
				results.add(tuple);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return results;
	}
	
	public ResultSet query(String sql) {
		ResultSet rs;
		try {
			Statement sta = conn.createStatement();
			rs = sta.executeQuery(sql);
		} catch (SQLException e) {
			rs = null;
			e.printStackTrace();
		}
		return rs;
	}

	public void update(String sql) throws SQLException {
		Statement sta = conn.createStatement();
		sta.executeUpdate(sql);
		System.out.print(sql);
	}

	public void closeConnection() {
		try {
			if (conn != null) {
				conn.close();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	private static String convertFromUTF8(String s) {
        String out = null;
        try {
            out = new String(s.getBytes("ISO-8859-1"), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return null;
        }
        return out;
    }
}
