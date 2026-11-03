package manoellribeiro.dev.routes.colorPalettes

import manoellribeiro.dev.core.data.repositories.MartpRepository
import manoellribeiro.dev.routes.colorPalettes.models.ColorPaletteResponse
import manoellribeiro.dev.routes.colorPalettes.models.ColorPalettesResponse

class ColorPalettesControllerImpl(
    private val repository: MartpRepository,
) : ColorPalettesController {

    override suspend fun getColorPalettes(): ColorPalettesResponse = ColorPalettesResponse(
        palettes = repository.getColorPalettes().map { ColorPaletteResponse(name = it.name, colors = it.colors) },
    )
}
