/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rflink.device;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Predicate;

import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.rflink.config.RfLinkDeviceConfiguration;
import org.openhab.binding.rflink.exceptions.RfLinkException;
import org.openhab.binding.rflink.exceptions.RfLinkNotImpException;
import org.openhab.binding.rflink.message.RfLinkMessage;
import org.openhab.binding.rflink.packet.RfLinkPacket;
import org.openhab.binding.rflink.packet.RfLinkPacketType;
import org.openhab.binding.rflink.type.RfLinkTypeUtils;

/**
 * RfLink Abstract message - must be extended in specific implementations
 *
 * @author Cyril Cauchois - Initial contribution
 * @author cartemere - review Message management. add Reverse support for Switch/RTS
 * @author cartemere - Massive rework : split message vs device
 * @author cartemere - simplify initFromChannel & configuration management
 */
public abstract class RfLinkAbstractDevice implements RfLinkDevice {

    private RfLinkMessage message = null;
    private RfLinkDeviceConfiguration config = null;

    @Override
    public void initializeFromMessage(RfLinkDeviceConfiguration config, RfLinkMessage message) {
        setConfig(config);
        this.message = message;
        if (getConfig() != null && getConfig().hasLinkedAddressId()) {
            // must "echo" the input command to the linked DeviceId
        }
    }

    @Override
    public void initializeFromChannel(RfLinkDeviceConfiguration config, ChannelUID channelUID, Command command)
            throws RfLinkNotImpException, RfLinkException {
        setConfig(config);
        message = new RfLinkMessage(config, channelUID, command);
        if (!handleCommandTransmission()) {
            throw new RfLinkNotImpException("Message handler for " + config + "/" + channelUID
                    + " does not support command transmission " + command);
        }
    }

    protected boolean handleCommandTransmission() {
        return false;
    }

    protected void setConfig(RfLinkDeviceConfiguration config) {
        this.config = config;
    }

    @Override
    public Predicate<RfLinkMessage> eligibleMessageFunction() {
        // by default = do NOT handle any kind of message (to override in subclasses)
        return (message) -> false;
    }

    public RfLinkMessage getMessage() {
        return message;
    }

    public RfLinkDeviceConfiguration getConfig() {
        return config;
    }

    @Override
    public Collection<RfLinkPacket> buildOutputPackets() {
        return Collections.singleton(getMessage().buildRfLinkPacket(RfLinkPacketType.OUTPUT, getCommandSuffix()));
    }

    @Override
    public Collection<RfLinkPacket> buildEchoPackets() {
        if (config.hasLinkedAddressId()) {
            if (getMessage() != null) {
                String echoMessage = getMessage().getRawMessage();
                if (echoMessage != null) {
                    String sourceAddressId = "ID=" + getMessage().getDeviceId();
                    String targetAddressId = "ID=" + config.linkedAddressId;
                    if (echoMessage.contains(sourceAddressId)) {
                        // take the input message and replace the initial AddressId by the linked addressId
                        echoMessage = echoMessage.replace(sourceAddressId, targetAddressId);
                        RfLinkPacket packet = new RfLinkPacket(RfLinkPacketType.ECHO, echoMessage);
                        return Collections.singletonList(packet);
                    } else {
                        // original AddressId not found in the input Message
                    }
                } else {
                    // no initial raw message : unable to build echo message
                }
            } else {
                // no initial message (why are we here ?!?)
            }
        }
        return Collections.emptyList();
    }

    // to override in subClasses if needed
    public String getCommandSuffix() {
        return null;
    }

    @Override
    public String getKey() {
        return getMessage().getDeviceKey();
    }

    @Override
    public String getProtocol() {
        return getMessage().getProtocol();
    }

    @Override
    public Map<String, State> getStates() {
        return Collections.<String, State> emptyMap();
    }

    @Override
    public State getState(String key) {
        return getStates().get(key);
    }

    protected Command getEffectiveCommand(Command inputCommand) {
        if (config != null && config.isCommandReversed) {
            // reverse the command
            Command effectiveCommand = (Command) RfLinkTypeUtils.getAntonym(inputCommand);
            if (effectiveCommand == null) {
                // no reverse available : defaulting
                return inputCommand;
            }
            return effectiveCommand;
        }
        return inputCommand;
    }

    @Override
    public String toString() {
        return "" + getClass().getSimpleName() + " [message=" + message + ", config=" + config + ", ]";
    }

}
