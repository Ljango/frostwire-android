/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.activities;

import android.app.ActionBar;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import com.frostwire.android.R;
import com.frostwire.android.gui.dialogs.NewTransferDialog;
import com.frostwire.android.gui.fragments.SearchFragment;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.util.ImageLoader;
import com.frostwire.logging.Logger;
import com.frostwire.search.FileSearchResult;
import com.frostwire.util.Ref;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;

import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * @author gubatron
 * @author aldenml
 */
public final class PreviewPlayerActivity extends AbstractActivity implements AbstractDialog.OnDialogClickListener, TextureView.SurfaceTextureListener, MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnVideoSizeChangedListener, MediaPlayer.OnInfoListener {
    private static final Logger LOG = Logger.getLogger(PreviewPlayerActivity.class);
    public static WeakReference<FileSearchResult> srRef;

    private MediaPlayer mediaPlayer;
    private Surface surface;
    private String displayName;
    private String source;
    private String thumbnailUrl;
    private String streamUrl;
    private boolean hasVideo;
    private boolean audio;
    private boolean isPortrait = true;
    private boolean isFullScreen = false;
    private boolean videoSizeSetupDone = false;
    private boolean changedActionBarTitleToNonBuffering = false;

    public PreviewPlayerActivity() {
        super(R.layout.activity_preview_player);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void initComponents(Bundle savedInstanceState) {
        Intent i = getIntent();
        if (i == null) {
            finish();
            return;
        }

        displayName = i.getStringExtra("displayName");
        source = i.getStringExtra("source");
        thumbnailUrl = i.getStringExtra("thumbnailUrl");
        streamUrl = i.getStringExtra("streamUrl");
        hasVideo = i.getBooleanExtra("hasVideo", false);
        audio = i.getBooleanExtra("audio", false);
        isFullScreen = i.getBooleanExtra("isFullScreen", false);

        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.setHomeButtonEnabled(true);
            ab.setDisplayHomeAsUpEnabled(true);
            int mediaTypeStrId = audio ? R.string.audio : R.string.video;
            ab.setTitle(getString(R.string.media_preview, getString(mediaTypeStrId)) + " (buffering...)");
            int icon = audio ? R.drawable.browse_peer_audio_icon_selector_off :
                                   R.drawable.browse_peer_video_icon_selector_off;
            ab.setIcon(icon);
        } else {
            setTitle(displayName);
        }

        final TextureView v = findView(R.id.activity_preview_player_videoview);
        v.setSurfaceTextureListener(this);

        // when previewing audio, we make the video view really tiny.
        // hiding it will cause the player not to play.
        if (audio) {
            final ViewGroup.LayoutParams layoutParams = v.getLayoutParams();
            layoutParams.width = 1;
            layoutParams.height = 1;
            v.setLayoutParams(layoutParams);
        }

        final ImageView img = findView(R.id.activity_preview_player_thumbnail);

        final TextView trackName = findView(R.id.activity_preview_player_track_name);
        final TextView artistName = findView(R.id.activity_preview_player_artist_name);
        trackName.setText(displayName);
        artistName.setText(source);

        if (!audio) {
            v.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    toggleFullScreen(v);
                    return false;
                }
            });
        }

        if (thumbnailUrl != null) {
            ImageLoader.getInstance(this).load(Uri.parse(thumbnailUrl), img);
        }

        final ImageButton downloadButton = findView(R.id.activity_preview_player_download_button);
        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDownloadButtonClick();
            }
        });

        if (isFullScreen) {
            isFullScreen = false; //so it will make it full screen on what was an orientation change.
            toggleFullScreen(v);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (outState != null) {
            super.onSaveInstanceState(outState);
            outState.putString("displayName", displayName);
            outState.putString("source", source);
            outState.putString("thumbnailUrl", thumbnailUrl);
            outState.putString("streamUrl", streamUrl);
            outState.putBoolean("hasVideo", hasVideo);
            outState.putBoolean("audio", audio);
            outState.putBoolean("isFullScreen", isFullScreen);
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                outState.putInt("currentPosition", mediaPlayer.getCurrentPosition());
            }
        }
    }

    private void onVideoViewPrepared(final ImageView img) {
        final ImageButton downloadButton = findView(R.id.activity_preview_player_download_button);
        downloadButton.setVisibility(View.VISIBLE);
        if (!audio) {
            img.setVisibility(View.GONE);
        }
    }

    private void onDownloadButtonClick() {
        if (Ref.alive(srRef)) {
            NewTransferDialog dlg = NewTransferDialog.newInstance(srRef.get(), false);
            dlg.show(getFragmentManager());
        } else {
            finish();
        }
    }

    private String getFinalUrl(String url) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) (new URL(url).openConnection());
            con.setInstanceFollowRedirects(false);
            con.connect();
            String location = con.getHeaderField("Location");

            if (location != null) {
                return location;
            }

        } catch (Throwable e) {
            LOG.error("Unable to detect final url", e);
        } finally {
            if (con != null) {
                try {
                    con.disconnect();
                } catch (Throwable e) {
                    // ignore
                }
            }
        }
        return url;
    }

    @Override
    public void onDialogClick(String tag, int which) {
        if (tag.equals(NewTransferDialog.TAG) && which == AbstractDialog.BUTTON_POSITIVE) {
            if (Ref.alive(NewTransferDialog.srRef)) {
                SearchFragment.startDownload(this, NewTransferDialog.srRef.get(), getString(R.string.download_added_to_queue));
                UXStats.instance().log(UXAction.DOWNLOAD_CLOUD_FILE_FROM_PREVIEW);
            }
            finish();
        }
    }

    private void toggleFullScreen(TextureView v) {
        videoSizeSetupDone = false;
        DisplayMetrics metrics = new DisplayMetrics();
        final Display defaultDisplay = getWindowManager().getDefaultDisplay();
        defaultDisplay.getMetrics(metrics);
        isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

        final FrameLayout frameLayout = findView(R.id.activity_preview_player_framelayout);
        LinearLayout.LayoutParams frameLayoutParams = (LinearLayout.LayoutParams) frameLayout.getLayoutParams();

        LinearLayout header = findView(R.id.activity_preview_player_header);
        ImageView thumbnail = findView(R.id.activity_preview_player_thumbnail);
        ImageButton downloadButton =findView(R.id.activity_preview_player_download_button);
        ActionBar bar = getActionBar();

        // these ones only exist on landscape mode.
        LinearLayout rightSide = findView(R.id.activity_preview_player_right_side);
        View divider = findView(R.id.activity_preview_player_divider);
        View filler = findView(R.id.activity_preview_player_filler);

        // Let's Go into full screen mode.
        if (!isFullScreen) {
            if (bar!=null) {
                bar.hide();
            }
            setViewsVisibility(View.GONE, header, thumbnail, divider, downloadButton, rightSide, filler);

            if (isPortrait) {
                frameLayoutParams.width = metrics.heightPixels;
                frameLayoutParams.height = metrics.widthPixels;
            } else {
                frameLayoutParams.width = metrics.widthPixels;
                frameLayoutParams.height = metrics.heightPixels;
            }
            isFullScreen = true;
        } else {
            // restore components back from full screen mode.
            if (bar != null) {
                bar.show();
            }
            setViewsVisibility(View.VISIBLE, header, divider, downloadButton, rightSide, filler);
            v.setRotation(0);

            // restore the thumbnail on the way back only if doing audio preview.
            thumbnail.setVisibility(!audio ? View.GONE : View.VISIBLE);

            if (isPortrait) {
                frameLayoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
                frameLayoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            } else {
                frameLayoutParams.width = 0;
                frameLayoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
                frameLayoutParams.weight = 0.5f;
            }
            isFullScreen = false;
        }

        frameLayout.setLayoutParams(frameLayoutParams);
        changeVideoSize();
    }

    private void changeVideoSize() {
        int videoWidth = mediaPlayer.getVideoWidth();
        int videoHeight = mediaPlayer.getVideoHeight();
        final TextureView v = findView(R.id.activity_preview_player_videoview);
        DisplayMetrics metrics = new DisplayMetrics();
        final Display defaultDisplay = getWindowManager().getDefaultDisplay();
        defaultDisplay.getMetrics(metrics);

        final android.widget.FrameLayout.LayoutParams params = (android.widget.FrameLayout.LayoutParams) v.getLayoutParams();
        isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        float hRatio = (videoHeight * 1.0f) / (videoWidth * 1.0f);
        float rotation = 0;

        if (isPortrait) {
            if (isFullScreen) {
                params.width = metrics.heightPixels;
                params.height = metrics.widthPixels;
                params.gravity = Gravity.TOP | Gravity.LEFT;
                v.setPivotY((float) metrics.widthPixels / 2.0f);
                rotation = 90f;
            } else {
                params.width = metrics.widthPixels;
                params.height = (int) (params.width * hRatio);
                params.gravity = Gravity.CENTER;
            }
        } else {
            if (isFullScreen) {
                params.width = metrics.widthPixels;
                params.height = metrics.heightPixels;
            } else {
                params.width = Math.max(videoWidth, metrics.widthPixels / 2);
                params.height = (int) (params.width * hRatio);
                params.gravity = Gravity.CENTER;
            }
        }

        v.setRotation(rotation);
        v.setLayoutParams(params);
        videoSizeSetupDone = true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        //Disable rotation once the activity has started.
        super.onConfigurationChanged(newConfig);
        if (isPortrait && newConfig.orientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else if (!isPortrait && newConfig.orientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    /**
     * Utility method: change the visibility for a bunch of views. Skips null views.
     * @param visibility
     * @param views
     */
    private void setViewsVisibility(int visibility, View ... views) {
        if (visibility != View.INVISIBLE && visibility != View.VISIBLE && visibility != View.GONE) {
            return; //invalid visibility constant.
        }
        if (views != null) {
            for (int i=0; i < views.length; i++) {
                View v = views[i];
                if (v != null) {
                    v.setVisibility(visibility);
                }
            }
        }
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.setSurface(null);
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    protected void onDestroy() {
        releaseMediaPlayer();
        super.onDestroy();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        surface = new Surface(surfaceTexture);
        final PreviewPlayerActivity previewPlayerActivity = this;

        Thread t = new Thread() {
            @Override
            public void run() {
                final String url = getFinalUrl(streamUrl);
                final Uri uri = Uri.parse(url);
                mediaPlayer= new MediaPlayer();
                try {
                    mediaPlayer.setDataSource(previewPlayerActivity, uri);
                    mediaPlayer.setSurface(!audio ? surface : null);
                    mediaPlayer.setOnBufferingUpdateListener(previewPlayerActivity);
                    mediaPlayer.setOnCompletionListener(previewPlayerActivity);
                    mediaPlayer.setOnPreparedListener(previewPlayerActivity);
                    mediaPlayer.setOnVideoSizeChangedListener(previewPlayerActivity);
                    mediaPlayer.setOnInfoListener(previewPlayerActivity);
                    mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    mediaPlayer.prepare();
                    mediaPlayer.start();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        };
        t.start();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        if (mediaPlayer != null) {
            if (surface != null) {
                surface.release();
                surface = new Surface(surfaceTexture);
            }
            mediaPlayer.setSurface(!audio ? surface : null);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (mediaPlayer != null) {
            mediaPlayer.setSurface(null);
            this.surface.release();
            this.surface = null;
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        finish();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
       final ImageView img = findView(R.id.activity_preview_player_thumbnail);
       onVideoViewPrepared(img);
       if (mp != null) {
           changeVideoSize();
       }
    }

    @Override
    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        if (width > 0 && height > 0 && !videoSizeSetupDone) {
            changeVideoSize();
        }
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        boolean startedPlayback = false;
        switch (what) {
            case MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:
                //LOG.warn("Media is too complex to decode it fast enough.");
                //startedPlayback = true;
                break;
            case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                //LOG.warn("Start of media buffering.");
                //startedPlayback = true;
                break;
            case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                //LOG.warn("End of media buffering.");
                changeVideoSize();
                startedPlayback = true;
                break;
            case MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:
                break;
            case MediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
                break;
            case MediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                break;
            case MediaPlayer.MEDIA_INFO_UNKNOWN:
            default:
                break;
        }

        if (startedPlayback && !changedActionBarTitleToNonBuffering) {
            int mediaTypeStrId = audio ? R.string.audio : R.string.video;
            ActionBar bar = getActionBar();
            if (bar!=null) {
                bar.setTitle(getString(R.string.media_preview, getString(mediaTypeStrId)));
                changedActionBarTitleToNonBuffering = true;
            }
        }

        return false;
    }
}