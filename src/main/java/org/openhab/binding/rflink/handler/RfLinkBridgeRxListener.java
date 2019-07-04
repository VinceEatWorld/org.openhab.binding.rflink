/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rflink.handler;

import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.openhab.binding.rflink.connector.RfLinkRxListener;
import org.openhab.binding.rflink.internal.EventMessageListener;
import org.openhab.binding.rflink.message.RfLinkMessage;
import org.openhab.binding.rflink.packet.RfLinkPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author cartemere - Initial Contribution : externalize Rx/Tx from Bridge
 *
 */
public class RfLinkBridgeRxListener implements RfLinkRxListener {

    private Logger logger = LoggerFactory.getLogger(RfLinkBridgeRxListener.class);
    RfLinkBridgeHandler bridge = null;

    public RfLinkBridgeRxListener(RfLinkBridgeHandler bridge) {
        this.bridge = bridge;
    }

    @Override
    public synchronized void packetReceived(RfLinkPacket rfLinkPacket) {
        RfLinkMessage message = new RfLinkMessage(rfLinkPacket);
        if (isDebugLogMessage(message)) {
            // ignore Debug & OK response messages...
        } else {
            boolean packetProcessed = false;
            // 1 - HANDLE THING LISTENERS
            for (EventMessageListener eventMessageListener : bridge.getEventMessageListeners()) {
                try {
                    if (eventMessageListener.canHandleMessage(message)) {
                        packetProcessed = true;
                        eventMessageListener.handleIncomingMessage(bridge.getThing().getUID(), message);
                    }
                } catch (Exception e) {
                    logger.error("An exception occurred while calling the EventMessageListener for message " + message,
                            e);
                }
            }
            // 2 - HANDLE DISCOVERY
            if (!packetProcessed) {
                // current message is "unknown" (i.e. not handled by any existing Handler)
                if (bridge.getDiscoveryService() != null && !bridge.getConfiguration().disableDiscovery) {
                    try {
                        bridge.getDiscoveryService().discoverThing(bridge.getThing().getUID(), message);
                    } catch (Exception e) {
                        logger.error(
                                "An exception occurred while registring message to the DiscoveryService : " + message,
                                e);
                    }
                }
            }
        }
        bridge.updateStatus(ThingStatus.ONLINE, ThingStatusDetail.NONE);
    }

    @Override
    public void errorOccured(String error) {
        logger.error("Error occured: {}", error);
        bridge.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
    }

    private boolean isDebugLogMessage(RfLinkMessage message) {
        return "Debug".equals(message.getProtocol()) || "OK".equals(message.getProtocol());
    }
}
