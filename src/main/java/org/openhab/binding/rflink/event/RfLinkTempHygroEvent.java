/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rflink.event;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.eclipse.smarthome.core.library.types.DateTimeType;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.rflink.RfLinkBindingConstants;
import org.openhab.binding.rflink.config.RfLinkDeviceConfiguration;
import org.openhab.binding.rflink.exceptions.RfLinkException;
import org.openhab.binding.rflink.message.RfLinkMessage;

/**
 * RfLink data class for temperature message.
 *
 * @author Marek Majchrowski - Initial contribution
 */

public class RfLinkTempHygroEvent extends RfLinkAbstractEvent {
    private static final String KEY_TEMPERATURE = "TEMP";
    private static final String KEY_HUMIDITY = "HUM";
    private static final String KEY_HUMIDITY_STATUS = "HSTATUS";
    private static final String KEY_BATTERY = "BAT";
    private static final Collection<String> KEYS = Arrays.asList(KEY_TEMPERATURE, KEY_HUMIDITY, KEY_HUMIDITY_STATUS,
            KEY_BATTERY);

    public double temperature = 0;
    public int humidity = 0;
    public String humidity_status = "UNKNOWN";
    public Commands battery_status = Commands.OFF;

    public enum Commands {
        OFF("OK", OnOffType.OFF),
        ON("LOW", OnOffType.ON),

        UNKNOWN("", null);

        private final String command;
        private final OnOffType onOffType;

        Commands(String command, OnOffType onOffType) {
            this.command = command;
            this.onOffType = onOffType;
        }

        public String getText() {
            return this.command;
        }

        public OnOffType getOnOffType() {
            return this.onOffType;
        }

        public static Commands fromString(String text) {
            if (text != null) {
                for (Commands c : Commands.values()) {
                    if (text.equalsIgnoreCase(c.command)) {
                        return c;
                    }
                }
            }
            return null;
        }

        public static Commands fromCommand(Command command) {
            if (command != null) {
                for (Commands c : Commands.values()) {
                    if (command == c.onOffType) {
                        return c;
                    }
                }
            }
            return null;
        }
    }

    public RfLinkTempHygroEvent() {
    }

    @Override
    public ThingTypeUID getThingType() {
        return RfLinkBindingConstants.THING_TYPE_OREGONTEMPHYGRO;
    }

    @Override
    public Predicate<RfLinkMessage> eligibleMessageFunction() {
        return (message) -> {
            return !Collections.disjoint(message.getAttributesKeys(), KEYS);
        };
    }

    @Override
    public void initializeFromMessage(RfLinkDeviceConfiguration config, RfLinkMessage message) {
        super.initializeFromMessage(config, message);
        Map<String, String> values = getMessage().getAttributes();
        if (values.containsKey(KEY_TEMPERATURE)) {
            temperature = RfLinkDataParser.parseHexaToSignedDecimal(values.get(KEY_TEMPERATURE));
        }

        if (values.containsKey(KEY_HUMIDITY)) {
            humidity = RfLinkDataParser.parseToInt(values.get(KEY_HUMIDITY));
        }

        if (values.containsKey(KEY_HUMIDITY_STATUS)) {
            switch (Integer.parseInt(values.get(KEY_HUMIDITY_STATUS), 10)) {
                case 0:
                    humidity_status = "NORMAL";
                    break;
                case 1:
                    humidity_status = "COMFORT";
                    break;
                case 2:
                    humidity_status = "DRY";
                    break;
                case 3:
                    humidity_status = "WET";
                    break;
                default:
                    humidity_status = "UNKNOWN";
                    break;
            }
        }

        if (values.containsKey(KEY_BATTERY)) {
            try {
                battery_status = Commands.fromString(values.get(KEY_BATTERY));
                if (battery_status == null) {
                    throw new RfLinkException("Can't convert " + values.get(KEY_BATTERY) + " to Switch Command");
                }
            } catch (Exception e) {
                battery_status = Commands.UNKNOWN;
            }
        }
    }

    @Override
    public Map<String, State> getStates() {
        Map<String, State> map = new HashMap<>();
        map.put(RfLinkBindingConstants.CHANNEL_OBSERVATION_TIME, new DateTimeType(Calendar.getInstance()));
        map.put(RfLinkBindingConstants.CHANNEL_TEMPERATURE, new DecimalType(this.temperature));
        map.put(RfLinkBindingConstants.CHANNEL_HUMIDITY, new DecimalType(this.humidity));
        map.put(RfLinkBindingConstants.CHANNEL_HUMIDITY_STATUS, new StringType(this.humidity_status));
        if (this.battery_status.getOnOffType() != null) {
            map.put(RfLinkBindingConstants.CHANNEL_LOW_BATTERY, this.battery_status.getOnOffType());
        }
        return map;
    }

    @Override
    public String toString() {
        String str = "";
        str += super.toString();
        str += ", temperature = " + temperature;
        str += ", humidity = " + humidity;
        str += ", humidity status = " + humidity_status;
        str += ", low battery status = " + battery_status;
        return str;
    }
}
