/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui;

import com.frostwire.android.core.FileDescriptor;
import com.frostwire.localpeer.Finger;
import com.frostwire.localpeer.LocalPeer;

import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Peer {

    private String address;

    /**
     * 16 bytes (128bit - UUID identifier letting us know who is the sender)
     */
    private String nickname;
    private String clientVersion;

    private int hashCode = -1;

    private String key;

    public Peer(LocalPeer p) {
        this.key = p.address + ":" + p.port;
        this.address = p.address;

        this.nickname = p.nickname;
        this.clientVersion = p.clientVersion;

        this.hashCode = key.hashCode();
    }

    public String getAddress() {
        return address;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public Finger finger() {
        return Librarian.instance().finger();
    }

    public List<FileDescriptor> browse(byte fileType) {
        return Librarian.instance().getFiles(fileType, 0, Integer.MAX_VALUE, false);
    }

    @Override
    public String toString() {
        return "Peer(" + nickname + "@" + (address != null ? address : "unknown") + ", v:" + clientVersion + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof Peer)) {
            return false;
        }

        return hashCode() == ((Peer) o).hashCode();
    }

    @Override
    public int hashCode() {
        return this.hashCode != -1 ? this.hashCode : super.hashCode();
    }

    public String getKey() {
        return key;
    }
}
