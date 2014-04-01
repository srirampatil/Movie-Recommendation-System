package com.ire.fb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import com.ire.fb.RestFBExample.FqlFriendID;
import com.restfb.BinaryAttachment;
import com.restfb.Connection;
import com.restfb.DefaultFacebookClient;
import com.restfb.DefaultJsonMapper;
import com.restfb.FacebookClient;
import com.restfb.JsonMapper;
import com.restfb.Parameter;
import com.restfb.batch.BatchRequest;
import com.restfb.batch.BatchRequest.BatchRequestBuilder;
import com.restfb.batch.BatchResponse;
import com.restfb.exception.FacebookNetworkException;
import com.restfb.json.JsonObject;
import com.restfb.types.Page;
import com.restfb.types.User;

public class RecommendationManager {

	/*
	 * Copy and paste the access token from developers.facebook.com graph
	 * explorer It get invalidated after every half hour or so. :D
	 */
	private static final String MY_ACCESS_TOKEN = "CAACEdEose0cBABcVrwGeyMzmTDZCx8IH3R08UEZClF0asYHdtem7eQbzZC6MBzmmuOfZCKbHonNzX2Lhjuv6UwxjONFZBPNpH3Dm7DwhZALsiNKTYopZBymTSBqCi6Yq7U2hYLzRdJw63Fb56Ol3ATvhLA4327HEScZAYGFSIs9dDKosbNwySI44AEDSkSpAr80QucCHSqqFCwZDZD";

	private FacebookClient fbClient = null;
	private User user = null;
	private Map<Long, Movie> myMoviesMap = null;
	private Map<Long, Long> genreToCountMap = null;
	private Map<String, User> friendsMap = null;
	private Set<String> myMovieNamesSet;

	public RecommendationManager() {
		fbClient = new DefaultFacebookClient(
				RecommendationManager.MY_ACCESS_TOKEN);
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

	public List<String> buildRecommendationsList(int limit) {

		List<BatchResponse> responseList = fetchUserRelatedInfo();

		JsonMapper mapper = new DefaultJsonMapper();
		parseUserInfo(responseList.get(0).getBody(), mapper);
		parseFriends(responseList.get(1).getBody(), mapper);
		List<Page> moviePageList = parseMovies(responseList.get(2).getBody(),
				mapper);

		/*
		 * Fetching friends with common movie likes, eliminating duplicates by
		 * using set
		 */
		HashMap<String, Integer> commonPageFriendsMap = new HashMap<String, Integer>();
		for (Page moviePage : moviePageList) {
			/* Fetching friends who have liked this page(movie p) */
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
		int count = 0;
		for (int i = 0; count < limit && i < recoList.size(); i++) {
			if (movieNamesSet.add(recoList.get(i).movie.getMovieName()))
				count++;
		}

		System.out.println("Movie count: " + movieCount.get());

		return new ArrayList<String>(movieNamesSet);
	}

	private synchronized List<Page> parseMovies(String jsonString,
			JsonMapper mapper) {
		/* Fetching user liked movies list */
		List<Page> moviePageList = mapper.toJavaList(jsonString, Page.class);

		List<String> movieNamesList = new ArrayList<String>();
		for (Page page : moviePageList)
			movieNamesList.add(page.getName());

		List<Movie> moviesList = MovieManager.getInstance().getMoviesByNameIn(
				movieNamesList);

		// If there are multiple movies with the same name all will be
		// considered. :(
		Long genreId;
		Long count;
		myMoviesMap = new HashMap<Long, Movie>();
		genreToCountMap = new HashMap<Long, Long>();
		for (Movie movie : moviesList) {
			myMoviesMap.put(movie.getMovieId(), movie);

			// To check the dplicates later
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
	}

	private AtomicInteger movieCount = new AtomicInteger(0);

	private Map<Long, Recommendation> recosMapForFriend(User friend,
			final Map<String, Integer> commonPageFriendsMap) {

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
					Parameter.with("fields", "id,name"));
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
			return recosMapForFriend(friend, commonPageFriendsMap);
		}

		List<String> friendMovieNamesList = new ArrayList<String>();
		for (Page friendMoviePage : friendsMoviesConn.getData()) {
			friendMovieNamesList.add(friendMoviePage.getName());
		}

		List<Movie> friendMoviesList = MovieManager.getInstance()
				.getMoviesByNameIn(friendMovieNamesList);
		if (friendMoviesList == null || friendMoviesList.isEmpty())
			return null;

		ConcurrentHashMap<Long, Recommendation> movieRecommendationMap = new ConcurrentHashMap<Long, RecommendationManager.Recommendation>();

		Integer numberOfCommonLikes = commonPageFriendsMap.get(friend.getId());
		if (numberOfCommonLikes == null)
			numberOfCommonLikes = 0;

		for (Movie movie : friendMoviesList) {
			if (myMovieNamesSet.contains(movie.getMovieName()))
				continue;

			Recommendation reco = new Recommendation();
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
			reco.score += (numberOfCommonLikes * 5L + 10L);

			// Adding weight if the movie belongs to a genre
			// of movies that user likes
			if (movie.genreSet != null) {
				movie.genreSet.retainAll(genreToCountMap.keySet());
				if (!movie.genreSet.isEmpty()) {
					for (Long genreId : movie.genreSet)
						reco.score += (10L * genreToCountMap.get(genreId));
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

		for (final String friendId : friendsMap.keySet()) {

			Thread t = new Thread(new Runnable() {
				final User friend = friendsMap.get(friendId);

				@Override
				public void run() {

					Map<Long, Recommendation> movieRecommendationMap = recosMapForFriend(
							friend, commonPageFriendsMap);

					if (movieRecommendationMap == null
							|| movieRecommendationMap.isEmpty())
						return;

					lock.lock();
					try {
						recommendationsList.add(movieRecommendationMap);

					} finally {
						lock.unlock();
					}
				}
			});
			threadList.add(t);
			t.start();
		}

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
