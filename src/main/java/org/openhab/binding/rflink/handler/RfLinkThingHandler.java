/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rflink.handler;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.rflink.config.RfLinkDeviceConfiguration;
import org.openhab.binding.rflink.event.RfLinkEvent;
import org.openhab.binding.rflink.event.RfLinkEventFactory;
import org.openhab.binding.rflink.event.RfLinkRtsEvent;
import org.openhab.binding.rflink.exceptions.RfLinkException;
import org.openhab.binding.rflink.exceptions.RfLinkNotImpException;
import org.openhab.binding.rflink.internal.EventMessageListener;
import org.openhab.binding.rflink.message.RfLinkMessage;
import org.openhab.binding.rflink.packet.RfLinkPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link RfLinkThingHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Cyril Cauchois - Initial contribution
 * @author John Jore - Added initial support to send commands to devices
 * @author Arjan Mels - Added option to repeat messages
 * @author cartemere - handle RTS position tracking
 * @author cartemere - refactor to provide Handler config to the Device
 */
public class RfLinkThingHandler extends BaseThingHandler implements EventMessageListener {

    public static final int TIME_BETWEEN_COMMANDS = 50;
    private Logger logger = LoggerFactory.getLogger(RfLinkThingHandler.class);

    private RfLinkRtsPositionHandler rtsPositionTracker = null;

    private RfLinkBridgeHandler bridgeHandler;

    private RfLinkDeviceConfiguration config;

    public RfLinkThingHandler(Thing thing) {
        super(thing);
    }

    protected ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    protected RfLinkBridgeHandler getBridgeHandler() {
        return bridgeHandler;
    }

    public RfLinkDeviceConfiguration getConfiguration() {
        return config;
    }

    @Override
    protected void updateState(ChannelUID channelUID, State state) {
        super.updateState(channelUID, state);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Received channel: {}, command: {}", channelUID, command);
        if (bridgeHandler != null) {
            if (command instanceof RefreshType) {
                // Not supported
            } else {
                try {
                    RfLinkEvent event = RfLinkEventFactory.createEventFromType(getThing().getThingTypeUID());
                    event.initializeFromChannel(config, channelUID, command);
                    processEchoPackets(event);
                    if (handleRtsPositionTracker(this, event)) {
                        // need specific handling : the command is processed by the tracker
                    } else {
                        processOutputPackets(event);
                        updateThingStates(event);
                    }
                } catch (RfLinkNotImpException e) {
                    logger.error("Message not supported: {}", e.getMessage());
                } catch (RfLinkException e) {
                    logger.error("Transmitting error: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public boolean canHandleMessage(RfLinkMessage incomingMessage) {
        if (config != null && config.deviceId != null
                && config.deviceId.equalsIgnoreCase(incomingMessage.getDeviceKey())) {
            return true;
        }
        return false;
    }

    @Override
    public void handleIncomingMessage(ThingUID bridge, RfLinkMessage incomingMessage) throws Exception {
        RfLinkEvent event = RfLinkEventFactory.createEventFromMessage(incomingMessage);
        event.initializeFromMessage(config, incomingMessage);
        processEchoPackets(event);
        updateStatus(ThingStatus.ONLINE);
        handleRtsPositionTracker(this, event);
        updateThingStates(event);
    }

    private void processOutputPackets(RfLinkEvent event) throws RfLinkException {
        int repeats = Math.min(Math.max(getConfiguration().repeats, 1), 20);
        Collection<RfLinkPacket> packets = event.buildOutputPackets();
        for (int i = 0; i < repeats; i++) {
            bridgeHandler.processPackets(packets);
        }
    }

    private void processEchoPackets(RfLinkEvent event) throws RfLinkException {
        Collection<RfLinkPacket> echoPackets = event.buildEchoPackets();
        bridgeHandler.processPackets(echoPackets);
    }

    @Override
    public void initialize() {
        config = getConfigAs(RfLinkDeviceConfiguration.class);
        logger.debug("Initializing thing {}, deviceId={}", getThing().getUID(), config.deviceId);
        Bridge currentBridge = getBridge();
        if (currentBridge == null) {
            initializeBridge(null, null);
        } else {
            initializeBridge(currentBridge.getHandler(), currentBridge.getStatus());
        }
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        logger.debug("bridgeStatusChanged {} for thing {}", bridgeStatusInfo, getThing().getUID());
        Bridge currentBridge = getBridge();
        if (currentBridge == null) {
            initializeBridge(null, bridgeStatusInfo.getStatus());
        } else {
            initializeBridge(currentBridge.getHandler(), bridgeStatusInfo.getStatus());
        }
    }

    private void initializeBridge(ThingHandler thingHandler, ThingStatus bridgeStatus) {
        logger.debug("initializeBridge {} for thing {}", bridgeStatus, getThing().getUID());

        config = getConfigAs(RfLinkDeviceConfiguration.class);
        if (config.deviceId == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "RFLink device missing deviceId");
        } else if (thingHandler != null && bridgeStatus != null) {
            bridgeHandler = (RfLinkBridgeHandler) thingHandler;
            bridgeHandler.registerEventMessageListener(this);

            if (bridgeStatus == ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            }
        } else {
            updateStatus(ThingStatus.OFFLINE);
        }

        // super.bridgeHandlerInitialized(thingHandler, bridge);
    }

    @Override
    public void dispose() {
        logger.debug("Thing {} disposed.", getThing().getUID());
        if (bridgeHandler != null) {
            bridgeHandler.removeEventMessageListener(this);
        }
        bridgeHandler = null;
        super.dispose();
    }

    protected void updateThingStates(RfLinkEvent event) {
        Map<String, State> map = event.getStates();
        for (String channel : map.keySet()) {
            logger.debug("Update channel: {}, state: {}", channel, map.get(channel));
            updateState(new ChannelUID(getThing().getUID(), channel), map.get(channel));
        }
    }

    private boolean handleRtsPositionTracker(RfLinkThingHandler handler, RfLinkEvent event) {
        boolean processed = false;
        try {
            if (config.isRtsPositionTrackerEnabled()) {
                getPositionTracker(handler).handleCommand((RfLinkRtsEvent) event);
                processed = true;
            }
        } catch (Exception ex) {
            logger.error("RTS position tracker failed, processing event=" + event, ex);
        }
        return processed;
    }

    private RfLinkRtsPositionHandler getPositionTracker(RfLinkThingHandler handler) {
        if (rtsPositionTracker == null) {
            rtsPositionTracker = new RfLinkRtsPositionHandler(handler);
        }
        return rtsPositionTracker;
    }

    @Override
    public String toString() {
        return "RfLinkHandler [" + config + "]";
    }

}
