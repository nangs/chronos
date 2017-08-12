package com.afterapps.chronos.location;

import android.content.Intent;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.LinearLayout;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.StackingBehavior;
import com.afterapps.chronos.BaseLocationActivity;
import com.afterapps.chronos.Constants;
import com.afterapps.chronos.R;
import com.afterapps.chronos.beans.Location;
import com.google.android.gms.location.places.AutocompleteFilter;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.realm.OrderedRealmCollection;
import io.realm.Realm;

public class LocationActivity
        extends BaseLocationActivity<LocationView, LocationPresenter>
        implements LocationView, MaterialDialog.SingleButtonCallback {

    private static final int RC_PLACES_AUTO_COMPLETE_OVERLAY = 1;

    @BindView(R.id.places_toolbar)
    Toolbar mPlacesToolbar;
    @BindView(R.id.location_add)
    LinearLayout mLocationAdd;
    @BindView(R.id.locations_recycler)
    RecyclerView mLocationsRecycler;

    private Realm mRealm;

    @NonNull
    @Override
    public LocationPresenter createPresenter() {
        return new LocationPresenter();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);
        ButterKnife.bind(this);
        setSupportActionBar(mPlacesToolbar);
        mLocationsRecycler.setNestedScrollingEnabled(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mRealm = Realm.getDefaultInstance();
        displayLocations();
        EventBus.getDefault().register(this);
    }

    private void displayLocations() {
        OrderedRealmCollection<Location> locations = mRealm.where(Location.class).findAll();
        LocationsAdapter adapter = new LocationsAdapter(locations, this);
        mLocationsRecycler.setAdapter(adapter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mRealm.close();
        EventBus.getDefault().unregister(this);
    }

    @OnClick(R.id.location_add)
    public void onViewClicked() {
        new MaterialDialog.Builder(this)
                .title(R.string.dialog_title_location_method)
                .content(R.string.dialog_content_location_method)
                .positiveText(R.string.dialog_positive_location_method)
                .negativeText(R.string.dialog_negative_location_method)
                .stackingBehavior(StackingBehavior.ALWAYS)
                .onPositive(this)
                .onNegative(this)
                .show();
    }

    @Override
    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
        switch (which) {
            case POSITIVE:
                connectLocationClient();
                break;
            case NEGATIVE:
                startAutoCompleteOverlay();
                break;
        }
    }

    private void startAutoCompleteOverlay() {
        try {
            AutocompleteFilter autocompleteFilter = new AutocompleteFilter.Builder()
                    .setTypeFilter(AutocompleteFilter.TYPE_FILTER_CITIES)
                    .build();

            Intent intent = new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_OVERLAY)
                    .setFilter(autocompleteFilter)
                    .build(LocationActivity.this);

            startActivityForResult(intent, RC_PLACES_AUTO_COMPLETE_OVERLAY);

        } catch (Exception e) {
            Snackbar.make(mLocationsRecycler, R.string.error_play_services, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.action_play_services, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent browserIntent =
                                    new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.PLAY_SERVICES_LINK));
                            startActivity(browserIntent);
                        }
                    }).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_PLACES_AUTO_COMPLETE_OVERLAY && resultCode == RESULT_OK) {
            Place place = PlaceAutocomplete.getPlace(this, data);
            android.location.Location location = new android.location.Location(LocationManager.GPS_PROVIDER);
            location.setLatitude(place.getLatLng().latitude);
            location.setLongitude(place.getLatLng().longitude);
            onLocationChanged(location);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onLocationChanged(android.location.Location geoLocation) {
        presenter.onLocationDetected(geoLocation);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onLocationSelected(LocationsAdapter.LocationClickEvent event) {
        presenter.onLocationSelected(event.getTimezoneId());
    }

    @Override
    public void onLocationHandled() {
        finish();
    }


    @Override
    protected void showLocationDetectionError() {
        Snackbar.make(mLocationsRecycler, R.string.error_location_detection, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.action_location_manual, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startAutoCompleteOverlay();
                    }
                }).show();
    }

    @Override
    public void onLocationError() {
        showLocationDetectionError();
    }
}
