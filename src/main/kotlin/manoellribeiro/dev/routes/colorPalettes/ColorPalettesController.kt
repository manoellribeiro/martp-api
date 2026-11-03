package manoellribeiro.dev.routes.colorPalettes

import manoellribeiro.dev.routes.colorPalettes.models.ColorPalettesResponse

interface ColorPalettesController {
    suspend fun getColorPalettes(): ColorPalettesResponse
}
