package com.ire.fb;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.restfb.Connection;
import com.restfb.DefaultFacebookClient;
import com.restfb.Facebook;
import com.restfb.FacebookClient;
import com.restfb.Parameter;
import com.restfb.exception.FacebookNetworkException;
import com.restfb.types.Page;
import com.restfb.types.User;

public class RestFBExample {
	/*
	 * Copy your access token from graph api trial here. It will be valid for a
	 * while and then u have to regenerate again
	 */
	public static final String MY_ACCESS_TOKEN = "CAACEdEose0cBAKGaPWoDHkZAOqQlyteVFFqq3eWZCeMomWMkBA3bVnPnfW5gZAF5YS1KWbm3ePCq669bZCYZCZAqhUougstdvZCg3xZCTiE8EYR42F7ZA6Aa6TVj08ZAIu1xxSlJFGzd1TuTUA0hxVKYqencZClKZB3ZBt7YaiMXL1mJHMkEm5paLdea8eanb4gEKOUoZD";

	public static final String MY_APP_ID = "546304538782137";
	public static final String MY_APP_SECRET = "886e09ec9d680c328fe97df1836c83ff";

	public static class FqlUserID {
		@Facebook
		public String uid2;
	}

	public static class FqlFriendID {
		@Facebook
		public String uid;
	}


	public static void main(String[] args) {
		// final FacebookClient fbClient = new DefaultFacebookClient();
		// FacebookClient.AccessToken accessToken =
		// fbClient.obtainAppAccessToken(MY_APP_ID, MY_APP_SECRET);

		System.setProperty("https.proxyHost", "proxy.iiit.ac.in");
		System.setProperty("https.proxyPort", "8080");
		System.setProperty("http.proxyHost", "proxy.iiit.ac.in");
		System.setProperty("http.proxyPort", "8080");

		final FacebookClient fbClient = new DefaultFacebookClient(
				RestFBExample.MY_ACCESS_TOKEN);

		/* Fetching user information */
		User user = fbClient.fetchObject("me", User.class);

		/* Fetching user friends list */
		Connection<User> friends = fbClient.fetchConnection("me/friends",
				User.class, Parameter.with("fields", "id,name"));
		final Map<String, User> friendsMap = new HashMap<String, User>();
		for (User friend : friends.getData())
			friendsMap.put(friend.getId(), friend);

		friends = null;

		/* Fetching user liked movies list */
		Connection<Page> movieList = fbClient.fetchConnection("me/movies",
				Page.class);
		Map<String, Page> myMoviesMap = new HashMap<String, Page>();
		for (Page moviePage : movieList.getData())
			myMoviesMap.put(moviePage.getId(), moviePage);

		movieList = null;
		
		/*
		 * Fetching friends with common movie likes, eliminating duplicates by
		 * using set
		 */
		Set<String> commonPageFriendsSet = new HashSet<String>();
		for (Page moviePage : myMoviesMap.values()) {
			/* Fetching friends who have liked this page(movie p) */
			String query = "SELECT uid FROM page_fan WHERE page_id = "
					+ moviePage.getId()
					+ " AND uid IN (SELECT uid2 FROM friend WHERE uid1=me())";
			java.util.List<FqlFriendID> commonPageFriendList = fbClient
					.executeFqlQuery(query, FqlFriendID.class);

			for (final FqlFriendID fUser : commonPageFriendList)
				commonPageFriendsSet.add(fUser.uid);
		}

		/* Fetching movies liked by friends */
		final Map<String, MovieRecommendation> movieRecommendationMap = new HashMap<String, MovieRecommendation>();
		List<Thread> threadList = new ArrayList<Thread>();
		for (final String id : commonPageFriendsSet) {
			Thread t = new Thread(new Runnable() {
				final String friendName = friendsMap.get(id).getName();

				@Override
				public void run() {
					/*
					 * Fetching movie list of friends with whom I have common
					 * movies
					 */
					Connection<Page> friendsMoviesConn = null;

					try {
						friendsMoviesConn = fbClient.fetchConnection(id
								+ "/movies", Page.class,
								Parameter.with("fields", "id,name"));
					} catch (FacebookNetworkException e) {
						//TODO: this exception occurs for many users
//						System.out.println(friendsMap.get(id).getName());
//						e.printStackTrace();
						return;
					}
					
					for (Page friendMoviePage : friendsMoviesConn.getData()) {
						MovieRecommendation reco = movieRecommendationMap
								.get(friendMoviePage.getId());
						if (reco == null)
							reco = new MovieRecommendation(friendMoviePage,
									friendName);
						else
							reco.addRecommender(friendName);

						movieRecommendationMap.put(friendMoviePage.getId(),
								reco);
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

		System.out.println("***********************************************");
		System.out.println("\nMovie recommendations for you :)\n");
		for (MovieRecommendation reco : movieRecommendationMap.values()) {
			System.out.print(reco);
		}
	}
}
