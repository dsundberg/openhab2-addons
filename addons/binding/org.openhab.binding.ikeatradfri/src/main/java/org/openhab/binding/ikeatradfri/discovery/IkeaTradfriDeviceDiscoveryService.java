/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.ikeatradfri.discovery;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.binding.ikeatradfri.IkeaTradfriBindingConstants;
import org.openhab.binding.ikeatradfri.handler.IkeaTradfriGatewayHandler;
import org.openhab.binding.ikeatradfri.internal.IkeaTradfriDiscoverListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.openhab.binding.ikeatradfri.IkeaTradfriBindingConstants.*;


/**
 * The {@link IkeaTradfriDeviceDiscoveryService} is responsible for discovering all things
 * except the IKEA Tradfri gateway itself
 *
 * @author Daniel Sundberg - Initial contribution
 */
public class IkeaTradfriDeviceDiscoveryService extends AbstractDiscoveryService implements IkeaTradfriDiscoverListener {

    private final Logger logger = LoggerFactory.getLogger(IkeaTradfriDeviceDiscoveryService.class);

    private static final int SEARCH_TIME = 10;
    private IkeaTradfriGatewayHandler bridgeHandler;

    /**
     * Creates a IkeaTradlosDeviceDiscoveryService with background discovery disabled.
     */
    public IkeaTradfriDeviceDiscoveryService(IkeaTradfriGatewayHandler bridgeHandler) {
        super(IkeaTradfriBindingConstants.SUPPORTED_DEVICE_TYPES_UIDS, SEARCH_TIME, true);
        this.bridgeHandler = bridgeHandler;
    }

    public void activate() {
        bridgeHandler.registerDeviceListener(this);
    }

    @Override
    public void deactivate() {
        bridgeHandler.unregisterDeviceListener(this);
    }

    @Override
    protected void startScan() {
        logger.debug("Starting IKEA Tradfri discovery scan");
    }

    @Override
    public void onDeviceFound(ThingUID bridge, JSONObject data) {
        if (bridge != null && data != null) {
            try {
                if (data.has(TRADFRI_LIGHT) && data.has(TRADFRI_INSTANCE_ID)) {
                    String id = Integer.toString(data.getInt(TRADFRI_INSTANCE_ID));
                    ThingUID thingId;

                    JSONObject state = data.getJSONArray(TRADFRI_LIGHT).getJSONObject(0);

                    // White spectrum light
                    if(state.has(TRADFRI_COLOR)) {
                        thingId = new ThingUID(IkeaTradfriBindingConstants.THING_TYPE_WS_BULB, bridge, id);
                    }
                    else {
                        thingId = new ThingUID(IkeaTradfriBindingConstants.THING_TYPE_WW_BULB, bridge, id);
                    }

                    String label = "IKEA Tradfri bulb";
                    try {
                        label = data.getString(TRADFRI_NAME);
                    }
                    catch (JSONException e) {
                        logger.error("JSON error: {}", e.getMessage());
                    }

                    Map<String, Object> properties = new HashMap<>(1);
                    logger.debug("Adding new Tradfri Bulb {} to inbox", thingId);
                    DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingId).withBridge(bridge)
                            .withLabel(label).withProperties(properties).build();
                    thingDiscovered(discoveryResult);

                }
            }
            catch (JSONException e) {
                logger.error("JSON error: {}", e.getMessage());
            }
        }
    }
}
