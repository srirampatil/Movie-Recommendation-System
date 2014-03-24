package com.ire.db;
import java.util.HashSet;
import java.util.Set;


public class SPUtils {
	public static Set<Long> split(String source, String delim) {
		if(source == null || delim == null || source.length() == 0)
			return null;
		
		Set<Long> splitSet = new HashSet<Long>();
		String[] splits = source.split(delim);
		for (String string : splits)
			splitSet.add(Long.parseLong(string));
		
		return splitSet;
	}
}
