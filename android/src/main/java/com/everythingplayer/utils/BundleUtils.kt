package com.everythingplayer.utils

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.RatingCompat
import com.facebook.react.views.imagehelper.ResourceDrawableIdHelper
import androidx.media3.common.Rating
import androidx.media3.common.HeartRating
import androidx.media3.common.ThumbRating
import androidx.media3.common.StarRating
import androidx.media3.common.PercentageRating

object BundleUtils {
    fun getUri(context: Context, data: Bundle?, key: String?): Uri? {
        if (data == null || !data.containsKey(key)) return null
        val obj = data[key]
        if (obj is String) {
            if (obj.trim().isEmpty()) throw RuntimeException("$key: The URL cannot be empty")
            return Uri.parse(obj)
        } else if (obj is Bundle) {
            val uri = obj.getString("uri")
            val helper = ResourceDrawableIdHelper.getInstance()
            val id = helper.getResourceDrawableId(context, uri)
            return if (id > 0) {
                val res = context.resources
                Uri.Builder()
                    .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                    .authority(res.getResourcePackageName(id))
                    .appendPath(res.getResourceTypeName(id))
                    .appendPath(res.getResourceEntryName(id))
                    .build()
            } else {
                Uri.parse(uri)
            }
        }
        return null
    }

    fun getRawResourceId(context: Context, data: Bundle, key: String?): Int {
        if (!data.containsKey(key)) return 0
        val obj = data[key] as? Bundle ?: return 0
        var name = obj.getString("uri")
        if (name.isNullOrEmpty()) return 0
        name = name.lowercase().replace("-", "_")
        return try {
            name.toInt()
        } catch (ex: NumberFormatException) {
            context.resources.getIdentifier(name, "raw", context.packageName)
        }
    }

    fun getRating(data: Bundle, key: String?, ratingType: Int): Rating? {
        return when (ratingType) {
            RatingCompat.RATING_HEART -> HeartRating(data.getBoolean(key, true))
            RatingCompat.RATING_THUMB_UP_DOWN -> ThumbRating(data.getBoolean(key, true))
            RatingCompat.RATING_PERCENTAGE -> PercentageRating(data.getFloat(key, 0f))
            RatingCompat.RATING_3_STARS, RatingCompat.RATING_4_STARS, RatingCompat.RATING_5_STARS ->
                StarRating(ratingType, data.getFloat(key, 0f))
            else -> null
        }
    }

    fun setRating(data: Bundle, key: String?, rating: Rating) {
        if (!rating.isRated) return
        when (rating) {
            is HeartRating -> data.putBoolean(key, rating.isHeart)
            is ThumbRating -> data.putBoolean(key, rating.isThumbsUp)
            is PercentageRating -> data.putDouble(key, rating.percent.toDouble())
            is StarRating -> data.putDouble(key, rating.starRating.toDouble())
        }
    }

    fun getInt(data: Bundle?, key: String?, defaultValue: Int): Int {
        val value = data!![key]
        return if (value is Number) value.toInt() else defaultValue
    }

    fun getIntOrNull(data: Bundle?, key: String?): Int? {
        val value = data!![key]
        return if (value is Number) value.toInt() else null
    }

    fun getDoubleOrNull(data: Bundle?, key: String?): Double? {
        val value = data!![key]
        return if (value is Number) value.toDouble() else null
    }
}
