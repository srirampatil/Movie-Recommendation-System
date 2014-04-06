package com.ire;

import java.util.List;

import com.ire.db.MovieManager;
import com.ire.fb.RecommendationManager;
import com.ire.fb.RecommendationManager.FBMovie;
import com.ire.fb.RecommendationManager.FBMovie;

public class RecommendationMain {
	public static void main(String[] args) {
		System.setProperty("https.proxyHost", "proxy.iiit.ac.in");
		System.setProperty("https.proxyPort", "8080");
		System.setProperty("http.proxyHost", "proxy.iiit.ac.in");
		System.setProperty("http.proxyPort", "8080");

		MovieManager.getInstance();

		long time = System.currentTimeMillis();
		RecommendationManager rManager = new RecommendationManager(
				"CAACEdEose0cBAJSNjZCDqksULs2ttH17Te8fEV1ux03TMGZConjCnub4oRtrRoG4zsuD8O8DaFWvv2S8bQ9r6ttDUK2LM2Fx0P94YPApIM8mkLlfRDiXsrvfbC8j7k28maGBwpXFsRGSMvPIyNiZAzUhBdHbvfszmUIBsukEaNJfctB67Mkk8WXH52Vy6cZD");

		List<FBMovie> movieList = rManager.buildRecommendationsList(25);
		System.out.println("**********************************");
		for (FBMovie movie : movieList)
			System.out.println(movie.name + " (" + movie.link + ")");

		System.out.println((System.currentTimeMillis() - time) / 1000.0);
	}
}
