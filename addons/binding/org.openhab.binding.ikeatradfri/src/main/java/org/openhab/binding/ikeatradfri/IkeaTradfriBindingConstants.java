/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.ikeatradfri;

import com.google.common.collect.ImmutableSet;
import org.eclipse.smarthome.core.thing.ThingTypeUID;

import java.util.Set;

/**
 * The {@link IkeaTradfriBindingConstants} class defines common constants, which are
 * used across the whole binding.
 * 
 * @author Daniel Sundberg - Initial contribution
 */
public class IkeaTradfriBindingConstants {

    public static final String BINDING_ID = "ikeatradfri";
    
    // List of all Thing Type UIDs
    public final static ThingTypeUID THING_TYPE_GATEWAY = new ThingTypeUID(BINDING_ID, "gateway");
    public final static ThingTypeUID THING_TYPE_BULB = new ThingTypeUID(BINDING_ID, "white_bulb");
    public final static ThingTypeUID THING_TYPE_WS_BULB = new ThingTypeUID(BINDING_ID, "ws_bulb");
    public final static ThingTypeUID THING_TYPE_DIMMER = new ThingTypeUID(BINDING_ID, "dimmer");
    public final static ThingTypeUID THING_TYPE_CONTROLLER = new ThingTypeUID(BINDING_ID, "controller");

    public static final Set<ThingTypeUID> SUPPORTED_GATEWAY_TYPES_UIDS = ImmutableSet.of(THING_TYPE_GATEWAY);
    public static final Set<ThingTypeUID> SUPPORTED_BULB_TYPES_UIDS = ImmutableSet.of(THING_TYPE_BULB, THING_TYPE_WS_BULB);
    public static final Set<ThingTypeUID> SUPPORTED_DEVICE_TYPES_UIDS = ImmutableSet.of(THING_TYPE_BULB, THING_TYPE_DIMMER, THING_TYPE_CONTROLLER);

    // List of all Channel ids
    public static final String CHANNEL_BRIGHTNESS = "brightness";
    public static final String CHANNEL_FADETIME = "fadeTime";
    public static final String CHANNEL_STATE = "state";
    public static final String CHANNEL_COLOR = "color";
    public static final String CHANNEL_COLOR_TEMPERATURE = "colorTemperature";
}
