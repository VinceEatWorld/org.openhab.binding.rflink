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
import java.util.HashMap;
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
import org.openhab.binding.rflink.device.RfLinkDevice;
import org.openhab.binding.rflink.device.RfLinkDeviceFactory;
import org.openhab.binding.rflink.device.RfLinkRtsDevice;
import org.openhab.binding.rflink.exceptions.RfLinkException;
import org.openhab.binding.rflink.exceptions.RfLinkNotImpException;
import org.openhab.binding.rflink.internal.DeviceMessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link RfLinkHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Cyril Cauchois - Initial contribution
 * @author John Jore - Added initial support to send commands to devices
 * @author Arjan Mels - Added option to repeat messages
 * @author cartemere - handle RTS position tracking
 */
public class RfLinkHandler extends BaseThingHandler implements DeviceMessageListener {

    public static final int TIME_BETWEEN_COMMANDS = 50;
    private Logger logger = LoggerFactory.getLogger(RfLinkHandler.class);

    private static Map<String, RfLinkRtsPositionHandler> shutterInfosMap = new HashMap<>();

    private RfLinkBridgeHandler bridgeHandler;

    private RfLinkDeviceConfiguration config;

    public RfLinkHandler(Thing thing) {
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
                    RfLinkDevice device = RfLinkDeviceFactory.createDeviceFromType(getThing().getThingTypeUID());
                    device.initializeFromChannel(getConfigAs(RfLinkDeviceConfiguration.class), channelUID, command);
                    if (isRtsPositionTrackerEnabled(device)) {
                        // need specific handling : the command is processed by the tracker
                        handleRtsPositionTracker(this, device);
                    } else {
                        int repeats = Math.min(Math.max(getConfiguration().repeats, 1), 20);
                        Collection<String> packets = device.buildPackets();
                        for (int i = 0; i < repeats; i++) {
                            bridgeHandler.sendPackets(packets);
                        }
                        updateThingStates(device);
                    }
                } catch (RfLinkNotImpException e) {
                    logger.error("Message not supported: {}", e.getMessage());
                } catch (RfLinkException e) {
                    logger.error("Transmitting error: {}", e.getMessage());
                }
            }
        }
    }

    /**
     */
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
            bridgeHandler.registerDeviceStatusListener(this);

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
            bridgeHandler.unregisterDeviceStatusListener(this);
        }
        bridgeHandler = null;
        super.dispose();
    }

    @Override
    public void onDeviceMessageReceived(ThingUID bridge, RfLinkDevice device) {
        String id = device.getKey();
        // logger.debug("Matching Message from bridge {} from device [{}] with [{}]", bridge.toString(), id,
        // config.deviceId);
        if (config.deviceId.equals(id)) {
            logger.debug("Message from bridge {} from device [{}] type [{}] matched", bridge.toString(), id,
                    device.getClass().getSimpleName());
            updateStatus(ThingStatus.ONLINE);
            if (isRtsPositionTrackerEnabled(device)) {
                handleRtsPositionTracker(this, device);
            } else {
                updateThingStates(device);
            }
        }
    }

    protected void updateThingStates(RfLinkDevice device) {
        Map<String, State> map = device.getStates();
        for (String channel : map.keySet()) {
            logger.debug("Update channel: {}, state: {}", channel, map.get(channel));
            updateState(new ChannelUID(getThing().getUID(), channel), map.get(channel));
        }
    }

    private boolean isRtsPositionTrackerEnabled(RfLinkDevice device) {
        if (device instanceof RfLinkRtsDevice && getConfiguration().shutterDuration > 0) {
            return true;
        }
        return false;
    }

    private void handleRtsPositionTracker(RfLinkHandler handler, RfLinkDevice device) {
        try {
            RfLinkRtsPositionHandler shutterInfos = getShutterInfos(handler, device);
            shutterInfos.handleCommand((RfLinkRtsDevice) device);
        } catch (Exception ex) {
            logger.error("OOOPS, processing device=" + device, ex);
        }
    }

    private RfLinkRtsPositionHandler getShutterInfos(RfLinkHandler handler, RfLinkDevice device) {
        RfLinkRtsPositionHandler shutterInfos = shutterInfosMap.get(device.getKey());
        if (shutterInfos == null) {
            synchronized (shutterInfosMap) {
                shutterInfos = shutterInfosMap.get(device.getKey());
                if (shutterInfos == null) {
                    logger.debug("RTSHandler: create RtsHandler for " + device.getKey() + " on " + getThing().getUID());
                    shutterInfos = new RfLinkRtsPositionHandler(handler);
                    shutterInfosMap.put(device.getKey(), shutterInfos);
                }
            }
        }
        return shutterInfos;
    }

}
