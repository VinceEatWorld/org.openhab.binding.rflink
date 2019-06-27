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
import java.util.Map;
import java.util.function.Predicate;

import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.rflink.config.RfLinkDeviceConfiguration;
import org.openhab.binding.rflink.exceptions.RfLinkException;
import org.openhab.binding.rflink.exceptions.RfLinkNotImpException;
import org.openhab.binding.rflink.message.RfLinkMessage;
import org.openhab.binding.rflink.packet.RfLinkPacket;

/**
 * This interface defines interface which every device class should implement.
 *
 * @author Cyril Cauchois - Initial contribution
 * @author cartemere - refactoring + define Predicate
 * @author cartemere - use Config in initialize
 */
public interface RfLinkDevice {

    /**
     * Lambda function to check the eligibility of an incoming RfLinkMessage.
     *
     * @return a Predicate returning 'true' if the Message is eligible, 'false' otherwise
     */
    public Predicate<RfLinkMessage> eligibleMessageFunction();

    /**
     * Procedure generate RfLinkPacket[s] to send to the bridge
     *
     * @return Collection of RfLinkPacket messages to be send over serial (OUTPUT type)
     */

    public Collection<RfLinkPacket> buildOutputPackets();

    /**
     * Procedure generate RfLinkPackets[s] to send to the handler as Incoming messages (notification service)
     * 
     * @return Collection of RfLinkPacket[s] to handle as incoming events
     *         (ECHO type). Several elements in case of composite command
     */
    public Collection<RfLinkPacket> buildEchoPackets();

    /**
     * Procedure to get device unique Identifier
     *
     * @return the device Key.
     */
    String getKey();

    /**
     * Procedure to get device name.
     *
     * @return device Name.
     */
    String getProtocol();

    /**
     * Procedure to thingType linked to message.
     *
     * @return Thing type.
     */
    ThingTypeUID getThingType();

    /**
     * Get all the values in form of smarthome states
     *
     * @return
     */
    Map<String, State> getStates();

    /**
     * Get a specific State from the Device instance
     *
     * @param key the state keyword
     * @return the related State, null if not found
     */
    State getState(String key);

    /**
     * Initializes Device for transmission message
     *
     * @throws RfLinkException
     */
    void initializeFromChannel(RfLinkDeviceConfiguration config, ChannelUID channelUID, Command command)
            throws RfLinkNotImpException, RfLinkException;

    /**
     * Initializes Device from reception message
     *
     * @param config  TODO
     * @param message the RfLink message received from the bridge
     *
     * @throws RfLinkNotImpException
     * @throws RfLinkException
     */
    void initializeFromMessage(RfLinkDeviceConfiguration config, RfLinkMessage message)
            throws RfLinkNotImpException, RfLinkException;
}
