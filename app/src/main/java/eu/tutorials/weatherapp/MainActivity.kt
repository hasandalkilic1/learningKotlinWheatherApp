package eu.tutorials.weatherapp

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.location.LocationRequest.Builder
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.LocaleListCompat.create
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import java.net.URI.create


class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient:FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mFusedLocationClient= LocationServices.getFusedLocationProviderClient(this)

        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Your location provider is turned off. Please turn it on.",
                Toast.LENGTH_SHORT
            ).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withActivity(this)
                .withPermissions(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object :MultiplePermissionsListener{

                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()){
                            requestLocationData()
                        }
                        if (report.isAnyPermissionPermanentlyDenied){
                            Toast.makeText(this@MainActivity,
                            "You have denied location permission.Please turn it on",
                                Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread().check()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {

        val mLocationRequest = com.google.android.gms.location.LocationRequest()
        mLocationRequest.priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback=object :LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location =locationResult.lastLocation!!
            val latitude=mLastLocation.latitude
            Log.i("Current Latitude","$latitude")

            val longitude=mLastLocation.longitude
            Log.i("Current Longitude","$longitude")
            getLocationWeatherDetail()
        }
    }

    private fun getLocationWeatherDetail(){
        if(Constants.isNetworkAvailable(this)){
            Toast.makeText(this,"You have connected to Internet",Toast.LENGTH_SHORT).show()
        }
        else{
            Toast.makeText(this,"No Internet Connection",Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRationalDialogForPermissions(){
        AlertDialog.Builder(this)
            .setMessage("It looks like you have turned off permissions required for this feature. It can be enabled under Application settings")
            .setPositiveButton("GO TO SETTINGS"){_,_ ->
                try {
                    val intent=Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri=Uri.fromParts("package",packageName,null)
                    intent.data=uri
                    startActivity(intent)
                }catch (e:ActivityNotFoundException){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel"){dialog,_ ->
                dialog.dismiss()
            }.show()
    }

    private fun isLocationEnabled(): Boolean {

        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }
}