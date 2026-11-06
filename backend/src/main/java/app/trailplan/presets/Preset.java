package app.trailplan.presets;

import app.trailplan.routing.RoutingPreferences;

/** A rider's named riding style. */
public record Preset(long id, String name, RoutingPreferences preferences, int position) {}
