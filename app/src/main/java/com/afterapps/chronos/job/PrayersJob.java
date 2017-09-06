package com.afterapps.chronos.job;

/*
 * Created by mahmoud on 9/6/17.
 */

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;

import com.afterapps.chronos.R;
import com.afterapps.chronos.api.Responses.TimingsResponse;
import com.afterapps.chronos.beans.Location;
import com.afterapps.chronos.beans.Prayer;
import com.afterapps.chronos.home.PrayerModel;
import com.evernote.android.job.Job;
import com.evernote.android.job.JobRequest;

import java.util.Calendar;
import java.util.List;

import io.realm.Realm;
import retrofit2.Call;
import retrofit2.Response;

import static com.afterapps.chronos.home.PrayerModel.isResponseValid;

public class PrayersJob extends Job {

    static final String TAG = "prayersJobTag";

    public static int schedulePrayersJob() {
        return new JobRequest.Builder(TAG)
                .setExecutionWindow(5L, 1000L)
                .setBackoffCriteria(5_000L, JobRequest.BackoffPolicy.LINEAR)
                .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
                .setRequirementsEnforced(true)
                .setUpdateCurrent(true)
                .setPersisted(true)
                .build()
                .schedule();
    }

    @Override
    @NonNull
    protected Result onRunJob(Params params) {
        final Realm realm = Realm.getDefaultInstance();
        final SharedPreferences mPref = PreferenceManager.getDefaultSharedPreferences(getContext());
        final int method = mPref.getInt(getContext().getString(R.string.preference_key_method), 5);
        final int school = mPref.getInt(getContext().getString(R.string.preference_key_school), 0);
        final int latitudeMethod = mPref.getInt(getContext().getString(R.string.preference_key_latitude), 3);
        final Location location = realm.where(Location.class)
                .equalTo("selected", true)
                .findFirst();
        if (location == null) {
            realm.close();
            return Result.RESCHEDULE;
        }
        final String signature = location.getTimezoneId() + method + school + latitudeMethod;
        final Location locationDetached = realm.copyFromRealm(location);
        realm.close();
        Result result = loadPrayers(signature) ? Result.SUCCESS :
                fetchPrayers(method, school, latitudeMethod, locationDetached) ?
                        Result.SUCCESS : Result.RESCHEDULE;
        if (result == Result.SUCCESS) {
            handleSuccess();
        }
        return result;
    }

    private void handleSuccess() {
        Log.d(TAG, "handleSuccess: ");
        //todo
    }

    private boolean loadPrayers(String signature) {
        return PrayerModel.getStoredPrayers(signature).size() >= 6;
    }

    private boolean fetchPrayers(final int method,
                                 final int school,
                                 final int latitudeMethod,
                                 final Location locationDetached) {
        Call<TimingsResponse> currentMonthTimingsCall = PrayerModel.getTimingsCall(method,
                school,
                latitudeMethod,
                locationDetached,
                Calendar.getInstance(),
                false);
        Call<TimingsResponse> nextMonthTimingsCall = PrayerModel.getTimingsCall(method,
                school,
                latitudeMethod,
                locationDetached,
                Calendar.getInstance(),
                true);
        try {
            Response<TimingsResponse> currentMonthResponse = currentMonthTimingsCall.execute();
            Response<TimingsResponse> nextMonthResponse = nextMonthTimingsCall.execute();
            return !(!isResponseValid(currentMonthResponse) || !isResponseValid(nextMonthResponse)) &&
                    storePrayers(currentMonthResponse.body(),
                            nextMonthResponse.body(),
                            method,
                            school,
                            latitudeMethod,
                            locationDetached);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean storePrayers(
            final TimingsResponse currentMonthResponse,
            final TimingsResponse nextMonthResponse,
            final int method,
            final int school,
            final int latitudeMethod,
            final Location locationDetached) throws IllegalAccessException {
        final List<Prayer> currentMonthPrayers = currentMonthResponse.getPrayers(method,
                school,
                latitudeMethod,
                locationDetached);
        final List<Prayer> nextMonthPrayers = nextMonthResponse.getPrayers(method,
                school,
                latitudeMethod,
                locationDetached);
        final Realm realm = Realm.getDefaultInstance();
        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                realm.copyToRealmOrUpdate(currentMonthPrayers);
                realm.copyToRealmOrUpdate(nextMonthPrayers);

            }
        });
        realm.close();
        return true;
    }
}