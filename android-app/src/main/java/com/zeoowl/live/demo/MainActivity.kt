/*
 * Copyright 2024 teogor (Teodor Grigor)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zeoowl.live.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeoowl.live.demo.build.BuildProfile
import com.zeoowl.live.demo.res.Resources
import com.zeoowl.live.demo.test.Greeter
import com.zeoowl.live.demo.ui.theme.DemoTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    Greeter("Querent").greet()

    enableEdgeToEdge()
    setContent {
      DemoTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          Column(
            modifier = Modifier.padding(innerPadding)
              .padding(horizontal = 10.dp),
          ) {
            Item(
              content = "Is Debuggable: ${BuildProfile.isDebuggable}",
              modifier = Modifier.padding(vertical = 2.dp),
            )
            Item(
              content = "Version: ${BuildProfile.versionName} (${BuildProfile.versionCode})",
              modifier = Modifier.padding(vertical = 2.dp),
            )
            Item(
              content = "Build Type: ${BuildProfile.buildType}",
              modifier = Modifier.padding(vertical = 2.dp),
            )
          }
        }

        Resources.AppName
      }
    }
  }
}

@Composable
fun Item(content: String, modifier: Modifier = Modifier) {
  Text(
    text = content,
    modifier = modifier,
  )
}
