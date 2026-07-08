package com.styleconverter.test.style.typography

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.styleconverter.test.R

/**
 * Bundled Inter font family. Loaded from res/font/inter_*.ttf so it's
 * available without an Internet round-trip (avoids the Roboto/SF Pro
 * default-font divergence that was capping cross-platform SSIM on
 * every placeholder fixture).
 *
 * Mapped weights: 400 / 500 / 700 / 900. Anything in between resolves
 * to the nearest declared weight via Compose's standard font-matching.
 */
val InterFontFamily: FontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.inter_medium,  FontWeight.Medium, FontStyle.Normal),
    Font(R.font.inter_bold,    FontWeight.Bold,   FontStyle.Normal),
    Font(R.font.inter_black,   FontWeight.Black,  FontStyle.Normal),
)
