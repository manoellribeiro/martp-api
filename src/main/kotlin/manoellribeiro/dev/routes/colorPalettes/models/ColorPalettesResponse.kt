package manoellribeiro.dev.routes.colorPalettes.models

import kotlinx.serialization.Serializable

@Serializable
data class ColorPalettesResponse(
    val palettes: List<ColorPaletteResponse>,
)

@Serializable
data class ColorPaletteResponse(
    val name: String,
    val colors: List<String>,
)
