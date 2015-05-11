package controller;


import info.aduna.iteration.IteratorIteration;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;

//import model.Triple;
















import model.DBConnector;
import model.Instance;
import model.Triple;



import model.UserManager;

import org.openrdf.http.protocol.UnauthorizedException;
//import org.openrdf.OpenRDFException;
import org.openrdf.model.Graph;
import org.openrdf.model.Namespace;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.BNodeImpl;
import org.openrdf.model.impl.GraphImpl;
import org.openrdf.model.impl.LiteralImpl;
import org.openrdf.model.impl.StatementImpl;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.query.Binding;
import org.openrdf.query.BindingSet;
import org.openrdf.query.BooleanQuery;
import org.openrdf.query.GraphQuery;
import org.openrdf.query.GraphQueryResult;
import org.openrdf.query.Operation;
import org.openrdf.query.Query;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.query.Update;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.repository.config.RepositoryConfig;
import org.openrdf.repository.config.RepositoryConfigException;
import org.openrdf.repository.manager.LocalRepositoryManager;
import org.openrdf.repository.manager.RemoteRepositoryManager;
import org.openrdf.repository.manager.RepositoryManager;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.repository.util.RDFInserter;
import org.openrdf.rio.ParserConfig;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandler;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.RDFParser;
import org.openrdf.rio.RDFParser.DatatypeHandling;
import org.openrdf.rio.RDFWriter;
import org.openrdf.rio.Rio;
import org.openrdf.rio.UnsupportedRDFormatException;
import org.openrdf.sail.memory.MemoryStore;

/**
 * <p>
 * This sample application is intended to illustrate how to prepare, configure and run a <a
 * href="http://www.openrdf.org">Sesame</a> repository using the <a
 * href="http://www.ontotext.com/owlim/">OWLIM</a> SAIL. The basic operations are demonstrated in separate
 * methods: get namespaces, evaluate queries, add and delete statements, parse and load files, etc.
 * </p>
 * <p>
 * Addition and removal are performed only when the input parameter 'updates' is set to 'true'. Thus,
 * potentially slow and irrelevant delete operations are avoided in case the example is adapted for loading
 * large data-sets.
 * </p>
 * <p>
 * This application can be used also as an easy test-bed for loading and querying different ontologies and
 * data-sets without needing to build a separate application.
 * </p>
 * <p>
 * The command line parameters are given as key=value' pairs.
 * A full list of parameters is given in the online OWLIM documentation at http://owlim.ontotext.com 
 * </p>
 * 
 * Copyright (c) 2005-2012 Ontotext AD
 */
public class TripleStoreConnector {
	// Command line parameters
	public static String PARAM_CONFIG = "config";
	public static String PARAM_URL = "url";
	public static String PARAM_REPOSITORY = "repository";
	public static String PARAM_USERNAME = "";
	public static String PARAM_PASSWORD = "";

	// Query and miscellaneous parameters
	public static String PARAM_QUERYFILE = "queryfile";
	public static String PARAM_SHOWRESULTS = "showresults";
	public static String PARAM_SHOWSTATS = "showstats";
	public static String PARAM_UPDATES = "updates";

	// Export parameters
	public static String PARAM_EXPORT_FILE = "exportfile";
	public static String PARAM_EXPORT_FORMAT = "exportformat";
	public static String PARAM_EXPORT_TYPE = "exporttype";
	
	// Loading parameters
	public static String PARAM_PRELOAD = "preload";
	public static String PARAM_CONTEXT = "context";
	public static String PARAM_VERIFY = "verify";
	public static String PARAM_STOP_ON_ERROR = "stoponerror";
	public static String PARAM_PRESERVE_BNODES = "preservebnodes";
	public static String PARAM_DATATYPE_HANDLING = "datatypehandling";
	public static String PARAM_CHUNK_SIZE = "chunksize";
	
	public static String PARAM_PARALLEL_LOAD = "parallel";

	// The storage for the command line parameters
	private Map<String, String> parameters;

	// A map of namespace-to-prefix
	private Map<String, String> namespacePrefixes = new HashMap<String, String>();

	// The repository manager
	private RepositoryManager repositoryManager;

	// From repositoryManager.getRepository(...) - the actual repository we will
	// work with
	private Repository repository;

	// From repository.getConnection() - the connection through which we will
	// use the repository
	private RepositoryConnection repositoryConnection;

	// A flag to indicate whether query results should be output.
	private boolean showResults = false;
	
	private DBConnector connector;

	/**
	 * Constructor - uses a map of configuration parameters to initialise the application
	 * <ul>
	 * <li>uses the configuration file and repository ID to initialise a LocalRepositoryManager and
	 * instantiate a repository, OR</li>
	 * <li>initialises a RemoteRepositoryManager and connects to the remote repository given by the 'url'
	 * parameter</li>
	 * </ul>
	 * 
	 * @param parameters
	 *            a map of configuration parameters
	 * @throws UnauthorizedException 
	 */
	public TripleStoreConnector(Map<String, String> parameters, String username, String password) throws UnauthorizedException {

		this.parameters = parameters;

		log("===== Initialize and load imported ontologies =========");

		// Set the 'output results' flag
		showResults = isTrue(PARAM_SHOWRESULTS);

		String url = parameters.get(PARAM_URL);
		String repositoryId = null;
		if (url == null) {
			// The configuration file
			String configFilename = parameters.get(PARAM_CONFIG);
			File configFile = new File(configFilename);
			log("Using configuration file: " + configFile.getAbsolutePath());

			// Parse the configuration file, assuming it is in Turtle format
			Graph repositoryRdfDescription = null;

			try {
				repositoryRdfDescription = parseFile(configFile, RDFFormat.TURTLE, "http://example.org#");
			} catch (FileNotFoundException e) {
				log("The turtle configuration file '" + configFilename
						+ "' was not found, please check the '" + PARAM_CONFIG + "' parameter");
			} catch (Exception e) {
				log("An I/O error occurred while processing the configuration file '" + configFilename
						+ "': " + e.getMessage());
			}

			if (repositoryRdfDescription == null)
				System.exit(-1);

			// Look for the subject of the first matching statement for
			// "?s type Repository"
			final String repositoryUri = "http://www.openrdf.org/config/repository#Repository";
			final String repositoryIdUri = "http://www.openrdf.org/config/repository#repositoryID";
			Iterator<Statement> iter = repositoryRdfDescription.match(null, RDF.TYPE, new URIImpl(
					repositoryUri));
			Resource repositoryNode = null;
			if (iter.hasNext()) {
				Statement st = iter.next();
				repositoryNode = st.getSubject();
			}
			if (repositoryNode == null) {
				log("The turtle configuration file '"
						+ configFile.getName()
						+ "' does not contain a valid repository description, because it is missing a resource with rdf:type <"
						+ repositoryUri + ">");
				System.exit(-2);
			}

			// Get the repository ID (and ignore the one passed with the
			// 'repository' parameter
			iter = repositoryRdfDescription.match(repositoryNode, new URIImpl(repositoryIdUri), null);
			if (iter.hasNext()) {
				Statement st = iter.next();
				repositoryId = st.getObject().stringValue();
			} else {
				log("The turtle configuration file '" + configFile.getName()
						+ "' does not contain a valid repository description, because it is missing a <"
						+ repositoryUri + "> with a property <" + repositoryIdUri + ">");
				System.exit(-2);
			}

			try {
				// Create a manager for local repositories and initialise it
				repositoryManager = new LocalRepositoryManager(new File("."));
				repositoryManager.initialize();
			} catch (Exception e) {
				log("");
				System.exit(-3);
			}

			try {
				// Create a configuration object from the configuration file and
				// add
				// it to the repositoryManager
				RepositoryConfig repositoryConfig = RepositoryConfig.create(repositoryRdfDescription,
						repositoryNode);
				repositoryManager.addRepositoryConfig(repositoryConfig);
			} catch (Exception e) {
				log("Unable to process the repository configuration: " + e.getMessage());
				System.exit(-4);
			}
		} else {
			/*repositoryId = parameters.get(PARAM_REPOSITORY);
			if (repositoryId == null) {
				log("No repository ID specified. When using the '" + PARAM_URL
						+ "' parameter to specify a Sesame server, you must also use the '"
						+ PARAM_REPOSITORY + "' parameter to specify a repository on that server.");
				System.exit(-5);
			}
			try {
				// Create a manager for the remote Sesame server and initialise
				// it
				RemoteRepositoryManager remote = new RemoteRepositoryManager(url);

				String username = parameters.get(PARAM_USERNAME);
				String password = parameters.get(PARAM_PASSWORD);

				if (username != null || password != null) {
					if (username == null)
						username = "";
					if (password == null)
						password = "";
					remote.setUsernameAndPassword(username, password);
				}

				repositoryManager = remote;
				repositoryManager.initialize();
			} catch (RepositoryException e) {
				log("Unable to establish a connection with the Sesame server '" + url + "': "
						+ e.getMessage());
				System.exit(-5);
			}*/
			
			UserManager manager = new UserManager(username, password);
			connector = manager.getConnector();
			
		}

		// Get the repository to use
		/*try {
			repository = repositoryManager.getRepository(repositoryId);

			if (repository == null) {
				log("Unknown repository '" + repositoryId + "'");
				String message = "Please make sure that the value of the '" + PARAM_REPOSITORY
						+ "' parameter (current value '" + repositoryId + "') ";
				if (url == null) {
					message += "corresponds to the repository ID given in the configuration file identified by the '"
							+ PARAM_CONFIG
							+ "' parameter (current value '"
							+ parameters.get(PARAM_CONFIG)
							+ "')";
				} else {
					message += "identifies an existing repository on the Sesame server located at " + url;
				}
				log(message);
				System.exit(-6);
			}

			// Open a connection to this repository
			repositoryConnection = repository.getConnection();
			repositoryConnection.setAutoCommit(false);
		} catch (OpenRDFException e) {
			log("Unable to establish a connection to the repository '" + repositoryId + "': "
					+ e.getMessage());
			System.exit(-7);
		}*/
	}

	/**
	 * Parse the given RDF file and return the contents as a Graph
	 * 
	 * @param configurationFile
	 *            The file containing the RDF data
	 * @return The contents of the file as an RDF graph
	 * @throws RDFHandlerException
	 * @throws RDFParseException
	 * @throws IOException
	 */
	private Graph parseFile(File configurationFile, RDFFormat format, String defaultNamespace)
			throws RDFParseException, RDFHandlerException, IOException {
		Reader reader = new FileReader(configurationFile);

		final Graph graph = new GraphImpl();
		RDFParser parser = Rio.createParser(format);
		RDFHandler handler = new RDFHandler() {
			public void endRDF() throws RDFHandlerException {
			}

			public void handleComment(String arg0) throws RDFHandlerException {
			}

			public void handleNamespace(String arg0, String arg1) throws RDFHandlerException {
			}

			public void handleStatement(Statement statement) throws RDFHandlerException {
				graph.add(statement);
			}

			public void startRDF() throws RDFHandlerException {
			}
		};
		parser.setRDFHandler(handler);
		parser.parse(reader, defaultNamespace);
		return graph;
	}

	/**
	 * Parses and loads all files specified in PARAM_PRELOAD
	 */
	public void loadFiles() throws Exception {
		log(repositoryManager.getRepositoryIDs() + "");
		
		log("===== Load Files (from the '" + PARAM_PRELOAD + "' parameter) ==========");

		final AtomicLong statementsLoaded = new AtomicLong();
		
		// Load all the files from the pre-load folder
		String preload = parameters.get(PARAM_PRELOAD);

		if (preload == null)
			log("No pre-load directory/filename provided.");
		else {
			FileWalker.Handler handler = new FileWalker.Handler() {

				public void file(File file) throws Exception {
					statementsLoaded.addAndGet( loadFileChunked(file) );
				}

				public void directory(File directory) throws Exception {
					log("Loading files from: " + directory.getAbsolutePath());
				}
			};

			FileWalker walker = new FileWalker();
			walker.setHandler(handler);
			walker.walk(new File(preload));
		}
		
		log("TOTAL: " + statementsLoaded.get() + " statements loaded");
	}

	private long loadFileChunked(File file) throws Exception, IOException {
		
		//log("Loading file '" + file.getName());
		System.out.print("Loading " + file.getName() + " ");

		RDFFormat format = RDFFormat.forFileName(file.getName());
		
		if(format == null) {
			System.out.println();
			log("Unknown RDF format for file: " + file);
			return 0;
		}
		
		URI dumyBaseUrl = new URIImpl(file.toURI().toString());

		URI context = null;
		if(!format.equals(RDFFormat.NQUADS) && !format.equals(RDFFormat.TRIG) && ! format.equals(RDFFormat.TRIX)) {
			String contextParam = parameters.get(PARAM_CONTEXT);

			if (contextParam == null) {
				context = new URIImpl(file.toURI().toString());
			} else {
				if (contextParam.length() > 0) {
					context = new URIImpl(contextParam);
				}
			}
		}
		
		InputStream reader = null;
		try {
			if(file.getName().endsWith("gz")) {
				reader = new GZIPInputStream(new BufferedInputStream(new FileInputStream(file), 256 * 1024));
			}
			else {
				reader = new BufferedInputStream(new FileInputStream(file), 256 * 1024);
			}
			
			boolean verifyData = isTrue(PARAM_VERIFY);
			boolean stopAtFirstError = isTrue(PARAM_STOP_ON_ERROR);
			boolean preserveBnodeIds = isTrue(PARAM_PRESERVE_BNODES);
			DatatypeHandling datatypeHandling = stringToDatatypeHandling( parameters.get(PARAM_DATATYPE_HANDLING));
			long chunkSize = Long.parseLong(parameters.get(PARAM_CHUNK_SIZE));
			
			long start = System.currentTimeMillis();
			
			ParserConfig config = new ParserConfig(verifyData, stopAtFirstError, preserveBnodeIds, datatypeHandling);
			 
			RDFParser parser = Rio.createParser(format);
			parser.setParserConfig(config);
			
			// add our own custom RDFHandler to the parser. This handler takes care of adding
			// triples to our repository and doing intermittent commits
			ChunkCommitter handler = new ChunkCommitter(repositoryConnection, context, chunkSize);
			parser.setRDFHandler(handler);
			repositoryConnection.commit();
			repositoryConnection.begin();

			//Mitac hack: use parallel update
			if (parameters.get(PARAM_PARALLEL_LOAD).equals("true")) {
				URI up = new URIImpl("http://www.ontotext.com/useParallelInsertion");
				repositoryConnection.add(up, up, up);
			}
			
			parser.parse(reader, context == null ? dumyBaseUrl.toString() : context.toString());
			repositoryConnection.commit();
			long statementsLoaded = handler.getStatementCount();
			long time = System.currentTimeMillis() - start;
			System.out.println("Loaded " + statementsLoaded + " statements in " + time + " ms; avg speed = " + (statementsLoaded * 1000 / time) + " st/s");
			return statementsLoaded;
		} catch (Exception e) {
			repositoryConnection.rollback();
			System.out.println();
			log("Failed to load '" + file.getName() + "' (" + format.getName() + ")." + e);
			e.printStackTrace();
			return 0;
		} finally {
			if (reader != null)
				reader.close();
		}
	}

	/**
	 * This class is inspired by Jeen Broekstra
	 * http://rivuli-development.com/further-reading/sesame-cookbook/loading-large-file-in-sesame-native/
	 */
	static class ChunkCommitter implements RDFHandler {
		
		private final long chunkSize;
        private final RDFInserter inserter;
        private final RepositoryConnection conn;
        private final URI context;
        private final ValueFactory factory;
        
        private long count = 0L;
        
        public ChunkCommitter(RepositoryConnection conn, URI context, long chunkSize) {
        	this.chunkSize = chunkSize;
            this.context = context;
            this.conn = conn;
            this.factory = conn.getValueFactory();
            inserter = new RDFInserter(conn);
        }
        
        public long getStatementCount() {
        	return count;
        }
 
        public void startRDF() throws RDFHandlerException {
            inserter.startRDF();
        }
 
        public void endRDF() throws RDFHandlerException {
            inserter.endRDF();
        }
 
        public void handleNamespace(String prefix, String uri)
                throws RDFHandlerException {
            inserter.handleNamespace(prefix, uri);
        }
 
        public void handleStatement(Statement st) throws RDFHandlerException {
        	if(context !=null) {
        		st = factory.createStatement(st.getSubject(), st.getPredicate(), st.getObject(), context);
        	}
            inserter.handleStatement(st);
            count++;
            // do an intermittent commit whenever the number of triples
            // has reached a multiple of the chunk size
            if (count % chunkSize == 0) {
                try {
                    conn.commit();
                    System.out.print(".");
		    conn.begin();
                } catch (Exception e) {
                    throw new RDFHandlerException(e);
                }
            }
        }
 
        public void handleComment(String comment) throws RDFHandlerException {
            inserter.handleComment(comment);
        }
    }

	// A list of RDF file formats used in loadFile().
	private static final RDFFormat allFormats[] = new RDFFormat[] { RDFFormat.NTRIPLES, RDFFormat.N3,
			RDFFormat.RDFXML, RDFFormat.TURTLE, RDFFormat.TRIG, RDFFormat.TRIX, RDFFormat.NQUADS };

	private static RDFFormat stringToRDFFormat(String strFormat) {
		for (RDFFormat format : allFormats) {
			if (format.getName().equalsIgnoreCase(strFormat))
				return format;
		}
		throw new IllegalArgumentException("The RDF format '" + strFormat + "' is not recognised");
	}
	
	// A list of datatype handling strategies
	private static final DatatypeHandling allDatatypeHandling[] = new DatatypeHandling[] {
		DatatypeHandling.IGNORE, DatatypeHandling.NORMALIZE, DatatypeHandling.VERIFY
	};

	private static DatatypeHandling stringToDatatypeHandling(String strHandling) {
		for (DatatypeHandling handling : allDatatypeHandling) {
			if (handling.name().equalsIgnoreCase(strHandling))
				return handling;
		}
		throw new IllegalArgumentException("Datatype handling strategy for parsing '" + strHandling + "' is not recognised");
	}
	
	private boolean isTrue(String parameter) {
		return parameters.get(parameter).equalsIgnoreCase("true");
	}

	/**
	 * Show some initialisation statistics
	 */
	public void showInitializationStatistics(long startupTime) throws Exception {

		if (isTrue(PARAM_SHOWSTATS)) {
			long explicitStatements = numberOfExplicitStatements();
			long implicitStatements = numberOfImplicitStatements();

			log("Loaded: " + explicitStatements + " explicit statements.");
			log("Inferred: " + implicitStatements + " implicit statements.");

			if (startupTime > 0) {
				double loadSpeed = explicitStatements / (startupTime / 1000.0);
				log(" in " + startupTime + "ms.");
				log("Loading speed: " + loadSpeed + " explicit statements per second.");
			} else {
				log(" in less than 1 second.");
			}
			log("Total number of statements: " + (explicitStatements + implicitStatements));
		}
	}

	/**
	 * Two approaches for finding the total number of explicit statements in a repository.
	 * 
	 * @return The number of explicit statements
	 */
	private long numberOfExplicitStatements() throws Exception {

		// This call should return the number of explicit statements.
		long explicitStatements = repositoryConnection.size();

		// Another approach is to get an iterator to the explicit statements
		// (by setting the includeInferred parameter to false) and then counting
		// them.
		RepositoryResult<Statement> statements = repositoryConnection.getStatements(null, null, null, false);
		explicitStatements = 0;

		while (statements.hasNext()) {
			statements.next();
			explicitStatements++;
		}
		statements.close();
		return explicitStatements;
	}

	/**
	 * A method to count only the inferred statements in the repository. No method for this is available
	 * through the Sesame API, so OWLIM uses a special context that is interpreted as instruction to retrieve
	 * only the implicit statements, i.e. not explicitly asserted in the repository.
	 * 
	 * @return The number of implicit statements.
	 */
	private long numberOfImplicitStatements() throws Exception {
		// Retrieve all inferred statements
		RepositoryResult<Statement> statements = repositoryConnection.getStatements(null, null, null, true,
				new URIImpl("http://www.ontotext.com/implicit"));
		long implicitStatements = 0;

		while (statements.hasNext()) {
			statements.next();
			implicitStatements++;
		}
		statements.close();
		return implicitStatements;
	}

	/**
	 * Iterates and collects the list of the namespaces, used in URIs in the repository
	 */
	public void iterateNamespaces() throws Exception {
		log("===== Namespace List ==================================");

		log("Namespaces collected in the repository:");
		RepositoryResult<Namespace> iter = repositoryConnection.getNamespaces();

		while (iter.hasNext()) {
			Namespace namespace = iter.next();
			String prefix = namespace.getPrefix();
			String name = namespace.getName();
			namespacePrefixes.put(name, prefix);
			System.out.println(prefix + ":\t" + name);
		}
		iter.close();
	}

	/**
	 * Demonstrates query evaluation. First parse the query file. Each of the queries is executed against the
	 * prepared repository. If the printResults is set to true the actual values of the bindings are output to
	 * the console. We also count the time for evaluation and the number of results per query and output this
	 * information.
	 */
	public void evaluateQueries() throws Exception {
		log("===== Query Evaluation ======================");

		String queryFile = parameters.get(PARAM_QUERYFILE);
		if (queryFile == null) {
			log("No query file given in parameter '" + PARAM_QUERYFILE + "'.");
			return;
		}

		long startQueries = System.currentTimeMillis();

		// process the query file to get the queries
		String[] queries = collectQueries(queryFile);

		// evaluate each query and print the bindings if appropriate
		for (int i = 0; i < queries.length; i++) {
			final String name = queries[i].substring(0, queries[i].indexOf(":"));
			final String query = queries[i].substring(name.length() + 2).trim();
			log("Executing query '" + name + "'");

			executeSingleQuery(query);
		}

		long endQueries = System.currentTimeMillis();
		log("Queries run in " + (endQueries - startQueries) + " ms.");
	}

	/**
	 * The purpose of this method is to try to parse an operation locally in order to determine if it is a
	 * tuple (SELECT), boolean (ASK) or graph (CONSTRUCT/DESCRIBE) query, or even a SPARQL update.
	 * This happens automatically if the repository is local, but for a remote repository the local
	 * HTTPClient-side can not work it out.
	 * Therefore a temporary in memory SAIL is used to determine the operation type.
	 * 
	 * @param query
	 *            Query string to be parsed
	 * @param language
	 *            The query language to assume
	 * @return A parsed query object or null if not possible
	 * @throws RepositoryException
	 *             If the local repository used to test the query type failed for some reason
	 */
	private Query prepareQuery(String query, QueryLanguage language, RepositoryConnection tempLocalConnection) throws Exception {
		try {
			tempLocalConnection.prepareTupleQuery(language, query);
			return repositoryConnection.prepareTupleQuery(language, query);
		} catch (Exception e) {
		}

		try {
			tempLocalConnection.prepareBooleanQuery(language, query);
			return repositoryConnection.prepareBooleanQuery(language, query);
		} catch (Exception e) {
		}

		try {
			tempLocalConnection.prepareGraphQuery(language, query);
			return repositoryConnection.prepareGraphQuery(language, query);
		} catch (Exception e) {
		}

		return null;
	}

	private Operation prepareOperation(String query) throws Exception {
		Repository tempLocalRepository = new SailRepository(new MemoryStore());
		tempLocalRepository.initialize();
		RepositoryConnection tempLocalConnection = tempLocalRepository.getConnection();
		
		try {
			tempLocalConnection.prepareUpdate(QueryLanguage.SPARQL, query);
			return repositoryConnection.prepareUpdate(QueryLanguage.SPARQL, query);
		}
		catch(Exception e ) {
		}

		try {
			for (QueryLanguage language : queryLanguages) {
				Query result = prepareQuery(query, language, tempLocalConnection);
				if (result != null)
					return result;
			}
			// Can't prepare this query in any language
			return null;
		}
		finally {
			try {
				tempLocalConnection.close();
				tempLocalRepository.shutDown();
			}
			catch(Exception e ) {
			}
		}
	}
	
	public List<Triple> getAllTriples() throws Exception {
		List<Triple> results = new ArrayList<Triple>();
		
		RepositoryResult<Statement> iter = repositoryConnection.getStatements(null, null, null, true);
		boolean retrieved = false;
		while (iter.hasNext()) {
			Statement statement = iter.next();
			retrieved = true;
			Triple triple = new Triple();
			triple.setSubject(beautifyRDFValue(statement.getSubject()));
			triple.setPredicate(beautifyRDFValue(statement.getPredicate()));
			triple.setObject(beautifyRDFValue(statement.getObject()));
			System.out.println(beautifyStatement(iter.next()));
			results.add(triple);
		}
		// CLOSE the iterator to avoid memory leaks
		iter.close();
		
		return results;
	}

	private static final QueryLanguage[] queryLanguages = new QueryLanguage[] { QueryLanguage.SPARQL,
			QueryLanguage.SERQL, QueryLanguage.SERQO };
	
	/*public List<Instance> queryInstances(String query) throws QueryEvaluationException {
		List<Instance> results = new ArrayList<Instance>();
		Operation preparedOperation = executeSingleQuery(query);
		if (preparedOperation != null) {
			if (preparedOperation instanceof TupleQuery) {
				TupleQuery q = (TupleQuery) preparedOperation;
				long queryBegin = System.nanoTime();

				TupleQueryResult result = q.evaluate();

				int rows = 0;
				while (result.hasNext()) {
					BindingSet tuple = result.next();
					if (rows == 0) {
						for (Iterator<Binding> iter = tuple.iterator(); iter.hasNext();) {
							System.out.print(iter.next().getName());
							System.out.print("\t");
						}
						System.out.println();
						System.out.println("---------------------------------------------");
					}
					rows++;
					for (Iterator<Binding> iter = tuple.iterator(); iter.hasNext();) {
						try {
							String r = beautifyRDFValue(iter.next().getValue());
							if (showResults) {
								System.out.print(r + "\t");
							}
							Instance ins = new Instance();
							ins.setValue(r);
							results.add(ins);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
				if (showResults)
					System.out.println();

				result.close();

				long queryEnd = System.nanoTime();
				log(rows + " result(s) in " + (queryEnd - queryBegin) / 1000000 + "ms.");
			}
		}
		return results;
		
		return connector.queryInstances(query);
	}
	
	public List<Triple> queryTriples(String query) throws Exception {
		List<Triple> results = new ArrayList<Triple>();
		Operation preparedOperation = executeSingleQuery(query);
		if (preparedOperation != null) {
			if (preparedOperation instanceof TupleQuery) {
				TupleQuery q = (TupleQuery) preparedOperation;
				long queryBegin = System.nanoTime();

				TupleQueryResult result = q.evaluate();
				int rows = 0;
				while (result.hasNext()) {
					BindingSet tuple = result.next();
					rows++;
					
					Triple triple = new Triple();
					triple.setSubject(beautifyRDFValue(tuple.getValue("subject")));
					triple.setPredicate(beautifyRDFValue(tuple.getValue("predicate")));
					triple.setObject(beautifyRDFValue(tuple.getValue("object")));
					results.add(triple);
				}

				result.close();

				long queryEnd = System.nanoTime();
				log(rows + " result(s) in " + (queryEnd - queryBegin) / 1000000 + "ms.");
			}
		}
		return results;
		
		return connector.queryTriples(query);
	}*/
	
	public List<Map<String, String>> queryTuples(String query) throws IllegalArgumentException {
		/*List<Triple> results = new ArrayList<Triple>();
		Operation preparedOperation = executeSingleQuery(query);
		if (preparedOperation != null) {
			if (preparedOperation instanceof TupleQuery) {
				TupleQuery q = (TupleQuery) preparedOperation;
				long queryBegin = System.nanoTime();

				TupleQueryResult result = q.evaluate();
				int rows = 0;
				while (result.hasNext()) {
					BindingSet tuple = result.next();
					rows++;
					
					Triple triple = new Triple();
					triple.setSubject(beautifyRDFValue(tuple.getValue("subject")));
					triple.setPredicate(beautifyRDFValue(tuple.getValue("predicate")));
					triple.setObject(beautifyRDFValue(tuple.getValue("object")));
					results.add(triple);
				}

				result.close();

				long queryEnd = System.nanoTime();
				log(rows + " result(s) in " + (queryEnd - queryBegin) / 1000000 + "ms.");
			}
		}
		return results;*/
		
		
		try {
			return connector.queryTuples(query);
		} catch (SQLException e) {
			throw new IllegalArgumentException(e);
		}
	}
	
	public void update(String query) throws IllegalArgumentException {
		try {
			connector.update(query);
		} catch (SQLException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private Operation executeSingleQuery(String query) {
		
		try {
			Operation preparedOperation = prepareOperation(query);
			if (preparedOperation == null) {
				log("Unable to parse query: " + query);
				return null;
			}

			if( preparedOperation instanceof Update ) {
				( (Update) preparedOperation).execute();
				repositoryConnection.commit();
				return null;
			}

			if (preparedOperation instanceof BooleanQuery) {
				log("Result: " + ((BooleanQuery) preparedOperation).evaluate());
				return null;
			}

			return preparedOperation;
		} catch (Throwable e) {
			log("An error occurred during query execution: " + e.getMessage());
		}
		
		return null;
	}

	/**
	 * Creates a statement and adds it to the repository. Then deletes this statement and checks to make sure
	 * it is gone.
	 */
	private void insertAndDeleteStatement() throws Exception {
		if (isTrue(PARAM_UPDATES)) {
			log("===== Upload and Delete Statements ====================");

			// Add a statement directly to the SAIL
			log("----- Upload and check --------------------------------");
			// first, create the RDF nodes for the statement
			URI subj = repository.getValueFactory().createURI("http://example.org/owlim#Pilot");
			URI pred = RDF.TYPE;
			URI obj = repository.getValueFactory().createURI("http://example.org/owlim#Man");

			repositoryConnection.add(subj, pred, obj);
			
			repositoryConnection.commit();

			// Now check whether the new statement can be retrieved
			RepositoryResult<Statement> iter = repositoryConnection.getStatements(subj, null, obj, true);
			boolean retrieved = false;
			while (iter.hasNext()) {
				retrieved = true;
				System.out.println(beautifyStatement(iter.next()));
			}
			// CLOSE the iterator to avoid memory leaks
			iter.close();

			if (!retrieved)
				log("**** Failed to retrieve the statement that was just added. ****");

			// Remove the above statement in a separate transaction
			log("----- Remove and check --------------------------------");
			repositoryConnection.remove(subj, pred, obj);
			repositoryConnection.commit();

			// Check whether there is some statement matching the subject of the
			// deleted one
			iter = repositoryConnection.getStatements(subj, null, null, true);
			retrieved = false;
			while (iter.hasNext()) {
				retrieved = true;
				System.out.println(beautifyStatement(iter.next()));
			}
			// CLOSE the iterator to avoid memory leaks
			iter.close();
			if (retrieved)
				log("**** Statement was not deleted properly in last step. ****");
		}
	}
	
	public boolean insert(List<Triple> triples) throws Exception {
		List<Statement> statements = new ArrayList<Statement>();
		for (Triple triple : triples) {
			
			if (triple.getSubject() == null || triple.getPredicate() == null || triple.getObject() == null) {
				throw new NullPointerException();
			}
			URI subj = repository.getValueFactory().createURI(triple.getSubject());
			URI pred = repository.getValueFactory().createURI(triple.getPredicate());
			URI obj = repository.getValueFactory().createURI(triple.getObject());
			
			Statement statement = new StatementImpl(subj, pred, obj);
			statements.add(statement);
		}
		repositoryConnection.add(statements);
		repositoryConnection.commit(); 
		
		return true;
	}
	
	public boolean delete(List<Triple> triples) throws Exception {
		List<Statement> statements = new ArrayList<Statement>();
		for (Triple triple : triples) {
			
			URI subj = null;
			URI pred = null;
			URI obj = null;
			
			if (triple.getSubject() != null) {
				subj = repository.getValueFactory().createURI(triple.getSubject());
			}
			if (triple.getPredicate() != null) {
				pred = repository.getValueFactory().createURI(triple.getPredicate());
			}
			if (triple.getObject() != null) {
				obj = repository.getValueFactory().createURI(triple.getObject());
			}
			
			Statement statement = new StatementImpl(subj, pred, obj);
			statements.add(statement);
		}
		repositoryConnection.remove(statements);
		repositoryConnection.commit();
		
		return true;
	}

	/**
	 * Export the contents of the repository (explicit, implicit or all statements) to the given filename in
	 * the given RDF format,
	 */
	public void export() throws Exception, UnsupportedRDFormatException, IOException,
			RDFHandlerException {
		String filename = parameters.get(PARAM_EXPORT_FILE);
		if (filename != null) {
			log("===== Export ====================");
			RDFFormat exportFormat = stringToRDFFormat(parameters.get(PARAM_EXPORT_FORMAT));
			
			String type = parameters.get(PARAM_EXPORT_TYPE);

			log("Exporting " + type + " statements to " + filename + " (" + exportFormat.getName() + ")");
			
			Writer writer = new BufferedWriter(new FileWriter(filename), 256 * 1024);
			RDFWriter rdfWriter = Rio.createWriter(exportFormat, writer);

			// This approach to making a backup of a repository by using RepositoryConnection.exportStatements()
			// will work even for very large remote repositories, because the results are streamed to the client
			// and passed directly to the RDFHandler.
			// However, it is not possible to give any indication of progress using this method.
			
			try {
				if (type == null || type.equalsIgnoreCase("explicit"))
					repositoryConnection.exportStatements(null, null, null, false, rdfWriter);
				else if (type.equalsIgnoreCase("all"))
					repositoryConnection.exportStatements(null, null, null, true, rdfWriter);
				else if (type.equalsIgnoreCase("implicit"))
					repositoryConnection.exportStatements(null, null, null, true, rdfWriter,
							new URIImpl( "http://www.ontotext.com/implicit"));
				else {
					log("Unknown export type '" + type + "' - valid values are: explicit, implicit, all");
					return;
				}
				
	
				// This approach to making a backup of a repository by using RepositoryConnection.getNamespaces()
				// and RepositoryConnection.getStatements() allows the client to output progress information.
				// However, although results are streamed for a local repository, the HTTPRepository
				// implementation collects all results before returning from getStatements(), hence this approach
				// is not suitable for exporting large datasets from remote repositories.
				
//				rdfWriter.startRDF();
//
//				// Namespaces
//				RepositoryResult<Namespace> namespaces = repositoryConnection.getNamespaces();
//				while (namespaces.hasNext()) {
//					Namespace namespace = namespaces.next();
//					rdfWriter.handleNamespace(namespace.getPrefix(), namespace.getName());
//				}
//				namespaces.close();
//
//				// Statements
//				RepositoryResult<Statement> statements;
//				if (type == null || type.equalsIgnoreCase("explicit"))
//					statements = repositoryConnection.getStatements(null, null, null, false);
//				else if (type.equalsIgnoreCase("all"))
//					statements = repositoryConnection.getStatements(null, null, null, true);
//				else if (type.equalsIgnoreCase("implicit"))
//					statements = repositoryConnection.getStatements(null, null, null, true, new URIImpl(
//							"http://www.ontotext.com/implicit"));
//				else {
//					log("Unknown export type '" + type + "' - valid values are: explicit, implicit, all");
//					return;
//				}
//				
//				long count = 0;
//
//				while (statements.hasNext()) {
//					Statement statement = statements.next();
//					rdfWriter.handleStatement(statement);
//					++count;
//					if(count % 1000000 == 0) {
//						System.out.print(".");
//					}
//				}
//				System.out.println();
//				statements.close();
//				
//				log("Exported " + count + " statements");
//
//				rdfWriter.endRDF();
			}
			finally {
				writer.close();
			}
		}
	}

	/**
	 * Shutdown the repository and flush unwritten data.
	 */
	public void shutdown() {
		log("===== Shutting down ==========");
		/*if (repository != null) {
			try {
				System.out.println("NumberOfStatements=" + repositoryConnection.size());
				repositoryConnection.close();
				repository.shutDown();
				repositoryManager.shutDown();
			} catch (Exception e) {
				log("An exception occurred during shutdown: " + e.getMessage());
			}
		}*/
		
		connector.closeConnection();
	}

	/**
	 * Auxiliary method, printing an RDF value in a "fancy" manner. In case of URI, qnames are printed for
	 * better readability
	 * 
	 * @param value
	 *            The value to beautify
	 */
	public String beautifyRDFValue(Value value) throws Exception {
		if (value instanceof URI) {
			URI u = (URI) value;
			String namespace = u.getNamespace();
			String prefix = namespacePrefixes.get(namespace);
			if (prefix == null) {
				prefix = u.getNamespace();
			} else {
				prefix += ":";
			}
			return prefix + u.getLocalName();
		} else {
			return value.toString();
		}
	}

	/**
	 * Auxiliary method, nicely format an RDF statement.
	 * 
	 * @param statement
	 *            The statement to be formatted.
	 * @return The beautified statement.
	 */
	public String beautifyStatement(Statement statement) throws Exception {
		return beautifyRDFValue(statement.getSubject()) + " " + beautifyRDFValue(statement.getPredicate())
				+ " " + beautifyRDFValue(statement.getObject());
	}

	/**
	 * Parse the query file and return the queries defined there for further evaluation. The file can contain
	 * several queries; each query starts with an id enclosed in square brackets '[' and ']' on a single line;
	 * the text in between two query ids is treated as a SeRQL query. Each line starting with a '#' symbol
	 * will be considered as a single-line comment and ignored. Query file syntax example:
	 * 
	 * #some comment [queryid1] <query line1> <query line2> ... <query linen> #some other comment
	 * [nextqueryid] <query line1> ... <EOF>
	 * 
	 * @param queryFile
	 * @return an array of strings containing the queries. Each string starts with the query id followed by
	 *         ':', then the actual query string
	 */
	private static String[] collectQueries(String queryFile) throws Exception {
		try {
			List<String> queries = new ArrayList<String>();
			BufferedReader input = new BufferedReader(new FileReader(queryFile));
			String nextLine = null;

			for (;;) {
				String line = nextLine;
				nextLine = null;
				if (line == null) {
					line = input.readLine();
				}
				if (line == null) {
					break;
				}
				line = line.trim();
				if (line.length() == 0) {
					continue;
				}
				if (line.startsWith("#")) {
					continue;
				}
				if (line.startsWith("^[") && line.endsWith("]")) {
					StringBuffer buff = new StringBuffer(line.substring(2, line.length() - 1));
					buff.append(": ");

					for (;;) {
						line = input.readLine();
						if (line == null) {
							break;
						}
						line = line.trim();
						if (line.length() == 0) {
							continue;
						}
						if (line.startsWith("#")) {
							continue;
						}
						if (line.startsWith("^[")) {
							nextLine = line;
							break;
						}
						buff.append(line);
						buff.append(System.getProperty("line.separator"));
					}

					queries.add(buff.toString());
				}
			}

			String[] result = new String[queries.size()];
			for (int i = 0; i < queries.size(); i++) {
				result[i] = queries.get(i);
			}
			input.close();
			return result;
		} catch (Exception e) {
			log("Unable to load query file '" + queryFile + "':" + e);
			return new String[0];
		}
	}

	private static SimpleDateFormat logTimestamp = new SimpleDateFormat("HH:mm:ss ");

	private static void log(String message) {
		System.out.println(logTimestamp.format(new Date()) + message);
	}
	
	
	public static Parameters initiateParameters(String[] args) {
		// Special handling for JAXP XML parser that limits entity expansion
		// see
		// http://java.sun.com/j2se/1.5.0/docs/guide/xml/jaxp/JAXP-Compatibility_150.html#JAXP_security
		
		if (args == null) {
			args = new String[0];
		}
		
		System.setProperty("entityExpansionLimit", "1000000");

		// Parse all the parameters
		Parameters params = new Parameters(args);

		// Set default values for missing parameters
		params.setDefaultValue(PARAM_CONFIG, "./owlim.ttl");
		params.setDefaultValue(PARAM_SHOWRESULTS, "false");
		params.setDefaultValue(PARAM_SHOWSTATS, "false");
		params.setDefaultValue(PARAM_UPDATES, "true");
		params.setDefaultValue(PARAM_QUERYFILE, "./queries/sample.sparql");
		params.setDefaultValue(PARAM_EXPORT_FORMAT, RDFFormat.NTRIPLES.getName());

		params.setDefaultValue(PARAM_PRELOAD, "./preload");
		params.setDefaultValue(PARAM_VERIFY, "true");
		params.setDefaultValue(PARAM_STOP_ON_ERROR, "true");
		params.setDefaultValue(PARAM_PRESERVE_BNODES, "true");
		params.setDefaultValue(PARAM_DATATYPE_HANDLING, DatatypeHandling.VERIFY.name());
		params.setDefaultValue(PARAM_CHUNK_SIZE, "500000");

		params.setDefaultValue(PARAM_PARALLEL_LOAD, "false");
		params.setDefaultValue(PARAM_URL, "http://localhost:8080/openrdf-sesame");
		params.setDefaultValue(PARAM_REPOSITORY, "mining_metadata_repo");

		log("Using parameters:");
		log(params.toString());
		
		return params;
	}

	/**
	 * This is the entry point of the example application. First, the command-line parameters are intialised.
	 * Then these parameters are passed to an instance of the GettingStarted application and used to create,
	 * initialise and login to the local instance of Sesame.
	 * 
	 * @param args
	 *            Command line parameters
	 */
	/*public static void main(String[] args) {

		TripleStoreConnector gettingStartedApplication = null;
		try {
			long initializationStart = System.currentTimeMillis();
			// The ontologies and datasets specified in the 'import' parameter
			// of the Sesame configuration file are loaded during
			// initialization.
			// Thus, for large datasets the initialisation could take
			// considerable time.
			gettingStartedApplication = new TripleStoreConnector(initiateParameters(args).getParameters());

			// Demonstrate the basic operations on a repository
			gettingStartedApplication.loadFiles();
			gettingStartedApplication.showInitializationStatistics(System.currentTimeMillis()
					- initializationStart);
			gettingStartedApplication.iterateNamespaces();
			gettingStartedApplication.evaluateQueries();
			gettingStartedApplication.insertAndDeleteStatement();
			gettingStartedApplication.export();
		} catch (Throwable ex) {
			log("An exception occured at some point during execution:");
			ex.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
		}
	}*/

	/**
	 * Utility to read parameters from a string of name-value pairs or an array of string, each containing a
	 * name-value pair.
	 */
	static public class Parameters {

		/**
		 * Construct the parameters from a string
		 * 
		 * @param allNameValuePairs
		 *            A string of the form "param1=name1 param2=name2".
		 * @param pairSeparators
		 *            A list of characters that separate the name-value pairs, e.g. <space><tab><cr><lf>
		 * @param nameValueSeparator
		 *            The character that separates the name from the values, e.g. '='
		 */
		public Parameters(String allNameValuePairs, String pairSeparators, char nameValueSeparator) {
			StringTokenizer tokeniser = new StringTokenizer(allNameValuePairs, pairSeparators);

			int numTokens = tokeniser.countTokens();
			String[] nameValuePairs = new String[numTokens];
			for (int i = 0; i < numTokens; ++i)
				nameValuePairs[i] = tokeniser.nextToken();
			parseNameValuePairs(nameValuePairs, nameValueSeparator, true);
		}

		/**
		 * Construct the parameters from an array of name-value pairs, e.g. from "main( String[] args )"
		 * 
		 * @param nameValuePairs
		 *            The array of name-value pairs
		 * @param separator
		 *            The character that separates the name from its value
		 */
		public Parameters(String[] nameValuePairs, char separator) {
			parseNameValuePairs(nameValuePairs, separator, true);
		}

		/**
		 * Construct the parameters from an array of name-value pairs using equals '=' as the separator.
		 * 
		 * @param nameValuePairs
		 *            The array of name-value pairs
		 */
		public Parameters(String[] nameValuePairs) {
			parseNameValuePairs(nameValuePairs, '=', false);
		}

		/**
		 * Get the value associated with a parameter.
		 * 
		 * @param name
		 *            The name of the parameter.
		 * @return The value associated with the parameter.
		 */
		public String getValue(String name) {
			return mParameters.get(name);
		}

		/**
		 * Get the value associated with a parameter or return the given default if it is not available.
		 * 
		 * @param name
		 *            The name of the parameter.
		 * @param defaultValue
		 *            The default value to return.
		 * @return The value associated with the parameter.
		 */
		public String getValue(String name, String defaultValue) {
			String value = getValue(name);

			if (value == null)
				value = defaultValue;

			return value;
		}

		/**
		 * Associate the given value with the given parameter name.
		 * 
		 * @param name
		 *            The name of the parameter.
		 * @param value
		 *            The value of the parameter.
		 */
		public void setValue(String name, String value) {
			mParameters.put(name.trim().toLowerCase(), value);
		}

		/**
		 * Set a default value, i.e. set this parameter to have the given value ONLY if it has not already
		 * been set.
		 * 
		 * @param name
		 *            The name of the parameter.
		 * @param value
		 *            The value of the parameter.
		 */
		public void setDefaultValue(String name, String value) {
			if (getValue(name) == null)
				setValue(name, value);
		}

		/**
		 * The parse method that accepts an array of name-value pairs.
		 * 
		 * @param nameValuePairs
		 *            An array of name-value pairs, where each string is of the form:
		 *            "<name>'separator'<value>"
		 * @param separator
		 *            The character that separates the name from the value
		 * @param overWrite
		 *            true if the parsed values should overwrite existing value
		 */
		public void parseNameValuePairs(String[] nameValuePairs, char separator, boolean overWrite) {
			for (String pair : nameValuePairs) {
				int pos = pair.indexOf(separator);
				if (pos < 0)
					throw new IllegalArgumentException("Invalid name-value pair '" + pair
							+ "', expected <name>" + separator + "<value>");
				String name = pair.substring(0, pos).toLowerCase();
				String value = pair.substring(pos + 1);
				if (overWrite)
					setValue(name, value);
				else
					setDefaultValue(name, value);
			}
		}

		/**
		 * Get the name-value pairs as a Map<String,String>
		 * 
		 * @return
		 */
		public Map<String, String> getParameters() {
			return mParameters;
		}

		@Override
		public String toString() {
			StringBuilder result = new StringBuilder();

			List<String> keys = new ArrayList<String>(mParameters.keySet());
			Collections.sort(keys);

			for (String key : keys) {
				result.append(key).append('=').append(mParameters.get(key))
						.append(System.getProperties().get("line.separator"));
			}

			return result.toString();
		}

		private final Map<String, String> mParameters = new HashMap<String, String>();
	}

	/**
	 * Utility for a depth first traversal of a file-system starting from a given node (file or directory).
	 */
	public static class FileWalker {

		/**
		 * The call back interface for traversal.
		 */
		public interface Handler {
			/**
			 * Called to notify that a normal file has been encountered.
			 * 
			 * @param file
			 *            The file encountered.
			 */
			void file(File file) throws Exception;

			/**
			 * Called to notify that a directory has been encountered.
			 * 
			 * @param directory
			 *            The directory encountered.
			 */
			void directory(File directory) throws Exception;
		}

		/**
		 * Set the notification handler.
		 * 
		 * @param handler
		 *            The object that receives notifications of encountered nodes.
		 */
		public void setHandler(Handler handler) {
			this.handler = handler;
		}

		/**
		 * Start the walk at the given location, which can be a file, for a very short walk, or a directory
		 * which will be traversed recursively.
		 * 
		 * @param node
		 *            The starting point for the walk.
		 */
		public void walk(File node) throws Exception {
			if (node.isDirectory()) {
				handler.directory(node);
				File[] children = node.listFiles();
				Arrays.sort(children, new Comparator<File>() {

					public int compare(File lhs, File rhs) {
						return lhs.getName().compareTo(rhs.getName());
					}

				});
				for (File child : children) {
					walk(child);
				}
			} else {
				handler.file(node);
			}
		}

		private Handler handler;
	}
}
