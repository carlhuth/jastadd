import jastadd.JastAdd;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

public class RunTests {

	protected final static int MAX_TESTS = 250;
	protected static boolean verbose = true; //TODO When verbose is true, all tests will be reported as failed. This should be fixed.

	public static void main(String[] args) {
		if(args.length == 1)
			runTest(args[0], false);
		else if(args.length == 2 && args[1].equals("true"))
			runTest(args[0], true);
		else {
			for(int i = 1; i < MAX_TESTS; i++)
				runTest("test/Test" + i, false);
			//runTest("test/Test42", false);
		}
		
	}

	protected static void runTest(String testName, boolean verbose) {
		RunTests.verbose = verbose;
		runTest(testName);
	}

	protected static void runTest(String testName) {
		// check that test case exists
		if(!new File(testName  + ".java").exists())
			return;
		try {
			System.err.println("Running test " + testName);
			System.out.println(testName + ".java");

			removeGeneratedFiles();
			
			// redirect output stream
			
			PrintStream out = System.out;
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			PrintStream ps = new PrintStream(os);
			if(!verbose)
				System.setOut(ps);
			
			// run JastAdd to build .java files for test case
			// DONE: There should be a success check after this call
			if (!generateJavaFiles(testName)) {
				System.out.println("Error: Failed to generate java files from JastAdd specification");
			}
			else
			// Compile generated .java files using javac
			if (!compileGeneratedFiles(testName)) {
				System.out.println("Error: Failed to compile generated .java files");
				//System.exit(3);
			}
			else {
			// load test class in a separate class loader and invoke main method
				String className = testName.replace('/', '.');	
				loadAndInvoke(className);
			}
			
			// restore output stream
			if(verbose)
				System.out.println(os.toString());
			else
				System.setOut(out);

			// compare output stream result and expected output
			String result = simplifyComparison(os.toString());
			String correct = simplifyComparison(readFile(testName + ".result"));
			if(result.equals(correct)) {
				System.out.println(testName + ".java passed");
			}
			else {
				System.err.println(testName + ".java failed");
				System.err.println("[" + result + "]" + "\nDoes not equal\n" + "[" + correct + "]");
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	protected static String flattenStringArray(String[] args) {
		StringBuffer sb=new StringBuffer();
		for (int i=0; i<args.length; i++) {
		  if (i > 0) sb.append(" ");
		  sb.append(args[i]);
		}
		return sb.toString();
	}

	protected static String[] buildArgs(String testName) {
		// parse options file using a stream tokenizer
		ArrayList list = new ArrayList();
		File file = new File(testName + ".options");
		if(file.exists()) {
			try {
				StreamTokenizer st = new StreamTokenizer(new FileInputStream(file));
				st.resetSyntax();
				st.wordChars('a', 'z');
				st.wordChars('A', 'Z');
				st.wordChars(128 + 32, 255);
				st.whitespaceChars(0, ' ');
				st.quoteChar('"');
				st.wordChars('0', '9');
				st.wordChars('_', '_');
				st.wordChars('-', '-');
				while(st.nextToken() != StreamTokenizer.TT_EOF) {
					if(st.ttype == StreamTokenizer.TT_WORD) {
						list.add(st.sval);
					}
				}
			} catch (FileNotFoundException e) {
			} catch (IOException e) {
			}
		}

		// Package test.ast
		list.add("--package=test.ast");
		
		// Turn on rewrites
		list.add("--rewrite");
		
		// Generate output in test/src directory
		list.add("--o=" + System.getProperty("user.dir"));
		
		// add test case and jastadd run-time sources to command line arguments
		File grammarFile = new File(System.getProperty("user.dir") +  "/" + testName + ".ast");
		if (grammarFile.exists())
			list.add(testName + ".ast");
		
		// add aspect file if it exists
		File aspectFile = new File(System.getProperty("user.dir") +  "/" + testName + ".jrag");
		if (aspectFile.exists())
			list.add(testName + ".jrag");

		// create String[] from ArrayList
		String[] args = new String[list.size()];
		int count = 0;
		for(Iterator iter = list.iterator(); iter.hasNext(); count++) {
			String s = (String)iter.next();
			args[count] = s;
		}
		return args;
	}
	
	protected static boolean removeGeneratedFiles() {
		try {
			
			// Compile java files in test/ast
			StringBuffer buf = new StringBuffer();
			File dir = new File(System.getProperty("user.dir") + "/test/ast");
			if (dir.exists()) {
				FilenameFilter filter = new FilenameFilter() {
					public boolean accept(File dir, String name) {
						return name.endsWith(".java") || name.endsWith(".class");
					}
				};
				File[] files = dir.listFiles(filter);
				for (int i = 0; i < files.length; i++) {
					buf.append(files[i] + " ");
				}
			}
			
			Runtime runtime = Runtime.getRuntime();
			Process process = runtime.exec("rm " + buf.toString());
			int i = process.waitFor();
			String s = null;
			if (i == 0){
				BufferedReader input = new
				BufferedReader(new InputStreamReader(process.getInputStream()));
				while ((s = input.readLine()) != null)
				{
					System.out.println(s);
				}
			} else {

				// STDERR
				BufferedReader stderr = new
				BufferedReader(new InputStreamReader(process.getErrorStream()));
				while ((s = stderr.readLine()) !=
					null) {
					System.out.println(s);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	/**
	 * Runs JastAdd to generate Java files for a testcase.
	 * @param	testName name of the test case.
	 * @return			 false if there were errors in the JastAdd specification (or if JastAdd crashes).
	 */
	protected static boolean generateJavaFiles(String testName) {
		// Previously: new jastadd.JastAdd().compile(buildArgs(testName));
		// Now calls jastadd2.jar via an exec command instead.
		// This allows testcases to be easily run on other versions of jastadd2.jar
		try {
			Runtime runtime = Runtime.getRuntime();
			String cmd = "java -jar jastadd2.jar " + flattenStringArray(buildArgs(testName));
			if (verbose) System.out.println("Generate files: " + cmd);
			Process process = runtime.exec(cmd);
			int i = process.waitFor();
			String s = null;
			BufferedReader input = new
			BufferedReader(new InputStreamReader(process.getInputStream()));
			while ((s = input.readLine()) != null) {
				System.out.println(s);
			}
			return i == 0;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	
	protected static boolean compileGeneratedFiles(String testName) {
		try {
			/*
			Class[] classList = ClassScope.getLoadedClasses(ClassLoader.getSystemClassLoader());
			for (int i = 0; i < classList.length; i++) {
				System.out.println("\t1 loaded class\t" + classList[i].getName()); 
			}
			*/
			
			// Compile java files in test/ast
			StringBuffer buf = new StringBuffer();
			File dir = new File(System.getProperty("user.dir") + "/test/ast");
			FilenameFilter filter = new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.endsWith(".java");
				}
			};
			File[] files = dir.listFiles(filter);
			for (int i = 0; i < files.length; i++) {
				String name = files[i].getName();
				name = "test/ast/" + name;
				buf.append(" " + name);
			}
			//System.out.println("\t java files in test/ast : " + buf.toString());

			
			Runtime runtime = Runtime.getRuntime();
			String cmd = "javac -cp " + System.getProperty("user.dir") + " " + testName + ".java" + buf.toString();
			if (verbose) System.out.println("Compile files: " + cmd);
			Process process = runtime.exec(cmd);
			int i = process.waitFor();
			String s = null;
			if (i == 0){
				BufferedReader input = new
				BufferedReader(new InputStreamReader(process.getInputStream()));
				while ((s = input.readLine()) != null)
				{
					System.out.println(s);
				}
			} else {

				// STDERR
				BufferedReader stderr = new
				BufferedReader(new InputStreamReader(process.getErrorStream()));
				while ((s = stderr.readLine()) !=
					null) {
					System.out.println(s);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	protected static void loadAndInvoke(String className) {
		// load test class in a separate class loader and invoke main method
		try {
			ClassLoader loader = new URLClassLoader(new URL[] { new File(System.getProperty("user.dir")).toURL() }, null);
			Class clazz = loader.loadClass(className);

			// load all classes in test/ast
			File dir = new File(System.getProperty("user.dir") + "/test/ast");
			FilenameFilter filter = new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.endsWith(".class");
				}
			};
			File[] files = dir.listFiles(filter);
			for (int i = 0; i < files.length; i++) {
				String name = files[i].getName(); 
				int index = name.lastIndexOf('.');
				name = "test.ast." + name.substring(0,index);
				//System.out.println("\t loading from test/ast : " + name);
				loader.loadClass(name);
			}

			/*
			Class[] classList = ClassScope.getLoadedClasses(loader);
			for (int i = 0; i < classList.length; i++) {
				System.out.println("\t1 loaded class\t" + classList[i].getName()); 
			}
			*/
			
			Method m = clazz.getDeclaredMethod("main", new Class[] { String[].class });
			m.invoke(clazz, new Object[] {new String[] {}});
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}

	protected static String simplifyComparison(String s) {
		// remove leading and trailing whitespace + extra \r added in windows
		s = s.replaceAll("\r", "");
		s = s.trim();
		return s;
	}

	protected static String readFile(String name) {
		// return a string with the data in file name
		File file = new File(name);
		int maxsize = 256;
		byte[] bytes = new byte[maxsize];
		try {
			FileInputStream f = new FileInputStream(file);
			int offset = 0;
			int maxread = 0;
			while((maxread = f.available()) > 0) {
				// resize buffer if necessary, double size in each iteration
				while(maxread + offset >= maxsize) {
					byte[] newBytes = new byte[maxsize * 2];
					System.arraycopy(bytes, 0, newBytes, 0, maxsize);
					maxsize *= 2;
					bytes = newBytes;
				}
				offset += f.read(bytes, offset, maxread);
			}
			return new String(bytes);
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
		}
		return "";
	}
	
	public static class Reloader extends ClassLoader {

		@Override
		public Class<?> loadClass(String s) {
			return findClass(s);
		}

		@Override
		public Class<?> findClass(String s) {
			try {
				byte[] bytes = loadClassData(s);
				return defineClass(s, bytes, 0, bytes.length);
			} catch (IOException ioe) {
				try {
					return super.loadClass(s);
				} catch (ClassNotFoundException ignore) { }
				ioe.printStackTrace(System.out);
				return null;
			}
		}

		private byte[] loadClassData(String className) throws IOException {
			File f = new File(System.getProperty("user.dir") + "/" + className.replaceAll("\\.", "/") + ".class");
			int size = (int) f.length();
			byte buff[] = new byte[size];
			FileInputStream fis = new FileInputStream(f);
			DataInputStream dis = new DataInputStream(fis);
			dis.readFully(buff);
			dis.close();
			return buff;
		}
	}
	
	
	public static abstract class ClassScope
	{
		public static Class [] getLoadedClasses (final ClassLoader loader)
		{
			if (loader == null) throw new IllegalArgumentException ("null input: loader");
			if (CLASSES_VECTOR_FIELD == null)
				throw new RuntimeException ("ClassScope::getLoadedClasses() cannot" +
				" be used in this JRE", CVF_FAILURE);
			
			try
			{
				final Vector classes = (Vector) CLASSES_VECTOR_FIELD.get (loader);
				if (classes == null) return EMPTY_CLASS_ARRAY;
				
				final Class [] result;
				
				// Note: Vector is synchronized in Java 2, which helps us make
				// the following into a safe critical section:
				
				synchronized (classes)
				{
					result = new Class [classes.size ()];
					classes.toArray (result);
				}
				
				return result;
			}
			// This should not happen if <clinit> was successful:
			catch (IllegalAccessException e)
			{
				e.printStackTrace (System.out);
				
				return EMPTY_CLASS_ARRAY;
			}
		}
		
		
		private static final Field CLASSES_VECTOR_FIELD; // Set in <clinit> [can be null]
		
		private static final Class [] EMPTY_CLASS_ARRAY = new Class [0];
		private static final Throwable CVF_FAILURE; // Set in <clinit>
		
		static {
			Throwable failure = null;
			
			Field tempf = null;
			try
			{
				// This can fail if this is not a Sun-compatible JVM
				// or if the security is too tight:
				
				tempf = ClassLoader.class.getDeclaredField ("classes");
				if (tempf.getType () != Vector.class)
					throw new RuntimeException ("not of type java.util.Vector: " +
					tempf.getType ().getName ());
				tempf.setAccessible (true);
			}
			catch (Throwable t)
			{
				failure = t;
			}
			CLASSES_VECTOR_FIELD = tempf;
			CVF_FAILURE = failure;
		}
	} // End of class
}
