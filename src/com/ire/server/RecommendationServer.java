package com.ire.server;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ire.fb.RecommendationManager;
import com.ire.fb.RecommendationManager.FBMovie;
import com.restfb.DefaultJsonMapper;
import com.restfb.JsonMapper;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;

public class RecommendationServer {
	public static void main(String[] args) {
		System.setProperty("https.proxyHost", "proxy.iiit.ac.in");
		System.setProperty("https.proxyPort", "8080");
		System.setProperty("http.proxyHost", "proxy.iiit.ac.in");
		System.setProperty("http.proxyPort", "8080");

		final Configuration config = new Configuration();
		config.setClassForTemplateLoading(RecommendationServer.class, "/");

		Spark.get(new Route("/:access_token") {

			@Override
			public Object handle(Request request, Response response) {
				String accessToken = request.params(":access_token");

				RecommendationManager rManager = new RecommendationManager(
						accessToken);
				List<FBMovie> movieList = (List<FBMovie>) rManager
						.buildRecommendationsList(25);

				StringWriter writer = new StringWriter();
				try {
					Map<String, Object> moviesMap = new HashMap<String, Object>();
					moviesMap.put("movies", movieList);
					Template template = config.getTemplate("movies.ftl");
					template.process(moviesMap, writer);

				} catch (IOException e) {
					e.printStackTrace();
				} catch (TemplateException e) {
					e.printStackTrace();
				}

				return writer;
			}
		});
	}
}
