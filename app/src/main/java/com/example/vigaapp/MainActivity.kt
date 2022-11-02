package com.example.vigaapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.*
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*


private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val LOCATION_PERMISSION_REQUEST_CODE = 2
private const val BLUETOOTH_ALL_PERMISSIONS_REQUEST_CODE = 3
private const val SERVICE_UUID = "91bad492-b950-4226-aa2b-4ede9fa42f59"

private const val UUID_MAC = "04dc58aa-29a4-4928-b24a-359a35d4686f"
private const val UUID_WIFI_STATUS = "1fe56467-6777-47b7-a62c-9f89dbddc2b2"
private const val UUID_RSSI_WIFI = "6a5dcaa0-ee39-46ad-a63d-2dff8fff36dc"
private const val UUID_SSID_WIFI = "f24d8a50-47ef-4994-8c47-a196a517491f"
private const val UUID_IP_WIFI = "87278349-25ae-46e8-ae5f-b52f238f8e8a"
private const val UUID_FECHA_VIBOT = "d9fa2f3c-8fac-4807-9d58-c473fa3dca8b"
private const val UUID_N_VICONS = "54448631-d194-4284-a4a7-7a262c6ae158"
private const val UUID_PENDIENTES = "c64ac8f5-7697-4a68-bb56-663b204b83cb"
private const val UUID_VERSION = "8923c96a-8ac0-4754-b872-ec4519830c23"
private const val UUID_BATERIA = "55bba6fa-b578-4962-8458-f04dcf8a7cb5"

private const val CHAR_FOR_WRITE_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8"
private const val CHAR_FOR_INDICATE_UUID = "25AE1444-05D3-4C5B-8281-93D4E07420CF"
private const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb"


class MainActivity : AppCompatActivity() {
    enum class BLELifecycleState {
        Disconnected,
        Scanning,
        Connecting,
        ConnectedDiscovering,
        ConnectedSubscribing,
        Connected
    }
    private var job: Job? = null

    private var lifecycleState = BLELifecycleState.Disconnected
        set(value) {
            field = value
            appendLog("status = $value")
            runOnUiThread {
//                textViewLifecycleState.text = "State: ${value.name}"
//                if (value != BLELifecycleState.Connected) {
//                    textViewSubscription.text = getString(R.string.text_not_subscribed)
//                }
            }
        }

//    private val spinner: Spinner
//        get() = findViewById<Spinner>(R.id.spinner)

    private val sendCredentials: Button
    get() = findViewById<Button>(R.id.send_credentials)
    private val desconectarBT: Button
    get() = findViewById<Button>(R.id.desconectar)
    private val setHora: Button
        get() = findViewById<Button>(R.id.hora_button)
    private val showPass: Button
        get() = findViewById<Button>(R.id.showPass)
    private val scanBT: Button
        get() = findViewById<Button>(R.id.scan_button)
    private val text_id_vibot: TextView
        get() = findViewById<TextView>(R.id.id_vibot)
    private val text_rssi_bt: TextView
        get() = findViewById<TextView>(R.id.rssi_bt)
    private val view_state_bt: ImageView
        get() = findViewById<ImageView>(R.id.state_bt)

    private val wifi_status: TextView
        get() = findViewById<TextView>(R.id.wifi_status)
    private val rssi_wifi: TextView
        get() = findViewById<TextView>(R.id.rssi_wifi)
    private val mac_vibot: TextView
        get() = findViewById<TextView>(R.id.mac_vibot)
    private val ip_wifi: TextView
        get() = findViewById<TextView>(R.id.ip_wifi)
    private val ssid_wifi: TextView
        get() = findViewById<TextView>(R.id.ssid_wifi)
    private val fecha_vibot: TextView
        get() = findViewById<TextView>(R.id.fecha_vibot)
    private val bateria_vibot: TextView
        get() = findViewById<TextView>(R.id.bateria_vibot)
    private val pendientes: TextView
        get() = findViewById<TextView>(R.id.pendientes)
    private val n_vicons: TextView
        get() = findViewById<TextView>(R.id.n_vicons)
    private val version_vibot: TextView
        get() = findViewById<TextView>(R.id.version_vibot)

    private val inputSSID: EditText
        get() = findViewById<EditText>(R.id.input_SSID)
    private val inputPASS: EditText
        get() = findViewById<EditText>(R.id.input_pass)

    private val inputUSERNAME: EditText
        get() = findViewById<EditText>(R.id.input_username)
    private val inputPASSWORD: EditText
        get() = findViewById<EditText>(R.id.input_password)

    private val spinerSeguridad: Spinner
        get() = findViewById<Spinner>(R.id.spinner_wifi)
//    private val textViewIndicateValue: TextView
//        get() = findViewById<TextView>(R.id.textViewIndicateValue)
//    private val textViewSubscription: TextView
//        get() = findViewById<TextView>(R.id.textViewSubscription)
//    private val textViewLog: TextView
//        get() = findViewById<TextView>(R.id.textViewLog)
//    private val scrollViewLog: ScrollView
//        get() = findViewById<ScrollView>(R.id.scrollViewLog)

    private val textViewLog: TextView
        get() = findViewById<TextView>(R.id.textViewLog)
    private val scrollViewLog: ScrollView
        get() = findViewById<ScrollView>(R.id.scrollViewLog)

    private val list: MutableList<String> = ArrayList()

    private var userWantsToScanAndConnect = false
    private var isScanning = false
    private var connectedGatt: BluetoothGatt? = null
    private var characteristicForRead: BluetoothGattCharacteristic? = null
    private var characteristicForRead_2: BluetoothGattCharacteristic? = null
    private var characteristicForWrite: BluetoothGattCharacteristic? = null
    private var characteristicForIndicate: BluetoothGattCharacteristic? = null

    private var chars: List<BluetoothGattCharacteristic> = ArrayList()



    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        window.decorView.windowInsetsController!!.hide(
//            android.view.WindowInsets.Type.statusBars()
//        )
//        window.decorView.visibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        // Remember that you should never show the action bar if the
        // status bar is hidden, so hide that too if necessary.
        actionBar?.hide()
//        hideStatusBar()
//        requestWindowFeature(Window.FEATURE_NO_TITLE);//will hide the title.
        supportActionBar?.hide(); //hide the title bar.
        text_rssi_bt.text = " "
        text_id_vibot.text = " "
        view_state_bt.setImageDrawable(getResources().getDrawable(R.drawable.ellipse2))

        val listSecurity = arrayOf<String?>("WPA2 PERSONAL", "WPA2 ENTERPRISE")
        val mArrayAdapter = ArrayAdapter<Any?>(this, R.layout.spinner_list, listSecurity)
        mArrayAdapter.setDropDownViewResource(R.layout.spinner_list)
        spinerSeguridad.adapter = mArrayAdapter
        spinerSeguridad.setSelection(0)
        inputUSERNAME.isEnabled = false
        inputPASSWORD.isEnabled = false


        scanBT.setOnClickListener {
            userWantsToScanAndConnect = true
            when (userWantsToScanAndConnect) {
                true -> {
                    val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
                    wifi_status.text = ""
                    rssi_wifi.text = ""
                    mac_vibot.text = ""
                    ip_wifi.text = ""
                    ssid_wifi.text = ""
                    fecha_vibot.text = ""
                    bateria_vibot.text = ""
                    pendientes.text = ""
                    n_vicons.text = ""
                    version_vibot.text = ""
                    registerReceiver(bleOnOffListener, filter)


                }
                false -> {
                    unregisterReceiver(bleOnOffListener)
                }
            }
            bleRestartLifecycle()
        }
        var toggleShowPass = 0
        showPass.setOnClickListener {
            if (toggleShowPass == 0){
                inputPASS.transformationMethod = HideReturnsTransformationMethod.getInstance()
                toggleShowPass = 1
            }else{
                inputPASS.transformationMethod = PasswordTransformationMethod.getInstance()
                toggleShowPass = 0
            }

        }
        desconectarBT.setOnClickListener {
            cancelTask()
            view_state_bt.setImageDrawable(getResources().getDrawable(R.drawable.ellipse2))
            text_id_vibot.text = " "
            text_rssi_bt.text = " "
            selectVibot = false
            list.clear()
            bleEndLifecycle()
        }

        setHora.setOnClickListener {
            val año = obtenerFechaConFormato("yyyy", "America/Santiago_City")
            val mes = obtenerFechaConFormato("MM", "America/Santiago_City")
            val dia = obtenerFechaConFormato("dd", "America/Santiago_City")
            val hora = obtenerFechaConFormato("HH", "America/Santiago_City")
            val minuto = obtenerFechaConFormato("mm", "America/Santiago_City")
            val segundo = obtenerFechaConFormato("ss", "America/Santiago_City")

            var gatt = connectedGatt ?: run {
                appendLog("ERROR: write failed, no connected device")
                return@setOnClickListener
            }
            var characteristic = characteristicForWrite ?:  run {
                appendLog("ERROR: write failed, characteristic unavailable $CHAR_FOR_WRITE_UUID")
                return@setOnClickListener
            }
            if (!characteristic.isWriteable()) {
                appendLog("ERROR: write failed, characteristic not writeable $CHAR_FOR_WRITE_UUID")
                return@setOnClickListener
            }
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

            characteristic.value = ("{'action':'hora','año': '"+ año.toString() +"','mes': '"+ mes.toString() + "','dia': '"+ dia.toString() + "','hora': '"+ hora.toString() + "','min': '"+ minuto.toString() + "','seg': '"+ segundo.toString() +"','token': 'vigaVitacura1212'}").toByteArray(Charsets.UTF_8)

            gatt.writeCharacteristic(characteristic)

        }
        sendCredentials.setOnClickListener {
            var gatt = connectedGatt ?: run {
                appendLog("ERROR: write failed, no connected device")
                return@setOnClickListener
            }
            var characteristic = characteristicForWrite ?:  run {
                appendLog("ERROR: write failed, characteristic unavailable $CHAR_FOR_WRITE_UUID")
                return@setOnClickListener
            }
            if (!characteristic.isWriteable()) {
                appendLog("ERROR: write failed, characteristic not writeable $CHAR_FOR_WRITE_UUID")
                return@setOnClickListener
            }
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

            characteristic.value = ("{'action':'wifi','ssid': '"+ inputSSID.text.toString() +"','pass': '"+ inputPASS.text.toString() +"','token': 'vigaVitacura1212'}").toByteArray(Charsets.UTF_8)

            gatt.writeCharacteristic(characteristic)

        }
//        switchConnect.setOnCheckedChangeListener { _, isChecked ->
//            when (isChecked) {
//                true -> {
//                    val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
//                    registerReceiver(bleOnOffListener, filter)
//                }
//                false -> {
//                    unregisterReceiver(bleOnOffListener)
//                }
//            }
//            bleRestartLifecycle()
//        }
        appendLog("MainActivity.onCreate")
    }

    override fun onDestroy() {
        bleEndLifecycle()
        super.onDestroy()
    }

    fun startInfiniteExecution() {
        job = GlobalScope.launch {
            var counterCharacterist = 1
            while(true) {
                var gatt = connectedGatt ?: run {
                    appendLog("ERROR: read failed, no connected device")
                    return@launch
                }
//                var characteristic = characteristicForRead ?: run {
//                    appendLog("ERROR: read failed, characteristic unavailable $CHAR_FOR_READ_UUID")
//                    return@launch
//                }
//                gatt.readCharacteristic(characteristic)
//                var characteristic2 = characteristicForRead_2 ?: run {
//                    appendLog("ERROR: read failed, characteristic unavailable $CHAR_FOR_READ_UUID_2")
//                    return@launch
//                }
                gatt.readCharacteristic(chars.get(counterCharacterist))
                gatt.readRemoteRssi()
                delay(700)
                counterCharacterist = counterCharacterist+1
                if (counterCharacterist == chars.size){
                    counterCharacterist = 1
                }
            }
        }
    }

    fun cancelTask() {
        job?.cancel()
    }

    @SuppressLint("SimpleDateFormat")
    fun obtenerFechaConFormato(formato: String?, zonaHoraria: String?): String? {
        val calendar = Calendar.getInstance()
        val date = calendar.time
        val sdf: SimpleDateFormat
        sdf = SimpleDateFormat(formato)
        sdf.timeZone = TimeZone.getTimeZone(zonaHoraria)
//        appendLog(sdf.format(date).toString())
        return sdf.format(date)
    }

    fun onTapWrite(view: View) {
        var gatt = connectedGatt ?: run {
            appendLog("ERROR: write failed, no connected device")
            return
        }
        var characteristic = characteristicForWrite ?:  run {
            appendLog("ERROR: write failed, characteristic unavailable $CHAR_FOR_WRITE_UUID")
            return
        }
        if (!characteristic.isWriteable()) {
            appendLog("ERROR: write failed, characteristic not writeable $CHAR_FOR_WRITE_UUID")
            return
        }
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
//        characteristic.value = editTextWriteValue.text.toString().toByteArray(Charsets.UTF_8)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        gatt.writeCharacteristic(characteristic)
    }

    fun onTapClearLog(view: View) {
//        textViewLog.text = "Logs:"
        appendLog("log cleared")
    }

    private fun appendLog(message: String) {
        Log.d("appendLog", message)
        runOnUiThread {
            val strTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            textViewLog.text = textViewLog.text.toString() + "\n$strTime $message"

//             scroll after delay, because textView has to be updated first
            Handler().postDelayed({
                scrollViewLog.fullScroll(View.FOCUS_DOWN)
            }, 16)
        }
    }

    private fun bleEndLifecycle() {
        safeStopBleScan()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        connectedGatt?.close()
        setConnectedGattToNull()
        lifecycleState = BLELifecycleState.Disconnected
    }

    private fun setConnectedGattToNull() {
        connectedGatt = null
        characteristicForRead = null
        characteristicForWrite = null
        characteristicForIndicate = null
    }

    private fun bleRestartLifecycle() {
        runOnUiThread {
            if (userWantsToScanAndConnect) {
                if (connectedGatt == null) {
                    prepareAndStartBleScan()
                } else {
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.

                    }
                    connectedGatt?.disconnect()
                }
            } else {
                bleEndLifecycle()
            }
        }
    }

    private fun prepareAndStartBleScan() {
        ensureBluetoothCanBeUsed { isSuccess, message ->
            appendLog(message)
            if (isSuccess) {
                safeStartBleScan()
            }
        }
    }

    val timer = object: CountDownTimer(5000, 1000) {
        override fun onTick(millisUntilFinished: Long) {
//            Toast.makeText(applicationContext, "buscando dispositivos...", Toast.LENGTH_SHORT).show()
            appendLog("buscando dispositivos...")
        }

        override fun onFinish() {
//            Toast.makeText(applicationContext, "time out scan", Toast.LENGTH_SHORT).show()
//            safeStopBleScan()
            appendLog("tiempo de busqueda cumplido.")
            withItems(this@MainActivity)
        }

    }

    private fun safeStartBleScan() {
        if (isScanning) {
            Log.d("TAG", "Already scanning")
            appendLog("Already scanning")
            return
        }

        val serviceFilter = scanFilter.serviceUuid?.uuid.toString()
        appendLog("Starting BLE scan, filter: $serviceFilter")

        isScanning = true
        scanBT.isEnabled = false
        Toast.makeText(applicationContext,"INICIO DE ESCANEO",Toast.LENGTH_LONG).show()
        lifecycleState = BLELifecycleState.Scanning
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }


        timer.start()
        bleScanner.startScan(scanCallback)
        Log.d("TAG", "LLAMANDO AL SCANCALLBACK")
    }

    private fun safeStopBleScan() {
        if (!isScanning) {
            appendLog("Already stopped")
            return
        }

        appendLog("Stopping BLE scan")
        isScanning = false
        scanBT.isEnabled = true
        //TODO:
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        bleScanner.stopScan(scanCallback)
    }

    private fun subscribeToIndications(characteristic: BluetoothGattCharacteristic, gatt: BluetoothGatt) {
        val cccdUuid = UUID.fromString(CCC_DESCRIPTOR_UUID)
        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            if (!gatt.setCharacteristicNotification(characteristic, true)) {
                appendLog("ERROR: setNotification(true) failed for ${characteristic.uuid}")
                return
            }
            cccDescriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            gatt.writeDescriptor(cccDescriptor)
        }
    }

    private fun unsubscribeFromCharacteristic(characteristic: BluetoothGattCharacteristic) {
        val gatt = connectedGatt ?: return

        val cccdUuid = UUID.fromString(CCC_DESCRIPTOR_UUID)
        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            if (!gatt.setCharacteristicNotification(characteristic, false)) {
                appendLog("ERROR: setNotification(false) failed for ${characteristic.uuid}")
                return
            }
            cccDescriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(cccDescriptor)
        }
    }

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    //region BLE Scanning
    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val scanFilter = ScanFilter.Builder()
        .setServiceUuid(ParcelUuid(UUID.fromString(SERVICE_UUID)))
        .build()

    private val scanSettings: ScanSettings
        get() {
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                scanSettingsSinceM
            } else {
                scanSettingsBeforeM
            }
        }

    private val scanSettingsBeforeM = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
        .setReportDelay(0)
        .build()

    @RequiresApi(Build.VERSION_CODES.M)
    private val scanSettingsSinceM = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
        .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
        .setReportDelay(0)
        .build()

    private  var vibotSelected = ""

    var selectVibot = false
    var selectVibotConnectionError = false
    fun withItems(view: MainActivity) {

        val builder = AlertDialog.Builder(this@MainActivity)
        with(builder)
        {
            setTitle("Dispositivos encontrados")
            setItems(list.toTypedArray()) { dialog, which ->
//                Toast.makeText(applicationContext, list.toTypedArray()[which] + " is clicked", Toast.LENGTH_SHORT).show()
                vibotSelected= list.toTypedArray()[which].toString()
                selectVibot = true
            }

            show()
        }



    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name: String? = result.scanRecord?.deviceName ?: result.device.name
            if (name != null) {
                if (name.contains("VIBOT")){
                    Log.d("TAG", "onScanResult name=$name address= ${result.device?.address}")
                    appendLog("onScanResult name=$name address= ${result.device?.address}")
                    if (!list.contains(name)){
                        list.add(name)
                    }

                    if (selectVibot ){
                        if (vibotSelected == name){
                            safeStopBleScan()
                            lifecycleState = BLELifecycleState.Connecting
                            if (ActivityCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                // TODO: Consider calling
                                //    ActivityCompat#requestPermissions
                                // here to request the missing permissions, and then overriding
                                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                //                                          int[] grantResults)
                                // to handle the case where the user grants the permission. See the documentation
                                // for ActivityCompat#requestPermissions for more details.
                                return
                            }
                            result.device.connectGatt(this@MainActivity, false, gattCallback)
                        }
                    }
                }
            }

//            if (selectVibot){
//                if (name != null) {
//                    Log.d("TAG", "VIBOT-"+ text_id_vibot.text)
//                    if (name == "VIBOT-"+ text_id_vibot.text){
//
//                    }
//                }
//
//            }

        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            appendLog("onBatchScanResults, ignoring")
        }

        override fun onScanFailed(errorCode: Int) {
            appendLog("onScanFailed errorCode=$errorCode")
            safeStopBleScan()
            lifecycleState = BLELifecycleState.Disconnected
            bleRestartLifecycle()
        }
    }
    //region BLE events, when connected
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            // TODO: timeout timer: if this callback not called - disconnect(), wait 120ms, close()

            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    appendLog("Connected to $deviceAddress")
                    view_state_bt.setImageDrawable(getResources().getDrawable(R.drawable.ellipse_1))
                    selectVibotConnectionError = false
                    // TODO: bonding state
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return
                    }
//                    appendLog(gatt.device.name.toString())

                    // recommended on UI thread https://punchthrough.com/android-ble-guide/
                    Handler(Looper.getMainLooper()).post {
                        lifecycleState = BLELifecycleState.ConnectedDiscovering
                        text_id_vibot.text = gatt.device.name.toString().substringAfter("-")
                        gatt.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    cancelTask()
                    view_state_bt.setImageDrawable(getResources().getDrawable(R.drawable.ellipse2))
                    text_id_vibot.text = "------"
                    selectVibot = false
                    appendLog("Disconnected from $deviceAddress")
                    setConnectedGattToNull()
                    gatt.close()
                    lifecycleState = BLELifecycleState.Disconnected
                    bleRestartLifecycle()
                }
            } else {
                // TODO: random error 133 - close() and try reconnect

                appendLog("ERROR: onConnectionStateChange status=$status deviceAddress=$deviceAddress, disconnecting")

                cancelTask()
                view_state_bt.setImageDrawable(getResources().getDrawable(R.drawable.ellipse2))
                text_id_vibot.text = ""
                selectVibot = false
                appendLog("Disconnected from $deviceAddress")
                list.clear()
                setConnectedGattToNull()
                gatt.close()
                lifecycleState = BLELifecycleState.Disconnected
                //bleRestartLifecycle()
                Toast.makeText(applicationContext,"Reintentar Conexión!",Toast.LENGTH_LONG).show()
                bleEndLifecycle()
                cancelTask()

//                setConnectedGattToNull()
//                gatt.close()
//                lifecycleState = BLELifecycleState.Disconnected
//                bleRestartLifecycle()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            appendLog("onServicesDiscovered services.count=${gatt.services.size} status=$status")
            appendLog("onServicesDiscovered services =${gatt.services} ")
            if (status == 129 /*GATT_INTERNAL_ERROR*/) {
                // it should be a rare case, this article recommends to disconnect:
                // https://medium.com/@martijn.van.welie/making-android-ble-work-part-2-47a3cdaade07
                appendLog("ERROR: status=129 (GATT_INTERNAL_ERROR), disconnecting")
                gatt.disconnect()
                return
            }

            val service = gatt.getService(UUID.fromString(SERVICE_UUID)) ?: run {
                appendLog("ERROR: Service not found $SERVICE_UUID, disconnecting")
                gatt.disconnect()
                return
            }
            connectedGatt = gatt

            chars= service.characteristics.filterNotNull()
//            characteristicForRead = service.getCharacteristic(UUID.fromString(CHAR_FOR_READ_UUID))
//            characteristicForRead_2 = service.getCharacteristic(UUID.fromString(CHAR_FOR_READ_UUID_2))
            characteristicForWrite = service.getCharacteristic(UUID.fromString(CHAR_FOR_WRITE_UUID))
            characteristicForIndicate = service.getCharacteristic(UUID.fromString(CHAR_FOR_INDICATE_UUID))

            characteristicForIndicate?.let {
                lifecycleState = BLELifecycleState.ConnectedSubscribing
                subscribeToIndications(it, gatt)
            } ?: run {
                appendLog("WARN: characteristic not found $CHAR_FOR_INDICATE_UUID")
                lifecycleState = BLELifecycleState.Connected
            }
            appendLog("TERMINA ACA EL SERVICE DISCOVERED")
            startInfiniteExecution()
        }
        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("TAG", String.format("BluetoothGatt ReadRssi[%d]", rssi))
                text_rssi_bt.text = String.format("%ddB ", rssi)
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            when(characteristic.uuid){
                UUID.fromString(UUID_WIFI_STATUS)->{
                    val strValue = characteristic.value.toString(Charsets.UTF_8)
                    val log = "onCharacteristicRead " + when (status) {
                        BluetoothGatt.GATT_SUCCESS -> "OK WIFI STATUS, value=\"$strValue\" uuid \"${characteristic.uuid}\""
                        BluetoothGatt.GATT_READ_NOT_PERMITTED -> "not allowed"
                        else -> "error $status"
                    }
                    appendLog(log)
                    runOnUiThread {
                        if (strValue == "1"){
                            wifi_status.text = "CONECTADO"
                        }else{
                            wifi_status.text = "DESCONECTADO"
                        }

                    }
                }
                UUID.fromString(UUID_RSSI_WIFI)->{
                    val strValue = characteristic.value.toString(Charsets.UTF_8)
                    val log = "onCharacteristicRead " + when (status) {
                        BluetoothGatt.GATT_SUCCESS -> "OK RSSI WIFI, value=\"$strValue\" uuid \"${characteristic.uuid}\""
                        BluetoothGatt.GATT_READ_NOT_PERMITTED -> "not allowed"
                        else -> "error $status"
                    }
                    appendLog(log)
                    runOnUiThread {
                        rssi_wifi.text = strValue + " dB"
                    }
                }
                UUID.fromString(UUID_MAC)->{
                    val strValue = characteristic.value.toString(Charsets.UTF_8)
                    val log = "onCharacteristicRead " + when (status) {
                        BluetoothGatt.GATT_SUCCESS -> "OK MAC, value=\"$strValue\" uuid \"${characteristic.uuid}\""
                        BluetoothGatt.GATT_READ_NOT_PERMITTED -> "not allowed"
                        else -> "error $status"
                    }
                    appendLog(log)
                    runOnUiThread {
                        mac_vibot.text = strValue
                    }
                }
                UUID.fromString(UUID_IP_WIFI)->{
                    val strValue = characteristic.value.toString(Charsets.UTF_8)
                    val log = "onCharacteristicRead " + when (status) {
                        BluetoothGatt.GATT_SUCCESS -> "OK IP WIFI, value=\"$strValue\" uuid \"${characteristic.uuid}\""
                        BluetoothGatt.GATT_READ_NOT_PERMITTED -> "not allowed"
                        else -> "error $status"
                    }
                    appendLog(log)
                    runOnUiThread {
                        ip_wifi.text = strValue
                    }
                }
                UUID.fromString(UUID_SSID_WIFI)->{
                    val strValue = characteristic.value.toString(Charsets.UTF_8)
                    val log = "onCharacteristicRead " + when (status) {
                        BluetoothGatt.GATT_SUCCESS -> "OK SSID WIFI, value=\"$strValue\" uuid \"${characteristic.uuid}\""
                        BluetoothGatt.GATT_READ_NOT_PERMITTED -> "not allowed"
                        else -> "error $status"
                    }
                    appendLog(log)
                    runOnUiThread {
                        ssid_wifi.text = strValue
                    }
                }
                UUID.fromString(UUID_FECHA_VIBOT)->{
                    val strValue = characteristic.value.toString(Charsets.UTF_8)
                    val log = "onCharacteristicRead " + when (status) {
                        BluetoothGatt.GATT_SUCCESS -> "OK FECHA, value=\"$strValue\" uuid \"${characteristic.uuid}\""
                        BluetoothGatt.GATT_READ_NOT_PERMITTED -> "not allowed"
                        else -> "error $status"
                    }

                    runOnUiThread {
                        appendLog(log)
                        fecha_vibot.text = strValue
                    }
                }
                UUID.fromString(UUID_BATERIA)->{
                    val strValue = characteristic.value.toString(Charsets.UTF_8)
                    val log = "onCharacteristicRead " + when (status) {
                        BluetoothGatt.GATT_SUCCESS -> "OK BATERIA, value=\"$strValue\" uuid \"${characteristic.uuid}\""
                        BluetoothGatt.GATT_READ_NOT_PERMITTED -> "not allowed"
                        else -> "error $status"
                    }
                    appendLog(log)
                    runOnUiThread {
                        if (strValue == "1"){
                            bateria_vibot.text = "BATERIA"
                        }else{
                            bateria_vibot.text = "EXTERNA"
                        }
                    }
                }
                UUID.fromString(UUID_PENDIENTES)->{
                    val strValue = characteristic.value.toString(Charsets.UTF_8)
                    val log = "onCharacteristicRead " + when (status) {
                        BluetoothGatt.GATT_SUCCESS -> "OK PENDIENTES, value=\"$strValue\" uuid \"${characteristic.uuid}\""
                        BluetoothGatt.GATT_READ_NOT_PERMITTED -> "not allowed"
                        else -> "error $status"
                    }
                    appendLog(log)
                    runOnUiThread {
                        pendientes.text = strValue + " datos"
                    }
                }
                UUID.fromString(UUID_N_VICONS)->{
                    val strValue = characteristic.value.toString(Charsets.UTF_8)
                    val log = "onCharacteristicRead " + when (status) {
                        BluetoothGatt.GATT_SUCCESS -> "OK N VICONS, value=\"$strValue\" uuid \"${characteristic.uuid}\""
                        BluetoothGatt.GATT_READ_NOT_PERMITTED -> "not allowed"
                        else -> "error $status"
                    }
                    appendLog(log)
                    runOnUiThread {
                        n_vicons.text = strValue + " dispositivos"
                    }
                }
                UUID.fromString(UUID_VERSION)->{
                    val strValue = characteristic.value.toString(Charsets.UTF_8)
                    val log = "onCharacteristicRead " + when (status) {
                        BluetoothGatt.GATT_SUCCESS -> "OK VERSION, value=\"$strValue\" uuid \"${characteristic.uuid}\""
                        BluetoothGatt.GATT_READ_NOT_PERMITTED -> "not allowed"
                        else -> "error $status"
                    }
                    appendLog(log)
                    runOnUiThread {
                        version_vibot.text = strValue
                    }
                }
                else -> appendLog("onCharacteristicRead unknown uuid ${characteristic.uuid}")
            }
//            if (characteristic.uuid == UUID.fromString(CHAR_FOR_READ_UUID)) {
//                val strValue = characteristic.value.toString(Charsets.UTF_8)
//                val log = "onCharacteristicRead " + when (status) {
//                    BluetoothGatt.GATT_SUCCESS -> "OK, value=\"$strValue\""
//                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> "not allowed"
//                    else -> "error $status"
//                }
//                appendLog(log)
//                runOnUiThread {
//                    textViewReadValue.text = strValue
//                }
//            } else {
//                appendLog("onCharacteristicRead unknown uuid $characteristic.uuid")
//            }
//
//            if (characteristic.uuid == UUID.fromString(CHAR_FOR_READ_UUID_2)) {
//                val strValue = characteristic.value.toString(Charsets.UTF_8)
//                val log = "onCharacteristicRead " + when (status) {
//                    BluetoothGatt.GATT_SUCCESS -> "OK, value=\"$strValue\""
//                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> "not allowed"
//                    else -> "error $status"
//                }
//                appendLog(log)
//                runOnUiThread {
//                    textViewReadValue2.text = strValue
//                }
//            } else {
//                appendLog("onCharacteristicRead unknown uuid ${characteristic.uuid}")
//            }

        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (characteristic.uuid == UUID.fromString(CHAR_FOR_WRITE_UUID)) {
                val log: String = "onCharacteristicWrite " + when (status) {
                    BluetoothGatt.GATT_SUCCESS -> "!!!! ENVIO EXITOSO !!!!"
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> "not allowed"
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> "invalid length"
                    else -> "error $status"
                }
                appendLog(log)
            } else {
                appendLog("onCharacteristicWrite unknown uuid $characteristic.uuid")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == UUID.fromString(CHAR_FOR_INDICATE_UUID)) {
                val strValue = characteristic.value.toString(Charsets.UTF_8)
                appendLog("onCharacteristicChanged value=\"$strValue\"")
                runOnUiThread {
//                    textViewIndicateValue.text = strValue
                }
            } else {
                appendLog("onCharacteristicChanged unknown uuid $characteristic.uuid")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.characteristic.uuid == UUID.fromString(CHAR_FOR_INDICATE_UUID)) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val value = descriptor.value
                    val isSubscribed = value.isNotEmpty() && value[0].toInt() != 0
//                    val subscriptionText = when (isSubscribed) {
////                        true -> getString(R.string.text_subscribed)
////                        false -> getString(R.string.text_not_subscribed)
//                    }
//                    appendLog("onDescriptorWrite $subscriptionText")
//                    runOnUiThread {
////                        textViewSubscription.text = subscriptionText
//                    }
                } else {
                    appendLog("ERROR: onDescriptorWrite status=$status uuid=${descriptor.uuid} char=${descriptor.characteristic.uuid}")
                }

                // subscription processed, consider connection is ready for use
                lifecycleState = BLELifecycleState.Connected
            } else {
                appendLog("onDescriptorWrite unknown uuid $descriptor.characteristic.uuid")
            }
        }
    }

    //region BluetoothGattCharacteristic extension
    fun BluetoothGattCharacteristic.isReadable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

    fun BluetoothGattCharacteristic.isWriteable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

    fun BluetoothGattCharacteristic.isWriteableWithoutResponse(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

    fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)

    fun BluetoothGattCharacteristic.isIndicatable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE)

    private fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
        return (properties and property) != 0
    }
    //endregion

    //region Permissions and Settings management
    enum class AskType {
        AskOnce,
        InsistUntilSuccess
    }

    private var activityResultHandlers = mutableMapOf<Int, (Int) -> Unit>()
    private var permissionResultHandlers = mutableMapOf<Int, (Array<out String>, IntArray) -> Unit>()
    private var bleOnOffListener = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)) {
                BluetoothAdapter.STATE_ON -> {
                    appendLog("onReceive: Bluetooth ON")
                    if (lifecycleState == BLELifecycleState.Disconnected) {
                        bleRestartLifecycle()
                    }
                }
                BluetoothAdapter.STATE_OFF -> {
                    appendLog("onReceive: Bluetooth OFF")
                    bleEndLifecycle()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        activityResultHandlers[requestCode]?.let { handler ->
            handler(resultCode)
        } ?: runOnUiThread {
            appendLog("ERROR: onActivityResult requestCode=$requestCode result=$resultCode not handled")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionResultHandlers[requestCode]?.let { handler ->
            handler(permissions, grantResults)
        } ?: runOnUiThread {
            appendLog("ERROR: onRequestPermissionsResult requestCode=$requestCode not handled")
        }
    }

    private fun ensureBluetoothCanBeUsed(completion: (Boolean, String) -> Unit) {
        grantBluetoothCentralPermissions(AskType.AskOnce) { isGranted ->
            if (!isGranted) {
                completion(false, "Bluetooth permissions denied")
                return@grantBluetoothCentralPermissions
            }

            enableBluetooth(AskType.AskOnce) { isEnabled ->
                if (!isEnabled) {
                    completion(false, "Bluetooth OFF")
                    return@enableBluetooth
                }

                grantLocationPermissionIfRequired(AskType.AskOnce) { isGranted ->
                    if (!isGranted) {
                        completion(false, "Location permission denied")
                        return@grantLocationPermissionIfRequired
                    }

                    completion(true, "Bluetooth ON, permissions OK, ready")
                }
            }
        }
    }

    private fun enableBluetooth(askType: AskType, completion: (Boolean) -> Unit) {
        if (bluetoothAdapter.isEnabled) {
            completion(true)
        } else {
            val intentString = BluetoothAdapter.ACTION_REQUEST_ENABLE
            val requestCode = ENABLE_BLUETOOTH_REQUEST_CODE

            // set activity result handler
            activityResultHandlers[requestCode] = { result -> Unit
                val isSuccess = result == Activity.RESULT_OK
                if (isSuccess || askType != AskType.InsistUntilSuccess) {
                    activityResultHandlers.remove(requestCode)
                    completion(isSuccess)
                } else {
                    // start activity for the request again
                    startActivityForResult(Intent(intentString), requestCode)
                }
            }

            // start activity for the request
            startActivityForResult(Intent(intentString), requestCode)
        }
    }

    private fun grantLocationPermissionIfRequired(askType: AskType, completion: (Boolean) -> Unit) {
        val wantedPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // BLUETOOTH_SCAN permission has flag "neverForLocation", so location not needed
            completion(true)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || hasPermissions(wantedPermissions)) {
            completion(true)
        } else {
            runOnUiThread {
                val requestCode = LOCATION_PERMISSION_REQUEST_CODE

                // prepare motivation message
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Location permission required")
                builder.setMessage("BLE advertising requires location access, starting from Android 6.0")
                builder.setPositiveButton(android.R.string.ok) { _, _ ->
                    requestPermissionArray(wantedPermissions, requestCode)
                }
                builder.setCancelable(false)

                // set permission result handler
                permissionResultHandlers[requestCode] = { permissions, grantResults ->
                    val isSuccess = grantResults.firstOrNull() != PackageManager.PERMISSION_DENIED
                    if (isSuccess || askType != AskType.InsistUntilSuccess) {
                        permissionResultHandlers.remove(requestCode)
                        completion(isSuccess)
                    } else {
                        // show motivation message again
                        builder.create().show()
                    }
                }

                // show motivation message
                builder.create().show()
            }
        }
    }

    private fun grantBluetoothCentralPermissions(askType: AskType, completion: (Boolean) -> Unit) {
        val wantedPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            )
        } else {
            emptyArray()
        }

        if (wantedPermissions.isEmpty() || hasPermissions(wantedPermissions)) {
            completion(true)
        } else {
            runOnUiThread {
                val requestCode = BLUETOOTH_ALL_PERMISSIONS_REQUEST_CODE

                // set permission result handler
                permissionResultHandlers[requestCode] = { _ /*permissions*/, grantResults ->
                    val isSuccess = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                    if (isSuccess || askType != AskType.InsistUntilSuccess) {
                        permissionResultHandlers.remove(requestCode)
                        completion(isSuccess)
                    } else {
                        // request again
                        requestPermissionArray(wantedPermissions, requestCode)
                    }
                }

                requestPermissionArray(wantedPermissions, requestCode)
            }
        }
    }

    private fun Context.hasPermissions(permissions: Array<String>): Boolean = permissions.all {
        ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun Activity.requestPermissionArray(permissions: Array<String>, requestCode: Int) {
        ActivityCompat.requestPermissions(this, permissions, requestCode)
    }
    //endregion
}