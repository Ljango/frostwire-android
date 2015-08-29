/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(TM). All rights reserved.
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

package com.frostwire.android.gui.affiliates;

import android.app.Activity;
import com.applovin.adview.AppLovinInterstitialAd;
import com.applovin.adview.AppLovinInterstitialAdDialog;
import com.applovin.sdk.*;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.logging.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

public class AppLovinInterstitialAdapter implements InterstitialListener, AppLovinAdDisplayListener, AppLovinAdLoadListener {
    private static final Logger LOG = Logger.getLogger(AppLovinInterstitialAdapter.class);
    private WeakReference<Activity> activityRef;
    private AppLovinAffiliate appLovinAffiliate;
    private AppLovinAd ad;

    private boolean dismissAfter = false;
    private boolean shutdownAfter = false;
    private boolean isVideoAd = false;

    public AppLovinInterstitialAdapter(Activity parentActivity, AppLovinAffiliate appLovinAffiliate) {
        this.activityRef = Ref.weak(parentActivity);
        this.appLovinAffiliate = appLovinAffiliate;
    }

    public boolean isAdReadyToDisplay() {
        return ad != null && Ref.alive(activityRef) && AppLovinInterstitialAd.isAdReadyToDisplay(activityRef.get());
    }

    @Override
    public boolean isVideoAd() {
        return isVideoAd;
    }

    public boolean show(WeakReference<Activity> activityWeakReference) {
        boolean result = false;
        if (ad!=null && Ref.alive(activityWeakReference)) {
            try {
                this.activityRef = activityWeakReference;
                final AppLovinInterstitialAdDialog adDialog = AppLovinInterstitialAd.create(AppLovinSdk.getInstance(activityRef.get()), activityRef.get());
                adDialog.showAndRender(ad);
                result = true;
            } catch (Throwable t) {
                result = false;
            }
        }
        return result;
    }

    public void shutdownAppAfter(boolean shutdown) {
        shutdownAfter = shutdown;
    }

    public void dismissActivityAfterwards(boolean dismiss) {
        dismissAfter = dismiss;
    }

    @Override
    public void adDisplayed(AppLovinAd appLovinAd) {
        // Free the ad, load a new one.
        if (appLovinAd!=null) {
            ad = null;

            if (Ref.alive(activityRef)) {
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            appLovinAffiliate.loadNewInterstitial(activityRef.get());
                        } catch (Throwable e) {
                            LOG.error(e.getMessage(), e);
                        }
                    }
                }.start();
            }
        }
    }

    @Override
    public void adHidden(AppLovinAd appLovinAd) {
        if (Ref.alive(activityRef)) {
            Activity callerActivity = activityRef.get();

            if (dismissAfter) {
                callerActivity.finish();
            }
            if (shutdownAfter) {
                if (callerActivity instanceof MainActivity) {
                    ((MainActivity) callerActivity).shutdown();
                }
            }
        }
    }

    @Override
    public void adReceived(AppLovinAd appLovinAd) {
        if (appLovinAd != null) {
            ad = appLovinAd;
            isVideoAd = appLovinAd.isVideoAd();
        }
    }

    @Override
    public void failedToReceiveAd(int i) {
        LOG.warn("failed to receive ad ("+ i +")");
        if (AppLovinErrorCodes.NO_FILL == i) {
            new Thread("AppLovinInterstitialAdapter.onInterstitialFailed") {
                @Override
                public void run() {
                    try {
                        TimeUnit.MINUTES.sleep(30);
                        if (Ref.alive(activityRef)) {
                            Activity activity = activityRef.get();
                            if (activity instanceof MainActivity) {
                                appLovinAffiliate.loadNewInterstitial(activity);
                            }
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }.start();

        }
    }
}