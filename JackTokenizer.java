package compiler;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

/** tokenizes the given input file */
public class JackTokenizer {
	
	private static FileReader reader;
	private static String currentToken;
	private char currentChar;
	private static StringBuilder sb; 
	/** a table of the token-types KEYWORD and SYMBOL */
	private static HashMap<String,String> tokenTypeTable;
	/** is the current token '/'? */
	private static boolean tokenIsSlash;
	private int lineNumber;

	static {
		currentToken = null;
		sb = new StringBuilder();
		tokenTypeTable = new HashMap<String,String>();
		tokenIsSlash = false;
		
		//	KEYWORDS
		tokenTypeTable.put("class", "KEYWORD");
		tokenTypeTable.put("constructor", "KEYWORD");
		tokenTypeTable.put("function", "KEYWORD");
		tokenTypeTable.put("method", "KEYWORD");
		tokenTypeTable.put("field", "KEYWORD");
		tokenTypeTable.put("static", "KEYWORD");
		tokenTypeTable.put("var", "KEYWORD");
		tokenTypeTable.put("int", "KEYWORD");
		tokenTypeTable.put("char", "KEYWORD");
		tokenTypeTable.put("boolean", "KEYWORD");
		tokenTypeTable.put("void", "KEYWORD");
		tokenTypeTable.put("true", "KEYWORD");
		tokenTypeTable.put("false", "KEYWORD");
		tokenTypeTable.put("null", "KEYWORD");
		tokenTypeTable.put("this", "KEYWORD");
		tokenTypeTable.put("let", "KEYWORD");
		tokenTypeTable.put("do", "KEYWORD");
		tokenTypeTable.put("if", "KEYWORD");
		tokenTypeTable.put("else", "KEYWORD");
		tokenTypeTable.put("while", "KEYWORD");
		tokenTypeTable.put("return", "KEYWORD");
		
		//	SYMBOLS
		tokenTypeTable.put("{", "SYMBOL");
		tokenTypeTable.put("}", "SYMBOL");
		tokenTypeTable.put("(", "SYMBOL");
		tokenTypeTable.put(")", "SYMBOL");
		tokenTypeTable.put("[", "SYMBOL");
		tokenTypeTable.put("]", "SYMBOL");
		tokenTypeTable.put(".", "SYMBOL");
		tokenTypeTable.put(",", "SYMBOL");
		tokenTypeTable.put(";", "SYMBOL");
		tokenTypeTable.put("+", "SYMBOL");
		tokenTypeTable.put("-", "SYMBOL");
		tokenTypeTable.put("*", "SYMBOL");
		tokenTypeTable.put("/", "SYMBOL");
		tokenTypeTable.put("&", "SYMBOL");
		tokenTypeTable.put("|", "SYMBOL");
		tokenTypeTable.put("<", "SYMBOL");
		tokenTypeTable.put(">", "SYMBOL");
		tokenTypeTable.put("=", "SYMBOL");
		tokenTypeTable.put("~", "SYMBOL");	
	}
	
	JackTokenizer(String inputFile) throws FileNotFoundException{
		JackTokenizer.reader = new FileReader(inputFile);
		lineNumber = 1;
		currentChar = ' '; //so its not initialized to whitespace
	}
	
	public String getCurrentToken() {
		return currentToken;
	}
	
	public void setToken(String token) {
		currentToken = token;
	}
	
	public int getLineNumber() {
		return lineNumber; 
	}
	
	// checks if inputFile has more tokens
	public boolean hasMoreTokens() throws IOException {
		this.skipWhitespace();
		if((int)currentChar == 65535) { // value comes from: (int)((char)reader.read()), which, if reader.read() returns -1 comes out to be 65535 for whatever reason 
			return false;
		}
		else {
			return true;
		}
	}
	
	/** gets the next token **/
	public void advance() throws IOException {
		boolean isString = false;
		if(!tokenIsSlash) {
			if(tokenTypeTable.get(((Character)(currentChar)).toString()) != null) {
				sb.append((Character)(currentChar));
				currentChar = (char)reader.read();
			}
			else {
				while(isString || (!Character.isWhitespace(currentChar) && tokenTypeTable.get(((Character)(currentChar)).toString()) == null)) {
					if(currentChar == '"') {
						isString = true;
					}
					sb.append(((Character)(currentChar)).toString());
					currentChar = (char)reader.read();
					if(currentChar == '"') {
						isString = false;
						sb.append(((Character)(currentChar)).toString());
						currentChar = (char)reader.read();
						break;
					}
				}
			}
			currentToken = sb.toString();
			sb.setLength(0);
		} else {
			tokenIsSlash = false;
			currentToken = "/";
		}
	}	
	
	/** returns either KEYWORD, SYMBOL, STRING_CONST, INTEGER_CONST or IDENTIFIER based on the current token **/
	public String tokenType(String token) {
		//	takes care of KEYWORDs and SYMBOLs, i.e. stuff like: "class", "static", ";", "{", etc.
		if(tokenTypeTable.get(token) != null) {
			return tokenTypeTable.get(token);
		}
		
		//	if token == "someString" -out> STRING_CONST
		if(token.charAt(0) == '"' && token.charAt(token.length()-1) == '"') {
			//	token shouldn't contain any double quotes or newlines thus the difference in valid String constants must be strictly 2
			int difference = token.length() - token.replaceAll("\n","").replaceAll("\"","").length();
			if(difference == 2) {
				return "STRING_CONST";
			}
			else {
				return "Illegal STRING_CONST";
			}
		}
		
		//	if token == someInteger s.t. 32767 >= someInteger <= 0 -out> INTEGER_CONST
		if(Character.isDigit(token.charAt(0))) {
			try {
				
			/* check if integer constant is within the specified integer bounds set by the HACK hardware (i.e. 15-bit binary numbers) */
				if(Integer.parseInt(token) < 0 || Integer.parseInt(token) > 32767) {
					return "Illegal INT_CONST (OutOfBounds)";
				}
				else {
					return "INT_CONST";
				}
				
			/* handles error where token only starts with a digit but contains unsensible stuff afterwards */
			} catch(NumberFormatException e) {
				return "Illegal INT_CONST";
			}
		}
		
		if(tokenTypeTable.get(token) == "KEYWORD") {
			return "KEYWORD";
		}
		
		if(tokenTypeTable.get(token) == "SYMBOL") {
			return "SYMBOL";
		}
		
		if(token.matches("\\w++")) {
			return "IDENTIFIER";
		}
		else {
			System.out.println("Syntax Error: Illegal Identifier");
			return "Illegal token type";
		}	
	}
	
	//	only call if tokenType is SYMBOL
	public char symbol() {
		return currentToken.toCharArray()[0];
	}
	
	//	only call if tokenType is IDENTIFIER
	public String identifier() {
		return currentToken;
	}
	
	//	only call if tokenType is INT_CONST
	public int intVal() {
		return Integer.parseInt(currentToken);
	}
	
	//	only call if tokenType is STRING_CONST
	public String stringVal() {
		return currentToken.replace("\"", "");
	}
	
	public void close() throws IOException {
		JackTokenizer.reader.close();
	}
	

//	skips any whitespace, i.e. whitespace characters and comments.
//	comments are determined by skipWhitespace scanning one /, however only one / is an illegal expression and will throw an error
//	if we encounter a comment that looks like this: //comment, skipWhitespace will ignore everything until it reader reads \n
//	if we encounter a comment that looks like this: /* comment */, skipWhitespace will ignore everyhing until it reads */ again
//	once skipWhitespace encounters anything that isnt whitespace it returns with currentChar
	
	private void skipWhitespace() throws IOException {
		while(true) {
			
			/* increment lineNumber when reading a newLine character */
			if(currentChar == '\n') {lineNumber++;}
			
			/* handles comments */
			if(currentChar == '/') {
				currentChar = (char)reader.read();
				
				/* handles the case where token is '/', i.e. not a comment */
				if(currentChar != '/' && currentChar != '*') {
					tokenIsSlash = true;
					return;
				}
				
				/* doubleSlash-type comment */
				if(currentChar == '/') {
					while(currentChar != '\n') {
						currentChar = (char)reader.read();
						/* in case theres a comment at the very end of the file
						 * value comes from: (int)((char)reader.read()), which, if reader.read() returns -1 comes out to be 65535 
						 * for whatever reason */
						if((int)currentChar == 65535) { 
							return;
						}
					}
				}
				
				/* slash-asterisk-type comment */
				else if(currentChar == '*') {
					boolean astk = false;
					boolean slash = false;
					/* first see if we're reading a '*', if we are, set slash to true, next check if now we're reading '/'
					 * if that's the case, break out of the loop, if not keep reading until that condition is fulfilled.
					 * All the while you're doing this, whenever you read a newLine character increment lineNumber 
					 */
					while(true) {
						slash = false;
						currentChar = (char)reader.read();
						if(currentChar == '/') {
							slash = true;
						}
						
						if(astk == true && slash == true) {
							currentChar = (char)reader.read();
							break;
						}
						
						astk = false;
							
						if(currentChar == '*') {
							astk = true;
						}
						
						if(currentChar == '\n') {lineNumber++;}
					}
					
				}
				
				/* ILLEGAL */
				else {
					System.out.println("Illegal Comment");
				}
			}	
			
			/* handles normal whitespace */
			else if(Character.isWhitespace(currentChar) == true) {
				currentChar = (char)reader.read();
			}
			
			/* end of whitespace */
			else {
				break;
			}		
		}
	}
}
