package edu.tum.cs.mln;

import java.io.PrintStream;

public class MLNWriter {
	protected java.io.PrintStream out;
	
	public MLNWriter(PrintStream out) {
		this.out = out;
	}
	
	public void writePredicateDecl(String predName, String[] types) {
		out.print(formatAsPredName(predName));
		out.print('(');
		for(int i = 0; i < types.length; i++) {
			if(i > 0)
				out.print(", ");
			out.print(formatAsTypeName(types[i]));
		}
		out.println(')');
	}
	
	/**
	 * 
	 * @param predName
	 * @param params
	 * @param detParams parameters that are functionally determined by the others
	 */
	public void writeMutexDecl(String predName, String[] params, String[] detParams) {
		out.print(formatAsPredName(predName));
		out.print('(');
		for(int i = 0; i < params.length; i++) {
			if(i > 0) out.print(", ");
			out.print("a" + i);
			for(int j = 0; j < detParams.length; j++)
				if(params[i].equals(detParams[j])) {
					out.print("!");
					break;
				}					
		}
		out.println(')');
	}
	
	public static String formatAsPredName(String predName) {
		return lowerCaseString(predName);
	}
	
	public static String formatAsTypeName(String typeName) {
		return lowerCaseString(typeName);
	}
	
	/**
	 * returns a string where the first letter is lower case
	 * @param s the string to convert
	 * @return the string s with the first letter converted to lower case
	 */
	public static String lowerCaseString(String s) { 
		char[] c = s.toCharArray();
		c[0] = Character.toLowerCase(c[0]);
		return new String(c);
	}

	/**
	 * returns a string where the first letter is upper case
	 * @param s the string to convert
	 * @return the string s with the first letter converted to upper case
	 */
	public static String upperCaseString(String s) { 
		char[] c = s.toCharArray();
		c[0] = Character.toUpperCase(c[0]);
		return new String(c);
	}

}
