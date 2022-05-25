package com.udacity.project4.locationreminders.savereminder.selectreminderlocation

import android.Manifest
import android.content.IntentSender
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.navigation.fragment.findNavController
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.databinding.FragmentSelectLocationBinding
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import org.koin.android.ext.android.inject
import java.util.*

private const val TAG = "SaveReminderFragment"
private const val REQUEST_TURN_DEVICE_LOCATION_ON = 29

class SelectLocationFragment : BaseFragment(), OnMapReadyCallback {

    // Use Koin to get the view model of the SaveReminder
    override val _viewModel: SaveReminderViewModel by inject()
    private lateinit var binding: FragmentSelectLocationBinding
    private lateinit var map: GoogleMap

    private val FINE_LOCATION_ACCESS_REQUEST_CODE = 1

    private val DEFAULT_ZOOM = 17f
    private val defaultLocation = LatLng(44.97974325243857, 10.709856046507827)
    private lateinit var lastKnownLocation: Location
    private lateinit var selectedMarker: Marker

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_select_location, container, false)

        binding.viewModel = _viewModel
        binding.lifecycleOwner = this

        setHasOptionsMenu(true)
        setDisplayHomeAsUpEnabled(true)


        checkDeviceLocationSettings()
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.saveButton.setOnClickListener {
            onLocationSelected()
        }

        return binding.root
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        setMapStyle()
        setMapLongClick()
        setPoiClick()
        enableUserLocation()
    }

    private fun setPoiClick() {
        map.setOnPoiClickListener {
            map.clear()
            selectedMarker = map.addMarker(
                MarkerOptions()
                    .position(it.latLng)
                    .title(it.name)
            )
            selectedMarker.showInfoWindow()
        }
    }

    private fun setMapLongClick() {
        map.setOnMapLongClickListener {
            map.clear()
            val snippet = String.format(
                Locale.getDefault(),
                "Lat: %1$.5f, Long: %2$.5f",
                it.latitude,
                it.longitude
            )
            selectedMarker = map.addMarker(
                MarkerOptions()
                    .position(it)
                    .title(snippet)
                    .snippet(snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE))
            )
            selectedMarker.showInfoWindow()
        }
    }

    private fun setMapStyle() {
        try {
            val success = map.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    requireContext(),
                    R.raw.map_style
                )
            )
            if (!success) {
                Log.e(TAG, "Style parsing failed.")
            }
        } catch (e: Resources.NotFoundException) {
            Log.e(TAG, "Can't find style. Error: ", e)
        }
    }

    private fun enableUserLocation() {
        val fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireContext())
        when {
            (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) -> {

                // You can use the API that requires the permission.
                map.isMyLocationEnabled = true

                fusedLocationProviderClient.lastLocation.addOnSuccessListener(requireActivity()) { location ->
                    // Got last known location. In some rare situations this can be null.
                    if (location != null) {
                        lastKnownLocation = location
                        val currentLatLng = LatLng(location.latitude, location.longitude)
                        val markerOptions = MarkerOptions().position(currentLatLng)
                        selectedMarker = map.addMarker(markerOptions)
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 12f))
                    }
                }
//                Toast.makeText(context, "Location permission is granted.", Toast.LENGTH_LONG).show()
            }
            (ActivityCompat.shouldShowRequestPermissionRationale(
                requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION
            )) -> {
                // Explain why you need the permission
                // Add dialog
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    FINE_LOCATION_ACCESS_REQUEST_CODE
                )
            }

            else ->
                //Request permission
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    FINE_LOCATION_ACCESS_REQUEST_CODE
                )
        }

    }

    private fun onLocationSelected() {
        if (this::selectedMarker.isInitialized) {
            _viewModel.latitude.value = selectedMarker.position.latitude
            _viewModel.longitude.value = selectedMarker.position.longitude
            _viewModel.reminderSelectedLocationStr.value = selectedMarker.title ?: String.format(
                Locale.getDefault(),
                "Lat: %1$.5f, Long: %2$.5f",
                selectedMarker.position.latitude,
                selectedMarker.position.longitude
            )
            Log.d(TAG, "onLocationSelected: ${selectedMarker.title}")
            findNavController().popBackStack()
        } else {
            val toast = Toast.makeText(
                context,
                resources.getString(R.string.select_location),
                Toast.LENGTH_SHORT
            )
            toast.show()
        }
    }

    private fun checkDeviceLocationSettings(resolve: Boolean = true) {
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_LOW_POWER
        }
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)

        val settingsClient = LocationServices.getSettingsClient(requireContext())
        val locationSettingsResponseTask =
            settingsClient.checkLocationSettings(builder.build())

        locationSettingsResponseTask.addOnFailureListener { exception ->
            if (exception is ResolvableApiException && resolve) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    exception.startResolutionForResult(
                        activity,
                        REQUEST_TURN_DEVICE_LOCATION_ON
                    )
                } catch (sendEx: IntentSender.SendIntentException) {
                    Log.d(TAG, "Error getting location settings resolution: " + sendEx.message)
                }
            } else {
                Snackbar.make(
                    this.requireView(),
                    R.string.location_required_error, Snackbar.LENGTH_INDEFINITE
                ).setAction(android.R.string.ok) {
                    checkDeviceLocationSettings()
                }.show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Check if location permissions are granted and if so enable the location

        when (requestCode) {
            FINE_LOCATION_ACCESS_REQUEST_CODE -> {

                if (grantResults.isNotEmpty() && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission is granted. Continue...
                    enableUserLocation()

                } else {
                    // Explain to the user that the feature is unavailable because
                    // the features requires a permission that the user has denied.
                    // At the same time, respect the user's decision. Don't link to
                    // system settings in an effort to convince the user to change
                    // their decision.
                    Snackbar.make(
                        binding.root,
                        R.string.location_required_error, Snackbar.LENGTH_INDEFINITE
                    ).setAction(android.R.string.ok) {
                        requestPermissions(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                            FINE_LOCATION_ACCESS_REQUEST_CODE
                        )
                    }.show()
                }

            }

        }

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.map_options, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.normal_map -> {
            map.mapType = GoogleMap.MAP_TYPE_NORMAL
            true
        }
        R.id.hybrid_map -> {
            map.mapType = GoogleMap.MAP_TYPE_HYBRID
            true
        }
        R.id.satellite_map -> {
            map.mapType = GoogleMap.MAP_TYPE_SATELLITE
            true
        }
        R.id.terrain_map -> {
            map.mapType = GoogleMap.MAP_TYPE_TERRAIN
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun isPermissionGranted(): Boolean {
        return context?.let {
            ContextCompat.checkSelfPermission(
                it,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } == PackageManager.PERMISSION_GRANTED
    }
}
