/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rflink.messages;

/**
 * RFLink data parser
 *
 * @author Cyril Cauchois - Initial contribution
 */
public class RfLinkDataParser {

    public static final int BASE_TEN = 10;
    public static final int BASE_HEXA = 16;

    public static String cleanString(String value) {
        return value.replaceAll("[^A-Za-z0-9_-]", "");
    }

    public static int parseToInt(String value) {
        return Integer.parseInt(value, BASE_TEN);
    }

    public static int parseHexaToUnsignedInt(String value) {
        return Integer.parseInt(value, BASE_HEXA);
    }

    public static int parseHexaToSignedInt(String value) {
        int iValue = parseHexaToUnsignedInt(value);
        if ((iValue & 0x8000) > 0) {
            return (iValue & 0x7FFF) / -1;
        } else {
            return (iValue & 0x7FFF);
        }
    }

    public static double parseHexaToSignedDecimal(String value) {
        return parseHexaToSignedInt(value) / 10.0d;
    }

    public static double parseHexaToUnsignedDecimal(String value) {
        return parseHexaToUnsignedInt(value) / 10.0d;
    }

    /**
     * Parse Wind Direction
     *
     * @param value Wind direction (integer value from 0-15) reflecting 0-360 degrees in 22.5 degree steps
     * @return the wind direction in degrees
     */
    public static double parseIntTo360Direction(String value) {
        return parseToInt(value) * 22.5d;
    }

}
