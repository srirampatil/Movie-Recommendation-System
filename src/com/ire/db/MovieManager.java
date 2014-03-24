package com.ire.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class MovieManager {
	private static final String MOVIE_TABLE_NAME = "MOVIES";

	private static final String COLUMN_ID = "ID";
	private static final String COLUMN_YEAR = "YEAR";
	private static final String COLUMN_NAME = "NAME";
	private static final String COLUMN_GENRE_IDS = "GENRE_IDS";
	private static final String COLUMN_LANGUAGE_IDS = "LANGUAGE_IDS";
	private static final String COLUMN_ACTOR_IDS = "ACTOR_IDS";
	private static final String COLUMN_DIRECTOR_IDS = "DIRECTOR_IDS";

	private static Connection connection = null;
	private static MovieManager manager = null;

	static {
		manager = new MovieManager();
	}

	private MovieManager() {
		connection = DBUtils.initConnection();
	}

	public static MovieManager getInstance() {
		if (manager == null)
			manager = new MovieManager();

		return manager;
	}

	public static Movie movieFromResultSet(ResultSet resultSet) {
		Movie movie = new Movie();
		try {
			movie.setMovieId(resultSet.getLong(COLUMN_ID));
			movie.setYear(resultSet.getInt(COLUMN_YEAR));
			movie.setMovieName(resultSet.getString(COLUMN_NAME));
			movie.setGenreIdsStr(resultSet.getString(COLUMN_GENRE_IDS));
			movie.setLanguageIdsStr(resultSet.getString(COLUMN_LANGUAGE_IDS));
			movie.setActorIdsStr(resultSet.getString(COLUMN_ACTOR_IDS));
			movie.setDirectorIdsStr(resultSet.getString(COLUMN_DIRECTOR_IDS));

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return movie;
	}

	private List<Movie> listFromResultSet(ResultSet resultSet) {
		List<Movie> movieList = new ArrayList<Movie>();
		try {
			while (resultSet.next()) {
				Movie movie = movieFromResultSet(resultSet);
				if (movie != null && movie.genreSet != null
						&& !movie.genreSet.isEmpty())
					movieList.add(movie);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return movieList;
	}

	private List<Movie> getMovies(String query) {
		List<Movie> movieList = null;
		Statement statement = null;
		ResultSet resultSet = null;
		try {
			statement = connection.createStatement();
			resultSet = statement.executeQuery(query);

			movieList = listFromResultSet(resultSet);

		} catch (SQLException e) {
			System.err.println(query);
			//e.printStackTrace();
		} finally {
			try {
				if (statement != null)
					statement.close();

			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		/*
		 * // Removing duplicates if (movieList != null && !movieList.isEmpty())
		 * { HashMap<String, Integer> movieToGenreMap = new HashMap<String,
		 * Integer>(); Movie movie = null;
		 * 
		 * Iterator<Movie> it = movieList.iterator(); while (it.hasNext()) {
		 * movie = it.next(); Integer length =
		 * movieToGenreMap.get(movie.getMovieName()); if (length != null &&
		 * length < movie.getGenreIdsStr().length()) { length =
		 * movie.getGenreIdsStr().length(); it.remove(); } } }
		 */

		return movieList;
	}

	public List<Movie> getMoviesByNameIn(List<String> movieNameList) {
		if (movieNameList == null || movieNameList.size() == 0)
			return null;

		StringBuilder moviesString = new StringBuilder("(");
		for (String string : movieNameList) {
			string = string.replace("\"", "'");
			moviesString.append("\"" + string + "\", ");
		}

		moviesString.replace(moviesString.length() - 2, moviesString.length(),
				"");
		moviesString.append(");");

		String query = "SELECT * FROM " + MOVIE_TABLE_NAME + " WHERE "
				+ COLUMN_NAME + " IN " + moviesString.toString();

		return getMovies(query);
	}

	public List<Movie> getMovieByNameLike(String movieName) {
		String query = "SELECT * FROM " + MOVIE_TABLE_NAME + " WHERE "
				+ COLUMN_NAME + " LIKE '" + movieName + "'";

		return getMovies(query);
	}

	public static void main(String[] args) {
		/*
		 * long time = System.currentTimeMillis(); ManageMovie mm = new
		 * ManageMovie(); List<Movie> moviesList =
		 * mm.getMovieByNameLike("terminator");
		 * 
		 * System.out.println("Time: " + (System.currentTimeMillis() - time) /
		 * 1000.0);
		 * 
		 * for (Movie movie : moviesList) { System.out.println(movie); }
		 */

		MovieManager movieManager = MovieManager.getInstance();

		long time = System.currentTimeMillis();

		List<Movie> moviesList = movieManager.getMovieByNameLike("terminator");
		for (Movie movie : moviesList)
			System.out.println(movie);

		System.out.println("Time: " + (System.currentTimeMillis() - time)
				/ 1000.0);
	}
}
