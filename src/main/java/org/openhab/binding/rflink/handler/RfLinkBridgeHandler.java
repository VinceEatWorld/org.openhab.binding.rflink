/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rflink.handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.rflink.config.RfLinkBridgeConfiguration;
import org.openhab.binding.rflink.connector.RfLinkConnectorInterface;
import org.openhab.binding.rflink.connector.RfLinkSerialConnector;
import org.openhab.binding.rflink.exceptions.RfLinkException;
import org.openhab.binding.rflink.internal.EventMessageListener;
import org.openhab.binding.rflink.internal.discovery.RfLinkThingDiscoveryService;
import org.openhab.binding.rflink.packet.RfLinkPacket;
import org.openhab.binding.rflink.packet.RfLinkPacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link RfLinkBridgeHandler} is the handler for a RFLink transceivers. All
 * {@link RfLinkThingHandler}s use the {@link RfLinkBridgeHandler} to execute the
 * actual commands.
 *
 * @author Cyril Cauchois - Initial contribution
 * @author John Jore - Added initial support to transmit messages to devices
 * @author Marvyn Zalewski - Added getConfiguration Method
 * @author cartemere - refactor to provide Handler config to the Device + rework Discovery
 */
public class RfLinkBridgeHandler extends BaseBridgeHandler {

    private Logger logger = LoggerFactory.getLogger(RfLinkBridgeHandler.class);

    private RfLinkConnectorInterface connector = null;

    private RfLinkThingDiscoveryService discoveryService = null;
    private List<EventMessageListener> eventMessageListeners = new CopyOnWriteArrayList<>();

    private RfLinkBridgeConfiguration configuration = null;
    private ScheduledFuture<?> connectorTask = null;
    private ScheduledFuture<?> keepAliveTask = null;
    private RfLinkBridgeTxQueue transmitQueue = new RfLinkBridgeTxQueue(this);
    private RfLinkBridgeRxListener eventListener = new RfLinkBridgeRxListener(this);

    public RfLinkBridgeHandler(Bridge br) {
        super(br);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            // do nothing
        } else if (command instanceof StringType) {
            try {
                RfLinkPacketType packetType = null;
                if ("rawdata".equals(channelUID.getId())) {
                    packetType = RfLinkPacketType.OUTPUT;
                } else if ("echodata".equals(channelUID.getId())) {
                    packetType = RfLinkPacketType.ECHO;
                } else {
                    logger.error("ChannelUID" + channelUID + " not supported on Bridge");
                }
                RfLinkPacket packet = new RfLinkPacket(packetType, ((StringType) command).toString());
                processPackets(Collections.singleton(packet));
            } catch (RfLinkException e) {
                logger.error("Unable to send command : " + command, e);
            }
        } else {
            logger.debug("Bridge command type not supported : " + command);
        }
    }

    @Override
    public void dispose() {
        logger.debug("Handler disposed.");

        if (connector != null) {
            connector.removeEventListener(eventListener);
            connector.disconnect();
        }

        if (connectorTask != null && !connectorTask.isCancelled()) {
            connectorTask.cancel(true);
            connectorTask = null;
        }

        if (keepAliveTask != null && !keepAliveTask.isCancelled()) {
            keepAliveTask.cancel(true);
            keepAliveTask = null;
        }

        super.dispose();
    }

    @Override
    public void initialize() {
        logger.debug("Initializing RFLink bridge handler");
        updateStatus(ThingStatus.OFFLINE);

        configuration = getConfigAs(RfLinkBridgeConfiguration.class);

        if (connectorTask == null || connectorTask.isCancelled()) {
            connectorTask = scheduler.scheduleWithFixedDelay(new Runnable() {

                @Override
                public void run() {
                    if (thing.getStatus() != ThingStatus.ONLINE) {
                        logger.debug("Checking RFLink transceiver connection, thing status = {}", thing.getStatus());
                        connect();
                    }
                }
            }, 0, 60, TimeUnit.SECONDS);
        }

        if (configuration.keepAlivePeriod > 0 && (keepAliveTask == null || keepAliveTask.isCancelled())) {
            keepAliveTask = scheduler.scheduleWithFixedDelay(() -> {
                if (thing.getStatus() == ThingStatus.ONLINE) {
                    try {
                        processPackets(Collections.singleton(new RfLinkPacket(RfLinkPacketType.OUTPUT, "10;PING;")));
                    } catch (RfLinkException ex) {
                        logger.error("PING call failed on Bridge", ex);
                    }
                }

            }, configuration.keepAlivePeriod, configuration.keepAlivePeriod, TimeUnit.SECONDS);
        }

    }

    private void connect() {
        logger.debug("Connecting to RFLink transceiver on {} port", configuration.serialPort);

        try {

            if (connector == null) {
                connector = new RfLinkSerialConnector();
            }

            if (connector != null) {
                connector.disconnect();
                connector.connect(configuration.serialPort, configuration.baudRate);
                connector.addEventListener(eventListener);
                logger.debug("RFLink receiver started");
                updateStatus(ThingStatus.ONLINE);
            } else {
                logger.debug("connector is null");
            }
        } catch (Exception e) {
            logger.error("Connection to RFLink transceiver failed: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE);
        } catch (UnsatisfiedLinkError e) {
            logger.error("Error occured when trying to load native library for OS '{}' version '{}', processor '{}'",
                    System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"), e);
            updateStatus(ThingStatus.OFFLINE);
        }
    }

    public synchronized void processPackets(Collection<RfLinkPacket> rfLinkPackets) throws RfLinkException {
        Collection<RfLinkPacket> echoPackets = new ArrayList<RfLinkPacket>();
        Collection<RfLinkPacket> sendPackets = new ArrayList<RfLinkPacket>();
        for (RfLinkPacket rfLinkPacket : rfLinkPackets) {
            if (RfLinkPacketType.ECHO.equals(rfLinkPacket.getType())) {
                echoPackets.add(rfLinkPacket);
            } else if (RfLinkPacketType.OUTPUT.equals(rfLinkPacket.getType())) {
                sendPackets.add(rfLinkPacket);
            }
        }
        if (!echoPackets.isEmpty()) {
            for (RfLinkPacket echoPacket : echoPackets) {
                eventListener.packetReceived(echoPacket);
            }
        } else if (!sendPackets.isEmpty()) {
            try {
                transmitQueue.enqueue(sendPackets);
            } catch (IOException e) {
                logger.error("I/O Error", e);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            }
        }
    }

    public boolean registerEventMessageListener(EventMessageListener eventMessageListener) {
        if (eventMessageListener == null) {
            throw new IllegalArgumentException("It's not allowed to pass a null eventMessageListener.");
        }
        return eventMessageListeners.contains(eventMessageListener) ? false
                : eventMessageListeners.add(eventMessageListener);
    }

    public boolean removeEventMessageListener(EventMessageListener eventMessageListener) {
        if (eventMessageListener == null) {
            throw new IllegalArgumentException("It's not allowed to pass a null eventMessageListener.");
        }
        return eventMessageListeners.remove(eventMessageListener);
    }

    public RfLinkBridgeConfiguration getConfiguration() {
        return configuration;
    }

    public RfLinkThingDiscoveryService getDiscoveryService() {
        return discoveryService;
    }

    public void setDiscoveryService(RfLinkThingDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    public RfLinkConnectorInterface getConnector() {
        return connector;
    }

    public List<EventMessageListener> getEventMessageListeners() {
        return eventMessageListeners;
    }

    @Override
    public void updateStatus(ThingStatus status, ThingStatusDetail statusDetail) {
        // needs to be visible for Rx & Tx to update the status
        super.updateStatus(status, statusDetail);
    }

    public boolean isDiscoveryEnabled() {
        return !getConfiguration().disableDiscovery;
    }

}
