package com.jdcr.jdcrfile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.FragmentActivity
import com.jdcr.jdcrfile.ui.theme.JdcrFileTheme
import com.jdcr.jdcrlog.JdcrLog

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        JdcrLog.enable(true)
        setContent {
            JdcrFileTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        this@MainActivity,
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(activity: FragmentActivity, name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column {
        Text(
            text = "Hello $name!",
            modifier = modifier
        )
        Button(onClick = {
            JdcrFilePermissionUtils.requestMaxStoragePermission(activity) {

            }
        }) {
            Text("判断权限")
        }
        Button(onClick = {
            JdcrFileDirUtils.test()
        }) {
            Text("获取根目录")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {

    JdcrFileTheme {
    }
}