/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package net.sourceforge.subsonic.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.ParameterizableViewController;
import org.springframework.web.servlet.view.RedirectView;

import net.sourceforge.subsonic.domain.CoverArtScheme;
import net.sourceforge.subsonic.domain.Genre;
import net.sourceforge.subsonic.domain.MediaFile;
import net.sourceforge.subsonic.domain.MusicFolder;
import net.sourceforge.subsonic.domain.User;
import net.sourceforge.subsonic.service.MediaFileService;
import net.sourceforge.subsonic.service.MediaScannerService;
import net.sourceforge.subsonic.service.RatingService;
import net.sourceforge.subsonic.service.SearchService;
import net.sourceforge.subsonic.service.SecurityService;
import net.sourceforge.subsonic.service.SettingsService;

import static org.springframework.web.bind.ServletRequestUtils.getIntParameter;
import static org.springframework.web.bind.ServletRequestUtils.getStringParameter;

/**
 * Controller for the home page.
 *
 * @author Sindre Mehus
 */
public class HomeController extends ParameterizableViewController {

    private static final int LIST_SIZE = 40;

    private SettingsService settingsService;
    private MediaScannerService mediaScannerService;
    private RatingService ratingService;
    private SecurityService securityService;
    private MediaFileService mediaFileService;
    private SearchService searchService;

    protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws Exception {

        User user = securityService.getCurrentUser(request);
        if (user.isAdminRole() && settingsService.isGettingStartedEnabled()) {
            return new ModelAndView(new RedirectView("gettingStarted.view"));
        }
        int listOffset = getIntParameter(request, "listOffset", 0);
        String listType = getStringParameter(request, "listType", "recent");

        MusicFolder selectedMusicFolder = settingsService.getSelectedMusicFolder(user.getUsername());
        List<MusicFolder> musicFolders = settingsService.getMusicFoldersForUser(user.getUsername(),
                                                                                selectedMusicFolder == null ? null : selectedMusicFolder.getId());

        Map<String, Object> map = new HashMap<String, Object>();
        List<Album> albums = Collections.emptyList();
        if ("highest".equals(listType)) {
            albums = getHighestRated(listOffset, LIST_SIZE, musicFolders);
        } else if ("frequent".equals(listType)) {
            albums = getMostFrequent(listOffset, LIST_SIZE, musicFolders);
        } else if ("recent".equals(listType)) {
            albums = getMostRecent(listOffset, LIST_SIZE, musicFolders);
        } else if ("newest".equals(listType)) {
            albums = getNewest(listOffset, LIST_SIZE, musicFolders);
        } else if ("starred".equals(listType)) {
            albums = getStarred(listOffset, LIST_SIZE, user.getUsername(), musicFolders);
        } else if ("random".equals(listType)) {
            albums = getRandom(LIST_SIZE, musicFolders);
        } else if ("alphabetical".equals(listType)) {
            albums = getAlphabetical(listOffset, LIST_SIZE, true, musicFolders);
        } else if ("decade".equals(listType)) {
            List<Integer> decades = createDecades();
            map.put("decades", decades);
            int decade = getIntParameter(request, "decade", decades.get(0));
            map.put("decade", decade);
            albums = getByYear(listOffset, LIST_SIZE, decade, decade + 9, musicFolders);
        } else if ("genre".equals(listType)) {
            List<Genre> genres = mediaFileService.getGenres(true);
            map.put("genres", genres);
            if (!genres.isEmpty()) {
                String genre = getStringParameter(request, "genre", genres.get(0).getName());
                map.put("genre", genre);
                albums = getByGenre(listOffset, LIST_SIZE, genre, musicFolders);
            }
        }

        map.put("albums", albums);
        map.put("welcomeTitle", settingsService.getWelcomeTitle());
        map.put("welcomeSubtitle", settingsService.getWelcomeSubtitle());
        map.put("welcomeMessage", settingsService.getWelcomeMessage());
        map.put("isIndexBeingCreated", mediaScannerService.isScanning());
        map.put("musicFoldersExist", !settingsService.getAllMusicFolders().isEmpty());
        map.put("listType", listType);
        map.put("listSize", LIST_SIZE);
        map.put("coverArtSize", CoverArtScheme.MEDIUM.getSize());
        map.put("listOffset", listOffset);
        map.put("musicFolder", selectedMusicFolder);

        ModelAndView result = super.handleRequestInternal(request, response);
        result.addObject("model", map);
        return result;
    }

    private List<Album> getHighestRated(int offset, int count, List<MusicFolder> musicFolders) {
        List<Album> result = new ArrayList<Album>();
        for (MediaFile mediaFile : ratingService.getHighestRatedAlbums(offset, count, musicFolders)) {
            Album album = createAlbum(mediaFile);
            album.setRating((int) Math.round(ratingService.getAverageRating(mediaFile) * 10.0D));
            result.add(album);
        }
        return result;
    }

    private List<Album> getMostFrequent(int offset, int count, List<MusicFolder> musicFolders) {
        List<Album> result = new ArrayList<Album>();
        for (MediaFile mediaFile : mediaFileService.getMostFrequentlyPlayedAlbums(offset, count, musicFolders)) {
            Album album = createAlbum(mediaFile);
            album.setPlayCount(mediaFile.getPlayCount());
            result.add(album);
        }
        return result;
    }

    private List<Album> getMostRecent(int offset, int count, List<MusicFolder> musicFolders) {
        List<Album> result = new ArrayList<Album>();
        for (MediaFile mediaFile : mediaFileService.getMostRecentlyPlayedAlbums(offset, count, musicFolders)) {
            Album album = createAlbum(mediaFile);
            album.setLastPlayed(mediaFile.getLastPlayed());
            result.add(album);
        }
        return result;
    }

    private List<Album> getNewest(int offset, int count, List<MusicFolder> musicFolders) throws IOException {
        List<Album> result = new ArrayList<Album>();
        for (MediaFile file : mediaFileService.getNewestAlbums(offset, count, musicFolders)) {
            Album album = createAlbum(file);
            Date created = file.getCreated();
            if (created == null) {
                created = file.getChanged();
            }
            album.setCreated(created);
            result.add(album);
        }
        return result;
    }

    private List<Album> getStarred(int offset, int count, String username, List<MusicFolder> musicFolders) throws IOException {
        List<Album> result = new ArrayList<Album>();
        for (MediaFile file : mediaFileService.getStarredAlbums(offset, count, username, musicFolders)) {
            result.add(createAlbum(file));
        }
        return result;
    }

    private List<Album> getRandom(int count, List<MusicFolder> musicFolders) throws IOException {
        List<Album> result = new ArrayList<Album>();
        for (MediaFile file : searchService.getRandomAlbums(count, musicFolders)) {
            result.add(createAlbum(file));
        }
        return result;
    }

    private List<Album> getAlphabetical(int offset, int count, boolean byArtist, List<MusicFolder> musicFolders) throws IOException {
        List<Album> result = new ArrayList<Album>();
        for (MediaFile file : mediaFileService.getAlphabeticalAlbums(offset, count, byArtist, musicFolders)) {
            result.add(createAlbum(file));
        }
        return result;
    }

    private List<Album> getByYear(int offset, int count, int fromYear, int toYear, List<MusicFolder> musicFolders) {
        List<Album> result = new ArrayList<Album>();
        for (MediaFile file : mediaFileService.getAlbumsByYear(offset, count, fromYear, toYear, musicFolders)) {
            Album album = createAlbum(file);
            album.setYear(file.getYear());
            result.add(album);
        }
        return result;
    }

    private List<Integer> createDecades() {
        List<Integer> result = new ArrayList<Integer>();
        int decade = Calendar.getInstance().get(Calendar.YEAR) / 10;
        for (int i = 0; i < 10; i++) {
            result.add((decade - i) * 10);
        }
        return result;
    }

    private List<Album> getByGenre(int offset, int count, String genre, List<MusicFolder> musicFolders) {
        List<Album> result = new ArrayList<Album>();
        for (MediaFile file : mediaFileService.getAlbumsByGenre(offset, count, genre, musicFolders)) {
            result.add(createAlbum(file));
        }
        return result;
    }

    private Album createAlbum(MediaFile file) {
        Album album = new Album();
        album.setId(file.getId());
        album.setPath(file.getPath());
        album.setArtist(file.getArtist());
        album.setAlbumTitle(file.getAlbumName());
        album.setCoverArtPath(file.getCoverArtPath());
        return album;
    }

    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void setMediaScannerService(MediaScannerService mediaScannerService) {
        this.mediaScannerService = mediaScannerService;
    }

    public void setRatingService(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    public void setMediaFileService(MediaFileService mediaFileService) {
        this.mediaFileService = mediaFileService;
    }

    public void setSearchService(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Contains info for a single album.
     */
    public static class Album {
        private String path;
        private String coverArtPath;
        private String artist;
        private String albumTitle;
        private Date created;
        private Date lastPlayed;
        private Integer playCount;
        private Integer rating;
        private int id;
        private Integer year;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getCoverArtPath() {
            return coverArtPath;
        }

        public void setCoverArtPath(String coverArtPath) {
            this.coverArtPath = coverArtPath;
        }

        public String getArtist() {
            return artist;
        }

        public void setArtist(String artist) {
            this.artist = artist;
        }

        public String getAlbumTitle() {
            return albumTitle;
        }

        public void setAlbumTitle(String albumTitle) {
            this.albumTitle = albumTitle;
        }

        public Date getCreated() {
            return created;
        }

        public void setCreated(Date created) {
            this.created = created;
        }

        public Date getLastPlayed() {
            return lastPlayed;
        }

        public void setLastPlayed(Date lastPlayed) {
            this.lastPlayed = lastPlayed;
        }

        public Integer getPlayCount() {
            return playCount;
        }

        public void setPlayCount(Integer playCount) {
            this.playCount = playCount;
        }

        public Integer getRating() {
            return rating;
        }

        public void setRating(Integer rating) {
            this.rating = rating;
        }

        public void setYear(Integer year) {
            this.year = year;
        }

        public Integer getYear() {
            return year;
        }
    }
}
