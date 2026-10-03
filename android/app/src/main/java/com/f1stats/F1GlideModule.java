package com.f1stats;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;

/**
 * Glide setup: a disk cache big enough to hold every headshot and circuit image across a few
 * seasons, so images show on a cold start before any network call returns.
 */
@GlideModule
public final class F1GlideModule extends AppGlideModule {

    private static final long DISK_CACHE_BYTES = 250L * 1024 * 1024;

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, DISK_CACHE_BYTES));
        builder.setDefaultRequestOptions(new RequestOptions().diskCacheStrategy(DiskCacheStrategy.AUTOMATIC));
    }

    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}
