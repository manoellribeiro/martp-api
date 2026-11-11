package manoellribeiro.dev.core.models

/**
 * Where an art was made. Central America includes the Caribbean; Mexico is North America.
 * UNKNOWN when the lookup failed: the art is saved anyway and fixed by hand.
 */
enum class Continent {
    AFRICA,
    ANTARCTICA,
    ASIA,
    CENTRAL_AMERICA,
    EUROPE,
    NORTH_AMERICA,
    OCEANIA,
    SOUTH_AMERICA,
    OCEAN,
    UNKNOWN,
}
