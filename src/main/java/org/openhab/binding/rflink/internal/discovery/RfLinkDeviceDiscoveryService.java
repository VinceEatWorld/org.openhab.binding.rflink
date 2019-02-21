/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rflink.internal.discovery;

import java.util.Set;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.rflink.RfLinkBindingConstants;
import org.openhab.binding.rflink.device.RfLinkDevice;
import org.openhab.binding.rflink.handler.RfLinkBridgeHandler;
import org.openhab.binding.rflink.internal.DeviceMessageListener;
import org.openhab.binding.rflink.message.RfLinkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link RfLinkDeviceDiscoveryService} class is used to discover RfLink
 * devices that send messages to RfLink bridge.
 *
 * @author Pauli Anttila - Initial contribution
 * @author Daan Sieben - Modified for RfLink
 * @author Marvyn Zalewski - Added the ability to ignore discoveries
 */
public class RfLinkDeviceDiscoveryService extends AbstractDiscoveryService implements DeviceMessageListener {

    private final Logger logger = LoggerFactory.getLogger(RfLinkDeviceDiscoveryService.class);

    private RfLinkBridgeHandler bridgeHandler;

    public RfLinkDeviceDiscoveryService(RfLinkBridgeHandler rflinkBridgeHandler) {
        super(null, 1, false);
        this.bridgeHandler = rflinkBridgeHandler;
    }

    public void activate() {
        bridgeHandler.registerDeviceStatusListener(this);
    }

    @Override
    public void deactivate() {
        bridgeHandler.unregisterDeviceStatusListener(this);
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        return RfLinkBindingConstants.SUPPORTED_DEVICE_THING_TYPES_UIDS;
    }

    @Override
    protected void startScan() {
        // this can be ignored here as we discover devices from received messages
    }

    @Override
    public void onDeviceMessageReceived(ThingUID bridge, RfLinkDevice device) {
        try {
            if (!bridgeHandler.getConfiguration().disableDiscovery) {
                String id = device.getKey();
                ThingTypeUID uid = device.getThingType();
                ThingUID thingUID = new ThingUID(uid, bridge, id.replace(RfLinkMessage.ID_DELIMITER, "_"));
                logger.trace("Adding new RfLink {} with id '{}' to smarthome inbox", thingUID, id);
                String deviceType = device.getProtocol();
                DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withLabel(deviceType)
                        .withProperty(RfLinkBindingConstants.DEVICE_ID, device.getKey()).withBridge(bridge)
                        .build();
                thingDiscovered(discoveryResult);
            }
        } catch (Exception e) {
            logger.debug("Error occured during device discovery", e);
        }
    }
}
