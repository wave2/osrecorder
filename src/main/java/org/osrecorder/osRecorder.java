/**
 * Copyright (c) 2008-2012 Wave2 Limited. All rights reserved.
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
package org.osrecorder;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;

import org.osrecorder.config.osRecorderConfig;
import org.osrecorder.config.NotificationMethodConfig;

import org.incava.util.diff.*;

import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;

import org.kohsuke.args4j.*;

import org.yaml.snakeyaml.Yaml;

import org.osrecorder.config.FileSetConfig;


/**
 *
 * @author Alan Snelson
 */
public class osRecorder {

    //Command Line Arguments
    @Option(name = "--help")
    private boolean help;
    @Option(name = "-c", usage = "Path to osRecorder Config File")
    private String osRecorderConfig;
    @Option(name = "-v")
    public static boolean verbose;
    // receives other command line parameters than options
    @Argument
    private List<String> arguments = new ArrayList<String>();
    private String notifyMessage = "";
    private Properties properties;
    private String datadir = "";

    /**
     * Constructor
     *
     */
    public osRecorder() {
        //Load properties
        properties = new Properties();
        try {
            properties.load(getClass().getResourceAsStream("/application.properties"));
        }
        catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    /**
     * Send e-mail notification
     *
     * @param  props    Properties
     * @param  recipients   E-Mail Recipients
     * @param  subject  E-Mail Subject
     * @param  message  Notifiation message
     * @param  from E-Mail From address
     */
    private void sendMail(Properties props, List<String> recipients, String subject, String message, String from) throws MessagingException {
        boolean debug = false;

        // create some properties and get the default Session
        javax.mail.Session session = javax.mail.Session.getDefaultInstance(props, null);
        session.setDebug(debug);

        // create a message
        javax.mail.Message msg = new MimeMessage(session);

        // set the from and to address
        InternetAddress addressFrom = new InternetAddress(from);
        msg.setFrom(addressFrom);

        InternetAddress[] addressTo = new InternetAddress[recipients.size()];

        int i = 0;
        for (String recipient : recipients) {
            addressTo[i] = new InternetAddress(recipient);
            i++;
        }
        msg.setRecipients(javax.mail.Message.RecipientType.TO, addressTo);


        // Optional : You can also set your custom headers in the Email if you Want
        msg.addHeader("MyHeaderName", "myHeaderValue");

        // Setting the Subject and Content Type
        msg.setSubject(subject);
        msg.setContent(message, "text/plain");

        if (verbose) {
            System.out.println("\n-- SMTP Notification --");
            System.out.println("Sending E-Mail using SMTP server " + props.get("mail.smtp.host"));
            System.out.println("Recipients:");
            for (String recipient : recipients) {
                System.out.println("    " + recipient);
            }
        }
        Transport.send(msg);
    }

    /**
     * Send XMPP notification
     *
     * @param  username XMPP server username
     * @param  password XMPP server password
     * @param  hostname XMPP Server
     * @param  recipients Recipients
     * @param  text Notification message
     */
    private void sendXMPP(String username, String password, String hostname, List<String> recipients, String text) {
        XMPPConnection connection = new XMPPConnection(hostname);
        try {
            connection.connect();
            connection.login(username, password);
            ChatManager chatmanager = connection.getChatManager();

            org.jivesoftware.smack.packet.Message message;

            for (String recipient : recipients) {
                Chat chat = chatmanager.createChat(recipient, new MessageListener() {

                    @Override
                    public void processMessage(Chat chat, org.jivesoftware.smack.packet.Message message) {
                        //System.out.println("Received message: " + message);
                    }
                });
                chat.sendMessage(text);
            }
            if (verbose) {
                System.out.println("\n-- XMPP Notification --");
                System.out.println("Sending XMPP message using XMPP server " + hostname);
                System.out.println("Recipients:");
                for (String recipient : recipients) {
                    System.out.println("    " + recipient);
                }
            }
        }
        catch (XMPPException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Return diff output
     *
     * @param  newString New string to diff
     * @param  oldString Old string to compare against
     * @return  Diff output
     */
    public static String printDiffs(String[] newString, String[] oldString) {
        String unifiedDiff = "";
        List diffs = ( new Diff(newString, oldString) ).diff();
        Iterator it = diffs.iterator();
        while (it.hasNext()) {
            Difference diff = (Difference) it.next();
            int delStart = diff.getDeletedStart();
            int delEnd = diff.getDeletedEnd();
            int addStart = diff.getAddedStart();
            int addEnd = diff.getAddedEnd();
            String from = toString(delStart, delEnd);
            String to = toString(addStart, addEnd);
            String type = delEnd != Difference.NONE && addEnd != Difference.NONE ? "c" : ( delEnd == Difference.NONE ? "a" : "d" );

            unifiedDiff += "@@ -" + to + " +" + from + " @@\n";

            if (addEnd != Difference.NONE) {
                for (int lnum = addStart; lnum <= addEnd; ++lnum) {
                    unifiedDiff += "-" + " " + oldString[lnum] + "\n";
                }
            }
            if (delEnd != Difference.NONE) {
                for (int lnum = delStart; lnum <= delEnd; ++lnum) {
                    unifiedDiff += "+" + " " + newString[lnum] + "\n";
                }
                if (addEnd != Difference.NONE) {
                    unifiedDiff += "\n";
                }
            }
        }
        return unifiedDiff;
    }

    /**
     * Get application version
     *
     * @return Application version
     */
    public String getVersion() {
        return properties.getProperty("application.version");
    }

    private static String toString(int start, int end) {
        // adjusted, because file lines are one-indexed, not zero.

        StringBuilder buf = new StringBuilder();

        // match the line numbering from diff(1):
        buf.append(end == Difference.NONE ? start : ( 1 + start ));

        if (end != Difference.NONE && start != end) {
            buf.append(",").append(1 + end);
        }
        return buf.toString();
    }

    /**
     * Main entry point
     * 
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        new osRecorder().doMain(args);
    }

    /**
     * Parse command line arguments and invoke osRecorder
     *
     * @param  args  Command line arguments
     */
    public void doMain(String[] args) {

        String usage = "Usage: java -jar osRecorder.jar [-c Path to config.yml] [-v]\nOptions:\n    -c  Path to Config.yml\n    -v  Generate verbose output on standard output";
        CmdLineParser parser = new CmdLineParser(this);

        // if you have a wider console, you could increase the value;
        // here 80 is also the default
        parser.setUsageWidth(80);

        try {
            // parse the arguments.
            parser.parseArgument(args);

            if (help) {
                throw new CmdLineException("Print Help");
            }

            // after parsing arguments, you should check
            // if enough arguments are given.
            //if( arguments.isEmpty() )
            //throw new CmdLineException("No argument is given");

        }
        catch (CmdLineException e) {
            if (e.getMessage().equalsIgnoreCase("Print Help")) {
                System.err.println("osRecorder.java Ver " + getVersion() + "\nThis software comes with ABSOLUTELY NO WARRANTY. This is free software,\nand you are welcome to modify and redistribute it under the BSD license" + "\n\n" + usage);
                return;
            }
            // if there's a problem in the command line,
            // you'll get this exception. this will report
            // an error message.
            System.err.println(e.getMessage());
            // print usage.
            System.err.println(usage);
            return;
        }

        //Do we have a config file? if not try and open the default config.yml
        if (osRecorderConfig == null) {
            osRecorderConfig = "config.yml";
        }

        try {
            //Parse YAML config file
            Yaml yaml = new Yaml();
            osRecorderConfig config = yaml.loadAs ( new FileReader(osRecorderConfig.replace('/', File.separatorChar)), osRecorderConfig.class );
            if (!config.checkConfig()) {
                System.err.println(config.configError);
                System.exit(1);
            }
            datadir = config.getDatadir();
            Repository gitRepo = new GitRepo(datadir);

            //Process FileSets
            FileSetConfig[] fileSetConfigs = config.getFileset();
            for (FileSetConfig fileSetConf : fileSetConfigs) {
                FileSet fileSet = new FileSet(gitRepo);
                String fileSetResult = fileSet.processFileSet(fileSetConf);
                if (!fileSetResult.equals("")) {
                    notifyMessage += fileSetResult;
                }
            }


            //Notify if any changes found
            if (!notifyMessage.equals("")) {
                NotificationMethodConfig[] notifications = config.getNotification();
                for (NotificationMethodConfig notification : notifications) {
                    //SMTP Notification
                    if (notification.getMethod().equals("smtp")) {
                        //Set the host smtp address
                        Properties props = new Properties();
                        props.put("mail.smtp.host", notification.getServer());
                        List<String> recipients = new ArrayList<String>();
                        for (String recipient : notification.getRecipients()) {
                            recipients.add(recipient);
                        }
                        sendMail(props, recipients, "osRecorder notification", notifyMessage, notification.getSender());
                    }
                    //XMPP Notification
                    if (notification.getMethod().equals("xmpp")) {
                        List<String> recipients = new ArrayList<String>();
                        recipients.addAll(Arrays.asList(notification.getRecipients()));
                        sendXMPP(notification.getUsername(), notification.getPassword(), notification.getServer(), recipients, notifyMessage);
                    }
                }
            }
        }
        catch (IOException ioe) {
            System.out.println(ioe.getMessage());
            System.out.println("\n" + usage);
        }
        catch (MessagingException me) {
            System.out.println("Failed to send e-mail. Error: " + me.getMessage());
        }
    }
}
