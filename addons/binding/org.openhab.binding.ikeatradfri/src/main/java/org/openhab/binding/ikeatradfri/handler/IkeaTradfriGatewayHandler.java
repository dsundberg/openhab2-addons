/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.ikeatradfri.handler;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.pskstore.StaticPskStore;
import org.eclipse.smarthome.core.thing.*;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.binding.ikeatradfri.configuration.IkeaTradfriGatewayConfiguration;
import org.openhab.binding.ikeatradfri.internal.IkeaTradfriDiscoverListener;
import org.openhab.binding.ikeatradfri.internal.IkeaTradfriObserveListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The {@link IkeaTradfriGatewayHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 * 
 * @author Daniel Sundberg - Initial contribution
 */
public class IkeaTradfriGatewayHandler extends BaseBridgeHandler {

    private Logger logger = LoggerFactory.getLogger(IkeaTradfriGatewayHandler.class);

    private DTLSConnector dtlsConnector;
    private CoapEndpoint endPoint;

    private ScheduledFuture<?> authorizeJob;
    private List<IkeaTradfriDiscoverListener> dataListeners = new CopyOnWriteArrayList<>();
    private Map<ThingUID, CoapObserveRelation> observeRelationMap = new HashMap<>();
    private List<ThingUID> pendingObserve = new CopyOnWriteArrayList<>();

    public IkeaTradfriGatewayHandler(Bridge bridge) {
        super(bridge);
        authorizeJob = null;
        dtlsConnector = null;
        endPoint = null;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // No channels on the gateway yet
    }

    @Override
    public void dispose() {
        endPoint.destroy();
        dtlsConnector.destroy();
    }

    @Override
    public void initialize() {
        IkeaTradfriGatewayConfiguration configuration = getConfigAs(IkeaTradfriGatewayConfiguration.class);
        logger.debug("Initializing with host: {} token: {}", configuration.host, configuration.token);
        if(configuration != null) {

            if(configuration.host.isEmpty()) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "IKEA Tradfri Gateway host is not set in the thing configuration");
                return;
            }
            if(configuration.token.isEmpty()) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "IKEA Tradfri Gateway access token is not set in the thing configuration");
                return;
            }
            updateStatus(ThingStatus.OFFLINE);

            DtlsConnectorConfig.Builder builder = new DtlsConnectorConfig.Builder(new InetSocketAddress(0));
            builder.setPskStore(new StaticPskStore("", configuration.token.getBytes()));
            dtlsConnector = new DTLSConnector(builder.build());
            endPoint = new CoapEndpoint(dtlsConnector, NetworkConfig.getStandard());

            logger.debug("Binding will schedule a job to establish a connection...");
            if (authorizeJob == null || authorizeJob.isCancelled()) {
                authorizeJob = scheduler.schedule(authorizeRunnable, 1, TimeUnit.SECONDS);
            }

        }
        else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "IKEA Tradfri Gateway configuration is null");
            return;
        }
    }

    public void set(String uriString, String payload) {
        IkeaTradfriGatewayConfiguration configuration = getConfigAs(IkeaTradfriGatewayConfiguration.class);
        try {
            URI uri = new URI("coaps://" + configuration.host + "//"+uriString);
            CoapClient client = new CoapClient(uri);
            client.setEndpoint(endPoint);
            CoapResponse response = client.put(payload, MediaTypeRegistry.TEXT_PLAIN);
            if (response.isSuccess()) {
                logger.debug("COAP PUT Successful: {}", payload);
            } else {
                logger.debug("COAP PUT Error: {}", response.getCode().toString());
            }

            client.shutdown();

        } catch (URISyntaxException e) {
            logger.error("COAP URI exception: {}", e.getMessage());
        }
    }

    private void observeDevice(ThingUID thingUID, IkeaTradfriObserveListener listener) {
        String deviceId = thingUID.getId();
        logger.debug("Observing {}", deviceId);
        IkeaTradfriGatewayConfiguration configuration = getConfigAs(IkeaTradfriGatewayConfiguration.class);

        try {
            URI uri = new URI("coaps://" + configuration.host + "//15001/"+deviceId);

            CoapClient client = new CoapClient(uri);
            client.setEndpoint(endPoint);
            CoapHandler handler = new CoapHandler() {

                @Override
                public void onLoad(CoapResponse response) {
                    logger.debug("COAP Observe: \noptions: {}\npayload: {} ", response.getOptions().toString(), response.getResponseText());
                    listener.onDataUpdate(response.getResponseText());
                }

                @Override
                public void onError() {
                    logger.error("problem with observeDevice");
                }
            };

            CoapObserveRelation relation = client.observe(handler);
            observeRelationMap.put(thingUID, relation);
        } catch (URISyntaxException e) {
            logger.error("COAP URL error: {}", e.getMessage());
        }
    }

    private void stopObserve(ThingUID thingUID) {
        if(observeRelationMap.containsKey(thingUID)) {
            CoapObserveRelation relation = observeRelationMap.get(thingUID);
            relation.proactiveCancel();
            observeRelationMap.remove(thingUID);
        }
    }


    public String fetchData(String url) {
        IkeaTradfriGatewayConfiguration configuration = getConfigAs(IkeaTradfriGatewayConfiguration.class);
        try {

            URI uri = new URI("coaps://" + configuration.host + "//" + url);
            CoapClient client = new CoapClient(uri);
            client.setEndpoint(endPoint);

            CoapResponse response = client.get();
            String json = response.getResponseText();
            logger.debug("Got response for: {}\n   {}", url, json);
            client.shutdown();
            return json;
        }
        catch (URISyntaxException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.toString());
            e.printStackTrace();
        }
        return null;
    }

    private Runnable authorizeRunnable = new Runnable() {
        @Override
        @SuppressWarnings("null")
        public void run() {
            logger.debug("Authorize job...");
            String res = fetchData("15001");
            if(res != null) {
                updateStatus(ThingStatus.ONLINE);

                for(ThingUID thingUID: pendingObserve) {
                    Thing thing = getThingByUID(thingUID);
                    if(thing.getHandler() != null) {
                        observeDevice(thingUID, (IkeaTradfriBulbHandler)thing.getHandler());
                    }
                }
                pendingObserve.clear();

                try {
                    JSONArray array = new JSONArray(res);
                    for (int i=0; i<array.length(); i++) {
                        res = fetchData("15001/"+array.getInt(i));
                        // Trigger a new discovery of things
                        JSONObject json = new JSONObject(res);

                        for (IkeaTradfriDiscoverListener dataListener : dataListeners) {
                            dataListener.onDeviceFound(getThing().getUID(), json);
                        }
                    }
                } catch (JSONException e) {
                    logger.error("JSON error: {}", e.getMessage());
                }
            }
            else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Unable to fetch data from gateway.");
            }
        }
    };

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        logger.debug("Child handler initialized: {}", childThing.getThingTypeUID().toString());
        if(childHandler instanceof IkeaTradfriBulbHandler) {
            if(isInitialized() && endPoint != null) {
                observeDevice(childThing.getUID(), (IkeaTradfriBulbHandler)childHandler);
                updateStatus(ThingStatus.ONLINE);
            }
            else {
                pendingObserve.add(childThing.getUID());
            }
        }
    }

    @Override
    public void childHandlerDisposed(ThingHandler childHandler, Thing childThing) {
        logger.debug("Child handler disposed: {}", childThing.getThingTypeUID().toString());
        stopObserve(childThing.getUID());

        if (authorizeJob == null || authorizeJob.isDone() || authorizeJob.isCancelled()) {
            authorizeJob = scheduler.schedule(authorizeRunnable, 1, TimeUnit.SECONDS);
        }
    }

    public boolean registerDeviceListener(IkeaTradfriDiscoverListener dataListener) {
        if (dataListener == null) {
            throw new IllegalArgumentException("It's not allowed to pass a null dataListener.");
        }
        return dataListeners.add(dataListener);
    }

    public boolean unregisterDeviceListener(IkeaTradfriDiscoverListener dataListener) {
        if (dataListener == null) {
            throw new IllegalArgumentException("It's not allowed to pass a null dataListener.");
        }
        return dataListeners.remove(dataListener);
    }
}
