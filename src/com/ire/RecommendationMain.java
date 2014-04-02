package com.ire;

import java.util.List;

import com.ire.db.Movie;
import com.ire.db.MovieManager;
import com.ire.fb.RecommendationManager;

public class RecommendationMain {
	public static void main(String[] args) {
		System.setProperty("https.proxyHost", "proxy.iiit.ac.in");
		System.setProperty("https.proxyPort", "8080");
		System.setProperty("http.proxyHost", "proxy.iiit.ac.in");
		System.setProperty("http.proxyPort", "8080");

		MovieManager.getInstance();

		long time = System.currentTimeMillis();
		RecommendationManager rManager = new RecommendationManager(
				"CAACEdEose0cBAKer0uMk6OR3gxXkiDPgsApb1BhL4J4T3yEmgZC56557ZBqDDET0rxdZCSVziRZBvG9Es7NSwyXSdkAUelbvOOmlZCN41l0uG8gAf1verZAHlKRMqNswW1vjSvyPUOzEugeiFg3bU2gwMTtKGijNUUofGREyEt5S0DQuw4H11oqJ3fYbmY7QUzOk4DjtzC7AZDZD");

		List<String> movieList = rManager.buildRecommendationsList(25);
		System.out.println("**********************************");
		for (String movieName : movieList) {
			System.out.println(movieName);
		}

		System.out.println((System.currentTimeMillis() - time) / 1000.0);
	}
}
