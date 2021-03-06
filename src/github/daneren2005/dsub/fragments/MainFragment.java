package github.daneren2005.dsub.fragments;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StatFs;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;
import github.daneren2005.dsub.R;
import github.daneren2005.dsub.domain.MusicDirectory;
import github.daneren2005.dsub.domain.ServerInfo;
import github.daneren2005.dsub.service.DownloadService;
import github.daneren2005.dsub.util.Constants;
import github.daneren2005.dsub.util.FileUtil;
import github.daneren2005.dsub.util.LoadingTask;
import github.daneren2005.dsub.util.Pair;
import github.daneren2005.dsub.util.UserUtil;
import github.daneren2005.dsub.view.MergeAdapter;
import github.daneren2005.dsub.util.Util;
import github.daneren2005.dsub.service.MusicService;
import github.daneren2005.dsub.service.MusicServiceFactory;
import github.daneren2005.dsub.util.SilentBackgroundTask;
import github.daneren2005.dsub.view.ChangeLog;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainFragment extends SubsonicFragment {
	private static final String TAG = MainFragment.class.getSimpleName();
	private LayoutInflater inflater;
	private TextView countView;

	private static final int MENU_GROUP_SERVER = 10;
	private static final int MENU_ITEM_SERVER_BASE = 100;

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
		this.inflater = inflater;
		rootView = inflater.inflate(R.layout.home, container, false);

		createLayout();

		return rootView;
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
		menuInflater.inflate(R.menu.main, menu);

		if(!ServerInfo.isMadsonic(context) || !UserUtil.isCurrentAdmin()) {
			menu.setGroupVisible(R.id.madsonic, false);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(super.onOptionsItemSelected(item)) {
			return true;
		}

		switch (item.getItemId()) {
			case R.id.menu_log:
				getLogs();
				return true;
			case R.id.menu_about:
				showAboutDialog();
				return true;
			case R.id.menu_changelog:
				ChangeLog changeLog = new ChangeLog(context, Util.getPreferences(context));
				changeLog.getFullLogDialog().show();
				return true;
			case R.id.menu_faq:
				showFAQDialog();
				return true;
			case R.id.menu_rescan:
				rescanServer();
				return true;
		}

		return false;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, view, menuInfo);
		
		int serverCount = Util.getServerCount(context);
		int activeServer = Util.getActiveServer(context);
		for(int i = 1; i <= serverCount; i++) {
			android.view.MenuItem menuItem = menu.add(MENU_GROUP_SERVER, MENU_ITEM_SERVER_BASE + i, MENU_ITEM_SERVER_BASE + i, Util.getServerName(context, i));
			if(i == activeServer) {
				menuItem.setChecked(true);
			}
		}
		menu.setGroupCheckable(MENU_GROUP_SERVER, true, true);
		menu.setHeaderTitle(R.string.main_select_server);

		recreateContextMenu(menu);
	}

	@Override
	public boolean onContextItemSelected(android.view.MenuItem menuItem) {
		if(menuItem.getGroupId() != getSupportTag()) {
			return false;
		}
		
		int activeServer = menuItem.getItemId() - MENU_ITEM_SERVER_BASE;
		setActiveServer(activeServer);
		return true;
	}

	@Override
	protected void refresh(boolean refresh) {
		createLayout();
	}

	private void createLayout() {
		View buttons = inflater.inflate(R.layout.main_buttons, null);

		final View serverButton = buttons.findViewById(R.id.main_select_server);
		final TextView serverTextView = (TextView) serverButton.findViewById(R.id.main_select_server_2);
		final TextView offlineButton = (TextView) buttons.findViewById(R.id.main_offline);
		offlineButton.setText(Util.isOffline(context) ? R.string.main_online : R.string.main_offline);

		final View albumsTitle = buttons.findViewById(R.id.main_albums);
		final View albumsNewestButton = buttons.findViewById(R.id.main_albums_newest);
		countView = (TextView) buttons.findViewById(R.id.main_albums_recent_count);
		final View albumsRandomButton = buttons.findViewById(R.id.main_albums_random);
		final View albumsHighestButton = buttons.findViewById(R.id.main_albums_highest);
		final View albumsRecentButton = buttons.findViewById(R.id.main_albums_recent);
		final View albumsFrequentButton = buttons.findViewById(R.id.main_albums_frequent);
		final View albumsStarredButton = buttons.findViewById(R.id.main_albums_starred);
		final View albumsGenresButton = buttons.findViewById(R.id.main_albums_genres);
		final View albumsYearButton = buttons.findViewById(R.id.main_albums_year);

		final View dummyView = rootView.findViewById(R.id.main_dummy);

		final CheckBox albumsPerFolderCheckbox = (CheckBox) buttons.findViewById(R.id.main_albums_per_folder);
		if(!Util.isOffline(context) && ServerInfo.checkServerVersion(context, "1.11")) {
			albumsPerFolderCheckbox.setChecked(Util.getAlbumListsPerFolder(context));
			albumsPerFolderCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					Util.setAlbumListsPerFolder(context, isChecked);
				}
			});
		} else {
			albumsPerFolderCheckbox.setVisibility(View.GONE);
		}

		int instance = Util.getActiveServer(context);
		String name = Util.getServerName(context, instance);
		serverTextView.setText(name);

		ListView list = (ListView) rootView.findViewById(R.id.main_list);

		MergeAdapter adapter = new MergeAdapter();
		if (!Util.isOffline(context)) {
			adapter.addViews(Arrays.asList(serverButton), true);
		}
		adapter.addView(offlineButton, true);
		if (!Util.isOffline(context)) {
			adapter.addView(albumsTitle, false);
			adapter.addViews(Arrays.asList(albumsNewestButton, albumsRandomButton), true);
			if(!Util.isTagBrowsing(context)) {
				adapter.addView(albumsHighestButton, true);
			}
			adapter.addViews(Arrays.asList(albumsStarredButton, albumsGenresButton, albumsYearButton, albumsRecentButton, albumsFrequentButton), true);
		}
		list.setAdapter(adapter);
		registerForContextMenu(dummyView);

		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (view == serverButton) {
					dummyView.showContextMenu();
				} else if (view == offlineButton) {
					toggleOffline();
				} else if (view == albumsNewestButton) {
					showAlbumList("newest");
				} else if (view == albumsRandomButton) {
					showAlbumList("random");
				} else if (view == albumsHighestButton) {
					showAlbumList("highest");
				} else if (view == albumsRecentButton) {
					showAlbumList("recent");
				} else if (view == albumsFrequentButton) {
					showAlbumList("frequent");
				} else if (view == albumsStarredButton) {
					showAlbumList("starred");
				} else if(view == albumsGenresButton) {
					showAlbumList("genres");
				} else if(view == albumsYearButton) {
					showAlbumList("years");
				}
			}
		});
		setTitle(R.string.common_appname);

		if(!Util.isOffline(context)) {
			getMostRecentCount();
		}
	}

	private void setActiveServer(int instance) {
		if (Util.getActiveServer(context) != instance) {
			DownloadService service = getDownloadService();
			if (service != null) {
				service.clearIncomplete();
			}
			Util.setActiveServer(context, instance);
			context.invalidate();
			UserUtil.refreshCurrentUser(context, false, true);
		}
	}

	private void toggleOffline() {
		boolean isOffline = Util.isOffline(context);
		Util.setOffline(context, !isOffline);
		context.invalidate();
		DownloadService service = getDownloadService();
		if (service != null) {
			service.setOnline(isOffline);
		}

		// Coming back online
		if(isOffline) {
			int scrobblesCount = Util.offlineScrobblesCount(context);
			int starsCount = Util.offlineStarsCount(context);
			if(scrobblesCount > 0 || starsCount > 0){
				showOfflineSyncDialog(scrobblesCount, starsCount);
			}
		}
		
		UserUtil.seedCurrentUser(context);
	}

	private void showAlbumList(String type) {
		if("genres".equals(type)) {
			SubsonicFragment fragment = new SelectGenreFragment();
			replaceFragment(fragment);
		} else if("years".equals(type)) {
			SubsonicFragment fragment = new SelectYearFragment();
			replaceFragment(fragment);
		} else {
			// Clear out recently added count when viewing
			if("newest".equals(type)) {
				SharedPreferences.Editor editor = Util.getPreferences(context).edit();
				editor.putInt(Constants.PREFERENCES_KEY_RECENT_COUNT + Util.getActiveServer(context), 0);
				editor.commit();
				
				// Clear immediately so doesn't still show when pressing back
				setMostRecentCount(0);
			}
			
			SubsonicFragment fragment = new SelectDirectoryFragment();
			Bundle args = new Bundle();
			args.putString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE, type);
			args.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 20);
			args.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0);
			fragment.setArguments(args);

			replaceFragment(fragment);
		}
	}
	
	private void showOfflineSyncDialog(final int scrobbleCount, final int starsCount) {
		String syncDefault = Util.getSyncDefault(context);
		if(syncDefault != null) {
			if("sync".equals(syncDefault)) {
				syncOffline(scrobbleCount, starsCount);
				return;
			} else if("delete".equals(syncDefault)) {
				deleteOffline();
				return;
			}
		}
		
		View checkBoxView = context.getLayoutInflater().inflate(R.layout.sync_dialog, null);
		final CheckBox checkBox = (CheckBox)checkBoxView.findViewById(R.id.sync_default);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setIcon(android.R.drawable.ic_dialog_info)
			.setTitle(R.string.offline_sync_dialog_title)
			.setMessage(context.getResources().getString(R.string.offline_sync_dialog_message, scrobbleCount, starsCount))
			.setView(checkBoxView)
			.setPositiveButton(R.string.common_ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialogInterface, int i) {
					if(checkBox.isChecked()) {
						Util.setSyncDefault(context, "sync");
					}
					syncOffline(scrobbleCount, starsCount);
				}
			}).setNeutralButton(R.string.common_cancel, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialogInterface, int i) {
					dialogInterface.dismiss();
				}
			}).setNegativeButton(R.string.common_delete, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialogInterface, int i) {
					if(checkBox.isChecked()) {
						Util.setSyncDefault(context, "delete");
					}
					deleteOffline();
				}
			});

		builder.create().show();
	}
	
	private void syncOffline(final int scrobbleCount, final int starsCount) {
		new SilentBackgroundTask<Integer>(context) {
			@Override
			protected Integer doInBackground() throws Throwable {
				MusicService musicService = MusicServiceFactory.getMusicService(context);
				return musicService.processOfflineSyncs(context, null);
			}

			@Override
			protected void done(Integer result) {
				if(result == scrobbleCount) {
					Util.toast(context, context.getResources().getString(R.string.offline_sync_success, result));
				} else {
					Util.toast(context, context.getResources().getString(R.string.offline_sync_partial, result, scrobbleCount + starsCount));
				}
			}

			@Override
			protected void error(Throwable error) {
				Log.w(TAG, "Failed to sync offline stats", error);
				String msg = context.getResources().getString(R.string.offline_sync_error) + " " + getErrorMessage(error);
				Util.toast(context, msg);
			}
		}.execute();
	}
	private void deleteOffline() {
		SharedPreferences.Editor offline = Util.getOfflineSync(context).edit();
		offline.putInt(Constants.OFFLINE_SCROBBLE_COUNT, 0);
		offline.putInt(Constants.OFFLINE_STAR_COUNT, 0);
		offline.commit();
	}

	private void showAboutDialog() {
		new LoadingTask<String>(context) {
			@Override
			protected String doInBackground() throws Throwable {
				File rootFolder = FileUtil.getMusicDirectory(context);
				StatFs stat = new StatFs(rootFolder.getPath());
				long bytesTotalFs = (long) stat.getBlockCount() * (long) stat.getBlockSize();
				long bytesAvailableFs = (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();

				Pair<Long, Long> used = FileUtil.getUsedSize(context, rootFolder);

				return getResources().getString(R.string.main_about_text,
					context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName,
					used.getFirst(),
					Util.formatLocalizedBytes(used.getSecond(), context),
					Util.formatLocalizedBytes(Util.getCacheSizeMB(context) * 1024L * 1024L, context),
					Util.formatLocalizedBytes(bytesAvailableFs, context),
					Util.formatLocalizedBytes(bytesTotalFs, context));
			}

			@Override
			protected void done(String msg) {
				try {
					Util.info(context, R.string.main_about_title, msg);
				} catch(Exception e) {
					Util.toast(context, "Failed to open dialog");
				}
			}
		}.execute();
	}

	private void showFAQDialog() {
		Util.showHTMLDialog(context, R.string.main_faq_title, R.string.main_faq_text);
	}

	private void rescanServer() {
		new LoadingTask<Void>(context, false) {
			@Override
			protected Void doInBackground() throws Throwable {
				MusicService musicService = MusicServiceFactory.getMusicService(context);
				musicService.startRescan(context, this);
				return null;
			}

			@Override
			protected void done(Void value) {
				Util.toast(context, R.string.main_scan_complete);
			}
		}.execute();
	}

	private void getLogs() {
		try {
			final String version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
			new LoadingTask<File>(context) {
				@Override
				protected File doInBackground() throws Throwable {
					updateProgress("Gathering Logs");
					File logcat = new File(FileUtil.getSubsonicDirectory(context), "logcat.txt");
					Util.delete(logcat);
					Process logcatProc = null;

					try {
						List<String> progs = new ArrayList<String>();
						progs.add("logcat");
						progs.add("-v");
						progs.add("time");
						progs.add("-d");
						progs.add("-f");
						progs.add(logcat.getCanonicalPath());
						progs.add("*:I");

						logcatProc = Runtime.getRuntime().exec(progs.toArray(new String[progs.size()]));
						logcatProc.waitFor();
					} catch(Exception e) {
						Util.toast(context, "Failed to gather logs");
					} finally {
						if(logcatProc != null) {
							logcatProc.destroy();
						}
					}

					return logcat;
				}

				@Override
				protected void done(File logcat) {
					String footer = "Android SDK: " + Build.VERSION.SDK;
					footer += "\nDevice Model: " + Build.MODEL;
					footer += "\nDevice Name: " + Build.MANUFACTURER + " "  + Build.PRODUCT;
					footer += "\nROM: " + Build.DISPLAY;

					Intent email = new Intent(Intent.ACTION_SENDTO,
						Uri.fromParts("mailto", "dsub.android@gmail.com", null));
					email.putExtra(Intent.EXTRA_SUBJECT, "DSub " + version + " Error Logs");
					email.putExtra(Intent.EXTRA_TEXT, "Describe the problem here\n\n\n" + footer);
					Uri attachment = Uri.fromFile(logcat);
					email.putExtra(Intent.EXTRA_STREAM, attachment);
					startActivity(email);
				}
			}.execute();
		} catch(Exception e) {}
	}
	
	private void getMostRecentCount() {
		// Use stashed value until after refresh occurs
		SharedPreferences prefs = Util.getPreferences(context);
		final int startCount = prefs.getInt(Constants.PREFERENCES_KEY_RECENT_COUNT + Util.getActiveServer(context), 0);
		setMostRecentCount(startCount);
		
		new SilentBackgroundTask<Integer>(context) {
			@Override
			public Integer doInBackground() throws Exception {
				String recentAddedFile = Util.getCacheName(context, "recent_count");
				ArrayList<String> recents = FileUtil.deserialize(context, recentAddedFile, ArrayList.class);
				if(recents == null) {
					recents = new ArrayList<String>();
				}
				
				MusicService musicService = MusicServiceFactory.getMusicService(context);
				MusicDirectory recentlyAdded = musicService.getAlbumList("newest", 20, 0, context, null);
				
				// If first run, just put everything in it and return 0
				boolean firstRun = recents.isEmpty();
				
				// Count how many new albums are in the list
				int count = 0;
				for(MusicDirectory.Entry album: recentlyAdded.getChildren()) {
					if(!recents.contains(album.getId())) {
						recents.add(album.getId());
						count++;
					}
				}
				
				// Keep recents list from growing infinitely
				while(recents.size() > 40) {
					recents.remove(0);
				}
				FileUtil.serialize(context, recents, recentAddedFile);
				
				if(firstRun) {
					return 0;
				} else {
					// Add the old count which will get cleared out after viewing recents
					count += startCount;
					SharedPreferences.Editor editor = Util.getPreferences(context).edit();
					editor.putInt(Constants.PREFERENCES_KEY_RECENT_COUNT + Util.getActiveServer(context), count);
					editor.commit();
					
					return count;
				}
			}
			
			@Override
			public void done(Integer result) {
				setMostRecentCount(result);
			}
			
			@Override
			public void error(Throwable x) {
				Log.w(TAG, "Failed to refresh most recent count", x);
			}
		}.execute();
	}
	
	private void setMostRecentCount(int count) {
		if(count <= 0) {
			countView.setVisibility(View.GONE);
		} else {
			String displayValue;
			if(count < 10) {
				displayValue = "0" + count;
			} else {
				displayValue = "" + count;
			}
			
			countView.setText(displayValue);
			countView.setVisibility(View.VISIBLE);
		}
	}
}
