package com.ire.db;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBUtils {
	private static final String USERNAME = "root";
	private static final String PASSWORD = "pampyare";

	public static Connection initConnection() {
		try {
			Class.forName("com.mysql.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			System.out.println("Where is your MySQL JDBC Driver?");
			e.printStackTrace();
		}

		System.out.println("MySQL JDBC Driver Registered!");
		Connection connection = null;
		
		try {
			connection = DriverManager.getConnection(
					"jdbc:mysql://localhost:3306/IMDB", USERNAME, PASSWORD);

		} catch (SQLException e) {
			System.out.println("Connection Failed! Check output console");
			e.printStackTrace();
		}

		if (connection != null) {
			System.out.println("You made it, take control your database now!");
		} else {
			System.out.println("Failed to make connection!");
		}
		
		return connection;
	}
}
