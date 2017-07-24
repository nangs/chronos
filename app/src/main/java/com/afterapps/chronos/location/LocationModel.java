package com.afterapps.chronos.location;

/*
 * Created by mahmoudalyudeen on 7/24/17.
 */

import com.afterapps.chronos.Constants;
import com.afterapps.chronos.api.Responses.ReverseGeoLocResponse;
import com.afterapps.chronos.api.ReverseGeoLocService;
import com.afterapps.chronos.api.ServiceGenerator;
import com.afterapps.chronos.beans.Location;

import java.util.Date;

import io.realm.Realm;
import io.realm.RealmResults;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.afterapps.chronos.Constants.GOOGLE_REVERSE_GEO_LOC_API_KEY;

class LocationModel {

    interface LocationCallBack {

        void onLocationHandled();

        void onLocationError();
    }

    private final LocationCallBack mLocationCallBack;
    private Call<ReverseGeoLocResponse> mReverseGeoLocCall;

    LocationModel(LocationCallBack locationCallBack) {
        mLocationCallBack = locationCallBack;
    }

    void handleLocation(final android.location.Location geoLocation) {
        final ReverseGeoLocService service =
                ServiceGenerator.createService(ReverseGeoLocService.class, Constants.REVERSE_GEO_LOC_API_BASE_UEL);
        if (mReverseGeoLocCall != null) {
            mReverseGeoLocCall.cancel();
        }
        mReverseGeoLocCall = service.getReverseGeo(GOOGLE_REVERSE_GEO_LOC_API_KEY,
                Long.toString(new Date().getTime() / 1000),
                getCoordinates(geoLocation));

        mReverseGeoLocCall.enqueue(new Callback<ReverseGeoLocResponse>() {
            @Override
            public void onResponse(Call<ReverseGeoLocResponse> call, Response<ReverseGeoLocResponse> response) {
                if (isResponseValid(response)) {
                    handleReverseGeoLocResponse(geoLocation, response.body());
                } else {
                    mLocationCallBack.onLocationError();
                }
            }

            @Override
            public void onFailure(Call<ReverseGeoLocResponse> call, Throwable t) {
                mLocationCallBack.onLocationError();
            }
        });
    }

    private void handleReverseGeoLocResponse(final android.location.Location geoLocation,
                                             final ReverseGeoLocResponse reverseGeoLocResponse) {

        Realm realm = Realm.getDefaultInstance();

        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                final RealmResults<Location> locations = realm.where(Location.class).findAll();
                final Location location = locations.where().
                        equalTo("timezoneId", reverseGeoLocResponse.getTimeZoneId()).findFirst();

                if (location == null) {
                    Location newLocation = new Location(geoLocation, reverseGeoLocResponse);
                    realm.copyToRealmOrUpdate(newLocation);
                } else {
                    for (Location oldLocation : locations) {
                        oldLocation.setSelected(false);
                    }
                    location.setSelected(true);
                }
                realm.close();
            }
        });
    }

    private String getCoordinates(android.location.Location geoLocation) {
        return geoLocation.getLatitude() + "," + geoLocation.getLongitude();
    }

    @SuppressWarnings("ConstantConditions")
    private boolean isResponseValid(Response<ReverseGeoLocResponse> response) {
        return response.isSuccessful() &&
                response.body() != null &&
                response.body().getStatus() != null &&
                response.body().getStatus().equals("OK");
    }
}
