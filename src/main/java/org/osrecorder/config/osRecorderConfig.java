/**
 * Copyright (c) 2008-2011 Wave2 Limited. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of Wave2 Limited nor the names of its contributors may be
 * used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.osrecorder.config;

/**
 *
 * @author Alan Snelson
 */
public class osRecorderConfig {

    private NotificationMethodConfig[] notification;
    private FileSetConfig[] fileset;
    private String datadir;
    public String configError = "";

    public NotificationMethodConfig getNotification(int index) {
        return this.notification[index];
    }

    public NotificationMethodConfig[] getNotification() {
        return this.notification;
    }

    public void setNotification(int index, NotificationMethodConfig value) {
        this.notification[index] = value;
    }

    public void setNotification(NotificationMethodConfig[] value) {
        this.notification = value;
    }

    public FileSetConfig getFileset(int index) {
        return this.fileset[index];
    }

    public FileSetConfig[] getFileset() {
        return this.fileset;
    }

    public void setFileset(int index, FileSetConfig value) {
        this.fileset[index] = value;
    }

    public void setFileset(FileSetConfig[] value) {
        this.fileset = value;
    }

    public String getDatadir() {
        return this.datadir;
    }

    public void setDatadir(String value) {
        this.datadir = value;
    }

    public boolean checkConfig() {
        //Check data directory
        if (this.datadir == null) {
            configError = "No datadir found - please check config file.";
            return false;
        }
        //Check Notification methods
        if (this.notification == null) {
            configError = "No notification methods found - please check config file.";
            return false;
        } else {
            for (NotificationMethodConfig notify : this.notification) {
                if (notify.getMethod() == null) {
                    configError = "Notification method required. e.g. - method: smtp";
                    return false;
                } else {
                    if (notify.getRecipients() == null) {
                        configError = "Notification recipients required. e.g. recipients: [me@mycompany.com]";
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
