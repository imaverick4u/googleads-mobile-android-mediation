package com.google.ads.mediation.facebook.rtb;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.facebook.ads.Ad;
import com.facebook.ads.AdError;
import com.facebook.ads.AdListener;
import com.facebook.ads.AdOptionsView;
import com.facebook.ads.ExtraHints;
import com.facebook.ads.MediaView;
import com.facebook.ads.MediaViewListener;
import com.facebook.ads.NativeAd;
import com.facebook.ads.NativeAdLayout;
import com.facebook.ads.NativeAdListener;
import com.google.ads.mediation.facebook.FacebookAdapter;
import com.google.ads.mediation.facebook.FacebookMediationAdapter;
import com.google.android.gms.ads.formats.UnifiedNativeAdAssetNames;
import com.google.android.gms.ads.mediation.MediationAdLoadCallback;
import com.google.android.gms.ads.mediation.MediationNativeAdCallback;
import com.google.android.gms.ads.mediation.MediationNativeAdConfiguration;
import com.google.android.gms.ads.mediation.UnifiedNativeAdMapper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.google.ads.mediation.facebook.FacebookAdapter.*;
import static com.google.ads.mediation.facebook.FacebookAdapter.TAG;

public class FacebookRtbNativeAd extends UnifiedNativeAdMapper {

    private MediationNativeAdConfiguration adConfiguration;
    private MediationAdLoadCallback<UnifiedNativeAdMapper, MediationNativeAdCallback> callback;
    private NativeAd mNativeAd;
    private MediationNativeAdCallback mNativeAdCallback;
    private MediaView mMediaView;

    public FacebookRtbNativeAd(MediationNativeAdConfiguration adConfiguration,
            MediationAdLoadCallback<UnifiedNativeAdMapper, MediationNativeAdCallback> callback) {
        this.callback = callback;
        this.adConfiguration = adConfiguration;
    }

    public void render() {
        Bundle serverParameters = adConfiguration.getServerParameters();
        String placementID =
                FacebookMediationAdapter.getPlacementID(serverParameters);
        if (TextUtils.isEmpty(placementID)) {
            String message = "Failed to request ad, placementID is null or empty.";
            Log.e(TAG, message);
            callback.onFailure(message);
            return;
        }
        setMixedAudience(adConfiguration);
        mMediaView = new MediaView(adConfiguration.getContext());
        mNativeAd = new NativeAd(adConfiguration.getContext(), placementID);
        if (!TextUtils.isEmpty(adConfiguration.getWatermark())) {
            mNativeAd.setExtraHints(new ExtraHints.Builder()
                    .mediationData(adConfiguration.getWatermark()).build());
        }
        mNativeAd.loadAd(
                mNativeAd.buildLoadAdConfig()
                        .withAdListener(new NativeListener(adConfiguration.getContext(), mNativeAd))
                        .withBid(adConfiguration.getBidResponse())
                        .build());
    }

    private class NativeListener implements AdListener, NativeAdListener {
        /**
         * @param mContext required to create AdOptions View.
         */
        private WeakReference<Context> mContext;
        /**
         * Facebook native ad instance.
         */
        private NativeAd mNativeAd;

        NativeListener(Context mContext, NativeAd mNativeAd) {
            this.mNativeAd = mNativeAd;
            this.mContext  = new WeakReference<>(mContext);
        }

        @Override
        public void onAdClicked(Ad ad) {
            // TODO: Upon approval, add this callback back in.
            // mNativeAdCallback.reportAdClicked();
             mNativeAdCallback.onAdOpened();
             mNativeAdCallback.onAdLeftApplication();
        }

        @Override
        public void onLoggingImpression(Ad ad) {
            // Google Mobile Ads handles impression tracking.
        }

        @Override
        public void onAdLoaded(Ad ad) {
            if (ad != mNativeAd) {
                Log.w(TAG, "Ad loaded is not a native ad.");
                FacebookRtbNativeAd.this.callback.onFailure("Ad Loaded is not a Native Ad");
                return;
            }
            Context context = mContext.get();
            if (context == null) {
                Log.w(TAG, "Failed to create ad options view, Context is null.");
                FacebookRtbNativeAd.this.callback.onFailure("Context is null");
                return;
            }

            FacebookRtbNativeAd.this.mapNativeAd(context, new NativeAdMapperListener() {
                @Override
                public void onMappingSuccess() {
                    mNativeAdCallback = callback.onSuccess(FacebookRtbNativeAd.this);
                }

                @Override
                public void onMappingFailed() {
                    callback.onFailure("Ad Failed to Load");

                }
            });
        }

        @Override
        public void onError(Ad ad, AdError adError) {
            String errorMessage = adError.getErrorMessage();
            if (!TextUtils.isEmpty(errorMessage)) {
                Log.w(TAG, errorMessage);
            }
            callback.onFailure(adError.getErrorMessage());
        }

        @Override
        public void onMediaDownloaded(Ad ad) {
            Log.d(TAG, "onMediaDownloaded");
        }
    }

    /**
     * This method will map the Facebook {@link #mNativeAd} to this mapper and send a success
     * callback if the mapping was successful or a failure callback if the mapping was
     * unsuccessful.
     *
     * @param mapperListener used to send success/failure callbacks when mapping is done.
     */
    public void mapNativeAd(Context context, NativeAdMapperListener mapperListener) {
        if (!containsRequiredFieldsForUnifiedNativeAd(mNativeAd)) {
            Log.w(TAG, "Ad from Facebook doesn't have all assets required for the app install"
                    + " format.");
            mapperListener.onMappingFailed();
            return;
        }

        // Map all required assets (headline, one image, body, icon and call to
        // action).
        setHeadline(mNativeAd.getAdHeadline());
        List<com.google.android.gms.ads.formats.NativeAd.Image> images = new ArrayList<>();
        images.add(new FacebookAdapterNativeAdImage(null));
        setImages(images);
        setBody(mNativeAd.getAdBodyText());
        setIcon(new FacebookAdapterNativeAdImage(null));
        setCallToAction(mNativeAd.getAdCallToAction());
        setAdvertiser(mNativeAd.getAdvertiserName());

        mMediaView.setListener(new MediaViewListener() {
            @Override
            public void onPlay(MediaView mediaView) {
                // Google Mobile Ads SDK doesn't have a matching event. Do nothing.
            }

            @Override
            public void onVolumeChange(MediaView mediaView, float v) {
                // Google Mobile Ads SDK doesn't have a matching event. Do nothing.
            }

            @Override
            public void onPause(MediaView mediaView) {
                // Google Mobile Ads SDK doesn't have a matching event. Do nothing.
            }

            @Override
            public void onComplete(MediaView mediaView) {
                if (mNativeAdCallback != null) {
                    mNativeAdCallback.onVideoComplete();
                }
            }

            @Override
            public void onEnterFullscreen(MediaView mediaView) {
                // Google Mobile Ads SDK doesn't have a matching event. Do nothing.
            }

            @Override
            public void onExitFullscreen(MediaView mediaView) {
                // Google Mobile Ads SDK doesn't have a matching event. Do nothing.
            }

            @Override
            public void onFullscreenBackground(MediaView mediaView) {
                // Google Mobile Ads SDK doesn't have a matching event. Do nothing.
            }

            @Override
            public void onFullscreenForeground(MediaView mediaView) {
                // Google Mobile Ads SDK doesn't have a matching event. Do nothing.
            }
        });

        // Because the FAN SDK doesn't offer a way to determine whether a native ad contains
        // a video asset or not, the adapter always returns a MediaView and claims to have
        // video content.
        FacebookRtbNativeAd.this.setHasVideoContent(true);
        FacebookRtbNativeAd.this.setMediaView(mMediaView);

        // Map the optional assets.
        setStarRating(null);

        // Pass all the assets not supported by Google as extras.
        Bundle extras = new Bundle();
        extras.putCharSequence(KEY_ID, mNativeAd.getId());
        extras.putCharSequence(KEY_SOCIAL_CONTEXT_ASSET, FacebookRtbNativeAd.this.mNativeAd.getAdSocialContext());
        setExtras(extras);

        NativeAdLayout nativeAdLayout = new NativeAdLayout(context);
        AdOptionsView adOptionsView = new AdOptionsView(context, mNativeAd, nativeAdLayout);
        setAdChoicesContent(adOptionsView);
        mapperListener.onMappingSuccess();

    }

    /**
     * This method will check whether or not the given Facebook native ad contains all the
     * necessary fields for it to be mapped to Google Mobile Ads' native app install ad.
     *
     * @param nativeAd Facebook native ad.
     * @return {@code true} if the given ad contains all the necessary fields, {@link false}
     * otherwise.
     */
    private boolean containsRequiredFieldsForUnifiedNativeAd(NativeAd nativeAd) {
        return ((nativeAd.getAdHeadline() != null) && (nativeAd.getAdCoverImage() != null)
                && (nativeAd.getAdBodyText() != null) && (nativeAd.getAdIcon() != null)
                && (nativeAd.getAdCallToAction() != null) && (mMediaView != null));
    }

    @Override
    public void trackViews(View view,
                           Map<String, View> clickableAssetViews,
                           Map<String, View> nonClickableAssetViews) {

        // Facebook does its own click handling.
        setOverrideClickHandling(true);
        ImageView iconview = null;

        ArrayList<View> assetViews = new ArrayList<>();
        for (Map.Entry<String, View> clickableAssets : clickableAssetViews.entrySet()) {
            assetViews.add(clickableAssets.getValue());

            if (clickableAssets.getKey().equals(UnifiedNativeAdAssetNames.ASSET_ICON)) {
                iconview = (ImageView) clickableAssets.getValue();
            }

        }

        mNativeAd.registerViewForInteraction(view, mMediaView, iconview, assetViews);
    }


    @Override
    public void untrackView(View view) {
        super.untrackView(view);
    }

    private class FacebookAdapterNativeAdImage extends
            com.google.android.gms.ads.formats.NativeAd.Image {

        /**
         * A drawable for the Image.
         */
        private Drawable mDrawable;

        /**
         * An Uri from which the image can be obtained.
         */
        private Uri mUri;

        /**
         * Default constructor for {@link FacebookAdapter.FacebookAdapterNativeAdImage}, requires an {@link Uri}.
         *
         * @param uri required to initialize.
         */
        public FacebookAdapterNativeAdImage(Uri uri) {
            this.mUri = uri;
        }

        /**
         * @param drawable set to {@link #mDrawable}.
         */
        protected void setDrawable(Drawable drawable) {
            this.mDrawable = drawable;
        }

        @Override
        public Drawable getDrawable() {
            return mDrawable;
        }

        @Override
        public Uri getUri() {
            return mUri;
        }

        @Override
        public double getScale() {
            // Default scale is 1.
            return 1;
        }
    }

    private interface NativeAdMapperListener {

        /**
         * This method will be called once the native ad mapping is successfully.
         */
        void onMappingSuccess();

        /**
         * This method will be called if the native ad mapping failed.
         */
        void onMappingFailed();
    }

}
