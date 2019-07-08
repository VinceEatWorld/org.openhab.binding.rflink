/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rflink.message;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.rflink.config.RfLinkDeviceConfiguration;
import org.openhab.binding.rflink.event.RfLinkDataParser;
import org.openhab.binding.rflink.exceptions.RfLinkException;
import org.openhab.binding.rflink.exceptions.RfLinkNotImpException;
import org.openhab.binding.rflink.packet.RfLinkPacket;
import org.openhab.binding.rflink.packet.RfLinkPacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for RFLink data classes. All other data classes should extend this class.
 *
 * @author Cyril Cauchois - Initial contribution
 * @author cartemere - review Message management. add Reverse support for Switch/RTS
 * @author cartemere - Massive rework : split message vs event
 * @author cartemere - support RTS SHOW messages
 */
public class RfLinkMessage {

    private Logger logger = LoggerFactory.getLogger(RfLinkMessage.class);

    public final static String FIELDS_DELIMITER = ";";
    public final static String RTS_SHOW_DELIMITER = " ";
    public final static String VALUE_DELIMITER = "=";
    public final static String RTS_SHOW_VALUE_DELIMITER = ":";
    public final static String ID_DELIMITER = "-";

    private final static String NODE_NUMBER_FROM_GATEWAY = "20";
    private final static String NODE_NUMBER_TO_GATEWAY = "10";

    private static final String DEVICE_MASK_8 = "00000000";
    private static final String DEVICE_MASK_6 = "000000";

    public String rawMessage;
    private RfLinkPacketType packetType;
    private byte seqNbr = 0;
    private String protocol = null; // protocol Name (RTS, X10, etc.)
    protected String deviceId = null; // device Identifier (Rolling code, etc.)
    protected String deviceSubId = null; // switch Identifier (SWITCH=XX, etc.)
    protected Map<String, String> attributes = new HashMap<>();
    private boolean eligibleForProcessing = false;
    private boolean eligibleForDiscovery = false;

    public RfLinkMessage(RfLinkDeviceConfiguration config, ChannelUID channelUID, Command command)
            throws RfLinkNotImpException, RfLinkException {
        packetType = RfLinkPacketType.OUTPUT;
        String[] elements = config.deviceId.split(ID_DELIMITER);
        if (elements.length > 1) {
            protocol = elements[0];
            deviceId = elements[1];
            if (elements.length > 2) {
                deviceSubId = elements[2];
            }
            eligibleForProcessing = true;
        }
    }

    public RfLinkMessage(RfLinkPacket packet) {
        rawMessage = packet.getPacket();
        packetType = packet.getType();
        if (isStandardInputMessage()) {
            String[] elements = packet.getPacket().split(FIELDS_DELIMITER, 4);
            int size = elements.length;
            // Every message should have at least 5 parts
            // Example : 20;31;Mebus;ID=c201;TEMP=00cf;
            // Example : 20;02;RTS;ID=82e8ac;SWITCH=01;CMD=DOWN;
            // Example : 20;07;Debug;RTS P1;a729000068622e;
            if (size == 4 && !elements[3].isEmpty()) {
                // first element should be "20"
                seqNbr = (byte) Integer.parseInt(elements[1], 16);
                protocol = RfLinkDataParser.cleanString(elements[2]);
                // build the key>value map
                extractAttributes(attributes, elements[3], FIELDS_DELIMITER, VALUE_DELIMITER);
                deviceId = attributes.get("ID");
                deviceSubId = attributes.get("SWITCH");
                eligibleForProcessing = true;
                eligibleForDiscovery = true;
            }
        } else if (isRTSShowInputMessage()) {
            // RfLink protocol is "odd" on RTS show command : we must preprocess the String to get something parseable
            String formatedPacket = packet.getPacket().replaceAll(": ", ":");
            String[] elements = formatedPacket.split(" ", 2); // take all KeyValues after the "RTS" flag
            protocol = RfLinkDataParser.cleanString(elements[0]);
            extractAttributes(attributes, elements[1], RTS_SHOW_DELIMITER, RTS_SHOW_VALUE_DELIMITER);
            deviceId = attributes.get("Address");
            deviceSubId = "0"; // switch=0 for RTS (see RfLink protocol reference)
            if (!"FFFFFF".equalsIgnoreCase(deviceId) && !"FFFF".equalsIgnoreCase("RC")) {
                // ignore non initialized rows in the EPROM
                eligibleForDiscovery = true;
            }
        }
    }

    private boolean isStandardInputMessage() {
        return getRawMessage() != null && getRawMessage().startsWith(NODE_NUMBER_FROM_GATEWAY);
    }

    private boolean isRTSShowInputMessage() {
        return getRawMessage() != null && getRawMessage().startsWith("RTS");
    }

    public void extractAttributes(Map<String, String> attributesMap, String attributesAsString, String fieldsDelimiter,
            String valueDelimiter) {
        String[] elements = attributesAsString.split(fieldsDelimiter);
        for (String element : elements) {
            String[] keyValue = element.split(valueDelimiter, 2);
            if (keyValue.length > 1) {
                // Raw values are stored, and will be decoded by sub implementations
                attributesMap.put(keyValue[0], keyValue[1]);
            }
        }
    }

    @Override
    public String toString() {
        return getLabel();
    }

    public String getLabel() {
        if (isRTSShowInputMessage()) {
            return "[" + attributes.get("Record") + "] " + getDeviceKey() + " RC=" + attributes.get("RC");
        }
        return getDeviceKey();
    }

    public String getRawMessage() {
        return rawMessage;
    }

    public RfLinkPacketType getPacketType() {
        return packetType;
    }

    public String getDeviceKey() {
        String deviceIdKey = getBaseDeviceKey();
        if (getDeviceSubId() != null) {
            deviceIdKey += ID_DELIMITER + getDeviceSubId();
        }
        return deviceIdKey;
    }

    public String getBaseDeviceKey() {
        String deviceIdKey = getProtocol() + ID_DELIMITER + getDeviceId();
        return deviceIdKey;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getDeviceSubId() {
        return deviceSubId;
    }

    public boolean isEligibleForProcessing() {
        return eligibleForProcessing;
    }

    public boolean isEligibleForDiscovery() {
        return eligibleForDiscovery;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public Collection<String> getAttributesKeys() {
        return attributes.keySet();
    }

    public String buildPacket(String suffix) {
        // encode the message
        StringBuilder packet = new StringBuilder();
        appendToMessage(packet, NODE_NUMBER_TO_GATEWAY); // To Bridge
        appendToMessage(packet, this.getProtocol()); // Protocol
        appendToMessage(packet, formatDeviceId(deviceId));
        if (deviceSubId != null) {
            // some protocols, like X10 / Switch / RTS use multiple id parts
            appendToMessage(packet, deviceSubId);
        }
        if (suffix != null && !suffix.isEmpty()) {
            appendToMessage(packet, suffix);
        }

        logger.debug("Decoded message to be sent: {}, deviceName: {}, deviceChannel: {}, primaryId: {}", packet,
                this.getProtocol(), deviceId, deviceSubId);

        return packet.toString();
    }

    public RfLinkPacket buildRfLinkPacket(RfLinkPacketType type, String suffix) {
        String packet = buildPacket(suffix);
        return new RfLinkPacket(type, packet);
    }

    private String formatDeviceId(String deviceId) {
        // convert deviceId to 6 or 8 char String, RfLink spec is a bit unclear on this, but seems to work...
        if (deviceId.length() <= 6) {
            return DEVICE_MASK_6.substring(deviceId.length()) + deviceId;
        } else if (deviceId.length() <= 8) {
            return DEVICE_MASK_8.substring(deviceId.length()) + deviceId;
        } else {
            // seems SOOOO long, don't know if the bridge will handle this
            return deviceId;
        }
    }

    private void appendToMessage(StringBuilder message, String element) {
        message.append(element).append(FIELDS_DELIMITER);
    }

    public RfLinkPacket buildEchoPacket(String echoPattern) {
        if (getRawMessage() != null) {
            String echoPacket = getRawMessage();
            Map<String, String> overridenAttributes = new HashMap<>();
            extractAttributes(overridenAttributes, echoPattern, FIELDS_DELIMITER, VALUE_DELIMITER);
            for (String overridenAttributeKey : overridenAttributes.keySet()) {
                String sourceAttribute = overridenAttributeKey + "=" + getAttributes().get(overridenAttributeKey);
                String targetAttribute = overridenAttributeKey + "=" + overridenAttributes.get(overridenAttributeKey);
                echoPacket = echoPacket.replace(sourceAttribute, targetAttribute);
            }
            if (!getRawMessage().equalsIgnoreCase(echoPacket)) {
                return new RfLinkPacket(RfLinkPacketType.ECHO, echoPacket);
            }
        } else {
            // no initial raw message : unable to build echo message
        }
        return null;
    }

}
