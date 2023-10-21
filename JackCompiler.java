package compiler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class JackCompiler {
	/** tokenizes the input */
	public static JackTokenizer tokenizer;
	/** the main driver of the compilation process */
	public static CompilationEngine compilationEngine;
	/** does the given path represent a directory? (or just a single file?) */
	public static boolean isDirectory;
	/** index of the file the compiler is at right now in the compilation process */
	public static int directoryIndex;
	/** length of the given directory */
	public static int directoryLength;
	
	/** Scans input path and determines if the path is a directory or just a singular file. "isDirectory" is then adjusted accordingly.
	 *Outputs a String-Array that holds all the elements, ending in .vm, or the singular .vm file. 
	 *Outputs an empty String-Array if input path is a directory containing no .vm files or is a file that does not end in .vm.
	 *also this whole thing i described above is done in such a way that the resulting array looks as follows:
	 *[File1.Path, File2.Path, ...] */
	private static String[] directory2StringArray(String path) {
		File file = new File(path);
		if(file.listFiles() != null) {
			isDirectory = true;
			ArrayList<String> retList = new ArrayList<String>();
			for(File f : file.listFiles()) {
				if(f.getName().endsWith(".jack")) {
					retList.add(f.getPath());
				}
			}
			return retList.toArray(new String[retList.size()]);
		}
		else {
			isDirectory = false;
			String retArr[] = new String[1];
			if(file.exists() && file.getName().endsWith(".jack")) {
				retArr[0] = file.getPath();
			}
			return retArr;
		}
	}
	
	/** Moves through every .jack file in the given directory or just the one .jack-file. tokenizer and compilationEngine are dynamically
	* reallocated to a new JackTokenizer / CompilationEngine object as the program moves on to translate another .jack-file into a parsetree
	* in .xml format */
	public static void translate(String input) throws IOException {
		String[] directory = directory2StringArray(input);
		if(directory.length > 0) {
			for(int directoryIndex = 0; directoryIndex < directory.length; directoryIndex++) {
				
				//set variables
				JackCompiler.directoryIndex = directoryIndex;
				JackCompiler.directoryLength = directory.length;
				
				//translate
				tokenizer = new JackTokenizer(directory[directoryIndex]);
				compilationEngine = new CompilationEngine(directory[directoryIndex], directory[directoryIndex].replace(".jack", ".vm"));
				
				//close
				tokenizer.close();
				compilationEngine.close();
				
			}
		} else {
			System.out.println("Path contains no .jack-files");
		}
	}
	
	public static void main(String[] args) throws IOException {
		translate("E:\\nand2tetris\\nand2tetris\\tools\\Pong");
	}
}
