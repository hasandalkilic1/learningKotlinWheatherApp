package eu.tutorials.weatherapp

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import eu.tutorials.weatherapp.models.WeatherResponse
import eu.tutorials.weatherapp.network.WeatherService
import kotlinx.android.synthetic.main.activity_main.*
import retrofit.*
import java.text.SimpleDateFormat
import java.time.ZoneOffset.UTC
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient:FusedLocationProviderClient

    private var mProgressDialog:Dialog?=null

    private var mLatitude: Double = 0.0
    private var mLongitude: Double = 0.0

    private lateinit var mSharedPreferences:SharedPreferences


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mFusedLocationClient= LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences=getSharedPreferences(Constants.PREFERENCE_NAME,Context.MODE_PRIVATE)

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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        return super.onCreateOptionsMenu(menu)
    }
    //It's not necessary after the LocationRequest Update
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh->{
                getLocationWeatherDetails()
                true
            }
            else-> return super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLR=LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,5000L).build()
        //Old LocationRequest method.
        /*var mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY*/


        mFusedLocationClient.requestLocationUpdates(mLR, mLocationCallback, Looper.myLooper())
    }

    private val mLocationCallback=object :LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location =locationResult.lastLocation!!
            mLatitude=mLastLocation.latitude
            Log.i("Current Latitude","$mLatitude")

            mLongitude=mLastLocation.longitude
            Log.i("Current Longitude","$mLongitude")
            getLocationWeatherDetails()
        }
    }

    private fun getLocationWeatherDetails(){
        if(Constants.isNetworkAvailable(this)){
            val retrofit:Retrofit=Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service:WeatherService=retrofit.create<WeatherService>(WeatherService::class.java)

            val listCall:Call<WeatherResponse> = service.getWeather(
                mLatitude,mLongitude,Constants.METRIC_UNIT,Constants.APP_ID
            )
            //Showing Progress Bar
            //showCustomProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse> {
                @SuppressLint("SetTextI18n")
                override fun onResponse(
                    response: Response<WeatherResponse>,
                    retrofit: Retrofit
                ) {
                    if (response.isSuccess) {
                        //Hiding Progress Bar
                        hideProgressDialog()

                        val weatherList: WeatherResponse = response.body()

                        val weatherResponseJsonString=Gson().toJson(weatherList)
                        val editor=mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA,weatherResponseJsonString)
                        editor.apply()

                        setupUI()
                        Log.i("Response Result", "$weatherList")
                    } else {
                        val sc = response.code()
                        when (sc) {
                            400 -> {
                                Log.e("Error 400", "Bad Request")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                            }
                            else -> {
                                Log.e("Error", "Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(t: Throwable) {
                    Log.e("Errorrrrr", t.message.toString())
                    //Hiding Progress Bar
                    hideProgressDialog()
                }
            })
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

    private fun showCustomProgressDialog(){
        mProgressDialog= Dialog(this)

        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)

        mProgressDialog!!.show()
    }

    private fun hideProgressDialog(){
        if (mProgressDialog!=null){
            mProgressDialog!!.dismiss()
        }
    }

    private fun setupUI(){

        val weatherResponseJsonString=mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")

        if (!weatherResponseJsonString.isNullOrEmpty()){

            val weatherList=Gson().fromJson(weatherResponseJsonString,WeatherResponse::class.java)

            for (i in weatherList.weather.indices){
                Log.i("Weather Name",weatherList.weather.toString())

                tv_main.text=weatherList.weather[i].main
                tv_main_description.text=weatherList.weather[i].description
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    tv_temp.text=weatherList.main.temp.toString()+getUnit(application.resources.configuration.locales.toString())
                }
                else{
                    tv_temp.text="It do not be able to shown because of device's android version is old!"
                }
                tv_humidity.text=weatherList.main.humidity.toString()+" per cent"
                tv_min.text=weatherList.main.temp_min.toString()+" min"
                tv_max.text=weatherList.main.temp_max.toString()+" max"
                tv_speed.text=weatherList.wind.speed.toString()
                tv_name.text=weatherList.name
                tv_country.text=weatherList.sys.country

                tv_sunrise_time.text=unixTime(weatherList.sys.sunrise)
                tv_sunset_time.text=unixTime(weatherList.sys.sunset)

                when(weatherList.weather[i].icon){
                    "01d"->iv_main.setImageResource(R.drawable.sunny)
                    "02d"->iv_main.setImageResource(R.drawable.cloud)
                    "03d"->iv_main.setImageResource(R.drawable.cloud)
                    "04d"->iv_main.setImageResource(R.drawable.cloud)
                    "04n"->iv_main.setImageResource(R.drawable.cloud)
                    "10d"->iv_main.setImageResource(R.drawable.rain)
                    "11d"->iv_main.setImageResource(R.drawable.storm)
                    "13d"->iv_main.setImageResource(R.drawable.snowflake)
                    "01n"->iv_main.setImageResource(R.drawable.cloud)
                    "02n"->iv_main.setImageResource(R.drawable.cloud)
                    "03n"->iv_main.setImageResource(R.drawable.cloud)
                    "10n"->iv_main.setImageResource(R.drawable.cloud)
                    "11n"->iv_main.setImageResource(R.drawable.rain)
                    "13n"->iv_main.setImageResource(R.drawable.snowflake)
                }
            }
        }


    }

    private fun getUnit(value:String):String?{
        var value="°C"

        if("US"==value||"LR"== value||"MM"==value){
            value="°F"

        }
        return value
    }

    private fun unixTime(timex:Long):String{
        //This format working for GMC+3. You can change it according to your country
        val date= Date((timex*1000L+10800000L))
        val sdf=SimpleDateFormat("HH:mm")
        sdf.timeZone= TimeZone.getDefault()

        return sdf.format(date)
    }
}