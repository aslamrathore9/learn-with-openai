package com.varkyo.aitalkgpt

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONObject
import java.io.File

class MainActivity : ComponentActivity() {

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) Toast.makeText(this, "Mic permission needed", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ask microphone permission
        requestPermission.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            val vm: RealtimeViewModel = viewModel()
            val isRunning by vm.running.collectAsState()

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Spacer(Modifier.height(40.dp))
                Text("Realtime AI Call (Full Duplex)", style = MaterialTheme.typography.titleLarge)

                Spacer(Modifier.height(40.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Waveform Placeholder")
                }

                Spacer(Modifier.height(40.dp))

                Button(
                    onClick = {
                        if (!isRunning) startAISession(vm)
                        else vm.stopCall()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (!isRunning) "Start Call" else "Stop Call")
                }
            }
        }
    }

    private fun startAISession(vm: RealtimeViewModel) {
        CoroutineScope(Dispatchers.IO).launch {
            val token = fetchSessionToken()

            runOnUiThread {
                if (token == null) {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to fetch AI session token",
                        Toast.LENGTH_LONG
                    ).show()
                    return@runOnUiThread
                }

                Log.e("AccessToken: ",token)
                vm.setSessionToken(token)
                vm.startCall()
            }
        }
    }

    private fun fetchSessionToken(): String? {
        return try {
        //    val base = BuildConfig.BASE_URL ?: "http://10.0.2.2:4000"
            val base = "https://my-server-openai.onrender.com"
          //  val base = readLocalProperty("server.base_url") ?: "http://10.0.2.2:4000"

            val url = URL("$base/session-token")
            val conn = url.openConnection() as HttpURLConnection

            conn.requestMethod = "GET"
            conn.connectTimeout = 60000
            conn.readTimeout = 60000
            conn.connect()

            if (conn.responseCode == 200) {
                val jsonText = conn.inputStream.bufferedReader().readText()
                Log.d("AccessTOkenJson : ",jsonText)
                parseToken(jsonText)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


   /* private fun parseToken(json: String): String? {
        return try {
            val root = JSONObject(json)
            root.getJSONObject("client_secret").getString("value")
        } catch (e: Exception) {
            null
        }
    }
*/

    private fun parseToken(json: String): String? {
        return try {
            val root = JSONObject(json)
            root.getString("value")
        } catch (e: Exception) {
            null
        }
    }



    // read local.properties (stored inside project)
    private fun readLocalProperty(key: String): String? {
        return try {
            val file = File(applicationContext.filesDir.parentFile?.parentFile, "local.properties")
            if (!file.exists()) return null

            val map = file.readLines()
                .mapNotNull { line ->
                    val t = line.trim()
                    if (t.isEmpty() || t.startsWith("#")) null else t
                }
                .associate {
                    val p = it.split("=")
                    p[0].trim() to p.getOrElse(1) { "" }.trim()
                }

            map[key]
        } catch (_: Exception) {
            null
        }
    }
}
