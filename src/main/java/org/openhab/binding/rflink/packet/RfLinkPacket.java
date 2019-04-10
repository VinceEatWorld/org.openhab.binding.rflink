/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rflink.packet;

/**
 * RfLink packet Wrapper
 *
 * @author cartemere - Initial contribution
 */
public class RfLinkPacket {

    private RfLinkPacketType type;
    private String packet;

    public static String PING = "10;PING;";

    public RfLinkPacket(RfLinkPacketType type, String packet) {
        super();
        this.type = type;
        this.packet = packet;
    }

    public RfLinkPacketType getType() {
        return type;
    }

    public String getPacket() {
        return packet;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((packet == null) ? 0 : packet.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        RfLinkPacket other = (RfLinkPacket) obj;
        if (packet == null) {
            if (other.packet != null) {
                return false;
            }
        } else if (!packet.equals(other.packet)) {
            return false;
        }
        if (type != other.type) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "RfLinkPacket [" + type + "=" + packet + "]";
    }

}
