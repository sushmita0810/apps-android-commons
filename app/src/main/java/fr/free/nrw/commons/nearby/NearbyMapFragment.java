package fr.free.nrw.commons.nearby;

import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.PolygonOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.constants.Style;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.MapboxMapOptions;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.android.support.DaggerFragment;
import fr.free.nrw.commons.CommonsApplication;
import fr.free.nrw.commons.R;
import fr.free.nrw.commons.Utils;
import fr.free.nrw.commons.auth.LoginActivity;
import fr.free.nrw.commons.bookmarks.locations.BookmarkLocationsDao;
import fr.free.nrw.commons.contributions.ContributionController;
import fr.free.nrw.commons.kvstore.BasicKvStore;
import fr.free.nrw.commons.kvstore.JsonKvStore;
import fr.free.nrw.commons.utils.ImageUtils;
import fr.free.nrw.commons.utils.IntentUtils;
import fr.free.nrw.commons.utils.LocationUtils;
import fr.free.nrw.commons.utils.UriDeserializer;
import fr.free.nrw.commons.utils.ViewUtil;
import timber.log.Timber;

import static fr.free.nrw.commons.contributions.ContributionController.NEARBY_CAMERA_UPLOAD_REQUEST_CODE;
import static fr.free.nrw.commons.contributions.ContributionController.NEARBY_GALLERY_UPLOAD_REQUEST_CODE;
import static fr.free.nrw.commons.contributions.ContributionController.NEARBY_UPLOAD_IMAGE_LIMIT;
import static fr.free.nrw.commons.wikidata.WikidataConstants.PLACE_OBJECT;

public class NearbyMapFragment extends DaggerFragment {

    public MapView mapView;
    private List<NearbyBaseMarker> baseMarkerOptions;
    private fr.free.nrw.commons.location.LatLng curLatLng;
    public fr.free.nrw.commons.location.LatLng[] boundaryCoordinates;

    private View bottomSheetList;
    private View bottomSheetDetails;

    private BottomSheetBehavior bottomSheetListBehavior;
    private BottomSheetBehavior bottomSheetDetailsBehavior;
    private LinearLayout wikipediaButton;
    private LinearLayout wikidataButton;
    private LinearLayout directionsButton;
    private LinearLayout commonsButton;
    private LinearLayout bookmarkButton;
    private FloatingActionButton fabPlus;
    private FloatingActionButton fabCamera;
    private FloatingActionButton fabGallery;
    private FloatingActionButton fabRecenter;
    private View transparentView;
    private TextView description;
    private TextView title;
    private TextView distance;
    private ImageView icon;
    private ImageView bookmarkButtonImage;

    private TextView wikipediaButtonText;
    private TextView wikidataButtonText;
    private TextView commonsButtonText;
    private TextView directionsButtonText;

    private boolean isFabOpen = false;
    private Animation rotate_backward;
    private Animation fab_close;
    private Animation fab_open;
    private Animation rotate_forward;

    private Place place;
    private Marker selected;
    private Marker currentLocationMarker;
    public MapboxMap mapboxMap;
    private PolygonOptions currentLocationPolygonOptions;

    public Button searchThisAreaButton;
    public ProgressBar searchThisAreaButtonProgressBar;

    private boolean isBottomListSheetExpanded;
    private final double CAMERA_TARGET_SHIFT_FACTOR_PORTRAIT = 0.06;
    private final double CAMERA_TARGET_SHIFT_FACTOR_LANDSCAPE = 0.04;

    public boolean searchThisAreaModeOn = false;
    public boolean checkingAround = false;

    private Bundle bundleForUpdates;// Carry information from activity about changed nearby places and current location
    private boolean searchedAroundCurrentLocation = true;

    @Inject @Named("application_preferences") BasicKvStore applicationKvStore;
    @Inject @Named("defaultKvStore") BasicKvStore prefs;
    @Inject @Named("direct_nearby_upload_prefs") JsonKvStore directKvStore;
    @Inject @Named("default_preferences") BasicKvStore defaultKvStore;
    @Inject BookmarkLocationsDao bookmarkLocationDao;
    @Inject ContributionController controller;
    @Inject Gson gson;

    private static final double ZOOM_LEVEL = 14f;

    public NearbyMapFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Timber.d("Nearby map fragment created");

        Bundle bundle = this.getArguments();
        if (bundle != null) {
            String gsonPlaceList = bundle.getString("PlaceList");
            String gsonLatLng = bundle.getString("CurLatLng");
            Type listType = new TypeToken<List<Place>>() {
            }.getType();
            String gsonBoundaryCoordinates = bundle.getString("BoundaryCoord");
            List<Place> placeList = gson.fromJson(gsonPlaceList, listType);
            Type curLatLngType = new TypeToken<fr.free.nrw.commons.location.LatLng>() {
            }.getType();
            Type gsonBoundaryCoordinatesType = new TypeToken<fr.free.nrw.commons.location.LatLng[]>() {}.getType();
            curLatLng = gson.fromJson(gsonLatLng, curLatLngType);
            baseMarkerOptions = NearbyController
                    .loadAttractionsFromLocationToBaseMarkerOptions(curLatLng,
                            placeList,
                            getActivity());
            boundaryCoordinates = gson.fromJson(gsonBoundaryCoordinates, gsonBoundaryCoordinatesType);
        }
        if (curLatLng != null) {
            Mapbox.getInstance(getActivity(),
                    getString(R.string.mapbox_commons_app_token));
            Mapbox.getTelemetry().setUserTelemetryRequestState(false);
        }
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Timber.d("onCreateView called");
        if (curLatLng != null) {
            Timber.d("curLatLng found, setting up map view...");
            setupMapView(savedInstanceState);
        }

        setHasOptionsMenu(false);

        return mapView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.getView().setFocusableInTouchMode(true);
        this.getView().requestFocus();
        this.getView().setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (bottomSheetDetailsBehavior.getState() == BottomSheetBehavior
                        .STATE_EXPANDED) {
                    bottomSheetDetailsBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    return true;
                } else if (bottomSheetDetailsBehavior.getState() == BottomSheetBehavior
                        .STATE_COLLAPSED) {
                    bottomSheetDetailsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                    mapView.getMapAsync(MapboxMap::deselectMarkers);
                    selected = null;
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * Updates map slightly means it doesn't updates all nearby markers around. It just updates
     * location tracker marker of user.
     */
    public void updateMapSlightly() {
        Timber.d("updateMapSlightly called, bundle is:"+ bundleForUpdates);
        if (mapboxMap != null) {
            if (bundleForUpdates != null) {
                String gsonLatLng = bundleForUpdates.getString("CurLatLng");
                Type curLatLngType = new TypeToken<fr.free.nrw.commons.location.LatLng>() {}.getType();
                curLatLng = gson.fromJson(gsonLatLng, curLatLngType);
            }
            updateMapToTrackPosition();
        }

    }

    /**
     * Updates map significantly means it updates nearby markers and location tracker marker. It is
     * called when user is out of boundaries (south, north, east or west) of markers drawn by
     * previous nearby call.
     */
    public void updateMapSignificantlyForCurrentLocation() {
        Timber.d("updateMapSignificantlyForCurrentLocation called, bundle is:"+ bundleForUpdates);
        if (mapboxMap != null) {
            if (bundleForUpdates != null) {
                String gsonPlaceList = bundleForUpdates.getString("PlaceList");
                String gsonLatLng = bundleForUpdates.getString("CurLatLng");
                String gsonBoundaryCoordinates = bundleForUpdates.getString("BoundaryCoord");
                Type listType = new TypeToken<List<Place>>() {}.getType();
                List<Place> placeList = gson.fromJson(gsonPlaceList, listType);
                Type curLatLngType = new TypeToken<fr.free.nrw.commons.location.LatLng>() {}.getType();
                Type gsonBoundaryCoordinatesType = new TypeToken<fr.free.nrw.commons.location.LatLng[]>() {}.getType();
                curLatLng = gson.fromJson(gsonLatLng, curLatLngType);
                baseMarkerOptions = NearbyController
                        .loadAttractionsFromLocationToBaseMarkerOptions(curLatLng,
                                placeList,
                                getActivity());
                boundaryCoordinates = gson.fromJson(gsonBoundaryCoordinates, gsonBoundaryCoordinatesType);
            }
            mapboxMap.clear();
            addCurrentLocationMarker(mapboxMap);
            updateMapToTrackPosition();
            // We are trying to find nearby places around our current location, thus custom parameter is nullified
            addNearbyMarkersToMapBoxMap(null);
        }
    }

    /**
     * Will be used for map vew updates for custom locations (ie. with search this area method).
     * Clears the map, adds current location marker, adds nearby markers around custom location,
     * re-enables map gestures which was locked during place load, remove progress bar.
     * @param customLatLng custom location that we will search around
     * @param placeList places around of custom location
     */
    public void updateMapSignificantlyForCustomLocation(fr.free.nrw.commons.location.LatLng customLatLng, List<Place> placeList) {
        List<NearbyBaseMarker> customBaseMarkerOptions =  NearbyController
                .loadAttractionsFromLocationToBaseMarkerOptions(curLatLng, // Curlatlang will be used to calculate distances
                        placeList,
                        getActivity());
        mapboxMap.clear();
        // We are trying to find nearby places around our custom searched area, thus custom parameter is nonnull
        addNearbyMarkersToMapBoxMap(customBaseMarkerOptions);
        addCurrentLocationMarker(mapboxMap);
        // Re-enable mapbox gestures on custom location markers load
        mapboxMap.getUiSettings().setAllGesturesEnabled(true);
        searchThisAreaButtonProgressBar.setVisibility(View.GONE);
    }

    // Only update current position marker and camera view
    private void updateMapToTrackPosition() {

        if (currentLocationMarker != null) {
            LatLng curMapBoxLatLng = new LatLng(curLatLng.getLatitude(),curLatLng.getLongitude());
            ValueAnimator markerAnimator = ObjectAnimator.ofObject(currentLocationMarker, "position",
                    new LatLngEvaluator(), currentLocationMarker.getPosition(),
                    curMapBoxLatLng);
            markerAnimator.setDuration(1000);
            markerAnimator.start();

            List<LatLng> circle = createCircleArray(curLatLng.getLatitude(), curLatLng.getLongitude(),
                    curLatLng.getAccuracy() * 2, 100);
            if (currentLocationPolygonOptions != null){
                mapboxMap.removePolygon(currentLocationPolygonOptions.getPolygon());
                currentLocationPolygonOptions = new PolygonOptions()
                        .addAll(circle)
                        .strokeColor(Color.parseColor("#55000000"))
                        .fillColor(Color.parseColor("#11000000"));
                mapboxMap.addPolygon(currentLocationPolygonOptions);
            }

            // Make camera to follow user on location change
            CameraPosition position ;

            // Do not update camera position is search this area mode on
            if (!searchThisAreaModeOn) {
                if (ViewUtil.isPortrait(getActivity())){
                    position = new CameraPosition.Builder()
                            .target(isBottomListSheetExpanded ?
                                    new LatLng(curMapBoxLatLng.getLatitude()- CAMERA_TARGET_SHIFT_FACTOR_PORTRAIT,
                                            curMapBoxLatLng.getLongitude())
                                    : curMapBoxLatLng ) // Sets the new camera position
                            .zoom(isBottomListSheetExpanded ?
                                    ZOOM_LEVEL // zoom level is fixed when bottom sheet is expanded
                                    :mapboxMap.getCameraPosition().zoom) // Same zoom level
                            .build();
                }else {
                    position = new CameraPosition.Builder()
                            .target(isBottomListSheetExpanded ?
                                    new LatLng(curMapBoxLatLng.getLatitude()- CAMERA_TARGET_SHIFT_FACTOR_LANDSCAPE,
                                            curMapBoxLatLng.getLongitude())
                                    : curMapBoxLatLng ) // Sets the new camera position
                            .zoom(isBottomListSheetExpanded ?
                                    ZOOM_LEVEL // zoom level is fixed when bottom sheet is expanded
                                    :mapboxMap.getCameraPosition().zoom) // Same zoom level
                            .build();
                }

                mapboxMap.animateCamera(CameraUpdateFactory
                        .newCameraPosition(position), 1000);
            }
        }
    }

    /**
     * Initialize all views. TODO: Use bind view instead.
     */
    private void initViews() {
        Timber.d("initViews called");
        bottomSheetList = ((NearbyFragment)getParentFragment()).view.findViewById(R.id.bottom_sheet);
        bottomSheetListBehavior = BottomSheetBehavior.from(bottomSheetList);
        bottomSheetDetails = ((NearbyFragment)getParentFragment()).view.findViewById(R.id.bottom_sheet_details);
        bottomSheetDetailsBehavior = BottomSheetBehavior.from(bottomSheetDetails);
        bottomSheetDetailsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        bottomSheetDetails.setVisibility(View.VISIBLE);

        fabPlus = ((NearbyFragment)getParentFragment()).view.findViewById(R.id.fab_plus);
        fabCamera = ((NearbyFragment)getParentFragment()).view.findViewById(R.id.fab_camera);
        fabGallery = ((NearbyFragment)getParentFragment()).view.findViewById(R.id.fab_gallery);
        fabRecenter = ((NearbyFragment)getParentFragment()).view.findViewById(R.id.fab_recenter);

        fab_open = AnimationUtils.loadAnimation(getParentFragment().getActivity(), R.anim.fab_open);
        fab_close = AnimationUtils.loadAnimation(getParentFragment().getActivity(), R.anim.fab_close);
        rotate_forward = AnimationUtils.loadAnimation(getParentFragment().getActivity(), R.anim.rotate_forward);
        rotate_backward = AnimationUtils.loadAnimation(getParentFragment().getActivity(), R.anim.rotate_backward);

        transparentView = ((NearbyFragment)getParentFragment()).view.findViewById(R.id.transparentView);

        description = ((NearbyFragment)getParentFragment()).view.findViewById(R.id.description);
        title = ((NearbyFragment)getParentFragment()).view.findViewById(R.id.title);
        distance = ((NearbyFragment)getParentFragment()).view.findViewById(R.id.category);
        icon = ((NearbyFragment)getParentFragment()).view.findViewById(R.id.icon);

        wikidataButton = ((NearbyFragment)getParentFragment()).view.findViewById(R.id.wikidataButton);
        wikipediaButton = ((NearbyFragment)getParentFragment()).view.findViewById(R.id.wikipediaButton);
        directionsButton = ((NearbyFragment)getParentFragment()).view.findViewById(R.id.directionsButton);
        commonsButton = ((NearbyFragment)getParentFragment()).view.findViewById(R.id.commonsButton);

        wikidataButtonText = ((NearbyFragment)getParentFragment()).view.findViewById(R.id.wikidataButtonText);
        wikipediaButtonText = ((NearbyFragment)getParentFragment()).view.findViewById(R.id.wikipediaButtonText);
        directionsButtonText = ((NearbyFragment)getParentFragment()).view.findViewById(R.id.directionsButtonText);
        commonsButtonText = ((NearbyFragment)getParentFragment()).view.findViewById(R.id.commonsButtonText);

        bookmarkButton = getActivity().findViewById(R.id.bookmarkButton);
        bookmarkButtonImage = getActivity().findViewById(R.id.bookmarkButtonImage);

        searchThisAreaButton = ((NearbyFragment)getParentFragment()).view.findViewById(R.id.search_this_area_button);
        searchThisAreaButtonProgressBar = ((NearbyFragment)getParentFragment()).view.findViewById(R.id.search_this_area_button_progress_bar);

    }

    /**
     * Sets click listeners of FABs, and 2 bottom sheets
     */
    private void setListeners() {
        fabPlus.setOnClickListener(view -> {
            if (applicationKvStore.getBoolean("login_skipped", false)) {
                // prompt the user to login
                new AlertDialog.Builder(getContext())
                        .setMessage(R.string.login_alert_message)
                        .setPositiveButton(R.string.login, (dialog, which) -> {
                            // logout of the app
                            BaseLogoutListener logoutListener = new BaseLogoutListener();
                            CommonsApplication app = (CommonsApplication) getActivity().getApplication();
                            app.clearApplicationData(getContext(), logoutListener);

                        })
                        .show();
            }else {
                animateFAB(isFabOpen);
            }
        });

        bottomSheetDetails.setOnClickListener(view -> {
            if (bottomSheetDetailsBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetDetailsBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            } else {
                bottomSheetDetailsBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });

        fabRecenter.setOnClickListener(view -> {
            if (curLatLng != null) {
                mapView.getMapAsync(mapboxMap -> {
                    CameraPosition position;

                    if (ViewUtil.isPortrait(getActivity())){
                        position = new CameraPosition.Builder()
                                .target(isBottomListSheetExpanded ?
                                        new LatLng(curLatLng.getLatitude()- CAMERA_TARGET_SHIFT_FACTOR_PORTRAIT,
                                                curLatLng.getLongitude())
                                        : new LatLng(curLatLng.getLatitude(), curLatLng.getLongitude(), 0)) // Sets the new camera position
                                .zoom(isBottomListSheetExpanded ?
                                        ZOOM_LEVEL
                                        :mapboxMap.getCameraPosition().zoom) // Same zoom level
                                .build();
                    }else {
                        position = new CameraPosition.Builder()
                                .target(isBottomListSheetExpanded ?
                                        new LatLng(curLatLng.getLatitude()- CAMERA_TARGET_SHIFT_FACTOR_LANDSCAPE,
                                                curLatLng.getLongitude())
                                        : new LatLng(curLatLng.getLatitude(), curLatLng.getLongitude(), 0)) // Sets the new camera position
                                .zoom(isBottomListSheetExpanded ?
                                        ZOOM_LEVEL
                                        :mapboxMap.getCameraPosition().zoom) // Same zoom level
                                .build();
                    }

                    mapboxMap.animateCamera(CameraUpdateFactory
                            .newCameraPosition(position), 1000);

                });
            }
        });

        bottomSheetDetailsBehavior.setBottomSheetCallback(new BottomSheetBehavior
                .BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                prepareViewsForSheetPosition(newState);
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                if (slideOffset >= 0) {
                    transparentView.setAlpha(slideOffset);
                    if (slideOffset == 1) {
                        transparentView.setClickable(true);
                    } else if (slideOffset == 0) {
                        transparentView.setClickable(false);
                    }
                }
            }
        });

        bottomSheetListBehavior.setBottomSheetCallback(new BottomSheetBehavior
                .BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetDetailsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {

            }
        });

        // Remove button text if they exceed 1 line or if internal layout has not been built
        // Only need to check for directions button because it is the longest
        if (directionsButtonText.getLineCount() > 1 || directionsButtonText.getLineCount() == 0) {
            wikipediaButtonText.setVisibility(View.GONE);
            wikidataButtonText.setVisibility(View.GONE);
            commonsButtonText.setVisibility(View.GONE);
            directionsButtonText.setVisibility(View.GONE);
        }
    }

    /**
     * Sets up map view of first time it created, it passes MapBoxMap options and style assets.
     * @param savedInstanceState bundle coming from Nearby Fragment
     */
    private void setupMapView(Bundle savedInstanceState) {
        Timber.d("setupMapView called");
        MapboxMapOptions options = new MapboxMapOptions()
                .compassGravity(Gravity.BOTTOM | Gravity.LEFT)
                .compassMargins(new int[]{12, 0, 0, 24})
                .styleUrl(Style.OUTDOORS)
                .logoEnabled(false)
                .attributionEnabled(false)
                .camera(new CameraPosition.Builder()
                        .target(new LatLng(curLatLng.getLatitude(), curLatLng.getLongitude()))
                        .zoom(ZOOM_LEVEL)
                        .build());

        if (!getParentFragment().getActivity().isFinishing()) {
            mapView = new MapView(getParentFragment().getActivity(), options);
            // create map
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(mapboxMap -> {
                NearbyMapFragment.this.mapboxMap = mapboxMap;
                addMapMovementListeners();
                updateMapSignificantlyForCurrentLocation();
            });
            mapView.setStyleUrl("asset://mapstyle.json");
        }
    }

    /**
     * Adds map movement listener to understand swiping with fingers. So that we can display search
     * this area button to search nearby places for other locations
     */
    private void addMapMovementListeners() {

        mapboxMap.addOnCameraMoveListener(() -> {

            if (NearbyController.currentLocation != null) { // If our nearby markers are calculated at least once

                if (searchThisAreaButton.getVisibility() == View.GONE) {
                    searchThisAreaButton.setVisibility(View.VISIBLE);
                }
                double distance = mapboxMap.getCameraPosition().target
                        .distanceTo(new LatLng(NearbyController.currentLocation.getLatitude()
                                , NearbyController.currentLocation.getLongitude()));

                if (distance > NearbyController.searchedRadius*1000*3/4) { //Convert to meter, and compare if our distance is bigger than 3/4 or our searched area
                    checkingAround = true;
                    if (!searchThisAreaModeOn) { // If we are changing mode, then change click action
                        searchThisAreaModeOn = true;
                        searchThisAreaButton.setOnClickListener(view -> {
                            searchThisAreaModeOn = true;
                            // Lock map operations during search this area operation
                            mapboxMap.getUiSettings().setAllGesturesEnabled(false);
                            searchThisAreaButtonProgressBar.setVisibility(View.VISIBLE);
                            searchThisAreaButton.setVisibility(View.GONE);
                            searchedAroundCurrentLocation = false;
                            ((NearbyFragment)getParentFragment())
                                    .refreshViewForCustomLocation(LocationUtils
                                            .mapBoxLatLngToCommonsLatLng(mapboxMap.getCameraPosition().target), false);
                        });
                    }

                } else {
                    checkingAround = false;
                    if (searchThisAreaModeOn) {
                        searchThisAreaModeOn = false; // This flag will help us to understand should we folor users location or not
                        searchThisAreaButton.setOnClickListener(view -> {
                            searchThisAreaModeOn = true;
                            // Lock map operations during search this area operation
                            mapboxMap.getUiSettings().setAllGesturesEnabled(false);
                            searchThisAreaButtonProgressBar.setVisibility(View.GONE);
                            fabRecenter.callOnClick();
                            searchThisAreaButton.setVisibility(View.GONE);
                            searchedAroundCurrentLocation = true;
                            ((NearbyFragment)getParentFragment())
                                    .refreshViewForCustomLocation(LocationUtils
                                            .mapBoxLatLngToCommonsLatLng(mapboxMap.getCameraPosition().target), true);
                        });
                    }
                    if (searchedAroundCurrentLocation) {
                        searchThisAreaButton.setVisibility(View.GONE);
                    }
                }
            }
        });
    }

    /**
     * onLogoutComplete is called after shared preferences and data stored in local database are cleared.
     */
    private class BaseLogoutListener implements CommonsApplication.LogoutListener {
        @Override
        public void onLogoutComplete() {
            Timber.d("Logout complete callback received.");
            Intent nearbyIntent = new Intent( getActivity(), LoginActivity.class);
            nearbyIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            nearbyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(nearbyIntent);
            getActivity().finish();
        }
    }

    /**
     * Adds a marker for the user's current position. Adds a
     * circle which uses the accuracy * 2, to draw a circle
     * which represents the user's position with an accuracy
     * of 95%.
     *
     * Should be called only on creation of mapboxMap, there
     * is other method to update markers location with users
     * move.
     */
    private void addCurrentLocationMarker(MapboxMap mapboxMap) {
        Timber.d("addCurrentLocationMarker is called");
        if (currentLocationMarker != null) {
            currentLocationMarker.remove(); // Remove previous marker, we are not Hansel and Gretel
        }

        Icon icon = IconFactory.getInstance(getContext()).fromResource(R.drawable.current_location_marker);

        MarkerOptions currentLocationMarkerOptions = new MarkerOptions()
                .position(new LatLng(curLatLng.getLatitude(), curLatLng.getLongitude()));
        currentLocationMarkerOptions.setIcon(icon); // Set custom icon

        currentLocationMarker = mapboxMap.addMarker(currentLocationMarkerOptions);

        List<LatLng> circle = createCircleArray(curLatLng.getLatitude(), curLatLng.getLongitude(),
                curLatLng.getAccuracy() * 2, 100);

        currentLocationPolygonOptions = new PolygonOptions()
                .addAll(circle)
                .strokeColor(Color.parseColor("#55000000"))
                .fillColor(Color.parseColor("#11000000"));
        mapboxMap.addPolygon(currentLocationPolygonOptions);
    }

    /**
     * Checks whether current location marker is in visible region or not
     * @return true if point is in region
     */
    public boolean isCurrentLocationMarkerVisible() {
        if (currentLocationMarker != null) {
            return mapboxMap.getProjection().getVisibleRegion().latLngBounds.contains(currentLocationMarker.getPosition());
        } else {
            return false;
        }
    }

    /**
     * Adds markers for nearby places to mapbox map
     */
    private void addNearbyMarkersToMapBoxMap(@Nullable List<NearbyBaseMarker> customNearbyBaseMarker) {
        List<NearbyBaseMarker> baseMarkerOptions;
        Timber.d("addNearbyMarkersToMapBoxMap is called");
        if (customNearbyBaseMarker != null) {
            // If we try to update nearby points for a custom location choosen from map (we are not there)
            baseMarkerOptions = customNearbyBaseMarker;
        } else {
            // If we try to display nearby markers around our curret location
            baseMarkerOptions = this.baseMarkerOptions;
        }
        mapboxMap.addMarkers(baseMarkerOptions);
        mapboxMap.setOnInfoWindowCloseListener(marker -> {
            if (marker == selected) {
                bottomSheetDetailsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            }
        });
        mapView.getMapAsync(mapboxMap -> {
            mapboxMap.addMarkers(baseMarkerOptions);
            fabRecenter.setVisibility(View.VISIBLE);
            mapboxMap.setOnInfoWindowCloseListener(marker -> {
                if (marker == selected) {
                    bottomSheetDetailsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                }
            });

            mapboxMap.setOnMarkerClickListener(marker -> {

                if (marker instanceof NearbyMarker) {
                    this.selected = marker;
                    NearbyMarker nearbyMarker = (NearbyMarker) marker;
                    Place place = nearbyMarker.getNearbyBaseMarker().getPlace();
                    passInfoToSheet(place);
                    bottomSheetListBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                    bottomSheetDetailsBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

                }
                return false;
            });

        });
    }


    /**
     * Creates a series of points that create a circle on the map.
     * Takes the center latitude, center longitude of the circle,
     * the radius in meter and the number of nodes of the circle.
     *
     * @return List List of LatLng points of the circle.
     */
    private List<LatLng> createCircleArray(
            double centerLat, double centerLong, float radius, int nodes) {
        List<LatLng> circle = new ArrayList<>();
        float radiusKilometer = radius / 1000;
        double radiusLong = radiusKilometer
                / (111.320 * Math.cos(centerLat * Math.PI / 180));
        double radiusLat = radiusKilometer / 110.574;

        for (int i = 0; i < nodes; i++) {
            double theta = ((double) i / (double) nodes) * (2 * Math.PI);
            double nodeLongitude = centerLong + radiusLong * Math.cos(theta);
            double nodeLatitude = centerLat + radiusLat * Math.sin(theta);
            circle.add(new LatLng(nodeLatitude, nodeLongitude));
        }
        return circle;
    }

    /**
     * If nearby details bottom sheet state is collapsed: show fab plus
     * If nearby details bottom sheet state is expanded: show fab plus
     * If nearby details bottom sheet state is hidden: hide all fabs
     * @param bottomSheetState
     */
    public void prepareViewsForSheetPosition(int bottomSheetState) {

        switch (bottomSheetState) {
            case (BottomSheetBehavior.STATE_COLLAPSED):
                closeFabs(isFabOpen);
                if (!fabPlus.isShown()) showFAB();
                this.getView().requestFocus();
                break;
            case (BottomSheetBehavior.STATE_EXPANDED):
                this.getView().requestFocus();
                break;
            case (BottomSheetBehavior.STATE_HIDDEN):
                mapView.getMapAsync(MapboxMap::deselectMarkers);
                transparentView.setClickable(false);
                transparentView.setAlpha(0);
                closeFabs(isFabOpen);
                hideFAB();
                if (this.getView() != null) {
                    this.getView().requestFocus();
                }
                break;
        }
    }

    /**
     * Hides all fabs
     */
    private void hideFAB() {

        removeAnchorFromFABs(fabPlus);
        fabPlus.hide();

        removeAnchorFromFABs(fabCamera);
        fabCamera.hide();

        removeAnchorFromFABs(fabGallery);
        fabGallery.hide();

    }

    /*
     * We are not able to hide FABs without removing anchors, this method removes anchors
     * */
    private void removeAnchorFromFABs(FloatingActionButton floatingActionButton) {
        //get rid of anchors
        //Somehow this was the only way https://stackoverflow.com/questions/32732932
        // /floatingactionbutton-visible-for-sometime-even-if-visibility-is-set-to-gone
        CoordinatorLayout.LayoutParams param = (CoordinatorLayout.LayoutParams) floatingActionButton
                .getLayoutParams();
        param.setAnchorId(View.NO_ID);
        // If we don't set them to zero, then they become visible for a moment on upper left side
        param.width = 0;
        param.height = 0;
        floatingActionButton.setLayoutParams(param);
    }

    private void showFAB() {

        addAnchorToBigFABs(fabPlus, ((NearbyFragment)getParentFragment()).view.findViewById(R.id.bottom_sheet_details).getId());
        fabPlus.show();

        addAnchorToSmallFABs(fabGallery, ((NearbyFragment)getParentFragment()).view.findViewById(R.id.empty_view).getId());

        addAnchorToSmallFABs(fabCamera, ((NearbyFragment)getParentFragment()).view.findViewById(R.id.empty_view1).getId());
    }


    /*
     * Add anchors back before making them visible again.
     * */
    private void addAnchorToBigFABs(FloatingActionButton floatingActionButton, int anchorID) {
        CoordinatorLayout.LayoutParams params = new CoordinatorLayout.LayoutParams
                (ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setAnchorId(anchorID);
        params.anchorGravity = Gravity.TOP|Gravity.RIGHT|Gravity.END;
        floatingActionButton.setLayoutParams(params);
    }

    /*
     * Add anchors back before making them visible again. Big and small fabs have different anchor
     * gravities, therefore the are two methods.
     * */
    private void addAnchorToSmallFABs(FloatingActionButton floatingActionButton, int anchorID) {
        CoordinatorLayout.LayoutParams params = new CoordinatorLayout.LayoutParams
                (ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setAnchorId(anchorID);
        params.anchorGravity = Gravity.CENTER_HORIZONTAL;
        floatingActionButton.setLayoutParams(params);
    }

    /**
     * Same botom sheet carries information for all nearby places, so we need to pass information
     * (title, description, distance and links) to view on nearby marker click
     * @param place Place of clicked nearby marker
     */
    private void passInfoToSheet(Place place) {
        this.place = place;

        int bookmarkIcon;
        if (bookmarkLocationDao.findBookmarkLocation(place)) {
            bookmarkIcon = R.drawable.ic_round_star_filled_24px;
        } else {
            bookmarkIcon = R.drawable.ic_round_star_border_24px;
        }
        bookmarkButtonImage.setImageResource(bookmarkIcon);
        bookmarkButton.setOnClickListener(view -> {
            boolean isBookmarked = bookmarkLocationDao.updateBookmarkLocation(place);
            int updatedIcon = isBookmarked ? R.drawable.ic_round_star_filled_24px : R.drawable.ic_round_star_border_24px;
            bookmarkButtonImage.setImageResource(updatedIcon);
        });

        wikipediaButton.setEnabled(place.hasWikipediaLink());
        wikipediaButton.setOnClickListener(view -> openWebView(place.siteLinks.getWikipediaLink()));

        wikidataButton.setEnabled(place.hasWikidataLink());
        wikidataButton.setOnClickListener(view -> openWebView(place.siteLinks.getWikidataLink()));

        directionsButton.setOnClickListener(view -> {
            //Open map app at given position
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, this.place.location.getGmmIntentUri());
            if (mapIntent.resolveActivity(getActivity().getPackageManager()) != null) {
                startActivity(mapIntent);
            }
        });

        commonsButton.setEnabled(place.hasCommonsLink());
        commonsButton.setOnClickListener(view -> openWebView(place.siteLinks.getCommonsLink()));

        icon.setImageResource(place.getLabel().getIcon());

        title.setText(place.name);
        distance.setText(place.distance);
        description.setText(place.getLongDescription());
        title.setText(place.name.toString());
        distance.setText(place.distance.toString());

        fabCamera.setOnClickListener(view -> {
            if (fabCamera.isShown()) {
                Timber.d("Camera button tapped. Place: %s", place.toString());
                storeSharedPrefs();
                controller.initiateCameraPick(getActivity(), NEARBY_CAMERA_UPLOAD_REQUEST_CODE);
            }
        });

        fabGallery.setOnClickListener(view -> {
            if (fabGallery.isShown()) {
                Timber.d("Gallery button tapped. Place: %s", place.toString());
                storeSharedPrefs();
                controller.initiateGalleryPick(getActivity(), NEARBY_UPLOAD_IMAGE_LIMIT, NEARBY_GALLERY_UPLOAD_REQUEST_CODE);
            }
        });
    }

    void storeSharedPrefs() {
        Timber.d("Store place object %s", place.toString());
        directKvStore.putJson(PLACE_OBJECT, place);
    }

    private void openWebView(Uri link) {
        Utils.handleWebUrl(getContext(), link);
    }

    /**
     * Starts animation of fab plus (turning on opening) and other FABs
     * @param isFabOpen state of FAB buttons, open when clicked on fab button, closed on other click
     */
    private void animateFAB(boolean isFabOpen) {
        this.isFabOpen = !isFabOpen;
        if (fabPlus.isShown()){
            if (isFabOpen) {
                fabPlus.startAnimation(rotate_backward);
                fabCamera.startAnimation(fab_close);
                fabGallery.startAnimation(fab_close);
                fabCamera.hide();
                fabGallery.hide();
            } else {
                fabPlus.startAnimation(rotate_forward);
                fabCamera.startAnimation(fab_open);
                fabGallery.startAnimation(fab_open);
                fabCamera.show();
                fabGallery.show();
            }
            this.isFabOpen=!isFabOpen;
        }
    }

    /**
     * Hides camera and gallery FABs, turn back plus FAB
     * @param isFabOpen
     */
    private void closeFabs ( boolean isFabOpen){
        if (isFabOpen) {
            fabPlus.startAnimation(rotate_backward);
            fabCamera.startAnimation(fab_close);
            fabGallery.startAnimation(fab_close);
            fabCamera.hide();
            fabGallery.hide();
            this.isFabOpen = !isFabOpen;
        }
    }

    /**
     * This bundle is sent whenever and update for nearby map comes, not for recreation, for updates
     */
    public void setBundleForUpdates(Bundle bundleForUpdates) {
        this.bundleForUpdates = bundleForUpdates;
    }

    @Override
    public void onStart() {
        if (mapView != null) {
            mapView.onStart();
        }
        super.onStart();
    }

    @Override
    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        if (mapboxMap != null) {
            mapboxMap.getUiSettings().setAllGesturesEnabled(true);
        }
        searchThisAreaModeOn = false;
        checkingAround = false;
        searchedAroundCurrentLocation = true;
        boundaryCoordinates = null;
        initViews();
        setListeners();
        transparentView.setClickable(false);
        transparentView.setAlpha(0);
    }

    @Override
    public void onStop() {
        if (mapView != null) {
            mapView.onStop();
        }
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (mapView != null) {
            mapView.onDestroy();
        }
        selected = null;
        currentLocationMarker = null;

        super.onDestroyView();
    }

    private static class LatLngEvaluator implements TypeEvaluator<LatLng> {
        // Method is used to interpolate the marker animation.
        private LatLng latLng = new LatLng();

        @Override
        public LatLng evaluate(float fraction, LatLng startValue, LatLng endValue) {
            latLng.setLatitude(startValue.getLatitude()
                    + ((endValue.getLatitude() - startValue.getLatitude()) * fraction));
            latLng.setLongitude(startValue.getLongitude()
                    + ((endValue.getLongitude() - startValue.getLongitude()) * fraction));
            return latLng;
        }
    }
}
