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

package com.frostwire.android.gui.activities.internal;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.andrew.apollo.ui.activities.AudioPlayerActivity;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.activities.PreferencesActivity;
import com.frostwire.android.gui.activities.WizardActivity;
import com.frostwire.android.gui.dialogs.ShareIndicationDialog;
import com.frostwire.android.gui.fragments.BrowsePeerFragment;
import com.frostwire.android.gui.fragments.TransfersFragment;
import com.frostwire.android.gui.fragments.TransfersFragment.TransferStatus;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.OfferUtils;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.logging.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public final class MainController {

    private static final Logger LOG = Logger.getLogger(MainController.class);

    private final MainActivity activity;

    public MainController(MainActivity activity) {
        this.activity = activity;
    }

    public MainActivity getActivity() {
        return activity;
    }

    public void closeSlideMenu() {
        activity.closeSlideMenu();
    }

    public void switchFragment(int itemId) {
        Fragment fragment = activity.getFragmentByMenuId(itemId);
        if (fragment != null) {
            activity.switchContent(fragment);
        }
    }

    public void showPreferences() {
        Intent i = new Intent(activity, PreferencesActivity.class);
        activity.startActivity(i);
    }

    public void showFreeApps(Context context) {
        OfferUtils.onFreeAppsClick(context);
    }

    public void launchMyMusic() {
        Intent i = new Intent(activity, com.andrew.apollo.ui.activities.HomeActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(i);
    }

    public void showTransfers(TransferStatus status) {
        if (!(activity.getCurrentFragment() instanceof TransfersFragment)) {
            TransfersFragment fragment = (TransfersFragment) activity.getFragmentByMenuId(R.id.menu_main_transfers);
            fragment.selectStatusTab(status);
            switchFragment(R.id.menu_main_transfers);
        }
    }

    public void shutdown() {
        Intent i = new Intent(activity, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.putExtra("shutdown-" + ConfigurationManager.instance().getUUIDString(), true);
        activity.startActivity(i);
    }

    public void showMyFiles() {
        if (!(activity.getCurrentFragment() instanceof BrowsePeerFragment)) {
            switchFragment(R.id.menu_main_library);
        }
        if (ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_GUI_SHOW_SHARE_INDICATION)) {
            ShareIndicationDialog dlg = new ShareIndicationDialog();
            dlg.show(activity.getFragmentManager());
        }
    }

    public void startWizardActivity() {
        Intent i = new Intent(activity, WizardActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(i);
    }

    public void launchPlayerActivity() {
        if (Engine.instance().getMediaPlayer().getCurrentFD() != null) {
            Intent i = new Intent(activity, AudioPlayerActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(i);
        }
    }

    public void handleSendAction(Intent intent) {
        String action = intent.getAction();
        if (action.equals(Intent.ACTION_SEND)) {
            handleSendSingleFile(intent);
        } else if (action.equals(Intent.ACTION_SEND_MULTIPLE)) {
            handleSendMultipleFiles(intent);
        }
    }

    private void handleSendMultipleFiles(Intent intent) {
        ArrayList<Uri> fileUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (fileUris != null && fileUris.size() > 0) {
            int sharedFiles = 0;
            for (Uri uri : fileUris) {
                if (shareFileByUri(uri)) {
                    sharedFiles++;
                }
            }

            FileDescriptor fileDescriptor = Librarian.instance().getFileDescriptor(fileUris.get(0));
            showFile(fileDescriptor);
            UIUtils.showLongMessage(activity, activity.getString(R.string.n_files_shared, sharedFiles));
        }
    }

    private void handleSendSingleFile(Intent intent) {
        Uri uri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri == null) {
            return;
        }

        FileDescriptor fileDescriptor = Librarian.instance().getFileDescriptor(uri);

        if (fileDescriptor != null) {
            // Until we don't show .torrents on file manager, the most logical thing to do if user wants to
            // "Share" a `.torrent` from a third party app with FrostWire, that is starting the `.torrent` transfer.
            if (fileDescriptor.filePath != null && fileDescriptor.filePath.endsWith(".torrent")) {
                TransferManager.instance().downloadTorrent(uri.toString());
                activity.switchFragment(R.id.menu_main_transfers);
            } else {
                try {
                    if (shareFileByUri(uri)) {
                        showFile(fileDescriptor);
                        UIUtils.showLongMessage(activity, R.string.one_file_shared);
                    } else {
                        UIUtils.showLongMessage(activity, R.string.couldnt_share_file);
                    }
                } catch (Throwable t) {
                    UIUtils.showLongMessage(activity, R.string.couldnt_share_file);
                }
            }
        }
    }

    private void showFile(FileDescriptor fileDescriptor) {
        if (fileDescriptor != null) {
            showMyFiles();
            ((BrowsePeerFragment) activity.getFragmentByMenuId(R.id.menu_main_library)).showFile(fileDescriptor);
        }
    }

    private boolean shareFileByUri(Uri uri) {
        boolean result = false;
        if (uri == null) {
            return false;
        }

        FileDescriptor fileDescriptor = Librarian.instance().getFileDescriptor(uri);

        if (fileDescriptor != null && fileDescriptor.filePath != null && new File(fileDescriptor.filePath).exists()) {
            fileDescriptor.shared = true;
            Librarian.instance().updateSharedStates(fileDescriptor.fileType, Arrays.asList(fileDescriptor));
            result = true;
        }

        return result;
    }
}
