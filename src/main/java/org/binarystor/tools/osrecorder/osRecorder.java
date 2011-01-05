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

package org.binarystor.tools.osrecorder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.StringWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.sql.*;
import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;

import org.binarystor.tools.osrecorder.freebsd.FreeBSDRecorder;
import org.binarystor.tools.osrecorder.windows.WindowsRecorder;

import org.ho.yaml.*;
import org.ho.yaml.exception.YamlException;

import org.incava.util.diff.*;

import org.kohsuke.args4j.*;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;

import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;

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
    public static String version = "0.1";

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

    private void sendXMPP(String username, String password, String hostname, List<String> recipients, String text) {
        XMPPConnection connection = new XMPPConnection(hostname);
        try {
            connection.connect();
            connection.login(username, password);
            ChatManager chatmanager = connection.getChatManager();

            org.jivesoftware.smack.packet.Message message;

            for (String recipient : recipients) {
                Chat chat = chatmanager.createChat(recipient, new MessageListener() {

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
        } catch (XMPPException e) {
            System.out.println(e.getMessage());
        }
    }

    public static void addDir(SVNRepository readRepository, SVNRepository writeRepository, ISVNEditor editor, String dirPath) throws SVNException {
        SVNNodeKind nodeKind = readRepository.checkPath(dirPath, -1);
        if (nodeKind == SVNNodeKind.NONE) {
            editor.addDir(dirPath, null, -1);
            editor.closeDir();
        }
    }

    /*
     * This method performs commiting an addition of a  directory  containing  a
     * file.
     */
    public static void addFile(SVNRepository readRepository, SVNRepository writeRepository, ISVNEditor editor, String filePath, byte[] data) throws SVNException {
        SVNNodeKind nodeKind = readRepository.checkPath(filePath, -1);
        if (nodeKind == SVNNodeKind.NONE) {
            editor.addFile(filePath, null, -1);

            editor.applyTextDelta(filePath, null);

            SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
            String checksum = deltaGenerator.sendDelta(filePath, new ByteArrayInputStream(data), editor, true);

            editor.closeFile(filePath, checksum);
        }

    }

    /*
     * This method performs committing file modifications.
     */
    public static void modifyFile(SVNRepository repository, ISVNEditor editor, String dirPath,
            String filePath, byte[] oldData, byte[] newData, String comment) throws SVNException {

        editor.openDir(dirPath, -1);

        editor.openFile(filePath, -1);

        editor.applyTextDelta(filePath, null);

        SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
        String checksum = deltaGenerator.sendDelta(filePath, new ByteArrayInputStream(oldData), 0, new ByteArrayInputStream(newData), editor, true);

        editor.closeFile(filePath, checksum);

        editor.closeDir();

    }

    /*
     * Initializes the library to work with a repository via
     * different protocols.
     */
    private static void setupLibrary() {
        /*
         * For using over http:// and https://
         */
        DAVRepositoryFactory.setup();
        /*
         * For using over svn:// and svn+xxx://
         */
        SVNRepositoryFactoryImpl.setup();

        /*
         * For using over file:///
         */
        FSRepositoryFactory.setup();
    }

    /**
     * Main entry point for dbRecorder when run from command line
     *
     * @param  repository  Subversion repository to initialise.
     */
    private SVNRepository[] initialiseRepository(SVNURL url) {
        SVNRepository readRepository = null;
        SVNRepository writeRepository = null;
        try {
            //Try to create new repository if connection fails file:///
            readRepository = SVNRepositoryFactory.create(url);
            writeRepository = SVNRepositoryFactory.create(url);
            try {
                readRepository.testConnection();
                writeRepository.testConnection();
            } catch (SVNException svne) {
                if (svne.getErrorMessage().getErrorCode().equals(SVNErrorCode.RA_LOCAL_REPOS_OPEN_FAILED)) {
                    if (verbose) {
                        System.out.println("SVN Connection failed. Attempting to create local repository using path " + url.getPath());
                    }
                    SVNURL newurl = SVNRepositoryFactory.createLocalRepository(new File(url.getPath()), true, false);
                    writeRepository = SVNRepositoryFactory.create(newurl);
                    readRepository = SVNRepositoryFactory.create(newurl);
                }
            }
            SVNNodeKind nodeKind = writeRepository.checkPath("", -1);
            /*
             * Checks  up  if the current path really corresponds to a directory. If
             * it doesn't, the program exits. SVNNodeKind is that one who says  what
             * is located at a path in a revision.
             */
            if (nodeKind == SVNNodeKind.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "No entry at URL ''{0}''", url);
                throw new SVNException(err);
            } else if (nodeKind == SVNNodeKind.FILE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Entry at URL ''{0}'' is a file while directory was expected", url);
                throw new SVNException(err);
            }

            if ((writeRepository.checkPath("osRecorder/", -1) == SVNNodeKind.NONE) ||
                    (writeRepository.checkPath("osRecorder/FreeBSD", -1) == SVNNodeKind.NONE) ||
                    (writeRepository.checkPath("osRecorder/Windows", -1) == SVNNodeKind.NONE)) {
                ISVNEditor editor = writeRepository.getCommitEditor("Initialise dbRecorder Repository", null);
                editor.openRoot(-1);
                addDir(readRepository, writeRepository, editor, "osRecorder");
                addDir(readRepository, writeRepository, editor, "osRecorder/FreeBSD");
                addDir(readRepository, writeRepository, editor, "osRecorder/Windows");
                editor.closeEdit();
            }



        } catch (SVNException e) {
        }
        SVNRepository[] repositories = new SVNRepository[2];
        repositories[0] = writeRepository;
        repositories[1] = readRepository;
        return repositories;
    }

    public static String printDiffs(String[] currentTable, String[] reposTable) {
        String unifiedDiff = "";
        List diffs = (new Diff(currentTable, reposTable)).diff();
        Iterator it = diffs.iterator();
        while (it.hasNext()) {
            Difference diff = (Difference) it.next();
            int delStart = diff.getDeletedStart();
            int delEnd = diff.getDeletedEnd();
            int addStart = diff.getAddedStart();
            int addEnd = diff.getAddedEnd();
            String from = toString(delStart, delEnd);
            String to = toString(addStart, addEnd);
            String type = delEnd != Difference.NONE && addEnd != Difference.NONE ? "c" : (delEnd == Difference.NONE ? "a" : "d");

            unifiedDiff += "@@ -" + to + " +" + from + " @@\n";

            if (addEnd != Difference.NONE) {
                for (int lnum = addStart; lnum <= addEnd; ++lnum) {
                    unifiedDiff += "-" + " " + reposTable[lnum] + "\n";
                }
            }
            if (delEnd != Difference.NONE) {
                for (int lnum = delStart; lnum <= delEnd; ++lnum) {
                    unifiedDiff += "+" + " " + currentTable[lnum] + "\n";
                }
                if (addEnd != Difference.NONE) {
                    unifiedDiff += "\n";
                }
            }
        }
        return unifiedDiff;
    }

    private static String toString(int start, int end) {
        // adjusted, because file lines are one-indexed, not zero.

        StringBuffer buf = new StringBuffer();

        // match the line numbering from diff(1):
        buf.append(end == Difference.NONE ? start : (1 + start));

        if (end != Difference.NONE && start != end) {
            buf.append(",").append(1 + end);
        }
        return buf.toString();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        new osRecorder().doMain(args);
    }

    /**
     * Parse command line arguments and invoke dbRecorder
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

        } catch (CmdLineException e) {
            if (e.getMessage().equalsIgnoreCase("Print Help")) {
                System.err.println("osRecorder.java Ver " + version + "\nThis software comes with ABSOLUTELY NO WARRANTY. This is free software,\nand you are welcome to modify and redistribute it under the BSD license" + "\n\n" + usage);
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
            Map config = (Map) Yaml.load(new FileReader(osRecorderConfig.replace('/', File.separatorChar)));

            //Create the SVN repository objects
            setupLibrary();
            Map<String, SVNRepository[]> svnRepositories = new HashMap<String, SVNRepository[]>();
            List<Map> repositories = (ArrayList) config.get("Subversion");
            if (repositories == null) {
                System.err.println("No Repositories found - please check config file.");
                System.exit(1);
            }
            for (Map repository : repositories) {
                SVNURL url = SVNURL.parseURIEncoded((String) repository.get("URL"));
                svnRepositories.put((String) repository.get("Name"), initialiseRepository(url));
            }

            //Process FreeBSD
            if (config.containsKey("FreeBSD")) {
                FreeBSDRecorder freebsdRecorder = new FreeBSDRecorder(svnRepositories, config);
                notifyMessage += freebsdRecorder.record();
            }

            //Notify if any changes found
            if (!notifyMessage.equals("")) {
                List<Map> notifications = (ArrayList) config.get("Notification");
                for (Map notification : notifications) {
                    //SMTP Notification
                    if (notification.get("Method").toString().equals("smtp")) {
                        //Set the host smtp address
                        Properties props = new Properties();
                        props.put("mail.smtp.host", (String) notification.get("Server"));
                        List<String> recipients = new ArrayList();
                        if (notification.get("To") instanceof String) {
                            recipients.add((String) notification.get("To"));
                        }
                        if (notification.get("To") instanceof ArrayList) {
                            recipients = (ArrayList) notification.get("To");
                        }
                        sendMail(props, recipients, "osRecorder notification", notifyMessage, (String) notification.get("From"));
                    }
                    //XMPP Notification
                    if (notification.get("Method").toString().equals("xmpp")) {
                        List<String> recipients = new ArrayList();
                        if (notification.get("To") instanceof String) {
                            recipients.add((String) notification.get("To"));
                        }
                        if (notification.get("To") instanceof ArrayList) {
                            recipients = (ArrayList) notification.get("To");
                        }
                        sendXMPP((String) notification.get("Username"), (String) notification.get("Password"), (String) notification.get("Server"), recipients, notifyMessage);
                    }
                }
            }

        } catch (YamlException ye) {
            System.out.println("Error occured while trying to parse the config file (" + osRecorderConfig + "):\n\n" + "  " + ye.getMessage() + "\n\nCommon mistakes include missing braces on array definitions e.g.\n  To: test@osrecorder.org,test2@osrecorder.org\nShould be\n  To: [test@dosrecorder.org,test2@osrecorder.org]");
        } catch (IOException ioe) {
            System.out.println(ioe.getMessage());
            System.out.println("\n" + usage);
        } catch (MessagingException me) {
            System.out.println("Failed to send e-mail. Error: " + me.getMessage());
        } catch (SVNException svne) {
            System.out.println(svne.getErrorMessage());
        }
    }
}
