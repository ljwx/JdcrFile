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

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        JdcrFilePermissionUtils.checkExternal(this@MainActivity)
        setContent {
            JdcrFileTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column {
        Text(
            text = "Hello $name!",
            modifier = modifier
        )
        Button(onClick = {
            if (!JdcrFilePermissionUtils.checkPermissionAll()) {
                JdcrFilePermissionUtils.openSettings(context)
            }
        }) {
            Text("判断权限")
        }
        Button(onClick = {
            JdcrFileDirUtils.getRootDir(context)
        }) {
            Text("获取根目录")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {

    JdcrFileTheme {
        Column {
            Greeting("Android")

        }
    }
}