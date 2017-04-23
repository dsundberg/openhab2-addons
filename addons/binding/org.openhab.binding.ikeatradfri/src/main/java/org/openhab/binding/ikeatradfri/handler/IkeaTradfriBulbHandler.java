/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.ikeatradfri.handler;

import static org.openhab.binding.ikeatradfri.IkeaTradfriBindingConstants.*;

import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.*;
import org.eclipse.smarthome.core.thing.*;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.binding.ikeatradfri.internal.IkeaTradfriObserveListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link IkeaTradfriBulbHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Daniel Sundberg - Initial contribution
 */
public class IkeaTradfriBulbHandler extends BaseThingHandler implements IkeaTradfriObserveListener {

    private Logger logger = LoggerFactory.getLogger(IkeaTradfriBulbHandler.class);

    public IkeaTradfriBulbHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void onDataUpdate(String data) {
        logger.info("Got some data:\n{}", data);
        try {
            JSONObject json = new JSONObject(data);

            JSONObject state = json.getJSONArray("3311").getJSONObject(0);

            OnOffType onoff = state.getInt("5850") == 1 ? OnOffType.ON : OnOffType.OFF;
            updateState(CHANNEL_STATE, onoff);

            PercentType dimmer = new PercentType((int) Math.round(state.getInt("5851") / 2.54));
            updateState(CHANNEL_BRIGHTNESS, dimmer);

            try {
                String color = state.getString("5706");
                PercentType ctemp;
                switch (color) {
                    case "f5faf6":
                        ctemp = new PercentType(100);
                        break;
                    case "f1e0b5":
                        ctemp = new PercentType(50);
                        break;
                    case "efd275":
                        ctemp = new PercentType(0);
                        break;
                    default:
                        ctemp = new PercentType(50);
                        break;
                }
                updateState(CHANNEL_COLOR_TEMPERATURE, ctemp);
            } catch (JSONException ex) {

            }


            logger.warn("Updating channels brightness: {} state: {}", dimmer.toString(), onoff.toString());
        } catch (JSONException e) {
            logger.error("JSON error: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private void set(String payload) {
        String id = getThing().getUID().getId();
        logger.warn("Sending to: {} payload: {}", id, payload);
        ((IkeaTradfriGatewayHandler) getBridge().getHandler()).set("15001/" + id, payload);
    }

    private void setBrightness(float normalized) {
        try {
            JSONObject json = new JSONObject();
            JSONObject settings = new JSONObject();
            JSONArray array = new JSONArray();
            array.put(settings);
            json.put("3311", array);
            settings.put("5851", Math.round(normalized * 254));
            settings.put("5712", 3);    // second transition
            String payload = json.toString();
            set(payload);
        } catch (JSONException e) {
            logger.error("JSON Error: {}", e.getMessage());
        }
    }

    private void setState(boolean on) {
        try {
            JSONObject json = new JSONObject();
            JSONObject settings = new JSONObject();
            JSONArray array = new JSONArray();
            array.put(settings);
            json.put("3311", array);
            settings.put("5850", on ? 1 : 0);
            String payload = json.toString();
            set(payload);
        } catch (JSONException e) {
            logger.error("JSON Error: {}", e.getMessage());
        }
    }

    private void setColorTemperature(int percentValue) {
        int kelvin = (40 - 27) * percentValue + 2700;
        int color = kelvinToRGB(kelvin);

        String colorString = intToHex(color);

        // Override color calcs with 3 known supported color temps
        if (percentValue < 100) colorString = "f5faf6";
        if (percentValue < 66) colorString = "f1e0b5";
        if (percentValue < 33) colorString = "efd275";

        logger.warn("Color string: {}", colorString);

        try {
            JSONObject json = new JSONObject();
            JSONObject settings = new JSONObject();
            JSONArray array = new JSONArray();
            array.put(settings);
            json.put("3311", array);
            settings.put("5706", colorString);
            settings.put("5712", 3);    // second transition
            String payload = json.toString();
            set(payload);
        } catch (JSONException e) {
            logger.error("JSON Error: {}", e.getMessage());
        }
    }

    private String intToHex(int color) {
        String r = Integer.toHexString((color / 256 / 256) & 0xff);
        String g = Integer.toHexString((color / 256) & 0xff);
        String b = Integer.toHexString(color & 0xff);
        String colorString = r + g + b;
        return colorString;
    }

    private int kelvinToRGB(int kelvin) {
        // http://www.tannerhelland.com/4435/convert-temperature-rgb-algorithm-code/
        // Given a temperature (in Kelvin), estimate an RGB equivalent

        double tmpCalc;

        // Clamp temperature between 1000 and 40000 degrees
        double K = (double) Math.min(Math.max(1000, kelvin), 40000);

        // All calculations require tmpKelvin \ 100, so only do the conversion once
        K = K / 100.0;

        // Calculate each color in turn
        long r, g, b;

        //First: red
        if (K <= 66) r = 255;
        else {
            // Note: the R-squared value for this approximation is .988
            tmpCalc = K - 60.0;
            tmpCalc = 329.698727446 * Math.pow(tmpCalc, -0.1332047592);
            r = Math.min(Math.max(Math.round(tmpCalc), 0), 255);
        }
        // Second: green
        if (K <= 66.0) {
            // Note: the R-squared value for this approximation is .996
            tmpCalc = K;
            tmpCalc = 99.4708025861 * Math.log(tmpCalc) - 161.1195681661;
            g = Math.min(Math.max(Math.round(tmpCalc), 0), 255);
        } else {
            // Note: the R-squared value for this approximation is .987
            tmpCalc = K - 60.0;
            tmpCalc = 288.1221695283 * Math.pow(tmpCalc, -0.0755148492);
            g = Math.min(Math.max(Math.round(tmpCalc), 0), 255);
        }

        // Third: blue
        if (K >= 66.0) b = 255;
        else if (K <= 19.0) b = 0;
        else {
            // Note: the R-squared value for this approximation is .998
            tmpCalc = K - 10.0;
            tmpCalc = 138.5177312231 * Math.log(tmpCalc) - 305.0447927307;
            b = Math.min(Math.max(Math.round(tmpCalc), 0), 255);
        }
        int rgb = (int) (((r << 16) | (g << 8) | b) & 0xFFFFFF);
        logger.warn("ColorTemp to RGB, kelvin: {}, r: {}, g: {}, b: {} hex: {}", kelvin, r, g, b, intToHex(rgb));
        return rgb;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

        switch (channelUID.getId()) {
            case CHANNEL_BRIGHTNESS:
                if (command instanceof PercentType) {
                    float newBright = ((PercentType) command).floatValue();
                    setBrightness(newBright / 100.0f);
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
                    PercentType dimValue = (PercentType) command;
                    setColorTemperature(dimValue.intValue());
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
