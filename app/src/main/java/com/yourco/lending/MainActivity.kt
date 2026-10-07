package com.yourco.lending

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.yourco.lending.ui.DiscoveryViewModel
import com.yourco.lending.ui.LoanFinderApp
import com.yourco.lending.ui.LoanFinderTheme

class MainActivity : ComponentActivity() {

    private val vm: DiscoveryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LoanFinderTheme {
                LoanFinderApp(vm)
            }
        }
    }
}
