package com.ire.db;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;


public class Movie {
	@Getter @Setter private Long movieId;
	@Getter private String movieName;
	@Getter @Setter private Integer year;
	
	@Getter @Setter private String fbPageId;
	
	@Getter private String genreIdsStr;
	@Getter private String languageIdsStr;
	@Getter private String actorIdsStr;
	@Getter private String directorIdsStr;
	
	public Set<Long> genreSet;
	private Set<Long> languageSet;
	private Set<Long> actorSet;
	private Set<Long> directorSet;

	public void setMovieName(String name) {
		movieName = name.trim();
	}
	
	public void setGenreIdsStr(String genreIds) {
		this.genreIdsStr = genreIds;
		this.genreSet = SPUtils.split(this.genreIdsStr, SPConstants.DELIM); 
	}

	public void setLanguageIdsStr(String languageIds) {
		this.languageIdsStr = languageIds;
		this.languageSet = SPUtils.split(this.languageIdsStr, SPConstants.DELIM);
	}

	public void setActorIdsStr(String actorIds) {
		this.actorIdsStr = actorIds;
		this.actorSet = SPUtils.split(this.actorIdsStr, SPConstants.DELIM);
	}

	public void setDirectorIdsStr(String directorIds) {
		this.directorIdsStr = directorIds;
		this.directorSet = SPUtils.split(this.directorIdsStr, SPConstants.DELIM);
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(movieName);
		//builder.append(" (" + year + ")");
		return builder.toString();
	}
}
