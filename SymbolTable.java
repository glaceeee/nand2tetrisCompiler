package compiler;

import java.util.HashMap;
import java.util.Set;

/*
 * We will use two seperate symbol-tables: 
 * one for the class level that is never reset
 * and one for the subroutine level that is reset each time we process a new subroutine
 */

public class SymbolTable {
	private HashMap<String,String[]> symbolTable;
	private static Integer fieldIndex;
	private static Integer staticIndex;
	private static Integer localIndex; //i.e. 'var'
	private static Integer argumentIndex;
	
	static {
		/* initialized to -1 for addToSymbolTable */
		fieldIndex = -1;
		staticIndex = -1;
		localIndex = -1;
		argumentIndex = -1;
	}
	
	SymbolTable(){
		symbolTable = new HashMap<String,String[]>();
		fieldIndex = -1;
		staticIndex = -1;
		localIndex = -1;
		argumentIndex = -1;
	}
	
	public String[] get(String key) {
		return this.symbolTable.get(key);
	}
	
	public Set<String> getKeySet(){
		return symbolTable.keySet();
	}
	
	public int getLocalIndex() {
		return localIndex;
	}
	
	public int getArgumentIndex() {
		return argumentIndex;
	}
	
	public int getFieldIndex() {
		return fieldIndex;
	}
	
	/* start of a new subroutine/class scope, resets the subroutines symboltable */
	public void startSubroutine() {
		this.symbolTable.clear();
		localIndex = -1;
		argumentIndex = -1;
	}
	
	/* gets identifier's name, type and kind, assigns it to one of the running indicies and adds the quple to the symbol-table */
	public void define(String name, String type, String kind) {
		if(!nameExists(name)) {
			switch(kind) {
			case("STATIC"):
				addToSymbolTable(name,type,"STATIC");
				break;
			case("FIELD"):
				addToSymbolTable(name,type,"FIELD");
				break;
			case("VAR"):
				addToSymbolTable(name,type,"VAR");
				break;
			case("ARG"):
				addToSymbolTable(name,type,"ARG");
				break;
			default: 
				System.out.println("Invalid variable kind in SymbolTable.define");
			}
		}
	}	
	
	/* returns the running index of certain kind of identifier */
	public int varCount(String kind) {
		switch(kind) {
		case("STATIC"):
			return staticIndex;
		case("FIELD"):
			return fieldIndex;
		case("VAR"):
			return localIndex;
		case("ARG"):
			return argumentIndex;
		default: 
			System.out.println("Invalid amount of " + kind + " variables");
			return -1;
		}
	}
	
	/* returns type of a given identifier */
	public String typeOf(String name) {
		String type = this.symbolTable.get(name)[0];
		return type;
	}	
	
	/* returns the kind of a given identifier, if the identifier is unknown, returns "NONE" */
	public String kindOf(String name) {
		String kind = this.symbolTable.get(name)[1];
		if(!isValidKind(kind)) {
			return "NONE";
		}
		else {
			return kind;
		}
	}
	
	/* returns index of given identifier */
	public int indexOf(String name) {
		int index = Integer.parseInt(this.symbolTable.get(name)[2]);
		return index;
	}
	
	
	/** Helper functions **/
	/** adds (name,type,kind,index) quple to symbolTable **/
	private void addToSymbolTable(String name, String type, String kind) {
		Integer index = 0;
		switch(kind) {
			case("VAR"): 
				localIndex++;
				index = localIndex;
				break;
			case("ARG"): 
				argumentIndex++; 
				index = argumentIndex;
				break;
			case("STATIC"): 
				staticIndex++; 
				index = staticIndex;
				break;
			case("FIELD"): 
				fieldIndex++;
				index = fieldIndex;
				break;
			default: System.out.println("Invalid index (due to invalid kind) in addToSymbolTable"); return;
		}
		
		String[] TypeKindIndexTriplet = new String[3];
		TypeKindIndexTriplet[0] = type;
		TypeKindIndexTriplet[1] = kind;
		TypeKindIndexTriplet[2] = index.toString();
		this.symbolTable.put(name,TypeKindIndexTriplet);	
		

	}
	
	/** checks if current kind is valid **/
	private boolean isValidKind(String kind) {
		switch(kind) {
		case("STATIC"):
			return true;
		case("FIELD"):
			return true;
		case("VAR"):
			return true;
		case("ARG"):
			return true;
		default: 
			return false;
		}
	}
	
	/** checks if a given name exists **/
	public boolean nameExists(String name) {
		if(this.symbolTable.get(name) != null) {
			return true;
		}
		else {
			return false;
		}
	}
}
