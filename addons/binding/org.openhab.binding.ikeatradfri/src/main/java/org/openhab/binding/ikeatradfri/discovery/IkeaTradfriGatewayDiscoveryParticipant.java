package org.openhab.binding.ikeatradfri.discovery;

import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.io.transport.mdns.discovery.MDNSDiscoveryParticipant;

import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.ikeatradfri.IkeaTradfriBindingConstants;
import org.openhab.binding.ikeatradfri.configuration.IkeaTradfriGatewayConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jmdns.ServiceInfo;
import java.net.Inet4Address;
import java.util.*;

/**
 * Created by dansu on 2017-04-18.
 * The {@link IkeaTradfriGatewayDiscoveryParticipant} is responsible for discovering
 * the IKEA Tradfri gateway
 *
 * @author Daniel Sundberg - Initial contribution
 */

public class IkeaTradfriGatewayDiscoveryParticipant implements MDNSDiscoveryParticipant {
    private Logger logger = LoggerFactory.getLogger(IkeaTradfriGatewayDiscoveryParticipant.class);

    private static final String SERVICE_TYPE = "_coap._udp.local.";

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return IkeaTradfriBindingConstants.SUPPORTED_GATEWAY_TYPES_UIDS;
    }

    @Override
    public String getServiceType() {
        return SERVICE_TYPE;
    }

    @Override
    public ThingUID getThingUID(ServiceInfo service) {
        if (service != null) {
            //logger.info("getThingUID "+service.toString());

            if ((service.getType() != null) && service.getType().equals(getServiceType())
                    && (service.getName().matches("gw:([a-f0-9]{2}[-]?){6}"))) {
                return new ThingUID(IkeaTradfriBindingConstants.THING_TYPE_GATEWAY,
                        service.getName().replaceAll("[^A-Za-z0-9_]", "_"));
            }
        }
        return null;
    }

    @Override
    public DiscoveryResult createResult(ServiceInfo service) {
        logger.info("createResult ServiceInfo: {}", service);
        DiscoveryResult result = null;
        String ip = null;
        //StringBuilder sb = new StringBuilder(;
        List<String> sb = new LinkedList<>();

        sb.add("application");
        sb.add(service.getApplication());

        sb.add("type");
        sb.add(service.getType());

        sb.add("port");
        sb.add(Integer.toString(service.getPort()));

        sb.add("inet4:");
        for(Inet4Address addr:service.getInet4Addresses()) {
            sb.add(addr.getHostAddress());
        }
        sb.add("Host addresses:");
        for(String addr:service.getHostAddresses()) {
            sb.add(addr);
        }
        sb.add("Property names:");
        Enumeration<String> props = service.getPropertyNames();
        while(props.hasMoreElements()) {
            String s = props.nextElement();
            sb.add(s);
            sb.add("=");
            sb.add(service.getPropertyString(s));
        }

        sb.add("key:");
        sb.add(service.getKey());

        sb.add("name:");
        sb.add(service.getName());

        sb.add("nice:");
        sb.add(service.getNiceTextString());

        sb.add("qualified:");
        sb.add(service.getQualifiedNameMap().toString());

        logger.info("Discovered gateway, data: {}", String.join(" ", sb));

        if (service.getHostAddresses() != null && service.getHostAddresses().length > 0
                && !service.getHostAddresses()[0].isEmpty()) {
            ip = service.getHostAddresses()[0];
        }
        ThingUID thingUID = getThingUID(service);
        if (thingUID != null && ip != null) {
            logger.info("Created a DiscoveryResult for Ikea Trådfri Gateway {} on IP {}", thingUID, ip);
            Map<String, Object> properties = new HashMap<>(1);
            props = service.getPropertyNames();
            while(props.hasMoreElements()) {
                String s = props.nextElement();
                properties.put(s, service.getPropertyString(s));
            }


            properties.put(IkeaTradfriGatewayConfiguration.HOST, ip + ":" + service.getPort());
            //properties.put(IkeaTradfriGatewayConfiguration.TOKEN, "");

            result = DiscoveryResultBuilder.create(thingUID).withProperties(properties).withLabel(service.getName())
                    .build();
        }
        return result;
    }
}
