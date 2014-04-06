package com.ire.fb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.ire.db.Movie;
import com.ire.db.MovieManager;
import com.restfb.Connection;
import com.restfb.DefaultFacebookClient;
import com.restfb.DefaultJsonMapper;
import com.restfb.Facebook;
import com.restfb.FacebookClient;
import com.restfb.JsonMapper;
import com.restfb.Parameter;
import com.restfb.batch.BatchRequest;
import com.restfb.batch.BatchRequest.BatchRequestBuilder;
import com.restfb.batch.BatchResponse;
import com.restfb.exception.FacebookNetworkException;
import com.restfb.types.Page;
import com.restfb.types.User;

public class RecommendationManager {

	/*
	 * Copy and paste the access token from developers.facebook.com graph
	 * explorer It get invalidated after every half hour or so. :D
	 */
	private String MY_ACCESS_TOKEN;

	private FacebookClient fbClient = null;
	private User user = null;
	private Map<Long, Movie> myMoviesMap = null;
	private Map<Long, Long> genreToCountMap = null;
	private Map<String, User> friendsMap = null;
	private Set<String> myMovieNamesSet;

	public static class FqlUserID {
		@Facebook
		public String uid2;
	}

	public static class FqlFriendID {
		@Facebook
		public String uid;
	}

	public static class FBMovie {
		public String name;
		public String link;

		public FBMovie(String _name, String _link) {
			name = _name;
			link = _link;
		}
		
		@Override
		public String toString() {
			StringBuilder htmlBuilder = new StringBuilder("<A href=\"");
			htmlBuilder.append(link + "\">" + name + "</A>");
			return htmlBuilder.toString();
		}
	}

	public RecommendationManager(String token) {
		MY_ACCESS_TOKEN = token;

		fbClient = new DefaultFacebookClient(
				MY_ACCESS_TOKEN);
		myMovieNamesSet = new HashSet<String>();
	}

	private void parseUserInfo(String jsonString, JsonMapper mapper) {
		user = mapper.toJavaObject(jsonString, User.class);
	}

	private List<BatchResponse> fetchUserRelatedInfo() {
		BatchRequest meRequest = new BatchRequestBuilder("me").build();
		BatchRequest friendsRequest = new BatchRequestBuilder("me/friends")
				.parameters(Parameter.with("fields", "name,gender,birthday"))
				.build();
		BatchRequest moviesRequest = new BatchRequestBuilder("me/movies")
				.build();

		return fbClient.executeBatch(meRequest, friendsRequest, moviesRequest);
	}

	public List<FBMovie> buildRecommendationsList(int limit) {

		List<BatchResponse> responseList = fetchUserRelatedInfo();

		JsonMapper mapper = new DefaultJsonMapper();
		parseUserInfo(responseList.get(0).getBody(), mapper);
		parseFriends(responseList.get(1).getBody(), mapper);
		final List<Page> moviePageList = parseMovies(responseList.get(2)
				.getBody(), mapper);

		/*
		 * Fetching friends with common movie likes, eliminating duplicates by
		 * using set
		 */

		final HashMap<String, Integer> commonPageFriendsMap = new LinkedHashMap<String, Integer>();
		final List<Thread> threadList = new ArrayList<Thread>();

		for (int i = 0; i < 5; i++) {
			final int dupi = i;

			Thread t = new Thread(new Runnable() {
				private int id = dupi;

				@Override
				public void run() {
					for (int j = id; j < moviePageList.size(); j += 5) {
						Page moviePage = moviePageList.get(j);

						String query = "SELECT uid FROM page_fan WHERE page_id = "
								+ moviePage.getId()
								+ " AND uid IN (SELECT uid2 FROM friend WHERE uid1=me())";
						java.util.List<FqlFriendID> commonPageFriendList = fbClient
								.executeFqlQuery(query, FqlFriendID.class);

						System.out.println(moviePage.getName());

						for (final FqlFriendID fUser : commonPageFriendList) {
							Integer count = commonPageFriendsMap.get(fUser.uid);
							if (count == null)
								count = 0;
							commonPageFriendsMap.put(fUser.uid, count + 1);
						}
					}
				}
			});

			threadList.add(t);
			t.start();
		}

		for (Thread thread : threadList) {
			try {
				thread.join();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// for (Page moviePage : moviePageList) {
		// /* Fetching friends who have liked this page(movie p) */
		// String query = "SELECT uid FROM page_fan WHERE page_id = "
		// + moviePage.getId()
		// + " AND uid IN (SELECT uid2 FROM friend WHERE uid1=me())";
		// java.util.List<FqlFriendID> commonPageFriendList = fbClient
		// .executeFqlQuery(query, FqlFriendID.class);
		//
		// System.out.println(moviePage.getName());
		//
		// for (final FqlFriendID fUser : commonPageFriendList) {
		// Integer count = commonPageFriendsMap.get(fUser.uid);
		// if (count == null)
		// count = 0;
		// commonPageFriendsMap.put(fUser.uid, count + 1);
		// }
		// }

		// If there are not common likes among friends then consider all the
		// friends.
		if (commonPageFriendsMap.isEmpty()) {
			for (String key : friendsMap.keySet())
				commonPageFriendsMap.put(key, 1);
		}

		Map<Long, Recommendation> movieRecoMap = recommendFromFriends(commonPageFriendsMap);
		List<Recommendation> recoList = new ArrayList<RecommendationManager.Recommendation>(
				movieRecoMap.values());
		Collections.sort(recoList, new Comparator<Recommendation>() {

			@Override
			public int compare(Recommendation o1, Recommendation o2) {
				return (int) (o2.score - o1.score);
			}

		});

		Set<String> movieNamesSet = new LinkedHashSet<String>();
		List<FBMovie> fbMoviesList = new ArrayList<RecommendationManager.FBMovie>();
		int count = 0;
		for (int i = 0; count < limit && i < recoList.size(); i++) {
			if (movieNamesSet.add(recoList.get(i).movie.getMovieName())) {
				fbMoviesList.add(new FBMovie(recoList.get(i).movie
						.getMovieName(), recoList.get(i).fbUrl));
				count++;
			}
		}

		System.out.println("Movie count: " + movieCount.get());

		return fbMoviesList;
	}

	private synchronized List<Page> parseMovies(String jsonString,
			JsonMapper mapper) {
		/* Fetching user liked movies list */
		List<Page> moviePageList = new ArrayList<Page>(mapper.toJavaList(
				jsonString, Page.class));

		/* Used set here so that duplicate names will not appear */
		Set<String> movieNamesSet = new HashSet<String>();
		Iterator<Page> pageIt = moviePageList.iterator();
		while (pageIt.hasNext()) {
			Page page = pageIt.next();
			if (!movieNamesSet.add(page.getName()))
				pageIt.remove();
		}

		List<Movie> movieObjectsList = MovieManager.getInstance()
				.getMoviesByNameIn(new ArrayList(movieNamesSet));

		// If there are multiple movies with the same name all will be
		// considered. :(
		Long genreId;
		Long count;
		myMoviesMap = new HashMap<Long, Movie>();
		genreToCountMap = new HashMap<Long, Long>();
		for (Movie movie : movieObjectsList) {
			myMoviesMap.put(movie.getMovieId(), movie);

			// To check the duplicates later
			myMovieNamesSet.add(movie.getMovieName());

			String[] splits = movie.getGenreIdsStr().split(" ");
			for (String genreStr : splits) {
				if (genreStr != null && genreStr.length() > 0) {
					genreId = Long.parseLong(genreStr);
					count = genreToCountMap.get(genreId);

					if (count == null)
						genreToCountMap.put(genreId, 1L);
					else
						genreToCountMap.put(genreId, count + 1);
				}
			}
		}

		return moviePageList;
	}

	private class Recommendation {
		public Movie movie;
		public long score = 0L;
		public String fbUrl;
	}

	private AtomicInteger movieCount = new AtomicInteger(0);

	private Map<Long, Recommendation> recosMapForFriend(User friend) {

		String friendGender = friend.getGender();
		String userGender = user.getGender();

		boolean hasGender = (friendGender != null) && (userGender != null);

		/*
		 * Fetching movie list of friends with whom I have common movies
		 */
		Connection<Page> friendsMoviesConn = null;

		try {
			friendsMoviesConn = fbClient.fetchConnection(friend.getId()
					+ "/movies", Page.class,
					Parameter.with("fields", "id,name,link"));
		} catch (FacebookNetworkException e) {
			/*
			 * The proxy server returns 403 forbidden error if this is removed.
			 * I guess it reaches the maximum limit for number of requests
			 * within a given time period. So this sleep for random number of
			 * milli-seconds works like a charm :)
			 */
			try {
				Random random = new Random();
				Thread.sleep(random.nextInt(500) + 300);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			return recosMapForFriend(friend);
		}

		Map<String, String> urlMap = new HashMap<String, String>();
		List<String> friendMovieNamesList = new ArrayList<String>();
		for (Page friendMoviePage : friendsMoviesConn.getData()) {
			friendMovieNamesList.add(friendMoviePage.getName());
			urlMap.put(friendMoviePage.getName(), friendMoviePage.getLink());
		}

		List<Movie> friendMoviesList = MovieManager.getInstance()
				.getMoviesByNameIn(friendMovieNamesList);
		if (friendMoviesList == null || friendMoviesList.isEmpty())
			return null;

		ConcurrentHashMap<Long, Recommendation> movieRecommendationMap = new ConcurrentHashMap<Long, RecommendationManager.Recommendation>();

		/*
		 * Integer numberOfCommonLikes =
		 * commonPageFriendsMap.get(friend.getId()); if (numberOfCommonLikes ==
		 * null) numberOfCommonLikes = 0;
		 */

		for (Movie movie : friendMoviesList) {
			if (myMovieNamesSet.contains(movie.getMovieName()))
				continue;

			Recommendation reco = new Recommendation();
			reco.fbUrl = urlMap.get(movie.getMovieName());
			reco.movie = movie;

			Recommendation oldRecommendation = movieRecommendationMap
					.putIfAbsent(movie.getMovieId(), reco);

			if (oldRecommendation != null)
				reco = oldRecommendation;
			else
				movieCount.getAndIncrement();

			// If this friend and user has a few number of common
			// likes
			// give it some more weight
			/* reco.score += (numberOfCommonLikes * 5L); */

			// Adding weight if the movie belongs to a genre
			// of movies that user likes
			if (movie.genreSet != null) {
				movie.genreSet.retainAll(genreToCountMap.keySet());
				if (!movie.genreSet.isEmpty()) {
					for (Long genreId : movie.genreSet)
						reco.score += (10L * genreToCountMap.get(genreId));
					// reco.score += (5L * movie.genreSet.size());
				}
			}

			// Considering friends gender too! I think it matters
			// for movies
			if (hasGender && friendGender.equals(userGender))
				reco.score += 5L;

			if (reco != null)
				movieRecommendationMap.put(movie.getMovieId(), reco);

		}

		return movieRecommendationMap;
	}

	private Map<Long, Recommendation> recommendFromFriends(
			final Map<String, Integer> commonPageFriendsMap) {
		/* Fetching movies liked by friends */
		// final Hashtable<Long, Recommendation> movieRecommendationMap = new
		// Hashtable<Long, Recommendation>();

		final List<Map<Long, Recommendation>> recommendationsList = new ArrayList<Map<Long, Recommendation>>();

		List<Thread> threadList = new ArrayList<Thread>();

		final Lock lock = new ReentrantLock();

		for (int i = 0; i < 5; i++) {
			final int dupi = i;

			Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					String[] friends = commonPageFriendsMap.keySet().toArray(
							new String[commonPageFriendsMap.size()]);

					for (int j = dupi; j < friends.length; j += 5) {
						final User friend = friendsMap.get(friends[j]);
						Map<Long, Recommendation> movieRecommendationMap = recosMapForFriend(friend);

						if (movieRecommendationMap == null
								|| movieRecommendationMap.isEmpty())
							continue;

						lock.lock();
						try {
							recommendationsList.add(movieRecommendationMap);

						} finally {
							lock.unlock();
						}
					}
				}
			});
			threadList.add(t);
			t.start();
		}

		/*
		 * for (final String friendId : commonPageFriendsMap.keySet()) {
		 * 
		 * Thread t = new Thread(new Runnable() { final User friend =
		 * friendsMap.get(friendId);
		 * 
		 * @Override public void run() {
		 * 
		 * Map<Long, Recommendation> movieRecommendationMap = recosMapForFriend(
		 * friend, commonPageFriendsMap);
		 * 
		 * if (movieRecommendationMap == null ||
		 * movieRecommendationMap.isEmpty()) return;
		 * 
		 * System.out.println("Alive"); lock.lock(); try {
		 * recommendationsList.add(movieRecommendationMap);
		 * 
		 * } finally { lock.unlock(); } } }); threadList.add(t); t.start(); }
		 */

		for (Thread t : threadList) {
			try {
				t.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		Map<Long, Recommendation> finalRecommendationMap = new HashMap<Long, RecommendationManager.Recommendation>();
		for (Map<Long, Recommendation> recoMap : recommendationsList) {
			for (Long key : recoMap.keySet()) {
				Recommendation newThreadReco = recoMap.get(key);

				Recommendation reco = finalRecommendationMap
						.get(newThreadReco.movie.getMovieId());

				if (reco != null)
					newThreadReco.score += reco.score;

				finalRecommendationMap.put(key, newThreadReco);
			}
		}
		return finalRecommendationMap;
	}

	private synchronized void parseFriends(String jsonString, JsonMapper mapper) {
		if (friendsMap != null)
			return;

		List<User> friends = mapper.toJavaList(jsonString, User.class);

		friendsMap = new HashMap<String, User>();
		for (User friend : friends)
			friendsMap.put(friend.getId(), friend);

		friends = null;
	}

	public List<Movie> getRecommendedMovies(int limit) {

		return null;
	}
}
