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
package github.daneren2005.dsub.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import github.daneren2005.dsub.R;
import github.daneren2005.dsub.domain.MusicDirectory;
import github.daneren2005.dsub.domain.PodcastEpisode;
import github.daneren2005.dsub.service.DownloadService;
import github.daneren2005.dsub.service.DownloadFile;
import github.daneren2005.dsub.util.Util;

import java.io.File;

/**
 * Used to display songs in a {@code ListView}.
 *
 * @author Sindre Mehus
 */
public class SongView extends UpdateView implements Checkable {
    private static final String TAG = SongView.class.getSimpleName();

    private MusicDirectory.Entry song;

    private CheckedTextView checkedTextView;
    private TextView titleTextView;
    private TextView artistTextView;
    private TextView durationTextView;
    private TextView statusTextView;
	private ImageView statusImageView;
	private ImageView bookmarkButton;
	private View bottomRowView;
	
	private DownloadService downloadService;
	private long revision = -1;
	private DownloadFile downloadFile;
	private boolean dontChangeDownloadFile = false;

	private boolean playing = false;
	private boolean rightImage = false;
	private int moreImage = 0;
	private boolean isWorkDone = false;
	private boolean isSaved = false;
	private File partialFile;
	private boolean partialFileExists = false;
	private boolean loaded = false;
	private boolean isBookmarked = false;
	private boolean bookmarked = false;

    public SongView(Context context) {
        super(context);
        LayoutInflater.from(context).inflate(R.layout.song_list_item, this, true);

        checkedTextView = (CheckedTextView) findViewById(R.id.song_check);
        titleTextView = (TextView) findViewById(R.id.song_title);
        artistTextView = (TextView) findViewById(R.id.song_artist);
        durationTextView = (TextView) findViewById(R.id.song_duration);
        statusTextView = (TextView) findViewById(R.id.song_status);
		statusImageView = (ImageView) findViewById(R.id.song_status_icon);
		ratingBar = (RatingBar) findViewById(R.id.song_rating);
        starButton = (ImageButton) findViewById(R.id.song_star);
        starButton.setFocusable(false);
		bookmarkButton = (ImageButton) findViewById(R.id.song_bookmark);
		bookmarkButton.setFocusable(false);
		moreButton = (ImageView) findViewById(R.id.artist_more);
		moreButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				v.showContextMenu();
			}
		});
		bottomRowView = findViewById(R.id.song_bottom);
    }

    public void setObjectImpl(Object obj1, Object obj2) {
        this.song = (MusicDirectory.Entry) obj1;
		boolean checkable = (Boolean) obj2;
		
        StringBuilder artist = new StringBuilder(40);

		boolean isPodcast = song instanceof PodcastEpisode;
		if(!song.isVideo() || isPodcast) {
			if(isPodcast) {
				String date = ((PodcastEpisode)song).getDate();
				if(date != null) {
					int index = date.indexOf(" ");
					artist.append(date.substring(0, index != -1 ? index : date.length()));
				}
			}
			else if(song.getArtist() != null) {
				artist.append(song.getArtist());
			}
			
			if(isPodcast) {
				String status = ((PodcastEpisode) song).getStatus();
				int statusRes = -1;
				
				if("error".equals(status)) {
					statusRes = R.string.song_details_error;
				} else if("skipped".equals(status)) {
					statusRes = R.string.song_details_skipped;
				} else if("downloading".equals(status)) {
					statusRes = R.string.song_details_downloading;
				}
				
				if(statusRes != -1) {
					artist.append(" (");
					artist.append(getContext().getString(statusRes));
					artist.append(")");
				}
			}

			durationTextView.setText(Util.formatDuration(song.getDuration()));
			bottomRowView.setVisibility(View.VISIBLE);
		} else {
			bottomRowView.setVisibility(View.GONE);
			statusTextView.setText(Util.formatDuration(song.getDuration()));
		}
		
		String title = song.getTitle();
		Integer track = song.getTrack();
		if(track != null && Util.getDisplayTrack(context)) {
			title = String.format("%02d", track) + " " + title;
		}

        titleTextView.setText(title);
		artistTextView.setText(artist);
        checkedTextView.setVisibility(checkable && !song.isVideo() ? View.VISIBLE : View.GONE);

		this.setBackgroundColor(0x00000000);
		ratingBar.setVisibility(View.GONE);
		rating = 0;

		revision = -1;
		loaded = false;
		dontChangeDownloadFile = false;
	}
    
	public void setDownloadFile(DownloadFile downloadFile) {
		this.downloadFile = downloadFile;
		dontChangeDownloadFile = true;
	}
	
	public DownloadFile getDownloadFile() {
		return downloadFile;
	}
	
	@Override
	protected void updateBackground() {
        if (downloadService == null) {
			downloadService = DownloadService.getInstance();
			if(downloadService == null) {
				return;
			}
        }

		long newRevision = downloadService.getDownloadListUpdateRevision();
		if((revision != newRevision && dontChangeDownloadFile == false) || downloadFile == null) {
			downloadFile = downloadService.forSong(song);
			revision = newRevision;
		}

		isWorkDone = downloadFile.isWorkDone();
		isSaved = downloadFile.isSaved();
		partialFile = downloadFile.getPartialFile();
		partialFileExists = partialFile.exists();
		isStarred = song.isStarred();
		isBookmarked = song.getBookmark() != null;
		isRated = song.getRating();
		
		// Check if needs to load metadata: check against all fields that we know are null in offline mode
		if(song.getBitRate() == null && song.getDuration() == null && song.getDiscNumber() == null && isWorkDone) {
			song.loadMetadata(downloadFile.getCompleteFile());
			loaded = true;
		}
	}

	@Override
    protected void update() {
		if(loaded) {
			setObjectImpl(song, checkedTextView.getVisibility() == View.VISIBLE);
		}
        if (downloadService == null || downloadFile == null) {
            return;
        }

		if(song.isStarred()) {
			if(!starred) {
				starButton.setVisibility(View.VISIBLE);
				starred = true;
			}
		} else {
			if(starred) {
				starButton.setVisibility(View.GONE);
				starred = false;
			}
		}

        if (isWorkDone) {
			int moreImage = isSaved ? R.drawable.download_pinned : R.drawable.download_cached;
			if(moreImage != this.moreImage) {
				moreButton.setImageResource(moreImage);
				this.moreImage = moreImage;
			}
        } else if(this.moreImage != R.drawable.download_none_light) {
			int[] attrs = new int[] {R.attr.download_none};
			TypedArray typedArray = context.obtainStyledAttributes(attrs);
			moreButton.setImageResource(typedArray.getResourceId(0, 0));
			typedArray.recycle();
			this.moreImage = R.drawable.download_none_light;
		}

        if (downloadFile.isDownloading() && !downloadFile.isDownloadCancelled() && partialFileExists) {
			double percentage = (partialFile.length() * 100.0) / downloadFile.getEstimatedSize();
			percentage = Math.min(percentage, 100);
			statusTextView.setText((int)percentage + " %");
			if(!rightImage) {
				statusImageView.setVisibility(View.VISIBLE);
				rightImage = true;
			}
        } else if(rightImage) {
            statusTextView.setText(null);
			statusImageView.setVisibility(View.GONE);
			rightImage = false;
        }

        boolean playing = downloadService.getCurrentPlaying() == downloadFile;
        if (playing) {
			if(!this.playing) {
				this.playing = playing;
				int[] attrs = new int[] {R.attr.media_button_start};
				TypedArray typedArray = context.obtainStyledAttributes(attrs);
            	titleTextView.setCompoundDrawablesWithIntrinsicBounds(typedArray.getResourceId(0, 0), 0, 0, 0);
			}
        } else {
			if(this.playing) {
				this.playing = playing;
            	titleTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
			}
		}

		if(isBookmarked) {
			if(!bookmarked) {
				bookmarkButton.setVisibility(View.VISIBLE);
				bookmarked = true;
			}
		} else {
			if(bookmarked) {
				bookmarkButton.setVisibility(View.GONE);
				bookmarked = false;
			}
		}

		if(isRated != rating) {
			if(isRated > 1) {
				if(rating <= 1) {
					ratingBar.setVisibility(View.VISIBLE);
				}

				ratingBar.setNumStars(isRated);
				ratingBar.setRating(isRated);
			} else if(isRated <= 1) {
				if(rating > 1) {
					ratingBar.setVisibility(View.GONE);
				}
			}

			// Still highlight red if a 1-star
			if(isRated == 1) {
				this.setBackgroundColor(Color.RED);
				this.getBackground().setAlpha(20);
			} else if(rating == 1) {
				this.setBackgroundColor(0x00000000);
			}

			rating = isRated;
		}
    }

    @Override
    public void setChecked(boolean b) {
        checkedTextView.setChecked(b);
    }

    @Override
    public boolean isChecked() {
        return checkedTextView.isChecked();
    }

    @Override
    public void toggle() {
        checkedTextView.toggle();
    }

	public MusicDirectory.Entry getEntry() {
		return song;
	}
}
