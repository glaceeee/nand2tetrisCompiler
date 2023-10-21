# nand2tetrisCompiler
My implementation of the compiler of nand2tetris, a LL(0) compiler, that generates code immediately without looking ahead even just one token,
except for when it needs to differentiate between a variable, an array access or a subroutine, where it then looks ahead one token.
- JackCompiler.java is the main execution routine, which gets either a file or a folder of .jack files as input and is then tasked to create and call all the necessary object and object routines to translate those .jack files into .vm files
- CompilationEngine.java takes care of the whole compilation process, meaning it uses all the other classes to step through the code, token-by-token and generates nand2tetris VM-code.
- JackTokenizer.java creates the tokens the CompilationEngine reads, while skipping all the whitespace and comments. It also knows what type of concept a certain token is, either a KEYWORD, a SYMBOL, an INT_CONST, a STRING_CONST or an IDENTIFIER
- VMWriter.java simply writes the VM-commands the CompilationEngine tells it to write.
- SymbolTable.java records all the variables used within a given scope alongside their type (int, char, boolean, className, OSClassName) and kind (Static(STATIC), Member(FIELD), Argument(ARG), Local(VAR)).

Like I said in the commit this program only compiled my OS alongside all the provided test-scripts (yes that includes the pong-game) and is probably still unimaginably buggy. 
I just thought I'd upload it here so i can look back on it one day because I'm really proud of this project, it took me forever to finish :)
