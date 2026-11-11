package manoellribeiro.dev.core.data.database.tables

import manoellribeiro.dev.core.models.Continent
import manoellribeiro.dev.core.models.SketchArtType
import org.jetbrains.exposed.v1.core.Table

object MapArtsTable : Table("map_arts") {
    val id = uuid("id")
    val title = text("title")
    val latitude = double("latitude")
    val longitude = double("longitude")
    val dateInMillis = long("date_in_millis")
    val imageKey = text("image_key")
    val continent = enumerationByName<Continent>("continent", length = 32)
    val countryCode = text("country_code").nullable()
    val sketchArtType = enumerationByName<SketchArtType>("sketch_art_type", length = 32).nullable()
    val colorPalette = text("color_palette").nullable()
    val mapLayerRoadsColor = text("map_layer_roads_color").nullable()
    val mapLayerBuildingStrokeColor = text("map_layer_building_stroke_color").nullable()
    val mapLayerBuildingColor = text("map_layer_building_color").nullable()
    val mapLayerRoadsPathColor = text("map_layer_roads_path_color").nullable()
    val mapLayerWaterColor = text("map_layer_water_color").nullable()
    val mapLayerLandOthersColor = text("map_layer_land_others_color").nullable()
    val artistId = uuid("artist_id").references(UsersTable.id).nullable()
    val isApprovedForTheExhibition = bool("is_approved_for_the_exhibition")
    val isDeleted = bool("is_deleted")

    override val primaryKey = PrimaryKey(id)
}
