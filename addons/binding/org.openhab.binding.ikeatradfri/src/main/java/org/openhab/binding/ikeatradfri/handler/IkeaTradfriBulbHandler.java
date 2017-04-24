/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.ikeatradfri.handler;

import static org.openhab.binding.ikeatradfri.IkeaTradfriBindingConstants.*;

import com.google.gson.*;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.*;
import org.eclipse.smarthome.core.thing.*;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.ikeatradfri.internal.IkeaTradfriObserveListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * The {@link IkeaTradfriBulbHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Daniel Sundberg - Initial contribution
 */
public class IkeaTradfriBulbHandler extends BaseThingHandler implements IkeaTradfriObserveListener {

    private Logger logger = LoggerFactory.getLogger(IkeaTradfriBulbHandler.class);
    private static final JsonParser parser = new JsonParser();

    public IkeaTradfriBulbHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void onDataUpdate(String data) {
        LightProperties props = new LightProperties(data);

        try {
            OnOffType onoff = props.getOnOffState() ? OnOffType.ON : OnOffType.OFF;
            updateState(CHANNEL_STATE, onoff);
            logger.debug("Updating channel state: {}", onoff.toString());
        }
        catch(NullPointerException e) {}

        try {
            PercentType dimmer = props.getBrightness();
            updateState(CHANNEL_BRIGHTNESS, dimmer);
            logger.debug("Updating channel brightness: {} from {}", dimmer.toString(), props.getBrightness());
        }
        catch(NullPointerException e) {}

        try {
            PercentType colorTemp = props.getColorTemperature();
            updateState(CHANNEL_COLOR_TEMPERATURE, colorTemp);
            logger.debug("Updating channel color temp: {} ", colorTemp.toString());
        }
        catch(NullPointerException e) {}

    }

    private void set(String payload) {
        String id = getThing().getUID().getId();
        logger.debug("Sending to: {} payload: {}", id, payload);
        ((IkeaTradfriGatewayHandler) getBridge().getHandler()).set("15001/" + id, payload);
    }

    private class LightProperties {
        JsonObject root;
        JsonObject settings;
        JsonArray array;

        public LightProperties() {
            root = new JsonObject();
            settings = new JsonObject();
            array = new JsonArray();
            array.add(settings);
            root.add(TRADFRI_LIGHT, array);
        }

        public LightProperties(String jsonString) {
            try {
                root = parser.parse(jsonString).getAsJsonObject();
                array = root.getAsJsonArray(TRADFRI_LIGHT);
                settings = array.get(0).getAsJsonObject();
            }
            catch(JsonSyntaxException e) {
                logger.error("JSON error: {}", e.getMessage());
            }
        }

        LightProperties setBrightness(PercentType b) {
            settings.add(TRADFRI_DIMMER, new JsonPrimitive(Math.round(b.floatValue()/100.0f * 254)));
            return this;
        }

        PercentType getBrightness() {
            int b = settings.get(TRADFRI_DIMMER).getAsInt();
            if(b==1) // Handle corner case since we loose resolution
                return new PercentType(1);
            return new PercentType((int) Math.round(b / 2.54));
        }

        LightProperties setTransitionTime(int seconds) {
            settings.add(TRADFRI_TRANSITION_TIME, new JsonPrimitive(seconds));
            return this;
        }

        int getTransitionTime() {
            return settings.get(TRADFRI_TRANSITION_TIME).getAsInt();
        }

        private final List<Double> X = Arrays.asList(33137.0,30138.0, 24933.0);
        private final List<Double> Y = Arrays.asList(27211.0, 26909.0, 24691.0);


        LightProperties setColorTemperature(PercentType c) {
            double percentValue = c.doubleValue();

            long newX, newY;
            if(percentValue<50.0) {
                double p = percentValue/50.0;
                newX = Math.round(X.get(0)+p*(X.get(1)-X.get(0)));
                newY = Math.round(Y.get(0)+p*(Y.get(1)-Y.get(0)));
            }
            else {
                double p = (percentValue-50)/50.0;
                newX = Math.round(X.get(1)+p*(X.get(2)-X.get(1)));
                newY = Math.round(Y.get(1)+p*(Y.get(2)-Y.get(1)));
            }
            logger.debug("Setting new color: {} {} for {}", newX, newY, percentValue);

            settings.add(TRADFRI_COLOR_X, new JsonPrimitive(newX));
            settings.add(TRADFRI_COLOR_Y, new JsonPrimitive(newY));
            return this;
        }

        PercentType getColorTemperature() {
            double x = settings.get(TRADFRI_COLOR_X).getAsInt()/1.0, value = 0.0;
            if(x > X.get(1)) {
                value = (x-X.get(0))/(X.get(1)-X.get(0))/2.0;
            }
            else {
                value = (x-X.get(1))/(X.get(2)-X.get(1))/2.0+0.5;
            }
            return new PercentType((int)Math.round(value*100.0));
        }

        LightProperties setOnOffState(boolean on) {
            settings.add(TRADFRI_ONOFF, new JsonPrimitive(on ? 1 : 0));
            return this;
        }

        boolean getOnOffState() {
            return settings.get(TRADFRI_ONOFF).getAsInt() == 1;
        }

        String getJsonString() {
            return root.toString();
        }
    }

    private void setBrightness(PercentType dim) {
        LightProperties props = new LightProperties();
        props.setBrightness(dim).setTransitionTime(3);
        set(props.getJsonString());
    }

    private void setState(boolean on) {
        LightProperties props = new LightProperties();
        props.setOnOffState(on);
        set(props.getJsonString());
    }

    private void setColorTemperature(PercentType colordim) {
        LightProperties props = new LightProperties();
        props.setColorTemperature(colordim).setTransitionTime(3);
        set(props.getJsonString());
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

        switch (channelUID.getId()) {
            case CHANNEL_BRIGHTNESS:
                if (command instanceof PercentType) {
                    setBrightness((PercentType) command);
                }
                else if (command instanceof OnOffType) {
                    setState(((OnOffType) command) == OnOffType.ON ? true : false);
                }
                else {
                    logger.error("Can't handle command {} on channel {}", command, channelUID);
                }
                break;
            case CHANNEL_STATE:
                if (command instanceof OnOffType) {
                    setState(((OnOffType) command) == OnOffType.ON ? true : false);
                }
                else {
                    logger.error("Can't handle command {} on channel {}", command, channelUID);
                }
                break;
            case CHANNEL_COLOR_TEMPERATURE:
                if (command instanceof PercentType) {
                    setColorTemperature((PercentType) command);
                }
                else {
                    logger.error("Can't handle command {} on channel {}", command, channelUID);
                }
                break;
            default:
                logger.error("Can't handle command {} on channel {}", command, channelUID);
        }
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        logger.debug("bridgeStatusChanged {}", bridgeStatusInfo);
        String id = getThing().getUID().getId();
        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            updateStatus(ThingStatus.ONLINE);
        }
        else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    @Override
    public void initialize() {
        Configuration configuration = getConfig();
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
    }
}
