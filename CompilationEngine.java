package compiler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Set;
import java.util.Stack;

/* The Jack language has basically no typing and no type-checking. The only types that are being checked are class-types, 
 * all primitive types behave exactly the same 'int', 'char', 'boolean' are effectively just buzzwords that might help the user to just
 * sort of keep track of types themselves. 
 * Also the language is extremely insecure, in that it allows the user to access random memory registers just by using arrays, i.e. do something like: 
 * var Array arr; let arr = Array.new(1); from here on out let arr[0] = 3; is a valid thing to do just like let arr[32767] = -1;*/

/** Compiles one .jack-file. Gets tokenized inputs and compiles them with the help of two symbol tables. Writes the generated vm-commands to a .vm output-file */
public class CompilationEngine {
	/** tokenizes the input */
	private static JackTokenizer tokenizer;
	/** writes VM-commands to output-file */
	private static VMWriter vmWriter;
	/** symbol-table that keeps track of current 'static' and 'field' variables */
	private static SymbolTable classLevelSymbolTable;
	/** symbol-table that keeps track of current 'local' (i.e. 'var') and 'argument' (i.e. parameters) variables */
	private static SymbolTable subroutineLevelSymbolTable;
	/** the current token read by the tokenizer */
	private static String token;
	/** the name of the currently processed identifier */
	private static String currentIdentifierName;
	/** the type (int, boolean, className) of the currently processed identifier */
	private static String currentIdentifierType;
	/** the kind (static, field, local, argument) of the currently processed identifier */
	private static String currentIdentifierKind;
	/** 'true' if currently processed variable is being declared, 'false' if it's being used */
	private static boolean currentVariableIsBeingDeclared;
	/** name of the currently processed identifier that is some class-name (not necessarily the same class-name of the class we're currently processing)
	 * i.e. represents 'className' in these expressions: 'class className', 'var className someObj', 'className.Foo(expressionList)'. Updates itself everytime
	 * the compiler reads one of these expressions */
	private static String currentlyProcessedClassName;
	/** name of the currently processed .jack-file */
	private static String currentFileName;
	/** table of (currentlyProcessedClassName, CLASSNAME_DECLARED or CLASSNAME_NOT_DECLARED): 
	 * 
	 * puts (currentlyProcessedClassName, CLASSNAME_DECLARED) upon scanning an actual class-name in a class declaration, 
	 * puts (currentlyProcessedClassName, CLASSNAME_NOT_DECLARED) upon scanning something like 'var className obj;'
	 * puts (currentlyProcessedClassName, CLASSNAME_NOT_DECLARED) upon scanning something like 'do className.foo();'
	 * 
	 * updates CLASSNAME_NOT_DECLARED to CLASSNAME_DECLARED when it scans the corresponding class declaration 
	 * of the previously used className in the object-variable declaration
	 * if there is one or more buckets whose value is 'CLASSNAME_NOT_DECLARED' the end of the compilation process 
	 * the compiler will throw a compilation error */
	private static HashMap<String,String> classNameTable;
	/** name of the currently processed subroutine */
	private static String currentlyProcessedSubroutineName;
	/** name of the subroutine we're currently inside of */
	private static String currentSubroutineName;
	/** kind of the currently called or declared subroutine, i.e. 'constructor', 'method' or 'function' */
	private static String currentSubroutineKind;
	/** "CALLED" if currently processed subroutine is being called, "DECLARED" if currently processed subroutine is being declared */
	private static String currentSubroutineCalledOrDeclared;
	/** table of (subroutineName, (subroutineKind, "CALLED" or "DECLARED"), nArgs), where subroutineKind is either 'constructor', 'method' or 'function' 
	 * this helps check if a certain subroutine is the same kind as it's being called as (i.e. a subroutine that's declared as a 'function' can't
	 * be a called as a 'method', also checks at the end of compilation that all called subroutines have actually been declared, else its gonna throw
	 * a compilation error */
	private static HashMap<String,String[]> subroutineTable;
	/** is the current subroutine 'void'? */
	private static boolean isVoid;
	/** is the current identifier the name of an array? */
	private static boolean isArray;
	/** is the current varName being assigned to something right now? i.e. are we in a currently in a let-statement? */
	private static boolean nonArrayVariableIsBeingAssigned;
	/** the amount of arguments that a certain subroutine had listed in its expressionList, used when compiling a subroutineCall */
	private static Integer nArgs;
	/** index of a given while-loop within a certain class, within a certain subroutine */
	private static int whileIndex;
	/** index of a given if-statement within a certain class, within a certain subroutine */
	private static int ifIndex; 
	/** the currently processed operator */
	private static String currentOperator;
	/** Shows if there has been a syntax error on a certain token. If we detect a syntax error on a certain token, "terminate" the compiler
	 * (i.e. still processes all the files but doesn't write anything to the outputFile */
	private static boolean errorOnToken;
	
	static {
		token = "";
		currentIdentifierName = "";
		currentIdentifierType = "";
		currentIdentifierKind = "";
		currentVariableIsBeingDeclared = false;
		currentlyProcessedClassName = "";
		currentFileName = "";
		currentlyProcessedSubroutineName = "";
		currentSubroutineName = "";
		currentSubroutineKind = "";
		currentSubroutineCalledOrDeclared = "";
		classNameTable = new HashMap<String,String>();
		subroutineTable = new HashMap<String,String[]>();
		isVoid = false;
		isArray = false;
		nonArrayVariableIsBeingAssigned = false;
		whileIndex = 0;
		ifIndex = 0;
		nArgs = 0;
		currentOperator = "";
		errorOnToken = false;
	}
	
	CompilationEngine(String inputFile, String outputFile) throws IOException {
		tokenizer = JackCompiler.tokenizer;
		vmWriter = new VMWriter(outputFile);
		classLevelSymbolTable = new SymbolTable();
		subroutineLevelSymbolTable = new SymbolTable();
		currentFileName = inputFile.substring(inputFile.lastIndexOf("\\")+1).replace(".jack", "");
		advance(); //gets us to 'class' (necessarily btw)
		if(!errorOnToken) {this.compileClass();}
	}	

	/** compiles class className {classVarDec*, subroutineDec*} **/ 
	public void compileClass() throws IOException {
	if(!errorOnToken) {
		currentIdentifierKind = "className";
			
		/*class*/			compileKeyword("class"); 
		/*className*/ 		compileIdentifier(true); 
		/*{*/				compileSymbol("{");
		/*classVarDec**/ 	while(token.equals("static") || token.equals("field")) {
									compileClassVarDec();
							}
				
		/*subroutineDec**/ 	while(token.equals("constructor") || token.equals("function") || token.equals("method")) {
									compileSubroutineDec();
								}
		/*}*/				compileSymbol("}");
			
		putClassesUsedClassNameTypesOnClassNameTable();
		if(JackCompiler.directoryIndex == JackCompiler.directoryLength-1) {
			checkClassNameTableForInaccuracies();
			checkSubroutineTableForInaccuracies();
		}
		vmWriter.close();	
	}
	}
	
	/** compiles static / field type varName(',' varName)*; **/
	public void compileClassVarDec() throws IOException {
	if(!errorOnToken) {
		currentVariableIsBeingDeclared = true;
		/*static varName(',' varName)*/		if(token.equals("static")) {compileStaticVarDec();}
		/*field varName(',' varName)*/		else if(token.equals("field")) {compileFieldVarDec();}
											else if(!errorOnToken){
												System.out.println("Syntax Error: Expected 'static' / 'field' in class variable declaration");
												throwIllegal("kind");
											}
		currentVariableIsBeingDeclared = false;
	}
	}
	
	/** compiles 'constructur'/'function'/'method' 'void'/type subroutineName (parameterList) subroutineBody **/
	public void compileSubroutineDec() throws IOException {
	if(!errorOnToken) {
		/* clear the subroutineSymbolTable upon entering this new subroutine declaration */
		subroutineLevelSymbolTable.startSubroutine();	
		currentlyProcessedClassName = currentFileName;
		currentSubroutineCalledOrDeclared = "DECLARED";
		
							switch(token) {
		/*'constructor'*/	case("constructor"):
								compileKeyword("constructor");
								break;
		/*'function'*/		case("function"):
								compileKeyword("function");
								break;
		/*'method'*/		case("method"):
								compileKeyword("method");
								break;
							default:
								if(!errorOnToken) {
									System.out.println("Syntax Error: Expected 'constructor' / 'function' / 'method'");
									throwIllegal("keyword");
								}
							}

		/*'void'/type*/		if(tokenIsType()) {compileType();}
							else if(token.equals("void")) {compileKeyword("void");}
							else if(!errorOnToken){
								System.out.println("Syntax Error: Expected 'void' / type");
								throwIllegal("keyword / type");
							}	
							
							currentSubroutineName = token;
							/* advance for now, we still need to check for nArgs the subroutine uses so we can add it to the subroutine-table */
		/*subroutineName*/	advance();
		/*(*/				compileSymbol("(");
		/*parameterList*/	compileParameterList();
		/*)*/				compileSymbol(")");
		/*subroutineBody*/	compileSubroutineBody();
		
		putSubroutinesUsedClassNameTypesOnClassNameTable();
		whileIndex = 0;
		ifIndex = 0;
		isVoid = false;
	}
	}
	
	/** compiles '((type varName)(',' type varName)*)?', furthermore adds the subroutine to the subroutine-table **/
	public void compileParameterList() throws IOException {
	if(!errorOnToken) {
		String currentIdentifierKindSave = currentIdentifierKind;
		currentIdentifierKind = "ARG";
		
		/* need to define 'this' as argument 0 strictly in methods (obviously not in functions and not in 
		 * constructors either cause we're only creating the object there after already evaluating the constructors arguments). 
		 * Really this line is only here so argument 0 doesnt get assigned to some other random variable 
		 * in the method and argument 0 (i.e. the passed objects base address, although it is now stored in pointer 0) 
		 * doesn't change and everything works as expected (although it would be fine to meddle with argument 0 here, it is best practice to avoid doing so) 
		*/
		if(currentSubroutineKind.equals("method")) {
			subroutineLevelSymbolTable.define("this", currentFileName, "ARG");
		}	
		
		/* only if parameterList starts with type will it do something */
			if(tokenIsType()) {
				/*type*/ 		compileType();	
				/*varName*/		compileIdentifier(true);
				/*(',' type varName)* */	while(true) {
												if(token.equals(",")) {
													/*,*/	 	compileSymbol(",");
													/*type*/ 	compileType();
													/*varName*/	compileIdentifier(true);
												}
												/* positive break condition of while-loop -> syntax is correct (at least the ')' is right) */
												else if(token.equals(")")) {
													break;
												}
												/* negative break condition of while-loop -> syntax is incorrect, expected ')' */
												else if(!errorOnToken){
													System.out.println("Syntax Error: Expected ')' in parameterList");
													break;
												}
											}
			}
			/* if next token is anything but ')' that's a syntax error, because of the use of an invalid parameter type */
			else if(!token.equals(")") && !errorOnToken) {
				System.out.println("Syntax Error: Invalid parameter type");
				throwIllegal("parameter type");
			}
			
			
			/* now we have all the necessary information in order to add the DECLARED subroutine to the subroutine-table.
			 * currentSubroutineName is set to the current subroutine name, nArgs is determined, and we set currentSubroutineCalledOrDeclared to DECLARED*/
			String tokenSave = token;
			
			token = currentSubroutineName;
			currentIdentifierKind = "subroutineName";
			nArgs = subroutineLevelSymbolTable.getArgumentIndex() + 1;
			compileIdentifier(false);
			
			if(!errorOnToken) {
				token = tokenSave;
			}
			/* was only used so we could add subroutine to subroutine-table correctly, can now be reset to 0 */
			nArgs = 0;
			
			currentIdentifierKind = currentIdentifierKindSave;
	}
	}
	
	/** compiles {varDec* statements} (yes that means we cant declare any new variables in between if, while, let, do or return statements) **/
	public void compileSubroutineBody() throws IOException {
	if(!errorOnToken) {
		/*{*/			compileSymbol("{");
		/*varDec**/		while(token.equals("var")) {
							compileVarDec();
						}
		
		/* after adding all the local variables (and also argument variables, but those dont matter for this next bit)
		 * to the subroutineSymbolTable, vmWriter can write the VM-command: "function fileName.foo nLocals" to the outputFile */
		int nLocals = subroutineLevelSymbolTable.getLocalIndex() + 1;
		vmWriter.writeFunction(currentFileName + "." + currentlyProcessedSubroutineName, nLocals);
		
		/* if we're currently compiling a constructor declaration, call the OS-function Memory.alloc to allocate a new memory block
		 * for the new object. One RAM-Register for each field-variable. Then pop the returned base address of that memory block
		 * onto pointer 0, i.e. anchor the base address of the THIS-segment in the newly constructed object */
		if(currentSubroutineKind.equals("constructor")) {
			int nFields = classLevelSymbolTable.getFieldIndex() + 1;
			vmWriter.writePush("CONST", nFields);
			vmWriter.writeCall("Memory.alloc", 1);
			vmWriter.writePop("POINTER", 0);
		}
		else if(currentSubroutineKind.equals("method")) {
			/* anchor the THIS-segment onto base-address stored in argument 0 (i.e. the passed object on which the method operates) */
			vmWriter.writePush("ARG", 0);
			vmWriter.writePop("POINTER", 0);
		}
		
		/*statements*/	compileStatements();
		/*}*/			compileSymbol("}");
	}
	}
	
	/** compiles var type varName (',' type varName)* **/
	public void compileVarDec() throws IOException {
	if(!errorOnToken) {
		currentVariableIsBeingDeclared = true;
		currentIdentifierKind = "VAR"; //we're guaranteed that 'var' is the current token on entry of this function (cf. compileSubroutineBody)
		compileKeyword("var");
		compileTypeVarName();
		currentVariableIsBeingDeclared = false;
	}
	}
	
	/** compiles statement* **/
	public void compileStatements() throws IOException {
	if(!errorOnToken) {
		while(tokenIsStatement()) {
			switch(token) {
				case("let"): compileLet(); break;
				case("do"): compileDo(); break;
				case("if"): compileIf(); break;
				case("while"): compileWhile(); break;
				case("return"): compileReturn(); break;
				default:
					if(!errorOnToken) {
						System.out.println("Syntax Error: Expected 'let' / 'do' / 'if' / 'while' / 'return'");
						throwIllegal("statement(s)");
					}
			}
		}
	}
	}
	
	/** compiles 'let varName ([expression])? = expression;' **/
	public void compileLet() throws IOException {
	if(!errorOnToken) {	
		
		/*let*/								compileKeyword("let");
											determineKindOfIdentifier();
											String varName = token;
										
										/* set to true since we dont know whether we're assigning a non-array variable or an array-variable yet.
										 * distinction will be made in compileIdentifierPolymorphism */
										nonArrayVariableIsBeingAssigned = true;
			
		/*varName([expression])?*/			compileIdentifierPolymorphism();
										/* since compileIdentifierPolymorphism is evaluating whether isArray is true or not (i.e. if the current
										 * identifier represents the varName of an array or not) we want to store thisevaluated value in 
										 * assignedVarNameIsArray and then set isArray back to false so it works as expected*/
											boolean assignedVarNameIsArray = isArray;
											isArray = false;
										
										/* unnecessary line of code, for readability only */
										nonArrayVariableIsBeingAssigned = false;
										
		/*=*/								compileSymbol("=");
 		/*expression*/						compileExpression();
 		/*;*/								compileSymbol(";");
 		
 		if(assignedVarNameIsArray) {
 			vmWriter.writePop("TEMP", 0);
 			vmWriter.writePop("POINTER", 1);
 			vmWriter.writePush("TEMP", 0);
 			vmWriter.writePop("THAT", 0);
 			isArray = false;
 		} else {
 			lookUpVariableAndWritePopIfItExists(varName);
 		}
 		
		nonArrayVariableIsBeingAssigned = false;
	}
	}
	
	/** compiles 'do subroutineCall;' the compiler will assume that the do-statement called a 'void' function or method and thus 
	 * "throw away" the returned value of the function / method or even constructor (i.e. pop the returned value onto temp 0 **/
	public void compileDo() throws IOException {
	if(!errorOnToken) {
		/*do*/				compileKeyword("do");
							currentSubroutineCalledOrDeclared = "CALLED";
		/*subroutineCall*/	compileIdentifierPolymorphism();
		/*;*/				compileSymbol(";");
		vmWriter.writePop("TEMP", 0);
	}
	}
	
	/** compiles 'if (expression) {statements} (else {statements})?'**/
	public void compileIf() throws IOException {
	if(!errorOnToken) {
		int ifIndex = CompilationEngine.ifIndex;
		CompilationEngine.ifIndex++;
		String ELSE = currentFileName + "." + currentSubroutineName + "." + "IfStatementELSE" + "." + ifIndex;
		String END_IF = currentFileName + "." + currentSubroutineName + "." + "IfStatementEND" + "." + ifIndex;
		
		/*if*/						compileKeyword("if");
		/*(*/						compileSymbol("(");
		/*expression*/				compileExpression();
		/*)*/						compileSymbol(")");
		
									/* if expression evaluates to false, go to 'ELSE', if it doesn't, execute the following statements */
									vmWriter.writeArithmetic("NOT"); //because of how if-goto is implemented in the vm-translator
									vmWriter.writeIf(ELSE);
									
		/*{*/						compileSymbol("{");
		/*statements*/				compileStatements();
		/*}*/						compileSymbol("}");
									vmWriter.writeGoto(END_IF);
		
		/*(else {statements})?*/	vmWriter.writeLabel(ELSE);
									if(token.equals("else")) {
									/*else*/		compileKeyword("else");
									/*{*/			compileSymbol("{");
									/*statements*/	compileStatements();
									/*}*/			compileSymbol("}");
									}
									
									vmWriter.writeLabel(END_IF);
	}
	}
	
	
	/** compiles 'while (expression) {statements}'. Generates labels like so: 
	 * currentFileName.currentSubroutineName.WhileLoop.whileIndex, currentFileName.currentSubroutineName.WhileEndLoop.whileIndex **/
	public void compileWhile() throws IOException {
	if(!errorOnToken) {
		int whileIndex = CompilationEngine.whileIndex;
		CompilationEngine.whileIndex++;
		String LOOP = currentFileName + "." + currentSubroutineName + "." + "WhileLOOP" + "." + whileIndex;
		String END_LOOP = currentFileName + "." + currentSubroutineName + "." + "WhileEND_LOOP" + "." + whileIndex;
		
		/*while*/					compileKeyword("while");
									vmWriter.writeLabel(LOOP);
									
		/*(*/						compileSymbol("(");
		/*expression*/				compileExpression();
		/*)*/						compileSymbol(")");
									vmWriter.writeArithmetic("NOT"); //because of how if-goto is implemented in the vm-translator
									vmWriter.writeIf(END_LOOP);
									
		/*{*/						compileSymbol("{");
		/*statements*/				compileStatements();
		/*}*/						compileSymbol("}");
		
									vmWriter.writeGoto(LOOP);
									vmWriter.writeLabel(END_LOOP);
	}
	}

	/** compiles 'return (expression)?;' **/
	public void compileReturn() throws IOException {
	if(!errorOnToken) {
		/*return*/					compileKeyword("return");
		/*(expression)?*/			if(!token.equals(";")) {
										compileExpression();
									}
		/*;*/						compileSymbol(";");
		
		if(isVoid) {vmWriter.writePush("CONST", 0);}
		vmWriter.writeReturn();
	}
	}

	/** compiles 'term (op term)*' **/
	public void compileExpression() throws IOException {
	if(!errorOnToken) {
		/* the '-' in between two terms in the form of a StringBuilder, need this to make sure things like (a-b) - (c-d) work
		 * without having to explicitly write (a-b) + -(c-d)*/
		StringBuilder minusInBetweenTerms = new StringBuilder();
		/* the operators being used within this expression, placed on a stack (in order to ensure correct operator order) */
		Stack<String> operatorStack = new Stack<String>();
		/* stores whether the operator in this expression is '*' or '/' */
		boolean operatorIsMultiply = false;
		boolean operatorIsDivide = false;
		
		/*term*/		compileTerm();
		/*(op term)* */	while(tokenIsOperator() && !errorOnToken) {
							minusInBetweenTerms.setLength(0);
							
							compileOperator(operatorStack, minusInBetweenTerms);
							
							if(currentOperator.equals("*")) {operatorIsMultiply = true;}
							else if(currentOperator.equals("/")) {operatorIsDivide = true;}
							
							compileTerm();
							/* check if there was a '-' in between the two terms, if there was, compileOperator already did
							 * vmWriter.writeArithmetic("ADD") and now we need to negate the next term. This way we can ensure 
							 * (a-b) - (c-d) works as intended*/
							if(minusInBetweenTerms.toString().equals("-")) {
								vmWriter.writeArithmetic("NEG");
							}
							
							/* multiplication / division priority */
							if(operatorIsMultiply) {
								vmWriter.writeArithmetic("MULTIPLY");
								operatorIsMultiply = false;
							}
							else if(operatorIsDivide) {
								vmWriter.writeArithmetic("DIVIDE");
								operatorIsDivide = false;
							}
						}
		unloadExpressionsOperatorStack(operatorStack);
	}
	}
	
	/** compiles 'integerConstant || stringConstant || keywordConstant || varName || varName[expression] || subroutineCall || (expression) || unaryOp term' **/
	public void compileTerm() throws IOException {
	if(!errorOnToken) {
		StringBuilder unaryOpInTerm = new StringBuilder();
		unaryOpInTerm.setLength(0);
		
		/*constant*/		if(tokenIsConstant()) {
								compileConstant(); 
		/*varNamePolym.*/	} else if(tokenizer.tokenType(token) == "IDENTIFIER") {
								compileIdentifierPolymorphism();
								/* if an array is being used in an expression, push the value of arr[i] and set isArray back to false so it works as expected */
								if(isArray && !nonArrayVariableIsBeingAssigned) {
						 			vmWriter.writePop("POINTER", 1);
						 			vmWriter.writePush("THAT", 0);
						 			isArray = false;
								}
		/*(expression)*/	} else if(token.equals("(")) {
								compileSymbol("(");						
								compileExpression();
								compileSymbol(")");
		/*unaryOp term*/	} else if(tokenIsUnaryOperator()) {
								compileUnaryOp(unaryOpInTerm);
								compileTerm();
								writeUnaryOp(unaryOpInTerm);
							} else if(!errorOnToken){
								System.out.println("Illegal Term");
								throwIllegal("term");
							}
	}
	}
	
	/** compiles (expression (, expression)*)? **/
	public void compileExpressionList() throws IOException {
	if(!errorOnToken) {
						if(!token.equals(")")) {
		/*expression*/		compileExpression();
							nArgs++;
							while(token.equals(",")) {
		/*,*/					compileSymbol(",");
		/*expression*/			compileExpression();
								nArgs++;
							}
						}
	}
	}

	public void close() throws IOException {
		vmWriter.close();
	}

	/*** HELPER FUNCTIONS ***/
	/** advances **/
	private void advance() throws IOException {
		if(tokenizer.hasMoreTokens() && !errorOnToken) {
			tokenizer.advance();
			token = tokenizer.getCurrentToken();
		}
		else {
			token = " ";
		}
	}

	/** iterates through classLevelSymbolTable and checks if there are any variables that aren't of type:
	 * someOSclass (i.e. Memory, Screen, ...), int, boolean or char and puts them on the classNameTable like so:
	 * (certainClassName, "classNameAsType")*/
	private void putClassesUsedClassNameTypesOnClassNameTable() {
	if(!errorOnToken) {
		Set<String> classLevelVarNameTable = classLevelSymbolTable.getKeySet();
		String tokenSave = token;
		
		for(String varName : classLevelVarNameTable) {
			String typeOfVarName = classLevelSymbolTable.get(varName)[0];
			boolean classNameDeclared = (classNameTable.containsKey(typeOfVarName)) ? 
					classNameTable.get(typeOfVarName).equals("CLASSNAME_DECLARED") : false;
			token = varName;
			
			if(!isOSClassName() && !classNameDeclared)
				switch(typeOfVarName) {
				case("int"):
					break;
				case("boolean"):
					break;
				case("char"):
					break;
				default:
					classNameTable.put(typeOfVarName, "CLASSNAME_NOT_DECLARED");
			}
		}
		
		token = tokenSave;
	}
	}

	/** iterates through subroutineLevelSymbolTable and checks if there are any variables that aren't of type:
	 * someOSclass (i.e. Memory, Screen, ...), int, boolean or char and puts them on the classNameTable like so:
	 * (certainClassName, "classNameAsType")*/

	private void putSubroutinesUsedClassNameTypesOnClassNameTable() {
	if(!errorOnToken) {
		Set<String> subroutineLevelVarNameTable = subroutineLevelSymbolTable.getKeySet();
		String tokenSave = token;
		
		for(String varName : subroutineLevelVarNameTable) {
			String typeOfVarName = subroutineLevelSymbolTable.get(varName)[0];
			boolean classNameDeclared = (classNameTable.containsKey(typeOfVarName)) ? 
					classNameTable.get(typeOfVarName).equals("CLASSNAME_DECLARED") : false;
			token = varName;
			
			if(!isOSClassName() && !classNameDeclared)
				switch(typeOfVarName) {
				case("int"):
					break;
				case("boolean"):
					break;
				case("char"):
					break;
				default:
					classNameTable.put(typeOfVarName, "CLASSNAME_NOT_DECLARED");
			}
		}
		
		token = tokenSave;
	}
	}

	private void unloadExpressionsOperatorStack(Stack<String> operatorStack) throws IOException {
	if(!errorOnToken) {
		/* first we pushed all the terms' evaluated values on the stack, applied multiplication and division right away when needed
		 * now we need to push the remaining operators in between the rest of the terms that werent multiplied or divided onto the stack
		 * in the exact order in which they were stored in 'operatorStack' */
		int operatorStackSize = operatorStack.size();
		for(int index = 0; index < operatorStackSize; index++) {
			String operator = operatorStack.pop();
			switch(operator) {
			case("+"):
				vmWriter.writeArithmetic("ADD");
				break;
			case("-"):
				vmWriter.writeArithmetic("SUB");
				break;
			case("&"):
				vmWriter.writeArithmetic("AND");
				break;
			case("|"):
				vmWriter.writeArithmetic("OR");
				break;
			case("<"):
				vmWriter.writeArithmetic("LT");
				break;
			case(">"):
				vmWriter.writeArithmetic("GT");
				break;
			case("="):
				vmWriter.writeArithmetic("EQ");
			}
		}
	}
	}


	/** writes the corresponding VM-command based on the given unary-operator StringBuilder **/
	private void writeUnaryOp(StringBuilder unaryOp) throws IOException {
	if(!errorOnToken) {
		switch(unaryOp.toString()) {
		case("-"):
			vmWriter.writeArithmetic("NEG");
			unaryOp.setLength(0);
			break;
		case("~"):
			vmWriter.writeArithmetic("NOT");
			unaryOp.setLength(0);
			break;
		default:
			if(!errorOnToken) {
				unaryOp.setLength(0);
				System.out.println("Syntax Error: Invalid unary-operator");
				throwIllegal("unaryOp");
			}
		}
	}
	}
	
	/** iterates over all subroutineNames that have been used in this class and checks if each one of them has been declared,
	 * are to be (maybe?) implemented in another class, or are OS-functions **/
	private void checkSubroutineTableForInaccuracies() throws IOException {
		errorOnToken = false; //just set it to false now (so we can do throwIllegal), we're at the end of the file when entering this function
		Set<String> keyTable = subroutineTable.keySet();
		boolean error = false;
		boolean subroutineWasOnlyCalled = false;
		boolean subroutineIsOfOSClass = false; //i.e. is the subroutine something like Math.muliply()?
		String classNameOfSubroutine = currentFileName;
		String tokenSave = token;
		StringBuilder missingSubroutines = new StringBuilder();
		
		for(String key : keyTable) {
			subroutineWasOnlyCalled = subroutineTable.get(key)[1].equals("CALLED");
			
			classNameOfSubroutine = key.substring(0, key.indexOf('.'));
			token = classNameOfSubroutine;
			subroutineIsOfOSClass = isOSClassName();
			
			/** if there's a certain subroutine we only called (in a certain class of the directory) that is not an OS-subroutine,
			 * throw an error*/
			if(subroutineWasOnlyCalled && !subroutineIsOfOSClass) {
				System.out.println("Missing declaration for subroutine '" + key + "'");
				error = true;
				missingSubroutines.append("[");
				missingSubroutines.append(key);
				missingSubroutines.append("]");
			}
		}
		
		if(error == true && !errorOnToken) {
			token = missingSubroutines.toString();
			throwIllegal("subroutine(s), missing declarations");
		}
		
		token = tokenSave;
	}

	private void checkClassNameTableForInaccuracies() throws IOException {
		Set<String> keyTable = classNameTable.keySet();
		boolean error = false;
		boolean classNameWasDeclared = false;
		String tokenSave = token;
		
		for(String key : keyTable) {
			token = key;
			classNameWasDeclared = classNameTable.get(key).equals("CLASSNAME_DECLARED");
	
			if(!classNameWasDeclared && !isOSClassName()) {
				System.out.println("Missing declaration for class '" + key + "'");
				error = true;
			}
		}
		
		if(error == true && !errorOnToken) {
			throwIllegal("class");
		}
		
		token = tokenSave;
	}

	/** compiles a static variable declaration, i.e. adds static variable(s) to the classSymbolTable **/
	private void compileStaticVarDec() throws IOException {
	if(!errorOnToken) {
		currentIdentifierKind = "STATIC";
		advance();
		compileTypeVarName();
	}
	}
	
	/** compiles a field variable declaration, i.e. adds field variable(s) to the classSymbolTable **/
	private void compileFieldVarDec() throws IOException {
	if(!errorOnToken) {
		currentIdentifierKind = "FIELD";
		advance();
		compileTypeVarName();
	}
	}
	
	/** compiles constants **/
	private void compileConstant() throws IOException {
	if(!errorOnToken) {
		/*keyword*/		if(tokenIsKeywordConstant()) {
							switch(token) {
							case("true"):
								vmWriter.writePush("CONST", 0);
								vmWriter.writeArithmetic("NOT");
								advance();
								break;
							case("false"):
								vmWriter.writePush("CONST", 0);
								advance();
								break;
							case("null"):
								vmWriter.writePush("CONST", 0);
								advance();
								break;
							case("this"):
								compileKeyword("this");
								break;
							default:
								if(!errorOnToken) {
									System.out.println("Syntax Error: Illegal KeywordConstant");
									throwIllegal("KeywordConstant");
								}
							}
						}
		
						switch(tokenizer.tokenType(token)) {
		/*integer*/			case("INT_CONST"):
								vmWriter.writePush("CONST", tokenizer.intVal());
								advance();
								break;
		/*string*/			case("STRING_CONST"):
								compileStringConstant();
								break;
		/*ILLEGAL*/			case("Illegal STRING_CONST"):
								if(!errorOnToken) {
									System.out.println("Syntax Error: Illegal String Constant (contains double quotes and/or newlines)");
									throwIllegal("STRING_CONST");
									break;
								}
							case("Illegal INT_CONST"):
								if(!errorOnToken) {
									System.out.println("Syntax Error: Integer constant contains non-digits");
									throwIllegal("INT_CONST");
									break;
								}
							case("Illegal INT_CONST (OutOfBounds)"):
								if(!errorOnToken) {
									System.out.println("Compilation Error: Integer constant is out of bounds (max. value = 32767)");
									throwIllegal("INT_CONST (OutOfBounds)");
									break;
								}
							default:
								break;
						}
	}
	}
	
	/** compiles StringConstants, such that the base address of the newly created string-constant now lies on top of the stack */
	private void compileStringConstant() throws IOException {
	if(!errorOnToken) {
		token = tokenizer.stringVal();
		/* first construct a new String-object of length of token (i.e. current STRING_CONST) */
		vmWriter.writePush("CONST", token.length());
		vmWriter.writeCall("String.new", 1);
		
		/* now for each character in token push that character's ascii value onto the stack and call String.appendChar */
		for(char character : token.toCharArray()) {
			vmWriter.writePush("CONST", (int)character);
			vmWriter.writeCall("String.appendChar", 2);
		}
		advance();
	}
	}

	/** compiles symbols **/
	private void compileSymbol(String symbol) throws IOException {
	if(!errorOnToken) {
		if(token.equals(symbol)) {
			advance();
		}
		else if(!errorOnToken){
			System.out.println("Syntax Error: Expected '" + symbol + "'");
			throwIllegal("symbol");
		}
	}
	}
	
	/** compiles keywords **/
	private void compileKeyword(String keyword) throws IOException {
	if(!errorOnToken) {
		if(token.equals(keyword)) {
			switch(keyword) {
			case("void"):
				isVoid = true;
				break;
			case("constructor"):				
				/* no differenciationg between functions and constructors, except for the fact that constructors must
				 * call Memory.alloc and return this*/			
				currentSubroutineKind = "constructor";
				break;
			case("function"):
				currentSubroutineKind = "function";
				break;
			case("method"):
				currentSubroutineKind = "method";
				break;
			case("this"):
				vmWriter.writePush("POINTER", 0);
				break;
			default:
				break;
			}
			advance();
		}
		else if(!errorOnToken) {
			throwIllegal("keyword");
		}
	}
	}

	/** compiles types **/
	private void compileType() throws IOException {
	if(!errorOnToken) {
		if(tokenIsType()) {
			if((tokenizer.tokenType(token)).equals("IDENTIFIER")) {
				currentIdentifierType = token;
				advance();
			} else {
				currentIdentifierType = token;
				advance();
			}
		}
		else if(!errorOnToken){
			throwIllegal("type");	
		}
	}
	}
	
	/** compiles operators, pushes the compiled operator onto the passed operator-stack; StringBuilder unaryOp is used in compileExpression
	 * as a means to handle the '-' operator which acts as both a normal operator and a unary operator for the next term (cf. compileExpression 
	 * for more on this). This function appends '-' to the unaryOp-StringBuilder. */
	private void compileOperator(Stack<String> operatorStack, StringBuilder unaryOp) throws IOException {
	if(!errorOnToken) {
		if(tokenIsOperator()) {
			currentOperator = token;
			/* 1) if the current operator is a '-' make sure to update 'unaryOp' accordingly (i.e. append the unaryOp), so
			 * things like (a+b)-(c+d) work like they do in actual math (i.e. we have to translate the one '-' into add and neg 
			 * vm-commands (done in compileExpression)
			 * 2) if the current operator is * or / dont add it to the stack, instead apply it to the 2 respective terms
			 * immediatly after processing them (PEMDAS and all that) */
			if(currentOperator.equals("-")) {
				operatorStack.push("+");
				unaryOp.append("-");
			}
			else if(!currentOperator.equals("*") && !currentOperator.equals("/")) {
				operatorStack.push(token);
			}
			
			advance();
		} else if(!errorOnToken) {
			throwIllegal("operator");
		}
	}
	}
	
	/** compiles 'unaryOp', accepts a unaryOp-StringBuilder as argument and sets it to the currently scanned unary-operator **/
	private void compileUnaryOp(StringBuilder unaryOp) throws IOException {
	if(!errorOnToken) {
		if(tokenIsUnaryOperator()) {
			unaryOp.append(token);
			advance();
		} else if(!errorOnToken) {
			throwIllegal("unaryOp");
		}
	}
	}
	
	/** compiles 'type varName (',' varName)*;' **/
	private void compileTypeVarName() throws IOException {
	if(!errorOnToken) {
		/*type*/				compileType();
		/*varName*/				compileIdentifier(true);	
		
		/*(',' varName)* */		while(true) {
									if(token.equals(",")) {
										advance();
										compileIdentifier(true);
									}
									else if(token.equals(";")){
		/*;*/							compileSymbol(";");
										return;
									}
									else if(!errorOnToken){
										System.out.println("Illegal variable declaration");
										throwIllegal("symbol");
										return;
									}
								}
	}
	}
	
	/** compiles identifiers: className, varName, subroutineName.
	 * Before using this function make sure to set 'currentIdentifierKind' to the appropriate value **/
	private void compileIdentifier(boolean advance) throws IOException {
	if(!errorOnToken) {
		/* first check if identifier adheres to the JACK GRAMMAR SPECIFICATIONS of what an identifier should be */
		if(tokenizer.tokenType(token).equals("IDENTIFIER")) {
			currentIdentifierName = token;
			boolean alreadyOnClassLevelSymbolTable = classLevelSymbolTable.nameExists(currentIdentifierName);	
			boolean alreadyOnSubroutineLevelSymbolTable = subroutineLevelSymbolTable.nameExists(currentIdentifierName);
			
			/* then based on what KIND of identifier we're looking at write different things */
			switch(currentIdentifierKind) {
			
				/* NON-VARIABLES */
				/* class */
				case("className"):
					currentlyProcessedClassName = token;
					updateClassNameTable("CLASS_DECLARATION");
					break;
				case("classNameAsType"):
					currentlyProcessedClassName = token;
					updateClassNameTable("OBJECT_DECLARATION");
					break;
				case("classNameInSubroutineCall"):
					currentlyProcessedClassName = token;
					updateClassNameTable("CLASSNAME_SUBROUTINECALL"); 
					break;
				/* subroutine */
				case("subroutineName"):
					currentlyProcessedSubroutineName = token;
					updateSubroutineTable();
					break;
					
				/* CLASS-LEVEL-VARIABLES */
				case("STATIC"):
				
					/* add if variable is not on symbol table yet */
					if(!alreadyOnClassLevelSymbolTable) {
						classLevelSymbolTable.define(currentIdentifierName, currentIdentifierType, "STATIC");
					}
					/* can't declare the same variable name twice */
					else if(alreadyOnClassLevelSymbolTable && currentVariableIsBeingDeclared && !errorOnToken) {
						System.out.println("Compilation Error: Duplicate variable '" + currentIdentifierName + "'");
						throwIllegal("duplicate variable");
					}
					/* otherwise an already existing variable is just being called, which is perfectly fine, and we don't need to do anything else here */
	
					break;
				case("FIELD"):
				
					/* add if variable is not on symbol table yet */
					if(!alreadyOnClassLevelSymbolTable) {
						classLevelSymbolTable.define(currentIdentifierName, currentIdentifierType, "FIELD");
					}
					/* can't declare the same variable name twice */
					else if(alreadyOnClassLevelSymbolTable && currentVariableIsBeingDeclared && !errorOnToken) {
						System.out.println("Compilation Error: Duplicate variable '" + currentIdentifierName + "'");
						throwIllegal("duplicate variable");
					}
					/* otherwise an already existing variable is just being called, which is perfectly fine, and we don't need to do anything else here */
	
					break;
					
				/* SUBROUTINE-LEVEL-VARIABLES */				
				case("VAR"):
				
					/* add if variable is not on symbol table yet */
					if(!alreadyOnSubroutineLevelSymbolTable) {
						subroutineLevelSymbolTable.define(currentIdentifierName, currentIdentifierType, "VAR");
					}
					/* can't declare the same variable name twice */
					else if(alreadyOnSubroutineLevelSymbolTable && currentVariableIsBeingDeclared && !errorOnToken) {
						System.out.println("Compilation Error: Duplicate variable '" + currentIdentifierName + "'");
						throwIllegal("duplicate variable");
					}
					/* otherwise an already existing variable is just being called, which is perfectly fine, and we don't need to do anything else here */
					
					break;
				case("ARG"):
					
					/* add if variable is not on symbol table yet */
					if(!alreadyOnSubroutineLevelSymbolTable) {
						subroutineLevelSymbolTable.define(currentIdentifierName, currentIdentifierType, "ARG");
					}
					/* can't declare the same variable name twice */
					else if(alreadyOnSubroutineLevelSymbolTable && currentVariableIsBeingDeclared && !errorOnToken) {
						System.out.println("Compilation Error: Duplicate variable '" + currentIdentifierName + "'");
						throwIllegal("duplicate variable");
					}
					/* otherwise an already existing variable is just being called, which is perfectly fine, and we don't need to do anything else here */
					
					break;
	
				/* ILLEGAL */
				default:
					if(!errorOnToken) {
						System.out.println("Syntax Error: Illegal identifier kind " + currentIdentifierKind);
						throwIllegal("identifier kind");
					}
			}
			
			if(advance) {
				advance();
			}
		}
		else if(!errorOnToken){
			System.out.println("Syntax Error: Invalid className / varName / subroutineName");
			throwIllegal("identifier");
		}
	}
	}

	/** updates the class-table based on whether 'className' is used in the context of a class-declaration or
	 * an object declaration that is an instance of 'className'. Add the current class to the symbol table */
	private void updateClassNameTable(String typeOfUsage) throws IOException {
	if(!errorOnToken) {
		boolean classAlreadyOnTable = classNameTable.containsKey(currentlyProcessedClassName);
		
		if(!isOSClassName()) {
			switch(typeOfUsage) {
			case("CLASS_DECLARATION"):
				/* make sure className is the same as the name of the .jack-file it's located in */
				if(!token.equals(currentFileName) && !errorOnToken) {
					System.out.println("Class-name must be the same as file-name. Please change the class-name");
					throwIllegal("className");
					break;
				} else {
					classNameTable.put(currentlyProcessedClassName, "CLASSNAME_DECLARED");
					break;
				}
	
			case("OBJECT_DECLARATION"):
				if(!classAlreadyOnTable) {
					classNameTable.put(currentlyProcessedClassName, "CLASSNAME_NOT_DECLARED");
				}
				break;
			case("CLASSNAME_SUBROUTINECALL"):
				if(!classAlreadyOnTable) {
					classNameTable.put(currentlyProcessedClassName, "CLASSNAME_NOT_DECLARED");
				}
				break;
			default:
				if(!errorOnToken) {
					System.out.println("Invalid way to use a className");
					throwIllegal("type of usage of className");
				}
			}
		}
	}
	}

	/** updates the subroutine-table. Adds the current subroutine if it's not on there yet. Changes the current subroutine to "DECLARED"
	 * if it's been declared now */
	private void updateSubroutineTable() throws IOException {
	if(!errorOnToken) {
		String subroutineName = currentlyProcessedClassName + "." + currentlyProcessedSubroutineName;
		String[] currentSubroutineTableValues = new String[3];
		boolean subroutineIsBeingCalled = currentSubroutineCalledOrDeclared.equals("CALLED");
		boolean subroutineIsBeingDeclared = currentSubroutineCalledOrDeclared.equals("DECLARED");
		boolean subroutineWasAlreadyDeclared = (subroutineTable.containsKey(subroutineName)) ? 
												subroutineTable.get(subroutineName)[1].equals("DECLARED") : false;
		boolean onSubroutineTable = subroutineTable.containsKey(subroutineName);
		boolean subroutinesNArgsHaveChanged = false;
		String previousSubroutineKind = "NONE";
		String previousSubroutineCalledOrDeclared = "NONE";
		
		if(onSubroutineTable) {
			subroutinesNArgsHaveChanged = (Integer.parseInt(subroutineTable.get(subroutineName)[2]) == nArgs) ? false : true;
			previousSubroutineKind = subroutineTable.get(subroutineName)[0];
			previousSubroutineCalledOrDeclared = subroutineTable.get(subroutineName)[1];
		}
		
		if(subroutineIsBeingCalled && !onSubroutineTable) {
			currentSubroutineTableValues[0] = currentSubroutineKind;
			currentSubroutineTableValues[1] = currentSubroutineCalledOrDeclared;
			currentSubroutineTableValues[2] = nArgs.toString();
			
			subroutineTable.put(subroutineName, currentSubroutineTableValues);
			
		} else if(subroutineIsBeingDeclared && subroutineWasAlreadyDeclared && !errorOnToken) {
			System.out.println("Subroutine '" + subroutineName + "' has been declared twice");
			throwIllegal("subroutine");
			
		} else if(subroutineIsBeingDeclared) {
			currentSubroutineTableValues[0] = currentSubroutineKind;
			currentSubroutineTableValues[1] = currentSubroutineCalledOrDeclared;
			currentSubroutineTableValues[2] = nArgs.toString();
			
			subroutineTable.put(subroutineName, currentSubroutineTableValues);
		}
		
		/* make sure the subroutine kind hasn't changed over the course of the program. if it has throw a compilition error */
		boolean subroutineKindChanged = (subroutineTable.containsKey(subroutineName)) ?
				!subroutineTable.get(subroutineName)[0].equals(previousSubroutineKind) : false;
		if(previousSubroutineKind.equals("NONE")) {subroutineKindChanged = false;}
		
		boolean subroutineWasAlreadyDeclaredAsConstructorPreviously = previousSubroutineKind.equals("constructor");
		boolean subroutineWasAlreadyCalledAsFunctionPreviously = previousSubroutineKind.equals("function") 
																&& previousSubroutineCalledOrDeclared.equals("CALLED");
		boolean currentlyProcessedSubroutineKindIsFunction = currentSubroutineKind.equals("function");
		boolean currentlyProcessedSubroutineKindIsConstructor = currentSubroutineKind.equals("constructor");
		
		/* when you call a constructor, compileSubroutineCallOnClass will set the currentSubroutineKind to "function"
		 * instead of "constructor", this code handles this exception */
		
		/* subroutine was already declared as a constructor but is now being called as a function */
		if(subroutineIsBeingCalled && subroutineWasAlreadyDeclared) {
			if(subroutineWasAlreadyDeclaredAsConstructorPreviously && currentlyProcessedSubroutineKindIsFunction) {
				subroutineKindChanged = false;
			}
		}
		
		/* subroutine was already called as a function but is now being declared as a constructor */
		if(subroutineIsBeingDeclared && subroutineWasAlreadyCalledAsFunctionPreviously) {
			if(currentlyProcessedSubroutineKindIsConstructor) {
				subroutineKindChanged = false;
			}
		}
				
		if(subroutineKindChanged && !errorOnToken) {
			System.out.println("Compilation Error: Subroutine " + "'" + subroutineName + "' has been used as both a 'function' and a 'method'");
			throwIllegal("subroutine kind / usage of subroutine");
		}
		
		/* make sure the amount of arguments a certain subroutine uses haven't changed over the course of the program */
		if(subroutinesNArgsHaveChanged && !errorOnToken) {
			System.out.println("Subroutine '" + subroutineName + "' has been used at least twice (either called and/or declared) with different"
								+ " amounts of parameters");
			throwIllegal("number of parameters");
		}
	}
	}

	/** compiles 'varName || varName[expression] || subroutineCall (i.e. subroutine() || varName.subroutine())' **/
	private void compileIdentifierPolymorphism() throws IOException {
	if(!errorOnToken) {
		String previousToken = token;
		advance();
		String advancedToken = token;
		 /* to use a varName in this context, i.e. in a term/expression, the variable must have already been declared earlier
		  * thus the varName must necessarily exist on either the classSymbolTable or the subroutineSymbolTable	*/				
		switch(advancedToken) {
			case("("):
				/* method by default, a subroutineCall like this can ONLY be called from WITHIN a method (both in do-statements and
				 * in expressions), thus it knows to operate on the same object as the method it's called from, since we're assuming that
				 * the code that is to be compiled is correct (for the most part i guess), we can/have to assume that do subroutineCall() from
				 * within a certain method (can't do it anywhere else as previously explained), is strictly a method and not a function or a constructor */
				compileSimpleSubroutineCall(previousToken);
				break;
			case("."):
				/* could be either object.subroutineCall() or className.subroutineCall() */
				if(subroutineLevelSymbolTable.get(previousToken) != null || classLevelSymbolTable.get(previousToken) != null) {
					//method
					compileSubroutineCallOnObject(previousToken);
					break;
				} else {
					//function or constructor
					compileSubroutineCallOnClass(previousToken);
					break;
				}
				
			case("["):
				/* all that nonArrayVariableIsBeingAssigned is supposed to do is make it so we dont immediately push 
				 * varName of a certain non-array variable on the stack when we're in a let-statement
				 * since that wouldn't produce correct VM-code, since we're not looking at such a case right now we can safely
				 * set it to false */
				nonArrayVariableIsBeingAssigned = false;
				compileArrayAccess(previousToken);
				break;
			default:
				compileVarNameInCaseOfIdentifierPolymorphism(previousToken);
		}
	}
	}
	
	/** compiles 'subroutineName(expressionList)', this acts as a method. Then goes on to write a vm-Call command to the output-file. **/
	private void compileSimpleSubroutineCall(String subroutineName) throws IOException {
	if(!errorOnToken) {
		String currentOuterSubroutineKind = currentSubroutineKind;
		currentSubroutineKind = "method";
		currentSubroutineCalledOrDeclared = "CALLED";
		nArgs = 0;
		
							/* since we impose simpleSubroutineCall to be a method and we furthermore impose that simpleSubroutineCall
							 * can only be called within a method (that obviously already operates on a method) we now push the currently
							 * operated-upon object onto the stack as argument 0 for this here subroutineCall */
							if(currentOuterSubroutineKind.equals("constructor") || currentOuterSubroutineKind.equals("method")) {
								vmWriter.writePush("POINTER", 0);
								nArgs++;
							} else if(!errorOnToken){
								System.out.println("Cannot perform a simpleSubroutineCall (i.e. a function-call without a specified"
										+ " objectName or className seperated from the subroutineName by a '.') from within a 'function'.");
								throwIllegal("simpleSubroutineCall");
							}

							currentIdentifierKind = "subroutineName";
							currentlyProcessedClassName = currentFileName;
								
								
		/*subroutineName*/		/* skipped for now we first need to determine nArgs before we can do compileIdentifier,
								 * so we can update the subroutine-table correctly*/
		/*(*/					compileSymbol("(");
		/*expressionList*/		compileExpressionList();
		/*)*/					compileSymbol(")");
		
		String tokenSave = token; 
		token = subroutineName;
		compileIdentifier(false);
		if(!errorOnToken) {
			token = tokenSave;
		}
		
		vmWriter.writeCall(currentFileName + "." + subroutineName, nArgs);
		currentSubroutineKind = currentOuterSubroutineKind;
	}
	}

	/** compiles 'objectName.subroutineName(expressionList)', this acts as a method. Then goes on to write a vm-Call command to the output-file. **/
	private void compileSubroutineCallOnObject(String objectName) throws IOException {
	if(!errorOnToken) {
		String currentOuterSubroutineKind = currentSubroutineKind;
		
		currentSubroutineKind = "method";
		currentSubroutineCalledOrDeclared = "CALLED";
		nArgs = 0;
		
							String advancedToken = token;
							token = objectName;
		/*objectName*/			determineKindOfIdentifier();
								compileIdentifier(false);
								
							/* set currentlyProcessedClassName to objectName's type */
							if(subroutineLevelSymbolTable.nameExists(objectName)) {
								currentlyProcessedClassName = subroutineLevelSymbolTable.typeOf(objectName);
							}
							else if(classLevelSymbolTable.nameExists(objectName)) {
								currentlyProcessedClassName = classLevelSymbolTable.typeOf(objectName);
							}
							else if(!errorOnToken) {
								System.out.println("Compilation Error: Variable '" + objectName + "' has not been declared.");
								throwIllegal("variable");
							}
							 
							/* push the to-be-operated-upon object onto the stack as argument 0 */
							lookUpVariableAndWritePushIfItExists(objectName);
							nArgs++;
							
							token = advancedToken;
		/*.*/					compileSymbol(".");
		
		/*subroutineName*/		currentIdentifierKind = "subroutineName";
								currentlyProcessedSubroutineName = token;
								String currentSubroutineNameSave = currentlyProcessedSubroutineName;
								/* advance for now we first need to determine nArgs before we can do compileIdentifier,
		 						* so we can update the subroutine-table correctly*/
								advance();
								
		/*(*/					compileSymbol("(");
		/*expressionList*/		compileExpressionList();
		/*(*/					compileSymbol(")");
		
		String tokenSave = token; 
		token = currentlyProcessedSubroutineName;
		compileIdentifier(false);
		if(!errorOnToken) {
			token = tokenSave;
		}
		
		currentlyProcessedSubroutineName = currentSubroutineNameSave;
		vmWriter.writeCall(currentlyProcessedClassName + "." + currentlyProcessedSubroutineName, nArgs);
		currentSubroutineKind = currentOuterSubroutineKind;
	}
	}
	
	/** compiles 'className.subroutineName(expressionList)', this acts as a function (or a constructor, but in terms of calling it there's no difference).
	 * Then goes on to write a vm-Call command to the output-file. **/
	private void compileSubroutineCallOnClass(String className) throws IOException {
	if(!errorOnToken) {
		String currentOuterSubroutineKind = currentSubroutineKind;
		
		currentSubroutineKind = "function";
		currentSubroutineCalledOrDeclared = "CALLED";
		nArgs = 0;
		
		/* need these two lines so inside compileIdentifier, where currentIdentifierKind is 'subroutineName', we dont 
		 * add fileName.subroutineName to the table but instead add alreadyPresentClassName.subroutineName
		 * to the table */
		String currentFileNameSave = currentFileName;
		currentFileName = className;
		
								String advancedToken = token;
								token = className;
		/*className*/			currentIdentifierKind = "classNameInSubroutineCall";	
									compileIdentifier(false);
								token = advancedToken;
		/*.*/						compileSymbol(".");
		/*subroutineName*/		currentIdentifierKind = "subroutineName";
								currentlyProcessedSubroutineName = token;
								String currentSubroutineNameSave = currentlyProcessedSubroutineName;
								/* advance for now we first need to determine nArgs before we can do compileIdentifier,
								* so we can update the subroutine-table correctly*/
								advance();
								
		currentFileName = currentFileNameSave;
								
		/*(*/						compileSymbol("(");
		/*expressionList*/			compileExpressionList();
		/*(*/						compileSymbol(")");
		
		String tokenSave = token; 
		token = currentlyProcessedSubroutineName;
		compileIdentifier(false);
		if(!errorOnToken) {
			token = tokenSave;
		}
		
		currentlyProcessedSubroutineName = currentSubroutineNameSave;
		vmWriter.writeCall(className + "." + currentlyProcessedSubroutineName, nArgs);
		currentSubroutineKind = currentOuterSubroutineKind;
	}
	}
	
	/** compiles varName[expression] **/
	private void compileArrayAccess(String arrayName) throws IOException {
	if(!errorOnToken) {
		/* set this to true so we can use it as its expected to be used in a let-statement
		 * if its being used within an expression thats handled within compile-term */ 
		isArray = true;

						String advancedToken = token;
						token = arrayName;
		/*varName*/			determineKindOfIdentifier();
							/* push base-address of array on the stack (if it exists) */
							lookUpVariableAndWritePushIfItExists(arrayName);
						token = advancedToken;
		/*[*/				compileSymbol("[");
							/* don't know if next expression is array, also need to set it to false so it works as expected in compileTerm*/
							isArray = false;
							/* implicitly pushing specified array-offset (i.e. offset in arr[x] is x) */
		/*expression*/		compileExpression();
		/*]*/				compileSymbol("]");
		
		/* pop baseAdd + offset onto pointer 1 */
		vmWriter.writeArithmetic("ADD");
		/* compileTerm might set this to false, so, so we can use isArray as expected, do this */
		isArray = true;
	}
	}
	
	/** compiles varName **/
	private void compileVarNameInCaseOfIdentifierPolymorphism(String varName) throws IOException {
	if(!errorOnToken) {
		if(!nonArrayVariableIsBeingAssigned) {
			String advancedToken = token;
					token = varName;
		/*varName*/		determineKindOfIdentifier();
						lookUpVariableAndWritePushIfItExists(varName);
					token = advancedToken;
		}	
		nonArrayVariableIsBeingAssigned = false;
	}
	}
	
	/** looks through both symbol-tables for corresponding VM-name of variable (i.e. local 0, argument 1, etc.) and writes the corresponding
	 * VM-push command to the output-file to push the passed variable, throws error if not found */
	private void lookUpVariableAndWritePushIfItExists(String varName) throws IOException {
	if(!errorOnToken) {
	 	if(subroutineLevelSymbolTable.nameExists(varName)) {
	 		vmWriter.writePush(subroutineLevelSymbolTable.kindOf(varName), subroutineLevelSymbolTable.indexOf(varName));
	 	}
	 	else if(classLevelSymbolTable.nameExists(varName)) {
	 		vmWriter.writePush(classLevelSymbolTable.kindOf(varName), classLevelSymbolTable.indexOf(varName));
	 	}
	 	else if(!errorOnToken) {
	 		System.out.println("Compilation Error: Variable '" + varName + "' has not been declared.");
	 		throwIllegal("variable");
	 	}
	}
	}
	
	/** looks through both symbol-tables for corresponding VM-name of variable (i.e. local 0, argument 1, etc.) and writes the corresponding
	 * VM-pop command to the output-file to pop the passed variable, throws error if not found */
	private void lookUpVariableAndWritePopIfItExists(String varName) throws IOException {
	if(!errorOnToken) {
	 	if(subroutineLevelSymbolTable.nameExists(varName)) {
	 		vmWriter.writePop(subroutineLevelSymbolTable.kindOf(varName), subroutineLevelSymbolTable.indexOf(varName));
	 	}
	 	else if(classLevelSymbolTable.nameExists(varName)) {
	 		vmWriter.writePop(classLevelSymbolTable.kindOf(varName), classLevelSymbolTable.indexOf(varName));
	 	}
	 	else if(!errorOnToken) {
	 		System.out.println("Compilation Error: Variable '" + varName + "' has not been declared.");
	 		throwIllegal("variable");
	 	}
	}
	}
	
	/** given a certain grammatically valid identifier, determines its kind and sets 'currentIdentifierKind' to
	 * the determined kind, writes an error if its an illegal kind. Also implicitly checks if the identifier exisits
	 * on either the 'subroutineLevelSymbolTable' or the 'classLevelSymbolTable' **/
	private void determineKindOfIdentifier() throws IOException {
	if(!errorOnToken) {
		if(subroutineLevelSymbolTable.get(token) != null) {
			currentIdentifierKind = subroutineLevelSymbolTable.kindOf(token);
		}
		else if(classLevelSymbolTable.get(token) != null) {
			currentIdentifierKind = classLevelSymbolTable.kindOf(token);
		}
		else if(!errorOnToken) {
			System.out.println("Syntax Error: Undeclared variable in expression(/term)");
			throwIllegal("identifier");
		}
	}
 	}
	
	/** is currentToken a type? **/
	private boolean tokenIsType() {
		if(token.equals("int") || token.equals("char") || token.equals("boolean") || (tokenizer.tokenType(token)).equals("IDENTIFIER") 
		   /*className; but other invalid classNames would also work, due to Jack grammar specifications*/) {
			return true;
		}
		else {
			return false;
		}
	}
	
	/** is currentToken a statement type? **/
	private boolean tokenIsStatement() {
		if(token.equals("let") || token.equals("do") || token.equals("if") || token.equals("while") || token.equals("return")) {
			return true;
		}
		else {
			return false;
		}
	}
	
	/** is currentToken an operator? **/
	private boolean tokenIsOperator() {
		if(token.equals("+") || token.equals("-") || token.equals("*") || token.equals("/") || token.equals("&")
		|| token.equals("|") || token.equals("<") || token.equals(">") || token.equals("=")) {
			return true;
		}
		else {
			return false;
		}
	}
	
	/** is currentToken a unaryOp? **/
	private boolean tokenIsUnaryOperator() {
		if(token.equals("~") || token.equals("-")) {
			return true;
		}
		else {
			return false;
		}
	}
	
	/** is currentToken a constant? **/
	private boolean tokenIsConstant() {
		if(tokenizer.tokenType(token).equals("STRING_CONST") || tokenizer.tokenType(token).equals("INT_CONST") || tokenIsKeywordConstant()) {
			return true;
		}
		else {
			return false;
		}
	}
	
	
	/** is currentToken a keywordConstant? **/
	private boolean tokenIsKeywordConstant() {
		if(token.equals("true") || token.equals("false") || token.equals("null") || token.equals("this")) {
			return true;
		}
		else {
			return false;
		}
	}	
	
	/** is the current className a className that's already being used in the Jack OS? **/
	private boolean isOSClassName() {
		switch(token) {
		case("Math"): return true;
		case("String"): return true;
		case("Array"): return true;
		case("Output"): return true;
		case("Screen"): return true;
		case("Keyboard"): return true;
		case("Memory"): return true;
		case("Sys"): return true;
		default: return false;
		}
	}

	/** writes <token> INVALID TOKEN </token> **/
	private void throwIllegal(String exception) throws IOException {
		if(!errorOnToken) {
			errorOnToken = true;
			System.out.println("In '" + currentFileName + "' at line: " + tokenizer.getLineNumber() 
								+ ": Error on this token: '" + CompilationEngine.token + "'. Delete this token.");
			System.out.println("Exception: Illegal " + exception);
			advance();
		}
	}
}