package compiler;

import java.io.FileWriter;
import java.io.IOException;

public class VMWriter {
	private static FileWriter writer;
	
	VMWriter(String outputFile) throws IOException {
		writer = new FileWriter(outputFile);
	}
	
	public void writePush(String segment, int index) throws IOException {
		switch(segment) {
		case("ARG"):
			writer.write("push argument " + index + "\n");
			break;
		case("VAR"):
			writer.write("push local " + index + "\n");
			break;
		case("STATIC"):
			writer.write("push static " + index + "\n");
			break;
		case("FIELD"):
			writer.write("push this " + index + "\n");
			break;
		case("THAT"):
			writer.write("push that " + index + "\n");
			break;
		case("POINTER"):
			writer.write("push pointer " + index + "\n");
			break;
		case("TEMP"):
			writer.write("push temp " + index + "\n");
			break;
		case("CONST"):
			writer.write("push constant " + index + "\n");
			break;
		default:
			writer.write("INVALID PUSH COMMAND\n");
			System.out.println("INVALID PUSH COMMAND");
		}
	}
	
	public void writePop(String segment, int index) throws IOException {
		switch(segment) {
		case("ARG"):
			writer.write("pop argument " + index + "\n");
			break;
		case("VAR"):
			writer.write("pop local " + index + "\n");
			break;
		case("STATIC"):
			writer.write("pop static " + index + "\n");
			break;
		case("FIELD"):
			writer.write("pop this " + index + "\n");
			break;
		case("THAT"):
			writer.write("pop that " + index + "\n");
			break;
		case("POINTER"):
			writer.write("pop pointer " + index + "\n");
			break;
		case("TEMP"):
			writer.write("pop temp " + index + "\n");
			break;
		case("CONST"):
			writer.write("pop constant " + index + "\n");
			break;
		default:
			writer.write("INVALID POP COMMAND\n");
			System.out.println("INVALID POP COMMAND");
		}
	}
	
	public void writeArithmetic(String command) throws IOException {
		switch(command) {
		case("ADD"):
			writer.write("add\n");
			break;
		case("SUB"):
			writer.write("sub\n");
			break;
		case("NEG"):
			writer.write("neg\n");
			break;
		case("EQ"):
			writer.write("eq\n");
			break;
		case("GT"):
			writer.write("gt\n");
			break;
		case("LT"):
			writer.write("lt\n");
			break;
		case("AND"):
			writer.write("and\n");
			break;
		case("OR"):
			writer.write("or\n");
			break;
		case("NOT"):
			writer.write("not\n");
			break;
		case("MULTIPLY"):
			writer.write("call Math.multiply 2\n");
			break;
		case("DIVIDE"):
			writer.write("call Math.divide 2\n");
			break;
		default:
			writer.write("INVALID ARITHMETIC COMMAND\n");
			System.out.println("INVALID ARITHMETIC COMMAND");
		}
	}
	
	
	public void writeLabel(String label) throws IOException {
		writer.write("label " + label + "\n");
	}
	
	public void writeGoto(String label) throws IOException {
		writer.write("goto " + label + "\n");
	}
	
	public void writeIf(String label) throws IOException {
		writer.write("if-goto " + label + "\n");
	}
	
	public void writeCall(String name, int nArgs) throws IOException {
		writer.write("call " + name + " " + nArgs + "\n");
	}
	
	public void writeFunction(String name, int nLocals) throws IOException {
		writer.write("function " + name + " " + nLocals + "\n");
	}
	
	public void writeReturn() throws IOException {
		writer.write("return\n");
	}
	
	public void close() throws IOException {
		writer.close();
	}
}
