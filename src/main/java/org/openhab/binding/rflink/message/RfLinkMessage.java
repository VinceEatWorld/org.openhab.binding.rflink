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
import org.openhab.binding.rflink.device.RfLinkDataParser;
import org.openhab.binding.rflink.exceptions.RfLinkException;
import org.openhab.binding.rflink.exceptions.RfLinkNotImpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for RFLink data classes. All other data classes should extend this class.
 *
 * @author Cyril Cauchois - Initial contribution
 * @author cartemere - review Message management. add Reverse support for Switch/RTS
 * @author cartemere - Massive rework : split message vs device
 */
public class RfLinkMessage {

    private Logger logger = LoggerFactory.getLogger(RfLinkMessage.class);

    public final static String FIELDS_DELIMITER = ";";
    public final static String VALUE_DELIMITER = "=";
    public final static String ID_DELIMITER = "-";

    private final static String NODE_NUMBER_FROM_GATEWAY = "20";
    private final static String NODE_NUMBER_TO_GATEWAY = "10";

    private static final String DEVICE_MASK = "00000000";

    private final static int MINIMAL_SIZE_MESSAGE = 5;

    public String rawMessage;
    private byte seqNbr = 0;
    private String protocol; // protocol Name (RTS, X10, etc.)
    protected String deviceId; // device Identifier (Rolling code, etc.)
    protected String deviceSubId; // switch Identifier (SWITCH=XX, etc.)
    protected Map<String, String> attributes = new HashMap<>();

    public RfLinkMessage(RfLinkDeviceConfiguration config, ChannelUID channelUID, Command command)
            throws RfLinkNotImpException, RfLinkException {
        String[] elements = config.deviceId.split(ID_DELIMITER);
        if (elements.length > 1) {
            protocol = elements[0];
            deviceId = elements[1];
            if (elements.length > 2) {
                deviceSubId = elements[2];
            }
        }
    }

    public RfLinkMessage(String data) {
        rawMessage = data;
        final String[] elements = data.split(FIELDS_DELIMITER);
        final int size = elements.length;
        // Every message should have at least 5 parts
        // Example : 20;31;Mebus;ID=c201;TEMP=00cf;
        // Example : 20;02;RTS;ID=82e8ac;SWITCH=01;CMD=DOWN;
        // Example : 20;07;Debug;RTS P1;a729000068622e;
        if (size >= 3) {
            // first element should be "20"
            if (NODE_NUMBER_FROM_GATEWAY.equals(elements[0])) {
                seqNbr = (byte) Integer.parseInt(elements[1], 16);
                protocol = RfLinkDataParser.cleanString(elements[2]);
                // build the key>value map
                if (size >= 4) {
                    for (int i = 3; i < size; i++) {
                        String[] keyValue = elements[i].split(VALUE_DELIMITER, 2);
                        if (keyValue.length > 1) {
                            // Raw values are stored, and will be decoded by sub implementations
                            attributes.put(keyValue[0], keyValue[1]);
                        }
                    }
                }
                deviceId = attributes.get("ID");
                deviceSubId = attributes.get("SWITCH");
            }
        }
    }

    @Override
    public String toString() {
        return getDeviceKey();
    }

    public String getRawMessage() {
        return rawMessage;
    }

    public String getDeviceKey() {
        String deviceIdKey = protocol + ID_DELIMITER + deviceId;
        if (deviceSubId != null) {
            deviceIdKey += ID_DELIMITER + deviceSubId;
        }
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

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public Collection<String> getAttributesKeys() {
        return attributes.keySet();
    }

    public String buildMessage(String suffix) {
        // encode the message
        StringBuilder message = new StringBuilder();
        appendToMessage(message, NODE_NUMBER_TO_GATEWAY); // To Bridge
        appendToMessage(message, this.getProtocol()); // Protocol
        // convert channel to 8 character string, RfLink spec is a bit unclear on this, but seems to work...
        appendToMessage(message, DEVICE_MASK.substring(deviceId.length()) + deviceId);
        if (deviceSubId != null) {
            // some protocols, like X10 / Switch / RTS use multiple id parts
            appendToMessage(message, deviceSubId);
        }
        if (suffix != null && !suffix.isEmpty()) {
            appendToMessage(message, suffix);
        }

        logger.debug("Decoded message to be sent: {}, deviceName: {}, deviceChannel: {}, primaryId: {}", message,
                this.getProtocol(), deviceId, deviceSubId);

        return message.toString();
    }

    private void appendToMessage(StringBuilder message, String element) {
        message.append(element).append(FIELDS_DELIMITER);
    }

}
