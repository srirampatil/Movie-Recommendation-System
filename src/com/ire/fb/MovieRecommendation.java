package com.ire.fb;
import java.util.ArrayList;
import java.util.List;

import com.restfb.types.Page;

public class MovieRecommendation {
	private Page moviePage;
	private List<String> friendNamesList;
	private boolean isMore;

	public MovieRecommendation(Page newPage, String friendName) {
		moviePage = newPage;
		isMore = false;
		friendNamesList = new ArrayList<String>();
		friendNamesList.add(friendName);
	}

	public void addRecommender(String friendName) {
		if (isMore)
			return;

		friendNamesList.add(friendName);
		if (friendNamesList.size() >= 3)
			isMore = true;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(moviePage.getName() + "\t\t\t");
		for (String name : friendNamesList)
			builder.append(name + ", ");

		builder.replace(builder.length() - 2, builder.length() - 1, "");
		if (isMore)
			builder.append(" and more...");

		builder.append("\n");
		return builder.toString();
	}
}